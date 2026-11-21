package com.example.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val TAG = "GoogleAuthHelper"

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    if (Firebase.auth.currentUser != null) {
        onAuthSuccess()
        return
    }
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        onUnauthenticated()
        return
    }
    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    scope.launch {
        try {
            val cred = credentialManager.getCredential(context, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            onUnauthenticated()
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
) {
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        onAuthError("تعذر العثور على إعدادات Google Sign-In (default_web_client_id).")
        return
    }
    val activityContext = context.findActivity()
    if (activityContext == null) {
        onAuthError("يلزم سياق نشاط صالح لبدء تسجيل الدخول عبر Google.")
        return
    }

    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(signInOption)
        .build()

    scope.launch {
        try {
            val cred = credentialManager.getCredential(activityContext, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                onAuthSuccess()
            } else {
                onAuthError("نوع بيانات الاعتماد المستلم غير متوقع.")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            val msg = e.message.orEmpty()
            if (looksLikeShaOrConfigError(msg)) {
                onAuthError(shaMismatchHelpMessage())
            } else {
                onAuthCancelled()
            }
        } catch (e: NoCredentialException) {
            Log.w(TAG, "No Google credential available: ${e.message}", e)
            onAuthError(
                "تعذر العثور على حساب Google جاهز أو أن بصمة توقيع التطبيق (SHA-1) لا تطابق المسجّل في Firebase. تأكد من وجود حساب Google على الجهاز أو بناء APK بتوقيع debug المعتمد."
            )
        } catch (e: GetCredentialProviderConfigurationException) {
            Log.e(TAG, "Credential provider configuration issue", e)
            onAuthError(shaMismatchHelpMessage())
        } catch (e: GetCredentialUnsupportedException) {
            Log.e(TAG, "Credential Manager unsupported", e)
            onAuthError("خدمات Google Play أو مزود تسجيل الدخول غير مدعوم على هذا الجهاز.")
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Credential Manager error", e)
            val msg = e.message.orEmpty()
            if (looksLikeShaOrConfigError(msg)) {
                onAuthError(shaMismatchHelpMessage())
            } else {
                onAuthError(msg.ifBlank { "تعذر إتمام تسجيل الدخول عبر Google. حاول مرة أخرى." })
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed", e)
            val msg = e.localizedMessage.orEmpty()
            if (looksLikeShaOrConfigError(msg)) {
                onAuthError(shaMismatchHelpMessage())
            } else {
                onAuthError(msg.ifBlank { "تعذر تسجيل الدخول عبر Google. حاول مرة أخرى." })
            }
        }
    }
}

fun onAppleSignInClicked(
    context: Context,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    onAuthCancelled: () -> Unit = {}
) {
    val activityContext = context.findActivity()
    if (activityContext == null) {
        onAuthError("يلزم سياق نشاط صالح لبدء تسجيل الدخول عبر Apple.")
        return
    }

    val providerBuilder = OAuthProvider.newBuilder("apple.com")
        .setScopes(listOf("email", "name"))
        .addCustomParameter("locale", "ar")

    val auth = try {
        Firebase.auth
    } catch (e: Exception) {
        onAuthError("تعذر تهيئة خدمة المصادقة السحابية.")
        return
    }

    val pendingTask = auth.pendingAuthResult
    if (pendingTask != null) {
        pendingTask
            .addOnSuccessListener { onAuthSuccess() }
            .addOnFailureListener { error ->
                handleAppleAuthFailure(error, onAuthError, onAuthCancelled)
            }
        return
    }

    auth.startActivityForSignInWithProvider(activityContext, providerBuilder.build())
        .addOnSuccessListener { onAuthSuccess() }
        .addOnFailureListener { error ->
            handleAppleAuthFailure(error, onAuthError, onAuthCancelled)
        }
}

private fun handleAppleAuthFailure(
    error: Exception,
    onAuthError: (String) -> Unit,
    onAuthCancelled: () -> Unit
) {
    Log.w(TAG, "Apple Sign-In failed: ${error.message}", error)
    val errorCode = (error as? FirebaseAuthException)?.errorCode.orEmpty()
    val rawMessage = error.message.orEmpty()
    when {
        errorCode == "ERROR_WEB_CONTEXT_CANCELED" ||
            rawMessage.contains("canceled", ignoreCase = true) ||
            rawMessage.contains("cancelled", ignoreCase = true) -> {
            onAuthCancelled()
        }
        errorCode == "ERROR_OPERATION_NOT_ALLOWED" ||
            rawMessage.contains("not allowed", ignoreCase = true) ||
            rawMessage.contains("apple.com", ignoreCase = true) -> {
            onAuthError(
                "تسجيل الدخول عبر Apple يتطلب تفعيل مزوّد Apple داخل Firebase Console -> Authentication -> Sign-in method. يمكنك الدخول فوراً الآن بحساب Google."
            )
        }
        else -> {
            onAuthError(
                error.localizedMessage?.takeIf { it.isNotBlank() }
                    ?: "تعذر إتمام تسجيل الدخول عبر Apple. تأكد من الاتصال بالإنترنت أو جرّب حساب Google."
            )
        }
    }
}

private fun looksLikeShaOrConfigError(msg: String): Boolean {
    return msg.contains("28444") ||
        msg.contains("12500") ||
        msg.contains("10:") ||
        msg.contains("16:") ||
        msg.contains("developer console", ignoreCase = true) ||
        msg.contains("caller is not authorized", ignoreCase = true) ||
        msg.contains("sha", ignoreCase = true)
}

private fun shaMismatchHelpMessage(): String {
    return "توقيع نسخة الـ APK لا يطابق بصمة SHA-1 المسجّلة في Firebase. لبناء APK يعمل فيه الدخول تلقائياً استخدم debug.keystore المرفق عبر ملف بناء GitHub أو أضف SHA-1 الجديد في Firebase."
}

fun signOutUser(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    beforeSignOut: suspend () -> Unit = {},
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                beforeSignOut()
            } catch (e: Exception) {
                Log.w(TAG, "Cleanup before sign-out warning: ${e.message}")
            }
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear credential state: ${e.message}")
            } finally {
                Firebase.auth.signOut()
                onSignOutComplete()
            }
        }
    }
}
