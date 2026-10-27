package com.skillexchange.app.data.repository

import com.skillexchange.app.data.remote.call.CallRemoteDataSource
import com.skillexchange.app.domain.model.call.CallSession
import com.skillexchange.app.domain.model.call.CallToken
import com.skillexchange.app.domain.model.call.CallType
import com.skillexchange.app.domain.repository.ICallRepository

class CallRepositoryImpl(
    private val remoteDataSource: CallRemoteDataSource
) : ICallRepository {

    override suspend fun initiateCall(exchangeId: String, callType: CallType): Result<CallToken> = runCatching {
        val resp = remoteDataSource.initiateCall(exchangeId, callType.name)
        CallToken(
            url = resp.url,
            roomName = resp.roomName,
            token = resp.token,
            callType = CallType.fromString(resp.callType),
            sessionId = resp.sessionId
        )
    }

    override suspend fun joinCall(sessionId: String): Result<CallToken> = runCatching {
        val resp = remoteDataSource.joinCall(sessionId)
        CallToken(
            url = resp.url,
            roomName = resp.roomName,
            token = resp.token,
            callType = CallType.fromString(resp.callType),
            sessionId = resp.sessionId
        )
    }

    override suspend fun notifyConnected(sessionId: String): Result<CallSession> = runCatching {
        val resp = remoteDataSource.notifyConnected(sessionId)
        CallSession(
            id = resp.id,
            exchangeId = resp.exchangeId,
            callerId = resp.callerId,
            receiverId = resp.receiverId,
            roomName = resp.roomName,
            callType = CallType.fromString(resp.callType),
            status = resp.status,
            startedAt = resp.startedAt,
            connectedAt = resp.connectedAt,
            endedAt = resp.endedAt,
            durationSeconds = resp.durationSeconds
        )
    }

    override suspend fun rejectCall(sessionId: String): Result<CallSession> = runCatching {
        val resp = remoteDataSource.rejectCall(sessionId)
        CallSession(
            id = resp.id,
            exchangeId = resp.exchangeId,
            callerId = resp.callerId,
            receiverId = resp.receiverId,
            roomName = resp.roomName,
            callType = CallType.fromString(resp.callType),
            status = resp.status,
            startedAt = resp.startedAt,
            connectedAt = resp.connectedAt,
            endedAt = resp.endedAt,
            durationSeconds = resp.durationSeconds
        )
    }

    override suspend fun endCall(sessionId: String): Result<Int> = runCatching {
        val resp = remoteDataSource.endCall(sessionId)
        resp.durationSeconds
    }
}
