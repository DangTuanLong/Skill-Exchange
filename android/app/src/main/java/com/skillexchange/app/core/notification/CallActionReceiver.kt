package com.skillexchange.app.core.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.skillexchange.app.domain.repository.ICallRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

class CallActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CallActionReceiver"
        const val ACTION_REJECT = "com.skillexchange.app.ACTION_REJECT"
        const val EXTRA_SESSION_ID = "extra_session_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REJECT) {
            val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
            Log.d(TAG, "Nhận yêu cầu Từ chối cuộc gọi từ Notification: sessionId=$sessionId")

            // 1. Dừng chuông và hủy thông báo ngay lập tức để giải phóng người dùng
            IncomingCallNotificationHelper.cancelIncomingCallNotification(context)

            if (!sessionId.isNullOrBlank()) {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        kotlinx.coroutines.withTimeoutOrNull(8000L) {
                            val callRepository = GlobalContext.get().get<ICallRepository>()
                            val result = callRepository.rejectCall(sessionId)
                            result.onSuccess {
                                Log.d(TAG, "Gửi từ chối cuộc gọi lên server thành công: $sessionId")
                            }.onFailure { err ->
                                Log.w(TAG, "Lỗi khi gửi từ chối cuộc gọi lên server: ${err.message}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Ngoại lệ khi thực thi rejectCall (ví dụ: mất kết nối mạng): ${e.message}")
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
