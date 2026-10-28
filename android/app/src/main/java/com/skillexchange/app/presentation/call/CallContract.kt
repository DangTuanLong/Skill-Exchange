package com.skillexchange.app.presentation.call

import com.skillexchange.app.core.call.AppAudioDevice
import com.skillexchange.app.domain.model.call.CallUiStatus

data class CallState(
    val exchangeId: String = "",
    val isVideo: Boolean = false,
    val sessionId: String? = null,
    val partnerName: String = "Đang tải...",
    val partnerAvatar: String? = null,
    val skillName: String = "",
    val uiStatus: CallUiStatus = CallUiStatus.IDLE,
    val durationSeconds: Int = 0,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val selectedAudioDevice: AppAudioDevice = AppAudioDevice.EARPIECE,
    val availableAudioDevices: Set<AppAudioDevice> = setOf(AppAudioDevice.EARPIECE, AppAudioDevice.SPEAKER),
    val isMutedByAudioFocus: Boolean = false,
    val isInterruptedBySystemCall: Boolean = false,
    val errorMessage: String? = null,
    val canRetry: Boolean = false,
    val isActionLoading: Boolean = false
) {
    val formattedDuration: String
        get() {
            val minutes = durationSeconds / 60
            val seconds = durationSeconds % 60
            return "%02d:%02d".format(minutes, seconds)
        }
}

sealed interface CallIntent {
    data class InitCall(val exchangeId: String, val isVideo: Boolean) : CallIntent
    data object ToggleMute : CallIntent
    data object ToggleSpeaker : CallIntent
    data class SelectAudioDevice(val device: AppAudioDevice) : CallIntent
    data class BluetoothPermissionResult(val granted: Boolean) : CallIntent
    data object EndCall : CallIntent
    data object Retry : CallIntent
}

sealed interface CallSideEffect {
    data class ShowSnackbar(val message: String) : CallSideEffect
    data object RequestBluetoothPermission : CallSideEffect
    data object NavigateBack : CallSideEffect
}
