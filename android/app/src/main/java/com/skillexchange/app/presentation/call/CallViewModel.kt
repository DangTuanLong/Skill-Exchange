package com.skillexchange.app.presentation.call

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skillexchange.app.core.call.CallEngine
import com.skillexchange.app.core.call.CallEngineStatus
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
    private val tokenManager: TokenManager
) : ViewModel() {

    private val exchangeIdArg: String = savedStateHandle["exchangeId"] ?: ""
    private val isVideoArg: Boolean = savedStateHandle.get<Boolean>("isVideo")
        ?: savedStateHandle.get<String>("isVideo")?.toBooleanStrictOrNull()
        ?: false

    private val _state = MutableStateFlow(
        CallState(
            exchangeId = exchangeIdArg,
            isVideo = isVideoArg
        )
    )
    val state: StateFlow<CallState> = _state.asStateFlow()

    private val _effect = Channel<CallSideEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    private var durationJob: Job? = null
    private var ringingTimeoutJob: Job? = null

    init {
        observeEngine()
        if (exchangeIdArg.isNotBlank()) {
            loadExchangeAndStartCall(exchangeIdArg, isVideoArg)
        }
    }

    private fun observeEngine() {
        viewModelScope.launch {
            callEngine.status.collect { engineStatus ->
                when (engineStatus) {
                    CallEngineStatus.CONNECTED -> {
                        ringingTimeoutJob?.cancel()
                        _state.update {
                            it.copy(
                                uiStatus = CallUiStatus.CONNECTED,
                                errorMessage = null
                            )
                        }
                        startDurationTimer()
                    }
                    CallEngineStatus.DISCONNECTED -> {
                        if (_state.value.uiStatus == CallUiStatus.CONNECTED) {
                            _state.update { it.copy(uiStatus = CallUiStatus.ENDED) }
                        }
                    }
                    CallEngineStatus.ERROR -> {
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
            callEngine.isMuted.collect { muted ->
                _state.update { it.copy(isMuted = muted) }
            }
        }

        viewModelScope.launch {
            callEngine.isSpeakerphoneOn.collect { speakerOn ->
                _state.update { it.copy(isSpeakerOn = speakerOn) }
            }
        }
    }

    fun onIntent(intent: CallIntent) {
        when (intent) {
            is CallIntent.InitCall -> loadExchangeAndStartCall(intent.exchangeId, intent.isVideo)
            is CallIntent.ToggleMute -> toggleMute()
            is CallIntent.ToggleSpeaker -> toggleSpeaker()
            is CallIntent.EndCall -> endCall()
            is CallIntent.Retry -> retryCall()
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
                    callEngine.connect(token.url, token.roomName, token.token)
                },
                onFailure = { error ->
                    handleCallError(error)
                }
            )
        }
    }

    private fun handleCallError(error: Throwable) {
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
            delay(30_000L) // 30s timeout per BUSINESS_RULES §11
            if (_state.value.uiStatus == CallUiStatus.RINGING || _state.value.uiStatus == CallUiStatus.DIALING) {
                callEngine.disconnect()
                _state.update {
                    it.copy(
                        uiStatus = CallUiStatus.MISSED,
                        errorMessage = "Người nhận không trả lời (Quá 30 giây)",
                        canRetry = true
                    )
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
        val newSpeaker = !_state.value.isSpeakerOn
        callEngine.setSpeakerphoneEnabled(newSpeaker)
    }

    private fun endCall() {
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
        loadExchangeAndStartCall(_state.value.exchangeId, _state.value.isVideo)
    }

    override fun onCleared() {
        super.onCleared()
        ringingTimeoutJob?.cancel()
        durationJob?.cancel()
        callEngine.disconnect()
    }
}
