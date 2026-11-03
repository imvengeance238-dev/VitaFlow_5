package app.vitaflow.com.data.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import app.vitaflow.com.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class GoogleIdentity(val sub: String, val email: String?, val name: String?)

sealed interface GoogleResult {
    data class Ok(val identity: GoogleIdentity) : GoogleResult
    data class Err(val message: String) : GoogleResult
}

/**
 * Gets a Google ID token using Credential Manager, then verifies/authenticates it
 * through Firebase Authentication. The returned sub is the Firebase UID.
 */
suspend fun requestGoogleIdentity(activityContext: Context): GoogleResult {
    return try {
        val webClientId = activityContext.getString(R.string.default_web_client_id)
        if (webClientId.isBlank()) {
            return GoogleResult.Err("Firebase-এর Web Client ID পাওয়া যায়নি। google-services.json পরীক্ষা করুন।")
        }

        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val result = CredentialManager.create(activityContext).getCredential(activityContext, request)
        val cred = result.credential

        if (cred !is CustomCredential ||
            cred.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return GoogleResult.Err("Google account পাওয়া যায়নি।")
        }

        val google = GoogleIdTokenCredential.createFrom(cred.data)
        val firebaseCredential = GoogleAuthProvider.getCredential(google.idToken, null)
        val authResult = suspendCancellableCoroutine { continuation ->
            FirebaseAuth.getInstance().signInWithCredential(firebaseCredential)
                .addOnCompleteListener { task ->
                    if (!continuation.isActive) return@addOnCompleteListener
                    if (task.isSuccessful) {
                        val user = task.result.user
                        if (user != null) continuation.resume(user)
                        else continuation.resumeWithException(
                            IllegalStateException("Firebase user পাওয়া যায়নি।")
                        )
                    } else {
                        continuation.resumeWithException(
                            task.exception ?: IllegalStateException("Firebase login ব্যর্থ হয়েছে।")
                        )
                    }
                }
        }

        val uid = authResult.uid
        if (uid.isBlank()) {
            GoogleResult.Err("Firebase account ID পাওয়া যায়নি।")
        } else {
            GoogleResult.Ok(
                GoogleIdentity(
                    sub = uid,
                    email = authResult.email?.lowercase(),
                    name = authResult.displayName ?: google.displayName
                )
            )
        }
    } catch (e: GetCredentialException) {
        GoogleResult.Err("Google login হয়নি: ${e.message ?: "বাতিল হয়েছে"}")
    } catch (e: Exception) {
        GoogleResult.Err("Google/Firebase login হয়নি: ${e.localizedMessage ?: "অজানা সমস্যা"}")
    }
}
