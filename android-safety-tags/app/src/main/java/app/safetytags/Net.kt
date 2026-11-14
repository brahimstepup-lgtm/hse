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

/** Client du script Google Apps Script relié au Sheet. */
class Api(private val prefs: Prefs) {

    suspend fun call(action: String, body: JSONObject = JSONObject()): JSONObject = withContext(Dispatchers.IO) {
        val url = prefs.url
        if (!url.startsWith("https://")) throw IOException("Renseignez l’URL du script dans Réglages.")
        body.put("action", action)
        body.put("token", prefs.token)

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
        // Apps Script répond par une redirection vers googleusercontent.com
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
            throw IOException("Réponse inattendue du script (code $code). Vérifiez l’URL et le déploiement.")
        }
        if (!json.optBoolean("ok")) throw IOException(json.optString("error", "Erreur du script"))
        json
    }
}

/** Remplissage automatique par Claude (clé API Anthropic personnelle, optionnelle). */
object Ai {
    suspend fun fill(
        key: String, model: String, note: String, photo: Bitmap?, cfg: Config
    ): JSONObject = withContext(Dispatchers.IO) {
        fun choices(label: String, list: List<String>) =
            if (list.isEmpty()) "texte libre" else "de préférence un de ${JSONArray(list)} (ou une nouvelle valeur si aucun ne convient)"
        val rules = if (cfg.regles.isEmpty()) "(aucune)" else
            cfg.regles.joinToString("\n") { "- service ${it.service} / zone ${it.zone} → ${it.responsable}" }
        val prompt = """
Tu es animateur HSE dans une entreprise. À partir de la photo d'anomalie et de la note, rédige un Safety Tag en français, concis et professionnel.
Note : "${note.ifBlank { "(aucune)" }}"
Règles d'affectation connues :
$rules
Respecte les informations de la note (lieu, zone, responsable). N'invente pas de lieu précis ni de nom de personne.
Réponds UNIQUEMENT avec un objet JSON :
{"type": ${choices("type", cfg.types)},
 "gravite": "Élevée"|"Moyenne"|"Faible",
 "zone": ${choices("zone", cfg.zones)} ou "" si inconnue,
 "service": ${choices("service", cfg.services)} ou "" si inconnu,
 "responsable": "" sauf si la note nomme le responsable,
 "emplacement": string, "description": string (1 phrase), "risque": string (1 phrase),
 "action_urgente": string (1 phrase), "proposition": string (1 à 2 phrases)}
""".trimIndent()

        val content = JSONArray()
        if (photo != null) {
            content.put(JSONObject().put("type", "image").put("source", JSONObject()
                .put("type", "base64").put("media_type", "image/jpeg")
                .put("data", Img.toBase64(Img.scaleMax(photo, 1280), 80))))
        }
        content.put(JSONObject().put("type", "text").put("text", prompt))
        val body = JSONObject().put("model", model).put("max_tokens", 1200)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))

        val conn = URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 30_000
        conn.readTimeout = 120_000
        conn.setRequestProperty("content-type", "application/json")
        conn.setRequestProperty("x-api-key", key)
        conn.setRequestProperty("anthropic-version", "2023-06-01")
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val text = (if (code < 400) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
        conn.disconnect()
        if (code >= 400) {
            val msg = runCatching { JSONObject(text).getJSONObject("error").getString("message") }.getOrDefault(text.take(200))
            throw IOException("IA : $msg")
        }
        val answer = JSONObject(text).getJSONArray("content").let { arr ->
            (0 until arr.length()).map { arr.getJSONObject(it) }.filter { it.optString("type") == "text" }
                .joinToString("") { it.optString("text") }
        }
        val start = answer.indexOf('{')
        val end = answer.lastIndexOf('}')
        if (start < 0 || end <= start) throw IOException("IA : réponse illisible")
        JSONObject(answer.substring(start, end + 1))
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

    private fun hasOutlook(ctx: Context) = runCatching { ctx.packageManager.getPackageInfo(OUTLOOK, 0); true }.getOrDefault(false)

    /** Ouvre Outlook (ou le choix d'application) avec destinataires, objet, texte et fiche jointe. */
    fun email(ctx: Context, to: List<String>, cc: List<String>, subject: String, body: String, attach: Uri?) {
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
        if (hasOutlook(ctx)) {
            i.setPackage(OUTLOOK)
            ctx.startActivity(i)
        } else {
            ctx.startActivity(Intent.createChooser(i, "Envoyer l’email"))
        }
    }

    fun image(ctx: Context, uri: Uri, text: String) {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(i, "Partager la fiche"))
    }
}
