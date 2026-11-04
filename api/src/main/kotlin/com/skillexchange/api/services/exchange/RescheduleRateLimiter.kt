package com.skillexchange.api.services.exchange

import java.util.concurrent.ConcurrentHashMap

/**
 * Giới hạn tần suất đề xuất đổi lịch (Rate Limiter):
 * Mặc định 5 requests/phút/user bằng sliding window in-memory (DEC-025, TASK-069).
 * Áp dụng cho từng server instance hiện tại (in-memory per-instance).
 */
class RescheduleRateLimiter(
    private val maxPerMinute: Int = 5
) {
    private val minuteMap = ConcurrentHashMap<String, MutableList<Long>>()

    fun tryAcquire(userId: String): Boolean {
        val now = System.currentTimeMillis()
        val oneMinuteAgo = now - 60_000L

        val list = minuteMap.compute(userId) { _, currentList ->
            val safeList = currentList ?: mutableListOf()
            safeList.removeAll { it < oneMinuteAgo }
            safeList
        }!!

        synchronized(list) {
            list.removeAll { it < oneMinuteAgo }
            if (list.size >= maxPerMinute) {
                return false
            }
            list.add(now)
            return true
        }
    }

    fun reset() {
        minuteMap.clear()
    }
}

