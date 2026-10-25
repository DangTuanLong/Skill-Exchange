package com.skillexchange.api.services.auth

import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.origin
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Trích xuất IP khách (Client IP) an toàn đằng sau Reverse Proxy của Render.
 *
 * Lưu ý theo tài liệu vận hành của Render:
 * - Render reverse proxy không cấu hình Cloudflare phía trước trên tên miền mặc định (*.onrender.com),
 *   do đó KHÔNG tin cậy `CF-Connecting-IP` (client có thể tự gửi header giả mạo để bypass rate limiter).
 * - Reverse proxy của Render tự động nối (append) địa chỉ IP của client thực tế vào CUỐI danh sách `X-Forwarded-For`.
 *   Nếu client tự ý gửi header giả mạo dạng `X-Forwarded-For: client_fake_ip`, proxy của Render sẽ chuyển tiếp thành:
 *   `X-Forwarded-For: client_fake_ip, real_client_ip`.
 * - Do đó, phần tử CUỐI CÙNG (last element) sau khi split(',') và trim khoảng trắng là IP đáng tin cậy nhất
 *   do Render proxy ghi nhận.
 * - Fallback: `request.local.remoteAddress` cho môi trường phát triển cục bộ hoặc kết nối trực tiếp.
 */
fun ApplicationCall.getClientIp(): String {
    val xForwardedFor = request.headers["X-Forwarded-For"]
    if (!xForwardedFor.isNullOrBlank()) {
        val clientIp = xForwardedFor.split(",")
            .map { it.trim() }
            .lastOrNull { it.isNotBlank() }
        if (!clientIp.isNullOrBlank()) return clientIp
    }

    return request.local.remoteAddress
}

/**
 * Bộ kiểm soát tần suất truy cập In-Memory cho các endpoint Authentication (TASK-052).
 *
 * Lưu ý kiến trúc & Giới hạn:
 * - Bộ đếm lưu trữ trong RAM (ConcurrentHashMap) của một instance Ktor duy nhất.
 * - Khi server Render restart hoặc thức dậy từ chế độ ngủ (cold start), dữ liệu bộ đếm sẽ được đặt lại.
 * - Hỗ trợ dọn dẹp (eviction) tự động nhằm chống tràn bộ nhớ (memory leak / DoS heap).
 */
class AuthRateLimiter(
    val maxLoginPerMinute: Int = 5,
    val maxOtpPerMinute: Int = 5,
    val maxRegisterPerMinute: Int = 3,
    private val windowMs: Long = 60_000L
) {
    // Lưu danh sách timestamps truy cập theo key
    private val timestampsMap = ConcurrentHashMap<String, MutableList<Long>>()
    private val cleanupCounter = AtomicInteger(0)

    /**
     * Kiểm tra và ghi nhận lượt thử đăng nhập (khóa theo Email VÀ IP).
     */
    fun tryAcquireLogin(email: String, ip: String): Boolean {
        val key = "login:${email.lowercase().trim()}:$ip"
        return tryAcquire(key, maxLoginPerMinute)
    }

    /**
     * Kiểm tra và ghi nhận lượt thử xác thực OTP (khóa theo Email VÀ IP).
     */
    fun tryAcquireOtp(email: String, ip: String): Boolean {
        val key = "otp:${email.lowercase().trim()}:$ip"
        return tryAcquire(key, maxOtpPerMinute)
    }

    /**
     * Kiểm tra và ghi nhận lượt đăng ký tài khoản (khóa theo IP).
     */
    fun tryAcquireRegister(ip: String): Boolean {
        val key = "register:$ip"
        return tryAcquire(key, maxRegisterPerMinute)
    }

    private fun tryAcquire(key: String, maxLimit: Int): Boolean {
        val now = System.currentTimeMillis()
        val list = timestampsMap.computeIfAbsent(key) { mutableListOf() }

        val allowed = synchronized(list) {
            list.removeAll { it < now - windowMs }
            if (list.size >= maxLimit) {
                false
            } else {
                list.add(now)
                true
            }
        }

        // Định kỳ mỗi 50 thao tác thì quét dọn sạch các key rỗng / hết hạn để không phình RAM
        if (cleanupCounter.incrementAndGet() % 50 == 0) {
            cleanupExpired(now)
        }

        return allowed
    }

    /**
     * Dọn dẹp các entry cũ đã hết hạn khỏi bộ nhớ.
     */
    fun cleanupExpired(now: Long = System.currentTimeMillis()) {
        val iterator = timestampsMap.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val list = entry.value
            synchronized(list) {
                list.removeAll { it < now - windowMs }
                if (list.isEmpty()) {
                    iterator.remove()
                }
            }
        }
    }

    fun reset() {
        timestampsMap.clear()
        cleanupCounter.set(0)
    }
}
