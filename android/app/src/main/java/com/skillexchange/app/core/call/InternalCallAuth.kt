package com.skillexchange.app.core.call

import com.skillexchange.app.core.service.CallForegroundService
import com.skillexchange.app.domain.model.call.CallToken

/**
 * Cơ chế bảo vệ và xác thực Intent cuộc gọi nội bộ (TASK-080 Audit Fix).
 * Ngăn chặn ứng dụng bên thứ ba giả mạo Intent có type=CALL và sessionId tùy ý
 * để kích hoạt trái phép Screen.Call và tự động quay số/kết nối phòng gọi.
 */
object InternalCallAuth {
    @Volatile
    private var authorizedSessionId: String? = null

    @Volatile
    private var cachedTokenSessionId: String? = null

    @Volatile
    private var cachedCallToken: CallToken? = null

    fun authorize(sessionId: String) {
        authorizedSessionId = sessionId
    }

    fun isAuthorized(sessionId: String?): Boolean {
        if (sessionId.isNullOrBlank()) return false
        if (CallForegroundService.isRunning) return true
        val isAuth = (authorizedSessionId == sessionId)
        authorizedSessionId = null
        return isAuth
    }

    fun cacheJoinedToken(sessionId: String, token: CallToken) {
        cachedTokenSessionId = sessionId
        cachedCallToken = token
    }

    fun consumeJoinedToken(sessionId: String): CallToken? {
        val token = if (cachedTokenSessionId == sessionId) cachedCallToken else null
        cachedTokenSessionId = null
        cachedCallToken = null
        return token
    }

    fun clear() {
        authorizedSessionId = null
        cachedTokenSessionId = null
        cachedCallToken = null
    }
}

