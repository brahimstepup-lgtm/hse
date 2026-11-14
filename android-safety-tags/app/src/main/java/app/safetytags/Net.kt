package app.safetytags

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** POST JSON vers un script Google Apps Script (gère la redirection googleusercontent). */
object Http {
    suspend fun postScript(url: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        var conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.instanceFollowRedirects = false
        conn.connectTimeout = 30_000
        conn.readTimeout = 180_000
        conn.setRequestProperty("Content-Type", "text/plain;charset=utf-8")
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        var code = conn.responseCode
        var hops = 0
        while (code in 300..399 && hops < 5) {
            val loc = conn.getHeaderField("Location") ?: break
            conn.disconnect()
            conn = URL(loc).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.readTimeout = 180_000
            code = conn.responseCode
            hops++
        }
        val stream = if (code < 400) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        conn.disconnect()
        val json = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw IOException("Réponse inattendue du serveur (code $code).")
        }
        if (!json.optBoolean("ok")) throw IOException(json.optString("error", "Erreur du serveur"))
        json
    }
}

/** Client du script Google Apps Script relié au Sheet. */
class Api(private val prefs: Prefs) {
    suspend fun call(action: String, body: JSONObject = JSONObject()): JSONObject {
        val url = prefs.url
        if (!url.startsWith("https://")) throw IOException(tr("Renseignez l’URL du script dans Réglages."))
        body.put("action", action)
        body.put("token", prefs.token)
        return Http.postScript(url, body)
    }
}

/** Serveur Safety Tags Pro : statut Pro et IA (la clé IA reste sur le serveur). */
object ProApi {
    val configured get() = BuildConfig.PRO_API_URL.startsWith("https://")

    suspend fun status(idToken: String): Pair<Boolean, String> {
        val d = Http.postScript(BuildConfig.PRO_API_URL, JSONObject().put("action", "status").put("idToken", idToken)).getJSONObject("data")
        return d.optBoolean("pro") to d.optString("email")
    }

    suspend fun ai(idToken: String, prompt: String, imageB64: String?): String {
        val body = JSONObject().put("action", "ai").put("idToken", idToken).put("prompt", prompt)
        if (imageB64 != null) body.put("image", imageB64)
        return Http.postScript(BuildConfig.PRO_API_URL, body).getJSONObject("data").optString("text")
    }
}

/** Remplissage automatique d'un tag par l'IA (Pro). */
object Ai {
    fun prompt(note: String, cfg: Config): String {
        fun choices(list: List<String>) =
            if (list.isEmpty()) "texte libre" else "de préférence un de ${JSONArray(list)} (ou une nouvelle valeur si aucun ne convient)"
        val rules = if (cfg.regles.isEmpty()) "(aucune)" else
            cfg.regles.joinToString("\n") { "- service ${it.service} / zone ${it.zone} → ${it.responsable}" }
        return """
Tu es animateur HSE dans une entreprise. À partir de la photo d'anomalie et de la note, rédige un Safety Tag en français, concis et professionnel.
Note : "${note.ifBlank { "(aucune)" }}"
Règles d'affectation connues :
$rules
Respecte les informations de la note (lieu, zone, responsable). N'invente pas de lieu précis ni de nom de personne.
Rédige toutes les valeurs texte en ${L10n.aiLanguage()} (sauf les valeurs choisies dans les listes ci-dessous).
Réponds UNIQUEMENT avec un objet JSON :
{"type": ${choices(cfg.types)},
 "gravite": "Élevée"|"Moyenne"|"Faible",
 "zone": ${choices(cfg.zones)} ou "" si inconnue,
 "service": ${choices(cfg.services)} ou "" si inconnu,
 "responsable": "" sauf si la note nomme le responsable,
 "emplacement": string, "description": string (1 phrase), "risque": string (1 phrase),
 "action_urgente": string (1 phrase), "proposition": string (1 à 2 phrases)}
""".trimIndent()
    }

    fun parse(answer: String): JSONObject {
        val clean = answer.replace(Regex("(?s)<think>.*?</think>"), "")
        val start = clean.indexOf('{')
        val end = clean.lastIndexOf('}')
        if (start < 0 || end <= start) throw IOException("IA : réponse illisible")
        return JSONObject(clean.substring(start, end + 1))
    }
}

object Img {
    fun load(ctx: Context, uri: Uri, max: Int = 1600): Bitmap? {
        val cr = ctx.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= max || bounds.outHeight / (sample * 2) >= max) sample *= 2
        val raw = cr.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val rot = runCatching {
            cr.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)
        val scaled = scaleMax(raw, max)
        return if (rot == 0f) scaled else
            Bitmap.createBitmap(scaled, 0, 0, scaled.width, scaled.height, Matrix().apply { postRotate(rot) }, true)
    }

    fun scaleMax(b: Bitmap, max: Int): Bitmap {
        val r = max.toFloat() / maxOf(b.width, b.height)
        return if (r >= 1f) b else Bitmap.createScaledBitmap(b, (b.width * r).toInt(), (b.height * r).toInt(), true)
    }

    fun toBase64(b: Bitmap, quality: Int = 85): String {
        val bos = ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.JPEG, quality, bos)
        return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
    }

    fun toBase64Png(b: Bitmap): String {
        val bos = ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.PNG, 100, bos)
        return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
    }

    fun fromBase64(s: String): Bitmap? = runCatching {
        val bytes = Base64.decode(s, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()

    fun newCaptureUri(ctx: Context): Uri {
        val dir = File(ctx.cacheDir, "photos").apply { mkdirs() }
        val f = File(dir, "photo_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
    }
}

object Share {
    private const val OUTLOOK = "com.microsoft.office.outlook"

    fun savePng(ctx: Context, bmp: Bitmap, name: String): Uri {
        val dir = File(ctx.cacheDir, "fiches").apply { mkdirs() }
        val f = File(dir, name)
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
    }

    const val DEFAULT_MAIL_APP = OUTLOOK
    const val ASK = "ask"

    private fun installed(ctx: Context, pkg: String) = runCatching { ctx.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)

    /** Applications d'email installées : (nom affiché, package). */
    fun mailApps(ctx: Context): List<Pair<String, String>> {
        val pm = ctx.packageManager
        return pm.queryIntentActivities(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")), 0)
            .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
            .distinctBy { it.second }
            .sortedBy { it.first.lowercase() }
    }

    fun appLabel(ctx: Context, pkg: String): String = when (pkg) {
        ASK -> tr("Demander à chaque fois")
        else -> runCatching { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString() }
            .getOrDefault(if (pkg == OUTLOOK) "Outlook" else pkg)
    }

    /** Ouvre Outlook (ou le choix d'application) avec destinataires, objet, texte et fiche jointe. */
    /** Ouvre l'application d'email choisie dans Réglages (Outlook par défaut). */
    fun email(ctx: Context, to: List<String>, cc: List<String>, subject: String, body: String, attach: Uri?, app: String = DEFAULT_MAIL_APP) {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = if (attach != null) "image/png" else "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, to.toTypedArray())
            putExtra(Intent.EXTRA_CC, cc.toTypedArray())
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            if (attach != null) {
                putExtra(Intent.EXTRA_STREAM, attach)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        if (app != ASK && installed(ctx, app)) {
            i.setPackage(app)
            ctx.startActivity(i)
        } else {
            ctx.startActivity(Intent.createChooser(i, tr("Envoyer l’email")))
        }
    }

    fun image(ctx: Context, uri: Uri, text: String) {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(i, tr("Partager la fiche")))
    }
}
