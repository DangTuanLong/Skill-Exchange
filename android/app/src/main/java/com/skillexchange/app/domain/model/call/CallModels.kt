package com.skillexchange.app.domain.model.call

enum class CallType {
    VOICE,
    VIDEO;

    companion object {
        fun fromString(value: String): CallType =
            runCatching { valueOf(value.trim().uppercase()) }.getOrDefault(VOICE)
    }
}

enum class CallUiStatus(val label: String) {
    IDLE("Sẵn sàng"),
    DIALING("Đang gọi..."),
    RINGING("Đang đổ chuông..."),
    CONNECTED("Đã kết nối"),
    BUSY("Người nhận đang bận"),
    MISSED("Cuộc gọi nhỡ"),
    REJECTED("Cuộc gọi bị từ chối"),
    ENDED("Cuộc gọi đã kết thúc"),
    ERROR("Lỗi kết nối");

    val isTerminal: Boolean
        get() = this in setOf(BUSY, MISSED, REJECTED, ENDED, ERROR)
}

data class CallToken(
    val url: String,
    val roomName: String,
    val token: String,
    val callType: CallType,
    val sessionId: String
)

data class CallSession(
    val id: String,
    val exchangeId: String,
    val callerId: String,
    val receiverId: String,
    val roomName: String,
    val callType: CallType,
    val status: String,
    val startedAt: String,
    val connectedAt: String?,
    val endedAt: String?,
    val durationSeconds: Int
)
