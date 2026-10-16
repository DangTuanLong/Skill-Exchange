package com.skillexchange.api.services.graph

import java.util.concurrent.ConcurrentHashMap

/**
 * Giới hạn tần suất truy vấn đồ thị chu trình (Rate Limiter):
 * Mặc định 20 requests/phút/user bằng sliding window in-memory.
 * Lưu ý: Áp dụng cho từng server instance hiện tại (in-memory per-instance).
 */
class GraphRateLimiter(
    private val maxPerMinute: Int = 20
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
