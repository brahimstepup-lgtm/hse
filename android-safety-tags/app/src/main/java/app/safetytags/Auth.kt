package app.safetytags

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.io.IOException

data class GoogleUser(val name: String, val email: String, val idToken: String)

/** Connexion « Se connecter avec Google » via Credential Manager. */
object Auth {
    val configured get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    /** Renvoie null si l'utilisateur a annulé. */
    suspend fun signIn(activity: Activity): GoogleUser? {
        if (!configured) throw IOException(tr("La connexion Google n’est pas encore activée dans cette version. Continuez sans compte."))
        val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val result = CredentialManager.create(activity).getCredential(activity, request)
            val c = result.credential
            if (c is CustomCredential && c.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val g = GoogleIdTokenCredential.createFrom(c.data)
                GoogleUser(g.displayName ?: g.givenName ?: g.id, g.id, g.idToken)
            } else throw IOException("Réponse de connexion inattendue")
        } catch (e: GetCredentialCancellationException) {
            null
        } catch (e: GetCredentialException) {
            throw IOException(tr("Connexion Google impossible : {0}", e.message ?: e.type))
        }
    }

    /** Jeton Google récent sans afficher l'écran de choix (compte déjà autorisé). */
    suspend fun silentToken(activity: Activity): String? = runCatching {
        if (!configured) return null
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setFilterByAuthorizedAccounts(true)
            .setAutoSelectEnabled(true)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val c = CredentialManager.create(activity).getCredential(activity, request).credential
        if (c is CustomCredential && c.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
            GoogleIdTokenCredential.createFrom(c.data).idToken else null
    }.getOrNull()

    suspend fun signOut(ctx: Context) {
        runCatching { CredentialManager.create(ctx).clearCredentialState(ClearCredentialStateRequest()) }
    }
}
