package com.skillexchange.app.core.call

/**
 * Các loại quyết định xử lý khi nhận Push cuộc gọi đến.
 */
enum class IncomingPushAction {
    REJECT_BUSY,
    SHOW_IN_APP_DIALOG,
    SHOW_SYSTEM_NOTIFICATION,
    IGNORE
}

/**
 * Kết quả thẩm định trạng thái cuộc gọi trước khi người dùng nhấc máy.
 */
enum class CallAnswerEligibility {
    ELIGIBLE,
    NOT_RECEIVER,
    ALREADY_TERMINATED
}

/**
 * Loại định tuyến thông báo cuộc gọi đến (Full-screen Intent hay Heads-up Notification).
 */
enum class NotificationDisplayType {
    FULL_SCREEN_INTENT,
    HEADS_UP_NOTIFICATION
}

/**
 * Kiểu Foreground Service Type an toàn theo trạng thái quyền MICROPHONE.
 */
enum class SafeFgsType {
    PHONE_CALL_ONLY,
    PHONE_CALL_AND_MICROPHONE
}

/**
 * Lớp logic thuần túy (Pure Kotlin) quyết định các nhánh nghiệp vụ của cuộc gọi đến,
 * phục vụ kiểm thử đơn vị độc lập 100% JVM (DEC-026, TASK-077).
 */
object IncomingCallDecisionLogic {

    /**
     * Quyết định hành vi khi nhận FCM Push cuộc gọi đến:
     * - Nếu đang bận cuộc gọi khác -> REJECT_BUSY
     * - Nếu chưa đăng nhập -> IGNORE
     * - Nếu app đang foreground -> SHOW_IN_APP_DIALOG
     * - Nếu app ở background / killed -> SHOW_SYSTEM_NOTIFICATION
     */
    fun decidePushAction(
        isBusy: Boolean,
        isLoggedIn: Boolean,
        isForeground: Boolean
    ): IncomingPushAction {
        if (!isLoggedIn) return IncomingPushAction.IGNORE
        if (isBusy) return IncomingPushAction.REJECT_BUSY
        return if (isForeground) {
            IncomingPushAction.SHOW_IN_APP_DIALOG
        } else {
            IncomingPushAction.SHOW_SYSTEM_NOTIFICATION
        }
    }

    /**
     * Quyết định hiển thị Full-screen Intent hay Heads-up Notification:
     * - Android 14+ yêu cầu quyền USE_FULL_SCREEN_INTENT được cấp.
     * - Nếu chưa được cấp quyền (hoặc bị người dùng tắt) -> Fallback Heads-up Notification.
     */
    fun decideNotificationType(hasFullScreenPermission: Boolean): NotificationDisplayType {
        return if (hasFullScreenPermission) {
            NotificationDisplayType.FULL_SCREEN_INTENT
        } else {
            NotificationDisplayType.HEADS_UP_NOTIFICATION
        }
    }

    /**
     * Kiểm tra tính hợp lệ của cuộc gọi khi người nhận bấm [Trả lời]:
     * - Chỉ người nhận (receiver) mới được nghe.
     * - Trạng thái phải là DIALING hoặc RINGING. Nếu đã ENDED/MISSED/REJECTED -> ALREADY_TERMINATED.
     */
    fun checkAnswerEligibility(
        currentUserId: String,
        receiverId: String,
        status: String
    ): CallAnswerEligibility {
        if (currentUserId != receiverId) {
            return CallAnswerEligibility.NOT_RECEIVER
        }
        val terminalStatuses = setOf("ENDED", "MISSED", "REJECTED")
        if (terminalStatuses.contains(status.uppercase())) {
            return CallAnswerEligibility.ALREADY_TERMINATED
        }
        return CallAnswerEligibility.ELIGIBLE
    }

    /**
     * Quyết định Foreground Service Type an toàn:
     * - Trên Android 14 (API 34+), nếu gọi startForeground với MICROPHONE mà chưa được cấp RECORD_AUDIO,
     *   hệ thống sẽ ném SecurityException.
     * - Do đó chỉ thêm MICROPHONE khi RECORD_AUDIO đã được cấp.
     */
    fun decideFgsType(hasRecordAudioPermission: Boolean): SafeFgsType {
        return if (hasRecordAudioPermission) {
            SafeFgsType.PHONE_CALL_AND_MICROPHONE
        } else {
            SafeFgsType.PHONE_CALL_ONLY
        }
    }
}
