package com.skillexchange.app.core.network

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * Serializer hỗ trợ đọc cả String lẫn Number (Int) cho trường `code`.
 * Đảm bảo tương thích ngược: backend cũ trả code: 400 (Int),
 * backend mới trả code: "VALIDATION_FAILED" (String) đều không bị crash.
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
object LenientCodeSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LenientCode", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String?) {
        if (value != null) encoder.encodeString(value) else encoder.encodeNull()
    }

    override fun deserialize(decoder: Decoder): String? {
        val jsonDecoder = decoder as? JsonDecoder ?: return runCatching { decoder.decodeString() }.getOrNull()
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonNull -> null
            is JsonPrimitive -> element.content
            else -> element.toString()
        }
    }
}

/**
 * Model DTO đại diện cho response lỗi từ API server (TASK-009).
 */
@Serializable
data class ApiErrorDto(
    val success: Boolean = false,
    val status: Int? = null,
    @Serializable(with = LenientCodeSerializer::class)
    val code: String? = null,
    val message: String? = null,
    val fieldErrors: Map<String, String>? = null
)

/**
 * Lớp Exception chính thức mang thông tin lỗi đã chuẩn hóa (DEC-004, TASK-009).
 * - status: Mã HTTP status (400, 401, 403, 404, 409, 413, 415, 422, 429, 500)
 * - code: Mã lỗi chuỗi ổn định (VALIDATION_FAILED, UNAUTHORIZED, ...)
 * - message: Thông điệp tiếng Việt thân thiện với người dùng
 * - fieldErrors: Chi tiết lỗi theo từng trường (nếu có, ví dụ khi status=422)
 */
class ApiException(
    val status: Int?,
    val code: String?,
    override val message: String,
    val fieldErrors: Map<String, String>? = null,
    cause: Throwable? = null
) : Exception(message, cause) {

    companion object {
        const val CODE_VALIDATION_FAILED = "VALIDATION_FAILED"
        const val CODE_INVALID_TRANSITION = "INVALID_TRANSITION"
        const val CODE_ALREADY_RATED = "ALREADY_RATED"
        const val CODE_NOT_PARTICIPANT = "NOT_PARTICIPANT"
        const val CODE_UNAUTHORIZED = "UNAUTHORIZED"
        const val CODE_FORBIDDEN = "FORBIDDEN"
        const val CODE_NOT_FOUND = "NOT_FOUND"
        const val CODE_CONFLICT = "CONFLICT"
        const val CODE_PAYLOAD_TOO_LARGE = "PAYLOAD_TOO_LARGE"
        const val CODE_UNSUPPORTED_MEDIA = "UNSUPPORTED_MEDIA"
        const val CODE_RATE_LIMITED = "RATE_LIMITED"
        const val CODE_INTERNAL = "INTERNAL"
        const val CODE_BAD_REQUEST = "BAD_REQUEST"

        /**
         * Chuyển đổi từ HTTP status và ApiErrorDto sang ApiException.
         * Ưu tiên chuỗi code -> fallback status -> fallback server message -> thông điệp mặc định.
         * Đối với endpoint xác thực (auth), 401 sử dụng thông báo đăng nhập riêng biệt.
         */
        fun fromResponse(
            httpStatus: Int,
            errorDto: ApiErrorDto?,
            isAuthEndpoint: Boolean = false
        ): ApiException {
            val effectiveStatus = errorDto?.status ?: httpStatus
            val code = errorDto?.code
            val serverMessage = errorDto?.message?.trim()?.takeIf { it.isNotEmpty() }

            val message = if (isAuthEndpoint && (effectiveStatus == 401 || code == CODE_UNAUTHORIZED)) {
                serverMessage ?: "Email hoặc mật khẩu không đúng"
            } else if (serverMessage != null && isSpecificBusinessMessage(serverMessage)) {
                // Giữ nguyên message chi tiết từ server nếu có nội dung cụ thể (ví dụ validation, tên lỗi nghiệp vụ)
                serverMessage
            } else {
                resolveVietnameseMessage(code, effectiveStatus)
                    ?: serverMessage
                    ?: "Đã có lỗi xảy ra. Vui lòng thử lại sau."
            }

            return ApiException(
                status = effectiveStatus,
                code = code,
                message = message,
                fieldErrors = errorDto?.fieldErrors
            )
        }

        private fun isSpecificBusinessMessage(msg: String): Boolean {
            // Không ghi đè nếu server gửi message nghiệp vụ cụ thể (khác các câu chung chung)
            val genericPhrases = setOf(
                "Lỗi máy chủ. Vui lòng thử lại sau.",
                "Lỗi server. Vui lòng thử lại sau.",
                "Internal Server Error",
                "Bad Request"
            )
            return msg !in genericPhrases
        }

        /**
         * Ánh xạ thông điệp tiếng Việt: ưu tiên mã chuỗi ổn định, fallback theo mã HTTP status.
         */
        fun resolveVietnameseMessage(code: String?, status: Int?): String? {
            // 1. Ánh xạ theo chuỗi code
            code?.let {
                when (it) {
                    CODE_VALIDATION_FAILED -> return "Dữ liệu nhập không hợp lệ."
                    CODE_INVALID_TRANSITION -> return "Trạng thái trao đổi không hợp lệ."
                    CODE_ALREADY_RATED -> return "Bạn đã đánh giá buổi trao đổi này rồi."
                    CODE_NOT_PARTICIPANT -> return "Bạn không phải là người tham gia buổi trao đổi này."
                    CODE_UNAUTHORIZED -> return "Chưa xác thực. Vui lòng đăng nhập."
                    CODE_FORBIDDEN -> return "Bạn không có quyền thực hiện thao tác này."
                    CODE_NOT_FOUND -> return "Không tìm thấy dữ liệu yêu cầu."
                    CODE_CONFLICT -> return "Dữ liệu bị xung đột hoặc đã được cập nhật."
                    CODE_PAYLOAD_TOO_LARGE -> return "Kích thước tập tin vượt quá giới hạn cho phép."
                    CODE_UNSUPPORTED_MEDIA -> return "Định dạng tập tin không được hỗ trợ."
                    CODE_RATE_LIMITED -> return "Bạn đã gửi quá nhiều yêu cầu. Vui lòng thử lại sau ít phút."
                    CODE_INTERNAL -> return "Lỗi máy chủ. Vui lòng thử lại sau."
                    CODE_BAD_REQUEST -> return "Yêu cầu không hợp lệ."
                    else -> {}
                }
            }

            // 2. Fallback theo HTTP status code
            return when (status) {
                400 -> "Yêu cầu không hợp lệ."
                401 -> "Chưa xác thực. Vui lòng đăng nhập."
                403 -> "Bạn không có quyền thực hiện thao tác này."
                404 -> "Không tìm thấy dữ liệu yêu cầu."
                409 -> "Dữ liệu bị xung đột hoặc đã được cập nhật."
                413 -> "Kích thước tập tin vượt quá giới hạn cho phép."
                415 -> "Định dạng tập tin không được hỗ trợ."
                422 -> "Dữ liệu nhập không hợp lệ."
                429 -> "Bạn đã gửi quá nhiều yêu cầu. Vui lòng thử lại sau ít phút."
                500 -> "Lỗi máy chủ. Vui lòng thử lại sau."
                else -> null
            }
        }
    }
}
