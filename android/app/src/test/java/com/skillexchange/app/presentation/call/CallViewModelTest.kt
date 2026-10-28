package com.skillexchange.app.presentation.call

import androidx.lifecycle.SavedStateHandle
import com.skillexchange.app.core.call.AppAudioDevice
import com.skillexchange.app.core.call.AudioRoutingManager
import com.skillexchange.app.core.call.CallEngineStatus
import com.skillexchange.app.core.call.FakeCallEngine
import com.skillexchange.app.core.network.ApiException
import com.skillexchange.app.core.security.TokenManager
import com.skillexchange.app.data.remote.exchange.CreateExchangeRequestDto
import com.skillexchange.app.domain.model.call.CallSession
import com.skillexchange.app.domain.model.call.CallToken
import com.skillexchange.app.domain.model.call.CallType
import com.skillexchange.app.domain.model.call.CallUiStatus
import com.skillexchange.app.domain.model.exchange.ExchangeRequest
import com.skillexchange.app.domain.model.exchange.ExchangeStatus
import com.skillexchange.app.domain.model.exchange.MeetingMode
import com.skillexchange.app.domain.repository.ICallRepository
import com.skillexchange.app.domain.repository.IExchangeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class CallViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val tokenManager = TokenManager(null).apply { saveUserId("user_caller") }

    private var sampleExchange = ExchangeRequest(
        id = "ex_123",
        senderId = "user_caller",
        receiverId = "user_receiver",
        senderName = "Người gọi",
        receiverName = "Đối tác nhận",
        senderAvatarUrl = "https://avatar.com/caller.jpg",
        receiverAvatarUrl = "https://avatar.com/receiver.jpg",
        skillOfferedId = 1,
        skillOfferedName = "Lập trình Kotlin",
        skillWantedId = 2,
        skillWantedName = "Thiết kế UI/UX",
        status = ExchangeStatus.ACCEPTED,
        durationMinutes = 60,
        meetingMode = MeetingMode.ONLINE,
        scheduledAt = "2026-10-30T10:00:00Z",
        createdAt = "2026-10-25T00:00:00Z",
        updatedAt = "2026-10-25T00:00:00Z"
    )

    private val mockExchangeRepo = object : IExchangeRepository {
        override suspend fun createExchangeRequest(dto: CreateExchangeRequestDto): Result<ExchangeRequest> = error("Not used")
        override suspend fun getIncomingRequests(): Result<List<ExchangeRequest>> = error("Not used")
        override suspend fun getOutgoingRequests(): Result<List<ExchangeRequest>> = error("Not used")
        override suspend fun getExchangeRequest(id: String): Result<ExchangeRequest> = Result.success(sampleExchange)
        override suspend fun acceptRequest(id: String): Result<ExchangeRequest> = error("Not used")
        override suspend fun rejectRequest(id: String): Result<ExchangeRequest> = error("Not used")
        override suspend fun cancelRequest(id: String, reason: String?): Result<ExchangeRequest> = error("Not used")
        override suspend fun completeRequest(id: String): Result<ExchangeRequest> = error("Not used")
    }

    private var initiateCallResult: Result<CallToken> = Result.success(
        CallToken(
            url = "wss://skillexchange.livekit.cloud",
            roomName = "call_test_room",
            token = "dummy_token",
            callType = CallType.VOICE,
            sessionId = "session_123"
        )
    )

    private var joinCallResult: Result<CallToken> = Result.success(
        CallToken(
            url = "wss://skillexchange.livekit.cloud",
            roomName = "call_test_room",
            token = "dummy_token",
            callType = CallType.VOICE,
            sessionId = "session_incoming_456"
        )
    )

    private var endCallInvoked = false
    private val mockCallRepo = object : ICallRepository {
        override suspend fun initiateCall(exchangeId: String, callType: CallType): Result<CallToken> = initiateCallResult
        override suspend fun joinCall(sessionId: String): Result<CallToken> = joinCallResult
        override suspend fun notifyConnected(sessionId: String): Result<CallSession> = Result.success(
            CallSession(
                id = sessionId,
                exchangeId = "ex_123",
                callerId = "user_caller",
                receiverId = "user_receiver",
                roomName = "call_test_room",
                callType = CallType.VOICE,
                status = "CONNECTED",
                startedAt = "2026-10-28T00:00:00Z",
                connectedAt = "2026-10-28T00:00:00Z",
                endedAt = null,
                durationSeconds = 0
            )
        )
        override suspend fun rejectCall(sessionId: String): Result<CallSession> = error("Not used")
        override suspend fun endCall(sessionId: String): Result<Int> {
            endCallInvoked = true
            return Result.success(120)
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        endCallInvoked = false
        sampleExchange = sampleExchange.copy(status = ExchangeStatus.ACCEPTED)
        initiateCallResult = Result.success(
            CallToken(
                url = "wss://skillexchange.livekit.cloud",
                roomName = "call_test_room",
                token = "dummy_token",
                callType = CallType.VOICE,
                sessionId = "session_123"
            )
        )
        joinCallResult = Result.success(
            CallToken(
                url = "wss://skillexchange.livekit.cloud",
                roomName = "call_test_room",
                token = "dummy_token",
                callType = CallType.VOICE,
                sessionId = "session_incoming_456"
            )
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        exchangeId: String = "ex_123",
        isVideo: Boolean = false,
        sessionId: String? = null,
        isIncoming: Boolean = false,
        fakeEngine: FakeCallEngine = FakeCallEngine(autoConnect = true)
    ): CallViewModel {
        val args = mutableMapOf<String, Any>("exchangeId" to exchangeId, "isVideo" to isVideo)
        sessionId?.let { args["sessionId"] = it }
        if (isIncoming) args["isIncoming"] = true
        val savedStateHandle = SavedStateHandle(args)
        return CallViewModel(
            savedStateHandle = savedStateHandle,
            callRepository = mockCallRepo,
            callEngine = fakeEngine,
            exchangeRepository = mockExchangeRepo,
            tokenManager = tokenManager
        )
    }

    @Test
    fun testInitCall_Success_SetsStateToConnectedAndIncrementsDuration() = runTest(testDispatcher) {
        val fakeEngine = FakeCallEngine(autoConnect = true)
        val viewModel = createViewModel(fakeEngine = fakeEngine)

        testScheduler.runCurrent()

        val state = viewModel.state.value
        assertEquals("session_123", state.sessionId)
        assertEquals("Đối tác nhận", state.partnerName)
        assertEquals("Lập trình Kotlin", state.skillName)
        assertEquals(CallUiStatus.CONNECTED, state.uiStatus)

        // Advance 3 seconds -> duration increases
        advanceTimeBy(3100L)
        testScheduler.runCurrent()
        assertTrue(viewModel.state.value.durationSeconds >= 3)

        viewModel.onIntent(CallIntent.EndCall)
    }

    @Test
    fun testInitCall_ExchangeNotAccepted_SetsErrorState() = runTest(testDispatcher) {
        sampleExchange = sampleExchange.copy(status = ExchangeStatus.PENDING)
        val viewModel = createViewModel()

        testScheduler.runCurrent()

        val state = viewModel.state.value
        assertEquals(CallUiStatus.ERROR, state.uiStatus)
        assertTrue(state.errorMessage?.contains("chưa được chấp nhận") == true)
        assertFalse(state.canRetry)
    }

    @Test
    fun testInitCall_UserBusy_SetsBusyState() = runTest(testDispatcher) {
        initiateCallResult = Result.failure(
            ApiException(409, ApiException.CODE_USER_BUSY, "Người dùng đang bận")
        )
        val viewModel = createViewModel()

        testScheduler.runCurrent()

        val state = viewModel.state.value
        assertEquals(CallUiStatus.BUSY, state.uiStatus)
        assertTrue(state.errorMessage?.contains("bận") == true)
    }

    @Test
    fun testInitCall_CallCooldownActive_SetsErrorWithRetry() = runTest(testDispatcher) {
        initiateCallResult = Result.failure(
            ApiException(429, ApiException.CODE_CALL_COOLDOWN_ACTIVE, "Vui lòng đợi 10 giây trước khi gọi lại")
        )
        val viewModel = createViewModel()

        testScheduler.runCurrent()

        val state = viewModel.state.value
        assertEquals(CallUiStatus.ERROR, state.uiStatus)
        assertTrue(state.errorMessage?.contains("10 giây") == true)
        assertTrue(state.canRetry)
    }

    @Test
    fun testInitCall_LiveKitNotConfigured_SetsErrorWithoutRetry() = runTest(testDispatcher) {
        initiateCallResult = Result.failure(
            ApiException(503, ApiException.CODE_LIVEKIT_NOT_CONFIGURED, "Dịch vụ gọi chưa sẵn sàng")
        )
        val viewModel = createViewModel()

        testScheduler.runCurrent()

        val state = viewModel.state.value
        assertEquals(CallUiStatus.ERROR, state.uiStatus)
        assertFalse(state.canRetry)
    }

    @Test
    fun testInitCall_NetworkError_SetsErrorWithRetry() = runTest(testDispatcher) {
        initiateCallResult = Result.failure(IOException("Không thể kết nối đến máy chủ"))
        val viewModel = createViewModel()

        testScheduler.runCurrent()

        val state = viewModel.state.value
        assertEquals(CallUiStatus.ERROR, state.uiStatus)
        assertTrue(state.canRetry)
    }

    @Test
    fun testToggleMute_TogglesEngineState() = runTest(testDispatcher) {
        val fakeEngine = FakeCallEngine()
        val viewModel = createViewModel(fakeEngine = fakeEngine)
        testScheduler.runCurrent()

        assertFalse(viewModel.state.value.isMuted)

        viewModel.onIntent(CallIntent.ToggleMute)
        testScheduler.runCurrent()
        assertTrue(viewModel.state.value.isMuted)

        viewModel.onIntent(CallIntent.ToggleMute)
        testScheduler.runCurrent()
        assertFalse(viewModel.state.value.isMuted)

        viewModel.onIntent(CallIntent.EndCall)
    }

    @Test
    fun testToggleSpeaker_TogglesEngineState() = runTest(testDispatcher) {
        val fakeEngine = FakeCallEngine()
        val viewModel = createViewModel(fakeEngine = fakeEngine)
        testScheduler.runCurrent()

        assertFalse(viewModel.state.value.isSpeakerOn)

        viewModel.onIntent(CallIntent.ToggleSpeaker)
        testScheduler.runCurrent()
        assertTrue(viewModel.state.value.isSpeakerOn)

        viewModel.onIntent(CallIntent.ToggleSpeaker)
        testScheduler.runCurrent()
        assertFalse(viewModel.state.value.isSpeakerOn)

        viewModel.onIntent(CallIntent.EndCall)
    }

    @Test
    fun testEndCall_DisconnectsEngineAndNavigatesBack() = runTest(testDispatcher) {
        val fakeEngine = FakeCallEngine()
        val viewModel = createViewModel(fakeEngine = fakeEngine)
        testScheduler.runCurrent()

        var sideEffect: CallSideEffect? = null
        val job = launch {
            sideEffect = viewModel.effect.first()
        }

        viewModel.onIntent(CallIntent.EndCall)
        testScheduler.runCurrent()

        assertTrue(endCallInvoked)
        assertEquals(CallEngineStatus.DISCONNECTED, fakeEngine.status.value)
        assertEquals(CallSideEffect.NavigateBack, sideEffect)

        job.cancel()
    }

    @Test
    fun testRingingTimeout_Exceeds30Seconds_SetsMissedState() = runTest(testDispatcher) {
        val fakeEngine = FakeCallEngine(autoConnect = false)
        val viewModel = createViewModel(fakeEngine = fakeEngine)

        testScheduler.runCurrent()
        assertEquals(CallUiStatus.RINGING, viewModel.state.value.uiStatus)

        // Advance 31 seconds -> triggers 30s timeout
        advanceTimeBy(31_000L)
        testScheduler.runCurrent()

        assertEquals(CallUiStatus.MISSED, viewModel.state.value.uiStatus)
        assertTrue(viewModel.state.value.errorMessage?.contains("Quá 30 giây") == true)
        assertEquals(CallEngineStatus.DISCONNECTED, fakeEngine.status.value)
    }

    @Test
    fun testIncomingCall_Success_JoinsAndConnectsEngine() = runTest(testDispatcher) {
        val fakeEngine = FakeCallEngine(autoConnect = true)
        val viewModel = createViewModel(
            exchangeId = "ex_123",
            sessionId = "session_incoming_456",
            isIncoming = true,
            fakeEngine = fakeEngine
        )

        testScheduler.runCurrent()

        assertEquals("session_incoming_456", viewModel.state.value.sessionId)
        assertEquals(CallUiStatus.CONNECTED, viewModel.state.value.uiStatus)
        assertEquals(CallEngineStatus.CONNECTED, fakeEngine.status.value)

        // Dọn dẹp duration timer
        viewModel.onIntent(CallIntent.EndCall)
        testScheduler.runCurrent()
    }

    @Test
    fun testIncomingCall_CallAlreadyEnded_ShowsErrorWithoutCrash() = runTest(testDispatcher) {
        joinCallResult = Result.failure(ApiException(status = 409, code = "CONFLICT", message = "Cuộc gọi đã kết thúc"))
        val fakeEngine = FakeCallEngine(autoConnect = false)
        val viewModel = createViewModel(
            exchangeId = "ex_123",
            sessionId = "session_incoming_456",
            isIncoming = true,
            fakeEngine = fakeEngine
        )

        testScheduler.runCurrent()

        assertEquals(CallUiStatus.ENDED, viewModel.state.value.uiStatus)
        assertTrue(viewModel.state.value.errorMessage?.contains("kết thúc") == true)
        assertEquals(CallEngineStatus.IDLE, fakeEngine.status.value)
    }

    @Test
    fun testAudioRouting_SelectDevice_UpdatesSelectedDevice() = runTest(testDispatcher) {
        val fakeEngine = FakeCallEngine(autoConnect = true)
        val viewModel = createViewModel(fakeEngine = fakeEngine)
        testScheduler.runCurrent()

        assertEquals(AppAudioDevice.EARPIECE, viewModel.state.value.selectedAudioDevice)
        assertFalse(viewModel.state.value.isSpeakerOn)

        viewModel.onIntent(CallIntent.SelectAudioDevice(AppAudioDevice.SPEAKER))
        testScheduler.runCurrent()

        assertEquals(AppAudioDevice.SPEAKER, viewModel.state.value.selectedAudioDevice)
        assertTrue(viewModel.state.value.isSpeakerOn)

        viewModel.onIntent(CallIntent.SelectAudioDevice(AppAudioDevice.EARPIECE))
        testScheduler.runCurrent()

        assertEquals(AppAudioDevice.EARPIECE, viewModel.state.value.selectedAudioDevice)
        assertFalse(viewModel.state.value.isSpeakerOn)

        viewModel.onIntent(CallIntent.EndCall)
        testScheduler.runCurrent()
    }

    @Test
    fun testAudioRouting_BluetoothWithoutPermission_RequestsPermission() = runTest(testDispatcher) {
        val fakeEngine = FakeCallEngine(autoConnect = true)
        fakeEngine.simulateAvailableDevices(setOf(AppAudioDevice.EARPIECE, AppAudioDevice.SPEAKER, AppAudioDevice.BLUETOOTH))
        val viewModel = createViewModel(fakeEngine = fakeEngine)
        testScheduler.runCurrent()

        var sideEffect: CallSideEffect? = null
        val job = launch {
            sideEffect = viewModel.effect.first()
        }

        viewModel.onIntent(CallIntent.SelectAudioDevice(AppAudioDevice.BLUETOOTH))
        testScheduler.runCurrent()

        assertEquals(CallSideEffect.RequestBluetoothPermission, sideEffect)
        job.cancel()

        var snackbarEffect: CallSideEffect? = null
        val job2 = launch {
            snackbarEffect = viewModel.effect.first()
        }

        // Người dùng từ chối cấp quyền Bluetooth
        viewModel.onIntent(CallIntent.BluetoothPermissionResult(granted = false))
        testScheduler.runCurrent()

        assertTrue(snackbarEffect is CallSideEffect.ShowSnackbar)
        assertTrue((snackbarEffect as CallSideEffect.ShowSnackbar).message.contains("bị từ chối"))
        job2.cancel()

        viewModel.onIntent(CallIntent.EndCall)
        testScheduler.runCurrent()
    }

    @Test
    fun testAudioRouting_BluetoothGranted_SwitchesToBluetooth() = runTest(testDispatcher) {
        val fakeEngine = FakeCallEngine(autoConnect = true)
        fakeEngine.simulateAvailableDevices(setOf(AppAudioDevice.EARPIECE, AppAudioDevice.SPEAKER, AppAudioDevice.BLUETOOTH))
        val viewModel = createViewModel(fakeEngine = fakeEngine)
        testScheduler.runCurrent()

        viewModel.onIntent(CallIntent.BluetoothPermissionResult(granted = true))
        testScheduler.runCurrent()

        assertEquals(AppAudioDevice.BLUETOOTH, viewModel.state.value.selectedAudioDevice)

        viewModel.onIntent(CallIntent.EndCall)
        testScheduler.runCurrent()
    }

    @Test
    fun testAudioFocus_SystemCallInterruption_ShowsInterruptedState() = runTest(testDispatcher) {
        val fakeEngine = FakeCallEngine(autoConnect = true)
        val viewModel = createViewModel(fakeEngine = fakeEngine)
        testScheduler.runCurrent()

        assertFalse(viewModel.state.value.isInterruptedBySystemCall)
        assertFalse(viewModel.state.value.isMutedByAudioFocus)

        // Mô phỏng cuộc gọi SIM chen ngang
        fakeEngine.simulateAudioFocusChange(AudioRoutingManager.AUDIOFOCUS_LOSS_TRANSIENT)
        testScheduler.runCurrent()

        assertTrue(viewModel.state.value.isInterruptedBySystemCall)
        assertTrue(viewModel.state.value.isMutedByAudioFocus)

        // Mô phỏng cuộc gọi SIM kết thúc
        fakeEngine.simulateAudioFocusChange(AudioRoutingManager.AUDIOFOCUS_GAIN)
        testScheduler.runCurrent()

        assertFalse(viewModel.state.value.isInterruptedBySystemCall)
        assertFalse(viewModel.state.value.isMutedByAudioFocus)

        viewModel.onIntent(CallIntent.EndCall)
        testScheduler.runCurrent()
    }
}
