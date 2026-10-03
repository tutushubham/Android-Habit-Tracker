package com.habitsheet.app

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.habitsheet.platform.Logger
import com.habitsheet.platform.NoOpLogger
import com.habitsheet.platform.e
import com.habitsheet.platform.w
import com.habitsheet.sync.SheetTokenProvider
import java.io.IOException

/**
 * Google Identity authorization for the Sheets scope.
 *
 * Calls are expected to be serialised by [com.habitsheet.sync.SerializingTokenProvider] (AppGraph wraps this
 * class), but this class is also defensive on its own: only one consent screen can be pending, a second
 * interactive request is refused instead of overwriting the first, and every completion is delivered once.
 */
internal class AndroidSheetTokenProvider(
    private val activity: ComponentActivity,
    private val logger: Logger = NoOpLogger,
) : SheetTokenProvider {
    private val client = Identity.getAuthorizationClient(activity)
    private val lock = Any()
    private var pendingCompletion: ((String?, String?) -> Unit)? = null

    private val launcher = activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val completion = takePending() ?: return@registerForActivityResult
        if (result.resultCode != Activity.RESULT_OK || result.data == null) {
            // Cancelled (or dismissed) by the user: no token, no error text.
            completion(null, null)
        } else {
            try {
                completion(client.getAuthorizationResultFromIntent(result.data).accessToken, null)
            } catch (e: ApiException) {
                logger.w(TAG, "Authorization result failed (status ${e.statusCode})", e)
                completion(null, failureText(e))
            } catch (e: Exception) {
                logger.e(TAG, "Authorization result could not be read", e)
                completion(null, "Google authorization failed.")
            }
        }
    }

    private companion object {
        const val TAG = "GoogleAuth"
    }

    private fun takePending(): ((String?, String?) -> Unit)? = synchronized(lock) {
        pendingCompletion.also { pendingCompletion = null }
    }

    private fun failureText(error: Exception): String = when {
        error is IOException -> SheetTokenProvider.NETWORK_ERROR
        error is ApiException && error.statusCode == CommonStatusCodes.NETWORK_ERROR -> SheetTokenProvider.NETWORK_ERROR
        // Access was revoked in the Google Account, or the account no longer exists.
        error is ApiException && error.statusCode == CommonStatusCodes.SIGN_IN_REQUIRED -> "Google access was revoked. Sign in again."
        else -> "Google authorization unavailable on this device."
    }

    override fun requestToken(interactive: Boolean, completion: (String?, String?) -> Unit) {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope("https://www.googleapis.com/auth/spreadsheets")))
            .build()
        client.authorize(request)
            .addOnSuccessListener { result ->
                when {
                    !result.hasResolution() -> completion(result.accessToken, null)
                    // Consent or sign-in is needed but nobody is looking at the screen (revoked / first run).
                    !interactive -> completion(null, null)
                    else -> launchConsent(result.pendingIntent, completion)
                }
            }
            .addOnFailureListener {
                logger.w(TAG, "authorize() failed", it)
                completion(null, failureText(it))
            }
    }

    private fun launchConsent(pendingIntent: android.app.PendingIntent?, completion: (String?, String?) -> Unit) {
        if (pendingIntent == null) {
            completion(null, "Google authorization unavailable.")
            return
        }
        val accepted = synchronized(lock) {
            if (pendingCompletion != null) false else { pendingCompletion = completion; true }
        }
        if (!accepted) {
            completion(null, "Another Google sign-in is already open.")
            return
        }
        activity.runOnUiThread {
            try {
                launcher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
            } catch (e: Exception) {
                logger.e(TAG, "Consent screen could not be launched", e)
                // The Activity is finishing or the intent cannot be started: release the slot and report.
                takePending()?.invoke(null, "Google authorization could not be opened.")
            }
        }
    }
}
