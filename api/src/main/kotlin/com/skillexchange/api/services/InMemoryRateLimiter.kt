package com.skillexchange.api.services

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory rate limiter per user.
 * Lưu trữ danh sách timestamp của các request gần nhất trong bộ nhớ (sliding window).
 * Lưu ý: Bộ đếm sẽ mất khi server restart và chỉ có hiệu lực trên instance hiện tại (TASK-052).
 */
class InMemoryRateLimiter(
    private val maxRequests: Int = 5,
    private val windowMillis: Long = 60_000L // 1 phút
) {
    private val requestTimestamps = ConcurrentHashMap<String, ArrayDeque<Long>>()

    @Synchronized
    fun tryAcquire(key: String): Boolean {
        val now = System.currentTimeMillis()
        val queue = requestTimestamps.computeIfAbsent(key) { ArrayDeque() }

        // Loại bỏ các request đã quá thời gian sliding window
        while (queue.isNotEmpty() && (now - queue.first()) > windowMillis) {
            queue.removeFirst()
        }

        return if (queue.size < maxRequests) {
            queue.addLast(now)
            true
        } else {
            false
        }
    }

    fun reset(key: String) {
        requestTimestamps.remove(key)
    }

    fun clear() {
        requestTimestamps.clear()
    }
}
