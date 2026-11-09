package com.skillexchange.app.presentation.call

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skillexchange.app.core.call.AppAudioDevice
import com.skillexchange.app.core.call.CallEngine
import com.skillexchange.app.core.call.CallEngineStatus
import com.skillexchange.app.core.call.InternalCallAuth
import com.skillexchange.app.core.network.ApiException
import com.skillexchange.app.core.security.TokenManager
import com.skillexchange.app.domain.model.call.CallType
import com.skillexchange.app.domain.model.call.CallUiStatus
import com.skillexchange.app.domain.model.exchange.ExchangeStatus
import com.skillexchange.app.domain.repository.ICallRepository
import com.skillexchange.app.domain.repository.IExchangeRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class CallViewModel(
    savedStateHandle: SavedStateHandle,
    private val callRepository: ICallRepository,
    private val callEngine: CallEngine,
    private val exchangeRepository: IExchangeRepository,
    private val tokenManager: TokenManager,
    private val callTonePlayer: com.skillexchange.app.core.call.CallTonePlayer = com.skillexchange.app.core.call.AndroidCallTonePlayer()
) : ViewModel() {

    private val exchangeIdArg: String = savedStateHandle["exchangeId"] ?: ""
    private val isVideoArg: Boolean = savedStateHandle.get<Boolean>("isVideo")
        ?: savedStateHandle.get<String>("isVideo")?.toBooleanStrictOrNull()
        ?: false
    private val sessionIdArg: String = savedStateHandle["sessionId"] ?: ""
    private val isIncomingArg: Boolean = savedStateHandle.get<Boolean>("isIncoming")
        ?: savedStateHandle.get<String>("isIncoming")?.toBooleanStrictOrNull()
        ?: false

    private val _state = MutableStateFlow(
        CallState(
            exchangeId = exchangeIdArg,
            sessionId = sessionIdArg.ifBlank { null },
            isVideo = isVideoArg
        )
    )
    val state: StateFlow<CallState> = _state.asStateFlow()

    val localVideoTrack: StateFlow<io.livekit.android.room.track.VideoTrack?> = callEngine.localVideoTrack
    val remoteVideoTrack: StateFlow<io.livekit.android.room.track.VideoTrack?> = callEngine.remoteVideoTrack
    fun initVideoRenderer(view: io.livekit.android.renderer.SurfaceViewRenderer) = callEngine.initVideoRenderer(view)

    private val _effect = Channel<CallSideEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    private var durationJob: Job? = null
    private var ringingTimeoutJob: Job? = null

    init {
        observeEngine()
        if (isIncomingArg && sessionIdArg.isNotBlank()) {
            loadExchangeAndJoinCall(exchangeIdArg, sessionIdArg, isVideoArg)
        } else if (exchangeIdArg.isNotBlank()) {
            loadExchangeAndStartCall(exchangeIdArg, isVideoArg)
        }
    }

    private fun observeEngine() {
        viewModelScope.launch {
            callEngine.status.collect { engineStatus ->
                when (engineStatus) {
                    CallEngineStatus.CONNECTED -> {
                        // Khi kết nối SFU:
                        // Nếu là người gọi đi (isIncoming == false) và chưa có remote participant:
                        // Giữ trạng thái RINGING và phát âm thanh đổ chuông (Ringback tone)
                        if (!isIncomingArg && !callEngine.hasRemoteParticipant.value) {
                            _state.update { it.copy(uiStatus = CallUiStatus.RINGING) }
                            callTonePlayer.startRingback()
                        } else if (callEngine.hasRemoteParticipant.value) {
                            // Nếu đã có đối tác (cuộc gọi đến đã chấp nhận):
                            ringingTimeoutJob?.cancel()
                            callTonePlayer.stop()
                            _state.update {
                                it.copy(
                                    uiStatus = CallUiStatus.CONNECTED,
                                    errorMessage = null
                                )
                            }
                            startDurationTimer()
                        }
                    }
                    CallEngineStatus.DISCONNECTED -> {
                        callTonePlayer.stop()
                        ringingTimeoutJob?.cancel()
                        if (_state.value.uiStatus == CallUiStatus.CONNECTED) {
                            _state.update { it.copy(uiStatus = CallUiStatus.ENDED) }
                        }
                    }
                    CallEngineStatus.ERROR -> {
                        callTonePlayer.stop()
                        ringingTimeoutJob?.cancel()
                        durationJob?.cancel()
                        _state.update {
                            it.copy(
                                uiStatus = CallUiStatus.ERROR,
                                errorMessage = "Lỗi kết nối phòng gọi. Vui lòng thử lại.",
                                canRetry = true
                            )
                        }
                    }
                    else -> {}
                }
            }
        }

        viewModelScope.launch {
            callEngine.hasRemoteParticipant.collect { hasRemote ->
                if (hasRemote) {
                    // Đối tác đã bấm nhận cuộc gọi và kết nối vào phòng!
                    ringingTimeoutJob?.cancel()
                    callTonePlayer.stop()
                    if (_state.value.uiStatus != CallUiStatus.CONNECTED) {
                        _state.update {
                            it.copy(
                                uiStatus = CallUiStatus.CONNECTED,
                                errorMessage = null
                            )
                        }
                        startDurationTimer()
                    }
                } else {
                    // Nếu cuộc gọi đang CONNECTED mà đối tác rời phòng -> kết thúc
                    if (_state.value.uiStatus == CallUiStatus.CONNECTED) {
                        durationJob?.cancel()
                        callTonePlayer.stop()
                        _state.update {
                            it.copy(
                                uiStatus = CallUiStatus.ENDED,
                                errorMessage = "Đối tác đã rời cuộc gọi."
                            )
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            callEngine.isMuted.collect { muted ->
                _state.update { it.copy(isMuted = muted) }
            }
        }

        viewModelScope.launch {
            callEngine.isSpeakerphoneOn.collect { speakerOn ->
                _state.update { it.copy(isSpeakerOn = speakerOn) }
            }
        }

        viewModelScope.launch {
            callEngine.selectedAudioDevice.collect { device ->
                _state.update {
                    it.copy(
                        selectedAudioDevice = device,
                        isSpeakerOn = (device == AppAudioDevice.SPEAKER)
                    )
                }
            }
        }

        viewModelScope.launch {
            callEngine.availableAudioDevices.collect { devices ->
                _state.update { it.copy(availableAudioDevices = devices) }
            }
        }

        viewModelScope.launch {
            callEngine.isMutedByAudioFocus.collect { muted ->
                _state.update { it.copy(isMutedByAudioFocus = muted) }
            }
        }

        viewModelScope.launch {
            callEngine.isMicPublished.collect { published ->
                _state.update { it.copy(isMicPublished = published) }
            }
        }

        viewModelScope.launch {
            callEngine.isCameraEnabled.collect { enabled ->
                _state.update { it.copy(isCameraEnabled = enabled) }
            }
        }

        viewModelScope.launch {
            callEngine.isFrontCamera.collect { isFront ->
                _state.update { it.copy(isFrontCamera = isFront) }
            }
        }

        viewModelScope.launch {
            callEngine.errorMessage.collect { errorMsg ->
                if (errorMsg != null) {
                    _state.update { it.copy(errorMessage = errorMsg) }
                    _effect.send(CallSideEffect.ShowSnackbar(errorMsg))
                }
            }
        }

        viewModelScope.launch {
            var wasInterrupted = false
            callEngine.isInterruptedBySystemCall.collect { interrupted ->
                _state.update { it.copy(isInterruptedBySystemCall = interrupted) }
                if (interrupted && !wasInterrupted) {
                    _effect.send(CallSideEffect.ShowSnackbar("Cuộc gọi bị tạm dừng do cuộc gọi hệ thống"))
                } else if (!interrupted && wasInterrupted) {
                    _effect.send(CallSideEffect.ShowSnackbar("Đã khôi phục âm thanh cuộc gọi"))
                }
                wasInterrupted = interrupted
            }
        }
    }

    fun onIntent(intent: CallIntent) {
        when (intent) {
            is CallIntent.InitCall -> loadExchangeAndStartCall(intent.exchangeId, intent.isVideo)
            is CallIntent.ToggleMute -> toggleMute()
            is CallIntent.ToggleSpeaker -> toggleSpeaker()
            is CallIntent.SelectAudioDevice -> selectAudioDevice(intent.device)
            is CallIntent.BluetoothPermissionResult -> handleBluetoothPermissionResult(intent.granted)
            is CallIntent.RecordAudioPermissionResult -> handleRecordAudioPermissionResult(intent.granted)
            is CallIntent.ToggleCamera -> toggleCamera()
            is CallIntent.FlipCamera -> flipCamera()
            is CallIntent.CameraPermissionResult -> handleCameraPermissionResult(intent.granted)
            is CallIntent.TogglePip -> togglePip()
            is CallIntent.EndCall -> endCall()
            is CallIntent.Retry -> retryCall()
        }
    }

    private fun selectAudioDevice(device: AppAudioDevice) {
        if (device == AppAudioDevice.BLUETOOTH) {
            val success = callEngine.selectAudioDevice(device)
            if (!success) {
                viewModelScope.launch {
                    _effect.send(CallSideEffect.RequestBluetoothPermission)
                }
            }
        } else {
            callEngine.selectAudioDevice(device)
        }
    }

    private fun handleBluetoothPermissionResult(granted: Boolean) {
        callEngine.setBluetoothPermission(granted)
        if (granted) {
            callEngine.selectAudioDevice(AppAudioDevice.BLUETOOTH)
        } else {
            viewModelScope.launch {
                _effect.send(CallSideEffect.ShowSnackbar("Quyền Bluetooth bị từ chối. Tiếp tục cuộc gọi bằng loa."))
            }
        }
    }

    private fun handleRecordAudioPermissionResult(granted: Boolean) {
        if (granted) {
            _state.update { it.copy(errorMessage = null) }
            callEngine.enableMicrophone()
        } else {
            val errMsg = "Quyền micro bị từ chối. Không thể truyền âm thanh."
            _state.update { it.copy(errorMessage = errMsg) }
            viewModelScope.launch {
                _effect.send(CallSideEffect.ShowSnackbar(errMsg))
            }
        }
    }

    private fun toggleCamera() {
        val newCameraState = !_state.value.isCameraEnabled
        callEngine.setCameraEnabled(newCameraState)
    }

    private fun flipCamera() {
        callEngine.flipCamera()
    }

    private fun togglePip() {
        _state.update { it.copy(isLocalPipMinimized = !it.isLocalPipMinimized) }
    }

    private fun handleCameraPermissionResult(granted: Boolean) {
        if (granted) {
            _state.update { it.copy(isCameraEnabled = true) }
            callEngine.setCameraEnabled(true)
        } else {
            _state.update { it.copy(isVideo = false, isCameraEnabled = false) }
            callEngine.setCameraEnabled(false)
            viewModelScope.launch {
                _effect.send(CallSideEffect.ShowSnackbar("Quyền Camera bị từ chối. Tiếp tục cuộc gọi bằng âm thanh."))
            }
        }
    }

    private fun loadExchangeAndStartCall(exchangeId: String, isVideo: Boolean) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    exchangeId = exchangeId,
                    isVideo = isVideo,
                    uiStatus = CallUiStatus.DIALING,
                    errorMessage = null,
                    canRetry = false
                )
            }

            // 1. Tải thông tin exchange để hiển thị đối tác và kiểm tra trạng thái ACCEPTED
            val currentUserId = tokenManager.getUserId() ?: ""
            val exchangeResult = exchangeRepository.getExchangeRequest(exchangeId)
            val exchange = exchangeResult.getOrNull()

            if (exchange != null) {
                val partnerName = exchange.otherUserName(currentUserId)
                val partnerAvatar = exchange.otherUserAvatar(currentUserId)
                val skillName = exchange.teachSkillName(currentUserId)
                _state.update {
                    it.copy(
                        partnerName = partnerName,
                        partnerAvatar = partnerAvatar,
                        skillName = skillName
                    )
                }

                if (exchange.status != ExchangeStatus.ACCEPTED) {
                    _state.update {
                        it.copy(
                            uiStatus = CallUiStatus.ERROR,
                            errorMessage = "Buổi trao đổi chưa được chấp nhận hoặc đã kết thúc.",
                            canRetry = false
                        )
                    }
                    return@launch
                }
            }

            // 2. Gọi API xin token khởi tạo cuộc gọi
            val callType = if (isVideo) CallType.VIDEO else CallType.VOICE
            val tokenResult = callRepository.initiateCall(exchangeId, callType)

            tokenResult.fold(
                onSuccess = { token ->
                    _state.update {
                        it.copy(
                            sessionId = token.sessionId,
                            uiStatus = CallUiStatus.RINGING
                        )
                    }
                    // Bắt đầu timeout 30s đổ chuông nếu đối tác không nhấc máy
                    startRingingTimeout()
                    // Kết nối tới CallEngine
                    callEngine.connect(token.url, token.roomName, token.token, isVideo)
                },
                onFailure = { error ->
                    handleCallError(error)
                }
            )
        }
    }

    private fun loadExchangeAndJoinCall(exchangeId: String, sessionId: String, isVideo: Boolean) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    exchangeId = exchangeId,
                    sessionId = sessionId,
                    isVideo = isVideo,
                    uiStatus = CallUiStatus.RINGING,
                    errorMessage = null,
                    canRetry = false
                )
            }

            // 1. Tải thông tin exchange để hiển thị tên đối tác
            val currentUserId = tokenManager.getUserId() ?: ""
            if (exchangeId.isNotBlank()) {
                val exchangeResult = exchangeRepository.getExchangeRequest(exchangeId)
                val exchange = exchangeResult.getOrNull()
                if (exchange != null) {
                    val partnerName = exchange.otherUserName(currentUserId)
                    val partnerAvatar = exchange.otherUserAvatar(currentUserId)
                    val skillName = exchange.teachSkillName(currentUserId)
                    _state.update {
                        it.copy(
                            partnerName = partnerName,
                            partnerAvatar = partnerAvatar,
                            skillName = skillName
                        )
                    }
                }
            }

            // BUG-SEC-06: Nếu engine đã ở trạng thái CONNECTED (người dùng quay lại từ background notification), giữ nguyên trạng thái
            if (callEngine.status.value == CallEngineStatus.CONNECTED) {
                _state.update {
                    it.copy(
                        sessionId = sessionId,
                        uiStatus = CallUiStatus.CONNECTED
                    )
                }
                return@launch
            }

            // 2. Sử dụng lại token đã join & xác nhận kết nối nếu có, tránh gọi đúp joinCall + notifyConnected
            val cachedToken = InternalCallAuth.consumeJoinedToken(sessionId)
            if (cachedToken != null) {
                _state.update {
                    it.copy(
                        sessionId = cachedToken.sessionId,
                        uiStatus = CallUiStatus.RINGING
                    )
                }
                callEngine.connect(cachedToken.url, cachedToken.roomName, cachedToken.token, isVideo)
                return@launch
            }

            // Fallback: Lấy token phòng cuộc gọi từ server nếu chưa được cache trước đó
            val tokenResult = callRepository.joinCall(sessionId)
            tokenResult.fold(
                onSuccess = { token ->
                    _state.update {
                        it.copy(
                            sessionId = token.sessionId,
                            uiStatus = CallUiStatus.RINGING
                        )
                    }
                    // Kết nối tới CallEngine
                    callEngine.connect(token.url, token.roomName, token.token, isVideo)
                    // Báo cho server biết receiver đã kết nối
                    callRepository.notifyConnected(sessionId)
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            uiStatus = CallUiStatus.ENDED,
                            errorMessage = "Cuộc gọi đã kết thúc hoặc không thể tham gia.",
                            canRetry = false
                        )
                    }
                    _effect.send(CallSideEffect.ShowSnackbar("Cuộc gọi đã kết thúc"))
                }
            )
        }
    }

    private fun handleCallError(error: Throwable) {
        callTonePlayer.stop()
        ringingTimeoutJob?.cancel()
        val message = when (error) {
            is ApiException -> {
                when (error.code) {
                    ApiException.CODE_USER_BUSY -> {
                        _state.update { it.copy(uiStatus = CallUiStatus.BUSY) }
                        "Người nhận đang bận cuộc gọi khác"
                    }
                    ApiException.CODE_CALL_COOLDOWN_ACTIVE -> {
                        _state.update { it.copy(uiStatus = CallUiStatus.ERROR, canRetry = true) }
                        error.message
                    }
                    ApiException.CODE_EXCHANGE_NOT_ACTIVE -> {
                        _state.update { it.copy(uiStatus = CallUiStatus.ERROR, canRetry = false) }
                        "Buổi trao đổi chưa được chấp nhận hoặc đã kết thúc"
                    }
                    ApiException.CODE_LIVEKIT_NOT_CONFIGURED -> {
                        _state.update { it.copy(uiStatus = CallUiStatus.ERROR, canRetry = false) }
                        "Dịch vụ gọi trực tiếp chưa sẵn sàng"
                    }
                    else -> {
                        _state.update { it.copy(uiStatus = CallUiStatus.ERROR, canRetry = true) }
                        error.message
                    }
                }
            }
            else -> {
                _state.update { it.copy(uiStatus = CallUiStatus.ERROR, canRetry = true) }
                error.message ?: "Không thể kết nối cuộc gọi. Vui lòng thử lại."
            }
        }

        _state.update { it.copy(errorMessage = message) }
        viewModelScope.launch {
            _effect.send(CallSideEffect.ShowSnackbar(message))
        }
    }

    private fun startRingingTimeout() {
        ringingTimeoutJob?.cancel()
        ringingTimeoutJob = viewModelScope.launch {
            delay(60_000L) // 1 phút (60s) timeout theo yêu cầu người dùng
            if (_state.value.uiStatus == CallUiStatus.RINGING || _state.value.uiStatus == CallUiStatus.DIALING) {
                callTonePlayer.stop()
                callEngine.disconnect()
                _state.update {
                    it.copy(
                        uiStatus = CallUiStatus.MISSED,
                        errorMessage = "Người nhận không trả lời (Quá 1 phút)",
                        canRetry = true
                    )
                }
                val sid = _state.value.sessionId
                if (!sid.isNullOrBlank()) {
                    runCatching {
                        callRepository.endCall(sid)
                    }
                }
            }
        }
    }

    private fun startDurationTimer() {
        durationJob?.cancel()
        durationJob = viewModelScope.launch {
            while (isActive && _state.value.uiStatus == CallUiStatus.CONNECTED) {
                delay(1000L)
                _state.update { it.copy(durationSeconds = it.durationSeconds + 1) }
            }
        }
    }

    private fun toggleMute() {
        val newMute = !_state.value.isMuted
        callEngine.setMicrophoneMuted(newMute)
    }

    private fun toggleSpeaker() {
        val currentDevice = _state.value.selectedAudioDevice
        val available = _state.value.availableAudioDevices
        val target = if (currentDevice == AppAudioDevice.SPEAKER) {
            if (available.contains(AppAudioDevice.EARPIECE)) AppAudioDevice.EARPIECE
            else if (available.contains(AppAudioDevice.WIRED)) AppAudioDevice.WIRED
            else AppAudioDevice.SPEAKER
        } else {
            AppAudioDevice.SPEAKER
        }
        selectAudioDevice(target)
    }

    private fun endCall() {
        callTonePlayer.stop()
        ringingTimeoutJob?.cancel()
        durationJob?.cancel()
        callEngine.disconnect()

        val currentSessionId = _state.value.sessionId
        _state.update {
            it.copy(
                uiStatus = CallUiStatus.ENDED,
                isActionLoading = true
            )
        }

        viewModelScope.launch {
            if (!currentSessionId.isNullOrBlank()) {
                callRepository.endCall(currentSessionId)
            }
            _state.update { it.copy(isActionLoading = false) }
            _effect.send(CallSideEffect.NavigateBack)
        }
    }

    private fun retryCall() {
        callTonePlayer.stop()
        if (isIncomingArg && sessionIdArg.isNotBlank()) {
            loadExchangeAndJoinCall(_state.value.exchangeId, sessionIdArg, _state.value.isVideo)
        } else {
            loadExchangeAndStartCall(_state.value.exchangeId, _state.value.isVideo)
        }
    }

    override fun onCleared() {
        super.onCleared()
        callTonePlayer.stop()
        ringingTimeoutJob?.cancel()
        durationJob?.cancel()
        callEngine.disconnect()
    }
}
