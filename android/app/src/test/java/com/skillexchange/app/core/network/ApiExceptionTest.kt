package com.skillexchange.app.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiExceptionTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Serializable
    private data class LegacyOldClientErrorDto(
        val success: Boolean = true,
        val message: String? = null
    )

    @Test
    fun parseNewApiErrorJson_succeedsWithAllFields() {
        val jsonString = """
            {
                "success": false,
                "status": 422,
                "code": "VALIDATION_FAILED",
                "message": "Dữ liệu nhập không hợp lệ.",
                "fieldErrors": {
                    "email": "Email không đúng định dạng",
                    "password": "Mật khẩu quá ngắn"
                }
            }
        """.trimIndent()

        val dto = json.decodeFromString<ApiErrorDto>(jsonString)
        assertFalse(dto.success)
        assertEquals(422, dto.status)
        assertEquals("VALIDATION_FAILED", dto.code)
        assertEquals("Dữ liệu nhập không hợp lệ.", dto.message)
        assertEquals(2, dto.fieldErrors?.size)
        assertEquals("Email không đúng định dạng", dto.fieldErrors?.get("email"))
    }

    @Test
    fun parseLegacyEndpointJson_handlesIntegerCodeGracefully() {
        // Backend endpoint chưa migrate trả code kiểu Int (ví dụ: 400)
        val jsonString = """
            {
                "success": false,
                "code": 400,
                "message": "Email không hợp lệ"
            }
        """.trimIndent()

        val dto = json.decodeFromString<ApiErrorDto>(jsonString)
        assertFalse(dto.success)
        assertNull(dto.status)
        assertEquals("400", dto.code)
        assertEquals("Email không hợp lệ", dto.message)
    }

    @Test
    fun legacyClient_parsesNewUnifiedJsonWithoutFailure() {
        // Giả lập client phiên bản cũ chỉ có { success, message } parse JSON mới có thêm status, code, fieldErrors
        val jsonString = """
            {
                "success": false,
                "status": 409,
                "code": "CONFLICT",
                "message": "Xung đột trạng thái dữ liệu.",
                "fieldErrors": null,
                "unknown_extra_field": "test"
            }
        """.trimIndent()

        val legacyDto = json.decodeFromString<LegacyOldClientErrorDto>(jsonString)
        assertFalse(legacyDto.success)
        assertEquals("Xung đột trạng thái dữ liệu.", legacyDto.message)
    }

    @Test
    fun stringErrorCodeMapping_takesPrecedenceOverStatus() {
        val testCases = listOf(
            ApiException.CODE_VALIDATION_FAILED to "Dữ liệu nhập không hợp lệ.",
            ApiException.CODE_INVALID_TRANSITION to "Trạng thái trao đổi không hợp lệ.",
            ApiException.CODE_ALREADY_RATED to "Bạn đã đánh giá buổi trao đổi này rồi.",
            ApiException.CODE_NOT_PARTICIPANT to "Bạn không phải là người tham gia buổi trao đổi này.",
            ApiException.CODE_UNAUTHORIZED to "Chưa xác thực. Vui lòng đăng nhập.",
            ApiException.CODE_FORBIDDEN to "Bạn không có quyền thực hiện thao tác này.",
            ApiException.CODE_NOT_FOUND to "Không tìm thấy dữ liệu yêu cầu.",
            ApiException.CODE_CONFLICT to "Dữ liệu bị xung đột hoặc đã được cập nhật.",
            ApiException.CODE_PAYLOAD_TOO_LARGE to "Kích thước tập tin vượt quá giới hạn cho phép.",
            ApiException.CODE_UNSUPPORTED_MEDIA to "Định dạng tập tin không được hỗ trợ.",
            ApiException.CODE_RATE_LIMITED to "Bạn đã gửi quá nhiều yêu cầu. Vui lòng thử lại sau ít phút.",
            ApiException.CODE_INTERNAL to "Lỗi máy chủ. Vui lòng thử lại sau.",
            ApiException.CODE_BAD_REQUEST to "Yêu cầu không hợp lệ."
        )

        for ((code, expectedMessage) in testCases) {
            val resolved = ApiException.resolveVietnameseMessage(code, status = 500)
            assertEquals("Code $code should map to expected Vietnamese message", expectedMessage, resolved)
        }
    }

    @Test
    fun statusFallback_mapsCorrectlyWhenCodeIsNull() {
        val statusCases = listOf(
            400 to "Yêu cầu không hợp lệ.",
            401 to "Chưa xác thực. Vui lòng đăng nhập.",
            403 to "Bạn không có quyền thực hiện thao tác này.",
            404 to "Không tìm thấy dữ liệu yêu cầu.",
            409 to "Dữ liệu bị xung đột hoặc đã được cập nhật.",
            413 to "Kích thước tập tin vượt quá giới hạn cho phép.",
            415 to "Định dạng tập tin không được hỗ trợ.",
            422 to "Dữ liệu nhập không hợp lệ.",
            429 to "Bạn đã gửi quá nhiều yêu cầu. Vui lòng thử lại sau ít phút.",
            500 to "Lỗi máy chủ. Vui lòng thử lại sau."
        )

        for ((status, expectedMessage) in statusCases) {
            val resolved = ApiException.resolveVietnameseMessage(code = null, status = status)
            assertEquals("Status $status should map to fallback message", expectedMessage, resolved)
        }
    }

    @Test
    fun authEndpoint401_usesSpecificCredentialsMessage() {
        val errorDto = ApiErrorDto(
            status = 401,
            code = ApiException.CODE_UNAUTHORIZED,
            message = "Email hoặc mật khẩu không đúng"
        )

        val exception = ApiException.fromResponse(401, errorDto, isAuthEndpoint = true)
        assertEquals(401, exception.status)
        assertEquals(ApiException.CODE_UNAUTHORIZED, exception.code)
        assertEquals("Email hoặc mật khẩu không đúng", exception.message)
    }

    @Test
    fun normalRequest401_doesNotShowExpiredWhenRefreshCanHandle() {
        val errorDto = ApiErrorDto(
            status = 401,
            code = ApiException.CODE_UNAUTHORIZED,
            message = null
        )

        val exception = ApiException.fromResponse(401, errorDto, isAuthEndpoint = false)
        assertEquals(401, exception.status)
        assertEquals(ApiException.CODE_UNAUTHORIZED, exception.code)
        assertEquals("Chưa xác thực. Vui lòng đăng nhập.", exception.message)
    }

    @Test
    fun customBusinessMessageFromServer_isPreserved() {
        val specificMsg = "Thời gian bắt đầu phải trước thời gian kết thúc"
        val errorDto = ApiErrorDto(
            status = 422,
            code = ApiException.CODE_VALIDATION_FAILED,
            message = specificMsg
        )

        val exception = ApiException.fromResponse(422, errorDto, isAuthEndpoint = false)
        assertEquals(specificMsg, exception.message)
    }
}
