package com.skillexchange.app.core.call

import com.skillexchange.app.domain.model.call.CallSession
import com.skillexchange.app.domain.model.call.CallToken
import com.skillexchange.app.domain.model.call.CallType
import com.skillexchange.app.domain.repository.ICallRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InAppCallManagerTest {

    private val testDispatcher = StandardTestDispatcher()

    private var joinCallResult: Result<CallToken> = Result.success(
        CallToken(
            url = "wss://test.livekit.cloud",
            roomName = "call_room_1",
            token = "jwt_token_123",
            callType = CallType.VOICE,
            sessionId = "session_123"
        )
    )

    private var notifyConnectedResult: Result<CallSession> = Result.success(
        CallSession(
            id = "session_123",
            exchangeId = "ex_123",
            callerId = "caller_1",
            receiverId = "receiver_1",
            roomName = "call_room_1",
            callType = CallType.VOICE,
            status = "CONNECTED",
            startedAt = "2026-10-28T00:00:00Z",
            connectedAt = null,
            endedAt = null,
            durationSeconds = 0
        )
    )

    private var rejectCallCalled = false

    private val fakeCallRepository = object : ICallRepository {
        override suspend fun initiateCall(exchangeId: String, callType: CallType): Result<CallToken> =
            Result.failure(NotImplementedError())

        override suspend fun joinCall(sessionId: String): Result<CallToken> = joinCallResult

        override suspend fun notifyConnected(sessionId: String): Result<CallSession> = notifyConnectedResult

        override suspend fun rejectCall(sessionId: String): Result<CallSession> {
            rejectCallCalled = true
            return Result.success(
                CallSession(
                    id = sessionId,
                    exchangeId = "ex_123",
                    callerId = "caller_1",
                    receiverId = "receiver_1",
                    roomName = "call_room_1",
                    callType = CallType.VOICE,
                    status = "REJECTED",
                    startedAt = "2026-10-28T00:00:00Z",
                    connectedAt = null,
                    endedAt = null,
                    durationSeconds = 0
                )
            )
        }

        override suspend fun endCall(sessionId: String): Result<Int> = Result.success(0)
    }

    private lateinit var inAppCallManager: InAppCallManager

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        rejectCallCalled = false
        inAppCallManager = InAppCallManager(
            callRepository = fakeCallRepository,
            coroutineScope = kotlinx.coroutines.CoroutineScope(testDispatcher)
        )
    }

    @After
    fun tearDown() {
        inAppCallManager.dismissIncomingCall()
        Dispatchers.resetMain()
    }

    @Test
    fun `onIncomingCallPushReceived validates with server and emits incomingCall when valid`() = runTest(testDispatcher) {
        inAppCallManager.onIncomingCallPushReceived(
            sessionId = "session_123",
            callerId = "caller_1",
            callerName = "Nguyễn Văn A",
            callType = "VOICE",
            exchangeId = "ex_123"
        )
        runCurrent()

        val call = inAppCallManager.incomingCall.value
        assertNotNull(call)
        assertEquals("session_123", call?.sessionId)
        assertEquals("Nguyễn Văn A", call?.callerName)
        assertEquals("VOICE", call?.callType)

        inAppCallManager.dismissIncomingCall()
    }

    @Test
    fun `onIncomingCallPushReceived does not emit incomingCall when server returns error`() = runTest(testDispatcher) {
        joinCallResult = Result.failure(Exception("Cuộc gọi đã kết thúc"))

        inAppCallManager.onIncomingCallPushReceived(
            sessionId = "session_123",
            callerId = "caller_1",
            callerName = "Nguyễn Văn A",
            callType = "VOICE",
            exchangeId = "ex_123"
        )
        runCurrent()

        assertNull(inAppCallManager.incomingCall.value)
    }

    @Test
    fun `onCallCancelledPushReceived clears incomingCall immediately`() = runTest(testDispatcher) {
        inAppCallManager.onIncomingCallPushReceived(
            sessionId = "session_123",
            callerId = "caller_1",
            callerName = "Nguyễn Văn A",
            callType = "VOICE",
            exchangeId = "ex_123"
        )
        runCurrent()
        assertNotNull(inAppCallManager.incomingCall.value)

        inAppCallManager.onCallCancelledPushReceived("session_123")
        runCurrent()

        assertNull(inAppCallManager.incomingCall.value)
    }

    @Test
    fun `rejectIncomingCall calls repository and clears call state`() = runTest(testDispatcher) {
        inAppCallManager.onIncomingCallPushReceived(
            sessionId = "session_123",
            callerId = "caller_1",
            callerName = "Nguyễn Văn A",
            callType = "VOICE",
            exchangeId = "ex_123"
        )
        runCurrent()

        inAppCallManager.rejectIncomingCall()
        runCurrent()

        assertTrue(rejectCallCalled)
        assertNull(inAppCallManager.incomingCall.value)
    }

    @Test
    fun `acceptIncomingCall calls notifyConnected and invokes onSuccess callback`() = runTest(testDispatcher) {
        inAppCallManager.onIncomingCallPushReceived(
            sessionId = "session_123",
            callerId = "caller_1",
            callerName = "Nguyễn Văn A",
            callType = "VOICE",
            exchangeId = "ex_123"
        )
        runCurrent()

        var acceptedData: IncomingCallData? = null
        var errorMessage: String? = null

        inAppCallManager.acceptIncomingCall(
            onSuccess = { acceptedData = it },
            onError = { errorMessage = it }
        )
        runCurrent()

        assertNotNull(acceptedData)
        assertEquals("session_123", acceptedData?.sessionId)
        assertNull(errorMessage)
        assertNull(inAppCallManager.incomingCall.value)
    }

    @Test
    fun `acceptIncomingCall invokes onError when notifyConnected fails`() = runTest(testDispatcher) {
        notifyConnectedResult = Result.failure(Exception("Cuộc gọi đã kết thúc"))

        inAppCallManager.onIncomingCallPushReceived(
            sessionId = "session_123",
            callerId = "caller_1",
            callerName = "Nguyễn Văn A",
            callType = "VOICE",
            exchangeId = "ex_123"
        )
        runCurrent()

        var acceptedData: IncomingCallData? = null
        var errorMessage: String? = null

        inAppCallManager.acceptIncomingCall(
            onSuccess = { acceptedData = it },
            onError = { errorMessage = it }
        )
        runCurrent()

        assertNull(acceptedData)
        assertEquals("Cuộc gọi đã kết thúc", errorMessage)
        assertNull(inAppCallManager.incomingCall.value)
    }

    @Test
    fun `ringing timeout automatically clears incoming call after 30 seconds`() = runTest(testDispatcher) {
        inAppCallManager.onIncomingCallPushReceived(
            sessionId = "session_123",
            callerId = "caller_1",
            callerName = "Nguyễn Văn A",
            callType = "VOICE",
            exchangeId = "ex_123"
        )
        runCurrent()
        assertNotNull(inAppCallManager.incomingCall.value)

        // Sau 29s vẫn còn
        advanceTimeBy(29_000L)
        runCurrent()
        assertNotNull(inAppCallManager.incomingCall.value)

        // Sau 31s đã timeout
        advanceTimeBy(2_000L)
        runCurrent()
        assertNull(inAppCallManager.incomingCall.value)
    }
}
