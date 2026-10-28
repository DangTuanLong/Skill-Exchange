package com.skillexchange.app.core.call

import android.util.Log
import com.skillexchange.app.domain.model.call.CallToken
import com.skillexchange.app.domain.repository.ICallRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class IncomingCallData(
    val sessionId: String,
    val callerId: String,
    val callerName: String,
    val callType: String,
    val exchangeId: String,
    val token: CallToken
)

class InAppCallManager(
    private val callRepository: ICallRepository,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) {
    private val tag = "InAppCallManager"

    private val _incomingCall = MutableStateFlow<IncomingCallData?>(null)
    val incomingCall: StateFlow<IncomingCallData?> = _incomingCall.asStateFlow()

    private var timeoutJob: Job? = null

    /**
     * Xử lý khi nhận FCM Push thông báo có cuộc gọi đến khi app đang mở.
     * Xác minh tính hợp lệ với server (còn RINGING và mình là người nhận) trước khi hiển thị.
     */
    fun onIncomingCallPushReceived(
        sessionId: String,
        callerId: String,
        callerName: String,
        callType: String,
        exchangeId: String
    ) {
        if (_incomingCall.value != null) {
            Log.d(tag, "Đang có một cuộc gọi khác đang đổ chuông, bỏ qua sessionId: $sessionId")
            return
        }

        coroutineScope.launch {
            // Xác minh phiên gọi với server và lấy token tham gia
            val result = callRepository.joinCall(sessionId)
            result.onSuccess { tokenResp ->
                Log.d(tag, "Xác minh cuộc gọi thành công, bắt đầu đổ chuông in-app: $sessionId")
                val callData = IncomingCallData(
                    sessionId = sessionId,
                    callerId = callerId,
                    callerName = callerName.ifBlank { "Đối tác" },
                    callType = callType,
                    exchangeId = exchangeId,
                    token = tokenResp
                )
                _incomingCall.value = callData
                startTimeoutTimer(sessionId)
            }.onFailure { err ->
                Log.w(tag, "Không thể nhận cuộc gọi $sessionId (đã kết thúc hoặc không hợp lệ): ${err.message}")
            }
        }
    }

    /**
     * Xử lý khi nhận FCM Push CALL_CANCELLED (người gọi đã cúp máy trước khi người nhận trả lời).
     */
    fun onCallCancelledPushReceived(sessionId: String) {
        if (_incomingCall.value?.sessionId == sessionId) {
            Log.d(tag, "Người gọi đã hủy cuộc gọi $sessionId. Đóng giao diện chuông.")
            dismissIncomingCall()
        }
    }

    /**
     * Bấm từ chối cuộc gọi.
     */
    fun rejectIncomingCall() {
        val current = _incomingCall.value ?: return
        dismissIncomingCall()
        coroutineScope.launch {
            callRepository.rejectCall(current.sessionId)
                .onFailure { Log.e(tag, "Lỗi khi gửi từ chối cuộc gọi: ${it.message}") }
        }
    }

    /**
     * Bấm trả lời cuộc gọi.
     * Trả về dữ liệu cuộc gọi kèm token để điều hướng mở màn hình CallScreen.
     */
    fun acceptIncomingCall(
        onSuccess: (IncomingCallData) -> Unit,
        onError: (String) -> Unit
    ) {
        val current = _incomingCall.value ?: run {
            onError("Cuộc gọi không còn khả dụng")
            return
        }
        dismissIncomingCall()

        coroutineScope.launch {
            // Báo cho server biết người nhận đã nhấc máy
            val notifyResult = callRepository.notifyConnected(current.sessionId)
            notifyResult.onSuccess {
                onSuccess(current)
            }.onFailure { err ->
                Log.w(tag, "Xác nhận kết nối thất bại: ${err.message}")
                onError(err.message ?: "Cuộc gọi đã kết thúc")
            }
        }
    }

    private fun startTimeoutTimer(sessionId: String) {
        timeoutJob?.cancel()
        timeoutJob = coroutineScope.launch {
            delay(30_000L) // Timeout 30 giây chuông
            if (_incomingCall.value?.sessionId == sessionId) {
                Log.d(tag, "Hết 30 giây người dùng không nhấc máy, tự động bỏ qua cuộc gọi $sessionId")
                dismissIncomingCall()
            }
        }
    }

    fun dismissIncomingCall() {
        timeoutJob?.cancel()
        timeoutJob = null
        _incomingCall.value = null
    }
}
