package com.skillexchange.api.services.notification

/**
 * Bộ giới hạn tần suất truy cập cho Endpoint nội bộ quét nhắc nhở (/api/internal/jobs/process-reminders).
 *
 * Lưu ý nghiệp vụ (TASK-052):
 * - Giới hạn nhẹ nhàng: tối đa 10 requests / phút.
 * - Không khóa theo IP vì các GitHub Actions Runner thay đổi dải IP liên tục trên mỗi lần chạy.
 * - Chống lại hành vi spam/DDoS làm cạn kiệt tài nguyên CPU/DB hoặc tấn công dò secret.
 */
class ReminderRateLimiter(
    private val maxPerMinute: Int = 10,
    private val windowMs: Long = 60_000L
) {
    private val timestamps = mutableListOf<Long>()

    @Synchronized
    fun tryAcquire(): Boolean {
        val now = System.currentTimeMillis()
        timestamps.removeAll { it < now - windowMs }
        return if (timestamps.size >= maxPerMinute) {
            false
        } else {
            timestamps.add(now)
            true
        }
    }

    @Synchronized
    fun reset() {
        timestamps.clear()
    }
}
