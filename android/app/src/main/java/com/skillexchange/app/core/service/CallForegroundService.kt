package com.skillexchange.app.core.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.skillexchange.app.MainActivity
import com.skillexchange.app.R
import com.skillexchange.app.domain.repository.ICallRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

class CallForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "CallForegroundService"
        const val CHANNEL_ID = "ongoing_calls"
        const val CHANNEL_NAME = "Cuộc gọi đang diễn ra"
        const val ONGOING_NOTIFICATION_ID = 20002

        const val ACTION_START_CALL = "com.skillexchange.app.ACTION_START_CALL"
        const val ACTION_STOP_CALL = "com.skillexchange.app.ACTION_STOP_CALL"

        const val EXTRA_SESSION_ID = "extra_session_id"
        const val EXTRA_CALLER_NAME = "extra_caller_name"
        const val EXTRA_EXCHANGE_ID = "extra_exchange_id"
        const val EXTRA_CALL_TYPE = "extra_call_type"

        @Volatile
        var isRunning: Boolean = false
            private set

        fun startService(
            context: Context,
            sessionId: String,
            callerName: String,
            exchangeId: String,
            callType: String
        ) {
            val intent = Intent(context, CallForegroundService::class.java).apply {
                action = ACTION_START_CALL
                putExtra(EXTRA_SESSION_ID, sessionId)
                putExtra(EXTRA_CALLER_NAME, callerName)
                putExtra(EXTRA_EXCHANGE_ID, exchangeId)
                putExtra(EXTRA_CALL_TYPE, callType)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, CallForegroundService::class.java).apply {
                action = ACTION_STOP_CALL
            }
            context.startService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_START_CALL -> {
                val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: ""
                val callerName = intent.getStringExtra(EXTRA_CALLER_NAME) ?: "Đối tác"
                val exchangeId = intent.getStringExtra(EXTRA_EXCHANGE_ID) ?: ""
                val callType = intent.getStringExtra(EXTRA_CALL_TYPE) ?: "VOICE"

                Log.d(TAG, "Khởi động CallForegroundService cho sessionId=$sessionId, callerName=$callerName")
                startOngoingCallNotification(sessionId, callerName, exchangeId, callType)
                isRunning = true
            }
            ACTION_STOP_CALL -> {
                val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
                Log.d(TAG, "Nhận yêu cầu dừng CallForegroundService cho sessionId=$sessionId")
                serviceScope.launch {
                    if (!sessionId.isNullOrBlank()) {
                        try {
                            // Chờ endCall tối đa 3 giây; server tự dọn phiên gọi sau timeout là lưới an toàn nếu mạng ngắt
                            kotlinx.coroutines.withTimeoutOrNull(3000L) {
                                val callRepository = GlobalContext.get().get<ICallRepository>()
                                callRepository.endCall(sessionId)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Lỗi khi gọi endCall từ FGS: ${e.message}")
                        }
                    }
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    isRunning = false
                }
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        Log.d(TAG, "CallForegroundService đã bị hủy")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Hiển thị thông báo khi cuộc gọi thoại/video đang diễn ra"
                enableVibration(false)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startOngoingCallNotification(
        sessionId: String,
        callerName: String,
        exchangeId: String,
        callType: String
    ) {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("type", "CALL")
            putExtra("entityId", exchangeId)
            putExtra("sessionId", sessionId)
            putExtra("isVideo", callType.equals("VIDEO", ignoreCase = true))
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            sessionId.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val endCallIntent = Intent(this, CallForegroundService::class.java).apply {
            action = ACTION_STOP_CALL
            putExtra(EXTRA_SESSION_ID, sessionId)
        }
        val endCallPendingIntent = PendingIntent.getService(
            this,
            sessionId.hashCode() + 1,
            endCallIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val desc = if (callType.equals("VIDEO", ignoreCase = true)) {
            "Cuộc gọi video đang diễn ra..."
        } else {
            "Cuộc gọi thoại đang diễn ra..."
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(callerName)
            .setContentText(desc)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Kết thúc", endCallPendingIntent)
            .build()

        val hasRecordAudio = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Trên Android 14+ (API 34+): Chỉ thêm MICROPHONE khi quyền RECORD_AUDIO đã được cấp
                if (hasRecordAudio) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                }
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
            }
            startForeground(ONGOING_NOTIFICATION_ID, notification, fgsType)
        } else {
            startForeground(ONGOING_NOTIFICATION_ID, notification)
        }
    }
}
