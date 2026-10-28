package com.skillexchange.app.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.skillexchange.app.R
import com.skillexchange.app.presentation.call.IncomingCallActivity

object IncomingCallNotificationHelper {

    private const val TAG = "IncomingCallNotif"
    const val CHANNEL_ID = "incoming_calls"
    const val CHANNEL_NAME = "Cuộc gọi đến"
    const val INCOMING_CALL_NOTIFICATION_ID = 20001
    const val ACTION_CALL_CANCELLED_BROADCAST = "com.skillexchange.app.CALL_CANCELLED"

    /**
     * Khởi tạo Notification Channel riêng cho cuộc gọi đến với mức ưu tiên cao nhất (IMPORTANCE_HIGH),
     * chuông điện thoại USAGE_NOTIFICATION_RINGTONE và rung theo nhịp.
     */
    fun createIncomingCallNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .build()

            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Thông báo chuông và toàn màn hình khi có cuộc gọi thoại/video đến"
                setSound(ringtoneUri, audioAttributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 1000, 1000, 1000)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Kiểm tra quyền Full-Screen Intent theo quy định của Android 14+ (API 34+).
     * Từ Android 14, quyền USE_FULL_SCREEN_INTENT không được cấp mặc định cho ứng dụng debug/sideload.
     */
    fun canUseFullScreenIntent(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.canUseFullScreenIntent()
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    /**
     * Hiển thị thông báo cuộc gọi đến (Full-screen Intent nếu được cấp quyền, hoặc Heads-up Notification fallback).
     */
    fun showIncomingCallNotification(
        context: Context,
        sessionId: String,
        callerId: String,
        callerName: String,
        callType: String,
        exchangeId: String
    ) {
        createIncomingCallNotificationChannel(context)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Intent toàn màn hình mở IncomingCallActivity
        val fullScreenIntent = Intent(context, IncomingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(IncomingCallActivity.EXTRA_SESSION_ID, sessionId)
            putExtra(IncomingCallActivity.EXTRA_CALLER_ID, callerId)
            putExtra(IncomingCallActivity.EXTRA_CALLER_NAME, callerName)
            putExtra(IncomingCallActivity.EXTRA_CALL_TYPE, callType)
            putExtra(IncomingCallActivity.EXTRA_EXCHANGE_ID, exchangeId)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            sessionId.hashCode(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action [Từ chối]: Gửi broadcast tới CallActionReceiver để xử lý reject ngầm
        val rejectIntent = Intent(context, CallActionReceiver::class.java).apply {
            action = CallActionReceiver.ACTION_REJECT
            putExtra(CallActionReceiver.EXTRA_SESSION_ID, sessionId)
        }
        val rejectPendingIntent = PendingIntent.getBroadcast(
            context,
            sessionId.hashCode() + 1,
            rejectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action [Trả lời]: Mở IncomingCallActivity với cờ tự động kích hoạt trả lời
        val answerIntent = Intent(context, IncomingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(IncomingCallActivity.EXTRA_SESSION_ID, sessionId)
            putExtra(IncomingCallActivity.EXTRA_CALLER_ID, callerId)
            putExtra(IncomingCallActivity.EXTRA_CALLER_NAME, callerName)
            putExtra(IncomingCallActivity.EXTRA_CALL_TYPE, callType)
            putExtra(IncomingCallActivity.EXTRA_EXCHANGE_ID, exchangeId)
            putExtra(IncomingCallActivity.EXTRA_AUTO_ANSWER, true)
        }
        val answerPendingIntent = PendingIntent.getActivity(
            context,
            sessionId.hashCode() + 2,
            answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val callTypeDesc = if (callType.equals("VIDEO", ignoreCase = true)) {
            "Cuộc gọi video đến..."
        } else {
            "Cuộc gọi thoại đến..."
        }

        val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(callerName.ifBlank { "Đối tác" })
            .setContentText(callTypeDesc)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setSound(ringtoneUri)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Từ chối", rejectPendingIntent)
            .addAction(android.R.drawable.ic_menu_call, "Trả lời", answerPendingIntent)

        val displayType = com.skillexchange.app.core.call.IncomingCallDecisionLogic.decideNotificationType(
            hasFullScreenPermission = canUseFullScreenIntent(context)
        )
        Log.d(TAG, "showIncomingCallNotification: sessionId=$sessionId, displayType=$displayType")

        if (displayType == com.skillexchange.app.core.call.NotificationDisplayType.FULL_SCREEN_INTENT) {
            builder.setFullScreenIntent(fullScreenPendingIntent, true)
        } else {
            // Fallback Heads-up Notification: Gán ContentIntent để người dùng chạm vào mở Activity
            builder.setContentIntent(fullScreenPendingIntent)
        }

        notificationManager.notify(INCOMING_CALL_NOTIFICATION_ID, builder.build())
    }

    /**
     * Hủy thông báo cuộc gọi đến (dừng chuông và rung).
     */
    fun cancelIncomingCallNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(INCOMING_CALL_NOTIFICATION_ID)
    }
}
