package com.skillexchange.api.services.nlp

import java.util.concurrent.ConcurrentHashMap

class NlpRateLimiter(
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
