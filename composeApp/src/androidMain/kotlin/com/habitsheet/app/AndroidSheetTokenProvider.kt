package com.habitsheet.app

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.habitsheet.sync.SheetTokenProvider

internal class AndroidSheetTokenProvider(private val activity: ComponentActivity) : SheetTokenProvider {
    private val client = Identity.getAuthorizationClient(activity)
    private var pendingCompletion: ((String?, String?) -> Unit)? = null
    private val launcher = activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val completion = pendingCompletion ?: return@registerForActivityResult
        pendingCompletion = null
        if (result.resultCode != Activity.RESULT_OK || result.data == null) {
            completion(null, null)
        } else {
            try {
                completion(client.getAuthorizationResultFromIntent(result.data).accessToken, null)
            } catch (_: Exception) {
                completion(null, "Google authorization failed.")
            }
        }
    }

    override fun requestToken(interactive: Boolean, completion: (String?, String?) -> Unit) {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope("https://www.googleapis.com/auth/spreadsheets")))
            .build()
        client.authorize(request)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    if (!interactive) {
                        completion(null, null)
                    } else {
                        val pendingIntent = result.pendingIntent
                        if (pendingIntent == null) completion(null, "Google authorization unavailable.")
                        else {
                            pendingCompletion = completion
                            launcher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                        }
                    }
                } else completion(result.accessToken, null)
            }
            .addOnFailureListener { completion(null, "Google authorization unavailable on this device.") }
    }
}
