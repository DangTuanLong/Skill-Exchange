package com.skillexchange.api.models

/**
 * Các hằng số giới hạn độ dài dữ liệu đầu vào dùng chung cho toàn bộ API (TASK-052).
 * Đảm bảo tính nhất quán giữa các routes và ngăn chặn DoS bằng payload quá khổ.
 */
object ValidationConstants {
    const val MAX_EMAIL_LENGTH = 254 // RFC 5321 chuẩn quốc tế
    const val MIN_PASSWORD_LENGTH = 6
    const val MAX_PASSWORD_LENGTH = 256 // Hỗ trợ mật khẩu mạnh, chống CPU DoS băm chuỗi khổng lồ
    const val MAX_FULL_NAME_LENGTH = 100
    const val MAX_CITY_LENGTH = 100
    const val MAX_BIO_LENGTH = 500
    const val MAX_EXCHANGE_MESSAGE_LENGTH = 1000
    const val MAX_CANCELLATION_REASON_LENGTH = 500
    const val MAX_RATING_COMMENT_LENGTH = 200
    const val MAX_DEVICE_INFO_LENGTH = 200
    const val MAX_FCM_TOKEN_LENGTH = 4096
}
