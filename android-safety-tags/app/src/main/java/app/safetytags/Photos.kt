package app.safetytags

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.common.api.Scope
import com.google.android.gms.auth.api.identity.Identity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Copie des photos dans la Galerie du téléphone (album « Safety Tags »). */
object Gallery {
    const val ALBUM = "Safety Tags"

    fun save(ctx: Context, bmp: Bitmap, name: String): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + ALBUM)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val r = ctx.contentResolver
            val uri = r.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            r.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0)
            r.update(uri, values, null, null)
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ALBUM).apply { mkdirs() }
            val f = File(dir, name)
            FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            android.media.MediaScannerConnection.scanFile(ctx, arrayOf(f.absolutePath), arrayOf("image/jpeg"), null)
        }
        true
    }.getOrDefault(false)
}

/** Photos dans le Google Drive de l'utilisateur (accès limité aux fichiers créés par l'application). */
object DrivePhotos {
    private const val SCOPE = "https://www.googleapis.com/auth/drive.file"
    const val FOLDER = "Safety Tags – photos"
    private const val API = "https://www.googleapis.com/drive/v3/files"

    var launcher: ActivityResultLauncher<IntentSenderRequest>? = null
    private var pending: CompletableDeferred<String?>? = null

    /** Jeton d'accès Drive ; affiche l'écran d'autorisation Google la première fois. Null si refusé. */
    suspend fun token(act: Activity): String? {
        val req = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()
        val res = Identity.getAuthorizationClient(act).authorize(req).await()
        if (res.hasResolution()) {
            val l = launcher ?: return null
            val d = CompletableDeferred<String?>()
            pending = d
            l.launch(IntentSenderRequest.Builder(res.pendingIntent!!.intentSender).build())
            return d.await()
        }
        return res.accessToken
    }

    fun onAuthResult(act: Activity, data: Intent?) {
        val t = runCatching { Identity.getAuthorizationClient(act).getAuthorizationResultFromIntent(data).accessToken }.getOrNull()
        pending?.complete(t); pending = null
    }

    private fun conn(url: String, token: String, method: String = "GET"): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 30_000; readTimeout = 120_000
            setRequestProperty("Authorization", "Bearer $token")
        }

    private fun read(c: HttpURLConnection): String {
        val code = c.responseCode
        val text = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
        c.disconnect()
        if (code >= 400) throw IOException("Drive : " + runCatching { JSONObject(text).getJSONObject("error").optString("message") }.getOrDefault("erreur $code"))
        return text
    }

    private fun folderId(token: String, prefs: Prefs): String {
        prefs.driveFolder.takeIf { it.isNotBlank() }?.let { id ->
            val ok = runCatching { JSONObject(read(conn("$API/$id?fields=id,trashed", token))).optBoolean("trashed").not() }.getOrDefault(false)
            if (ok) return id
        }
        val q = URLEncoder.encode("mimeType='application/vnd.google-apps.folder' and name='$FOLDER' and trashed=false", "UTF-8")
        val found = JSONObject(read(conn("$API?q=$q&fields=files(id)", token))).optJSONArray("files")
        val id = if (found != null && found.length() > 0) found.getJSONObject(0).getString("id") else {
            val c = conn("$API?fields=id", token, "POST")
            c.doOutput = true; c.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            c.outputStream.use { it.write(JSONObject().put("name", FOLDER).put("mimeType", "application/vnd.google-apps.folder").toString().toByteArray()) }
            JSONObject(read(c)).getString("id")
        }
        prefs.driveFolder = id
        return id
    }

    /** Envoie la photo et renvoie son lien Drive. */
    suspend fun upload(token: String, prefs: Prefs, bmp: Bitmap, name: String): String = withContext(Dispatchers.IO) {
        val folder = folderId(token, prefs)
        val jpg = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }.toByteArray()
        val b = "safetytags" + System.currentTimeMillis()
        val meta = JSONObject().put("name", name).put("parents", org.json.JSONArray().put(folder)).toString()
        val c = conn("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id", token, "POST")
        c.doOutput = true
        c.setRequestProperty("Content-Type", "multipart/related; boundary=$b")
        c.outputStream.use { o ->
            o.write("--$b\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n--$b\r\nContent-Type: image/jpeg\r\n\r\n".toByteArray())
            o.write(jpg)
            o.write("\r\n--$b--\r\n".toByteArray())
        }
        val id = JSONObject(read(c)).getString("id")
        "https://drive.google.com/file/d/$id/view"
    }

    fun idOf(link: String): String? = Regex("/d/([-\\w]{20,})|id=([-\\w]{20,})").find(link)?.let { it.groupValues[1].ifBlank { it.groupValues[2] } }

    suspend fun download(token: String, link: String): Bitmap? = withContext(Dispatchers.IO) {
        val id = idOf(link) ?: return@withContext null
        runCatching {
            val c = conn("$API/$id?alt=media", token)
            if (c.responseCode >= 400) { c.disconnect(); null }
            else c.inputStream.use { android.graphics.BitmapFactory.decodeStream(it) }.also { c.disconnect() }
        }.getOrNull()
    }
}
