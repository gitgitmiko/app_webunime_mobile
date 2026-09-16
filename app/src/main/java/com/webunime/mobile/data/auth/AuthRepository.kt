package com.webunime.mobile.data.auth

import android.app.Activity
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.webunime.mobile.BuildConfig
import kotlinx.coroutines.tasks.await

class AuthRepository {

    private val auth: FirebaseAuth? = runCatching { FirebaseAuth.getInstance() }.getOrNull()

    val isFirebaseReady: Boolean get() = auth != null

    val currentUser get() = auth?.currentUser

    fun isSignedIn(): Boolean = currentUser != null

    fun googleSignInClient(activity: Activity): GoogleSignInClient? {
        val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID.trim()
        if (webClientId.isEmpty()) return null
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(activity, gso)
    }

    fun signInIntent(activity: Activity): Intent? =
        googleSignInClient(activity)?.signInIntent

    suspend fun handleGoogleSignInResult(data: Intent?): Result<Unit> {
        val a = auth ?: return Result.failure(IllegalStateException("Firebase Auth belum siap"))
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            val token = account.idToken
                ?: return Result.failure(IllegalStateException("ID token kosong — cek GOOGLE_WEB_CLIENT_ID"))
            val credential = GoogleAuthProvider.getCredential(token, null)
            a.signInWithCredential(credential).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signOut(activity: Activity) {
        auth?.signOut()
        runCatching { googleSignInClient(activity)?.signOut()?.await() }
    }
}
