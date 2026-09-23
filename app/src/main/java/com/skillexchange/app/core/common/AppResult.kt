package com.skillexchange.app.core.common

/**
 * Wrapper chuẩn cho tất cả kết quả từ domain layer.
 * KHÔNG sử dụng Kotlin built-in Result vì không thể serialize.
 *
 * Sử dụng:
 * ```
 * when (result) {
 *     is AppResult.Success -> { /* use result.data */ }
 *     is AppResult.Error   -> { /* show result.message */ }
 *     is AppResult.Loading -> { /* show loading indicator */ }
 * }
 * ```
 */
sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class Error(val message: String, val code: Int? = null) : AppResult<Nothing>()
    data object Loading : AppResult<Nothing>()
}

/**
 * Helper: chạy block và bắt exception → AppResult
 */
inline fun <T> runCatching(block: () -> T): AppResult<T> {
    return try {
        AppResult.Success(block())
    } catch (e: Exception) {
        AppResult.Error(e.message ?: "Lỗi không xác định")
    }
}
