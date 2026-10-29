package com.skillexchange.app.presentation.chat.detail

import com.skillexchange.app.domain.model.chat.ChatMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SystemCallFormatter {

    fun formatDuration(durationSeconds: Int): String {
        if (durationSeconds <= 0) return "00:00"
        val hours = durationSeconds / 3600
        val minutes = (durationSeconds % 3600) / 60
        val seconds = durationSeconds % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    fun isSuccessful(callStatus: String?): Boolean {
        val s = callStatus?.trim()?.uppercase()
        return s == "COMPLETED" || s == "ENDED"
    }

    fun formatTitle(message: ChatMessage, currentUserId: String): String {
        val effectiveCallerId = message.callerId ?: message.senderId
        val isCaller = effectiveCallerId == currentUserId
        val isVideo = message.callType.equals("VIDEO", ignoreCase = true)
        val typeName = if (isVideo) "video" else "thoại"
        val status = message.callStatus?.trim()?.uppercase() ?: "COMPLETED"

        return if (isCaller) {
            when (status) {
                "COMPLETED", "ENDED" -> {
                    val duration = if (message.durationSeconds > 0) " (${formatDuration(message.durationSeconds)})" else ""
                    "Cuộc gọi $typeName đi$duration"
                }
                "MISSED" -> "Cuộc gọi đi không trả lời"
                "REJECTED" -> "Cuộc gọi đi bị từ chối"
                "BUSY" -> "Đối phương đang bận"
                else -> "Cuộc gọi $typeName đi"
            }
        } else {
            when (status) {
                "COMPLETED", "ENDED" -> {
                    val duration = if (message.durationSeconds > 0) " (${formatDuration(message.durationSeconds)})" else ""
                    "Cuộc gọi $typeName đến$duration"
                }
                "MISSED" -> "Cuộc gọi nhỡ"
                "REJECTED" -> "Đã từ chối cuộc gọi"
                "BUSY" -> "Cuộc gọi nhỡ (Máy bận)"
                else -> "Cuộc gọi $typeName đến"
            }
        }
    }

    fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}

