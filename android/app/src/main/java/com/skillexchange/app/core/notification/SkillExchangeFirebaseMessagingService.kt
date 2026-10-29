package com.skillexchange.app.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.skillexchange.app.MainActivity
import com.skillexchange.app.R
import com.skillexchange.app.core.security.TokenManager
import com.skillexchange.app.domain.repository.IDeviceRepository
import com.skillexchange.app.presentation.call.IncomingCallActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class SkillExchangeFirebaseMessagingService : FirebaseMessagingService() {

    private val tag = "FCMService"
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val deviceRepository: IDeviceRepository by inject()
    private val tokenManager: TokenManager by inject()
    private val inAppCallManager: com.skillexchange.app.core.call.InAppCallManager by inject()

    private val callRepository: com.skillexchange.app.domain.repository.ICallRepository by inject()

    companion object {
        const val CHANNEL_ID = "exchange_requests"
        const val CHANNEL_NAME = "Yêu cầu trao đổi"
        const val EXTRA_TYPE = "type"
        const val EXTRA_ENTITY_ID = "entityId"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(tag, "FCM onNewToken nhận token mới")
        if (tokenManager.isLoggedIn()) {
            serviceScope.launch {
                val deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
                deviceRepository.registerDevice(token, deviceInfo)
                    .onSuccess { Log.d(tag, "Cập nhật token mới lên server thành công") }
                    .onFailure { Log.e(tag, "Cập nhật token mới lên server thất bại: ${it.message}") }
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val title = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
            ?: "SkillExchange"
        val body = remoteMessage.notification?.body
            ?: remoteMessage.data["body"]
            ?: ""
        val type = remoteMessage.data["type"]
        val entityId = remoteMessage.data["entityId"]

        Log.d(tag, "FCM onMessageReceived: title=$title, type=$type, entityId=$entityId")

        // Xử lý cuộc gọi đến (TASK-075 & TASK-077)
        if (type == "INCOMING_CALL") {
            val sessionId = remoteMessage.data["sessionId"]
            val callerId = remoteMessage.data["callerId"] ?: ""
            val callerName = remoteMessage.data["callerName"] ?: "Đối tác"
            val callType = remoteMessage.data["callType"] ?: "VOICE"
            val exchangeId = remoteMessage.data["exchangeId"] ?: ""

            if (!sessionId.isNullOrBlank()) {
                val isBusy = (inAppCallManager.incomingCall.value != null) ||
                    com.skillexchange.app.core.service.CallForegroundService.isRunning

                val action = com.skillexchange.app.core.call.IncomingCallDecisionLogic.decidePushAction(
                    isBusy = isBusy,
                    isLoggedIn = tokenManager.isLoggedIn(),
                    isForeground = AppLifecycleTracker.isForeground
                )

                when (action) {
                    com.skillexchange.app.core.call.IncomingPushAction.IGNORE -> return
                    com.skillexchange.app.core.call.IncomingPushAction.REJECT_BUSY -> {
                        Log.d(tag, "Người dùng đang trong cuộc gọi khác (BUSY), tự động từ chối sessionId: $sessionId")
                        serviceScope.launch {
                            callRepository.rejectCall(sessionId)
                        }
                        showSystemNotification(
                            title = "Cuộc gọi nhỡ",
                            body = "Cuộc gọi nhỡ từ ${callerName.ifBlank { "Đối tác" }}",
                            type = "MISSED_CALL",
                            entityId = exchangeId
                        )
                        return
                    }
                    com.skillexchange.app.core.call.IncomingPushAction.SHOW_IN_APP_DIALOG -> {
                        inAppCallManager.onIncomingCallPushReceived(
                            sessionId = sessionId,
                            callerId = callerId,
                            callerName = callerName,
                            callType = callType,
                            exchangeId = exchangeId
                        )
                        return
                    }
                    com.skillexchange.app.core.call.IncomingPushAction.SHOW_SYSTEM_NOTIFICATION -> {
                        Log.d(tag, "Nhận cuộc gọi khi app ở background/killed, kích hoạt IncomingCallNotification")
                        IncomingCallNotificationHelper.showIncomingCallNotification(
                            context = this,
                            sessionId = sessionId,
                            callerId = callerId,
                            callerName = callerName,
                            callType = callType,
                            exchangeId = exchangeId
                        )
                        return
                    }
                }
            }
        } else if (type == "CALL_CANCELLED") {
            val sessionId = remoteMessage.data["sessionId"]
            if (!sessionId.isNullOrBlank()) {
                Log.d(tag, "Nhận thông báo CALL_CANCELLED cho sessionId: $sessionId")
                // Hủy thông báo in-app nếu app đang foreground
                if (AppLifecycleTracker.isForeground) {
                    inAppCallManager.onCallCancelledPushReceived(sessionId)
                }
                // Hủy thông báo cuộc gọi nền (dừng chuông/rung)
                IncomingCallNotificationHelper.cancelIncomingCallNotification(this)
                // Phát broadcast đóng IncomingCallActivity nếu đang hiển thị trên màn hình khóa
                val cancelIntent = Intent(IncomingCallNotificationHelper.ACTION_CALL_CANCELLED_BROADCAST).apply {
                    setPackage(packageName)
                    putExtra(IncomingCallActivity.EXTRA_SESSION_ID, sessionId)
                }
                sendBroadcast(cancelIntent)
                return
            }
        }

        // Nếu app đang mở ở foreground -> phát sự kiện hiển thị in-app banner
        if (AppLifecycleTracker.isForeground) {
            InAppNotificationManager.showNotification(
                InAppNotificationData(
                    title = title,
                    body = body,
                    type = type,
                    entityId = entityId
                )
            )
        } else {
            // Background -> hiển thị System Notification
            showSystemNotification(title, body, type, entityId)
        }
    }

    private fun showSystemNotification(
        title: String,
        body: String,
        type: String?,
        entityId: String?
    ) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Tạo Notification Channel trên Android 8.0+ (API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Thông báo khi có yêu cầu trao đổi kỹ năng mới hoặc cập nhật lịch hẹn"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Intent mở MainActivity kèm deep link data (type và entityId)
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TYPE, type)
            putExtra(EXTRA_ENTITY_ID, entityId)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            (entityId?.hashCode() ?: System.currentTimeMillis().toInt()),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationId = entityId?.hashCode() ?: System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notification)
    }
}
