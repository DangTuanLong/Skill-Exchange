package com.skillexchange.app.core.notification

import android.os.Build
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import com.skillexchange.app.core.security.TokenManager
import com.skillexchange.app.domain.repository.IDeviceRepository
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FcmTokenManager(
    private val deviceRepository: IDeviceRepository,
    private val tokenManager: TokenManager
) {
    private val tag = "FcmTokenManager"

    suspend fun syncCurrentToken(): Result<Unit> = runCatching {
        if (!tokenManager.isLoggedIn()) {
            Log.d(tag, "Chưa đăng nhập, bỏ qua đồng bộ FCM token.")
            return@runCatching
        }

        val token = try {
            FirebaseMessaging.getInstance().token.awaitTask()
        } catch (e: Exception) {
            Log.w(tag, "Không lấy được FCM token: ${e.message}")
            return@runCatching
        }

        if (token.isBlank()) return@runCatching

        val deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        deviceRepository.registerDevice(token, deviceInfo)
            .onSuccess { Log.d(tag, "Đăng ký FCM token thành công.") }
            .onFailure { Log.e(tag, "Đăng ký FCM token thất bại: ${it.message}") }
    }

    suspend fun unregisterCurrentToken(): Result<Unit> = runCatching {
        val token = try {
            FirebaseMessaging.getInstance().token.awaitTask()
        } catch (_: Exception) { null }

        if (!token.isNullOrBlank()) {
            deviceRepository.unregisterDevice(token)
                .onSuccess { Log.d(tag, "Xóa FCM token khỏi server thành công.") }
                .onFailure { Log.e(tag, "Xóa FCM token thất bại: ${it.message}") }
        }
    }
}

private suspend fun <T> Task<T>.awaitTask(): T =
    suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) {
                continuation.resume(task.result)
            } else {
                continuation.resumeWithException(task.exception ?: RuntimeException("Task failed"))
            }
        }
    }
