package com.skillexchange.app.core.auth

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.skillexchange.app.data.remote.auth.AuthRemoteDataSource
import kotlinx.coroutines.tasks.await

interface IFirebaseAuthManager {
    suspend fun ensureSignedIn(): Result<String>
    suspend fun signOutAndCleanup(): Result<Unit>
}

open class FirebaseAuthManager(
    private val authRemoteDataSource: AuthRemoteDataSource,
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : IFirebaseAuthManager {

    companion object {
        private const val TAG = "FirebaseAuthManager"
    }

    override suspend fun ensureSignedIn(): Result<String> = runCatching {
        val currentUser = firebaseAuth.currentUser
        if (currentUser != null) {
            return@runCatching currentUser.uid
        }

        val tokenResp = authRemoteDataSource.getFirebaseToken()
        val customToken = tokenResp.data?.token
            ?: throw IllegalStateException(tokenResp.message ?: "Không nhận được Firebase Custom Token từ máy chủ")

        val result = firebaseAuth.signInWithCustomToken(customToken).await()
        val uid = result.user?.uid
            ?: throw IllegalStateException("Đăng nhập Firebase bằng Custom Token không trả về user")

        uid
    }

    /**
     * Quy tắc §5 (AGENTS.md / Task-030):
     * Gọi terminate() trước clearPersistence() (clearPersistence chỉ chạy khi Firestore đã tắt).
     */
    override suspend fun signOutAndCleanup(): Result<Unit> = runCatching {
        try {
            firestore.terminate().await()
            firestore.clearPersistence().await()
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi dọn dẹp Firestore cache: ${e.message}", e)
        } finally {
            firebaseAuth.signOut()
        }
    }
}
