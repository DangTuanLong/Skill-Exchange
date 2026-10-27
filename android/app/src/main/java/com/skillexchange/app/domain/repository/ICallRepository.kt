package com.skillexchange.app.domain.repository

import com.skillexchange.app.domain.model.call.CallSession
import com.skillexchange.app.domain.model.call.CallToken
import com.skillexchange.app.domain.model.call.CallType

interface ICallRepository {
    suspend fun initiateCall(exchangeId: String, callType: CallType): Result<CallToken>
    suspend fun joinCall(sessionId: String): Result<CallToken>
    suspend fun notifyConnected(sessionId: String): Result<CallSession>
    suspend fun rejectCall(sessionId: String): Result<CallSession>
    suspend fun endCall(sessionId: String): Result<Int> // Trả về durationSeconds
}
