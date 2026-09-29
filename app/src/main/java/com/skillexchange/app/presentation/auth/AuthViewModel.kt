package com.skillexchange.app.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skillexchange.app.domain.repository.IAuthRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ─── MVI State ───────────────────────────────────────────────────────
data class AuthUiState(
    val isLoading: Boolean = false,
    val email: String = "",
    val password: String = "",
    val fullName: String = "",
    val otp: String = "",
    val passwordVisible: Boolean = false,
    val error: String? = null
)

// ─── One-time Effects (navigation, snackbar) ─────────────────────────
sealed class AuthEffect {
    data class NavigateToOtp(val email: String) : AuthEffect()
    object NavigateToHome : AuthEffect()
    data class ShowError(val message: String) : AuthEffect()
}

// ─── Intents ─────────────────────────────────────────────────────────
sealed class AuthIntent {
    data class EmailChanged(val email: String) : AuthIntent()
    data class PasswordChanged(val password: String) : AuthIntent()
    data class FullNameChanged(val name: String) : AuthIntent()
    data class OtpChanged(val otp: String) : AuthIntent()
    object TogglePasswordVisibility : AuthIntent()
    object SubmitLogin : AuthIntent()
    object SubmitRegister : AuthIntent()
    data class SubmitOtp(val email: String) : AuthIntent()
    object ClearError : AuthIntent()
}

class AuthViewModel(
    private val authRepository: IAuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    private val _effect = Channel<AuthEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    fun onIntent(intent: AuthIntent) {
        when (intent) {
            is AuthIntent.EmailChanged        -> {
                val cleanEmail = intent.email.replace("\n", "").replace("\r", "").replace(" ", "")
                _state.update { it.copy(email = cleanEmail, error = null) }
            }
            is AuthIntent.PasswordChanged     -> _state.update { it.copy(password = intent.password, error = null) }
            is AuthIntent.FullNameChanged     -> _state.update { it.copy(fullName = intent.name, error = null) }
            is AuthIntent.OtpChanged          -> _state.update { it.copy(otp = intent.otp, error = null) }
            is AuthIntent.TogglePasswordVisibility -> _state.update { it.copy(passwordVisible = !it.passwordVisible) }
            is AuthIntent.ClearError          -> _state.update { it.copy(error = null) }
            is AuthIntent.SubmitLogin         -> login()
            is AuthIntent.SubmitRegister      -> register()
            is AuthIntent.SubmitOtp           -> verifyOtp(intent.email)
        }
    }

    private fun login() {
        val s = _state.value
        if (s.email.isBlank() || s.password.isBlank()) {
            _state.update { it.copy(error = "Vui lòng nhập đầy đủ thông tin") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            authRepository.login(s.email.trim(), s.password)
                .onSuccess {
                    _state.update { it.copy(isLoading = false) }
                    _effect.send(AuthEffect.NavigateToHome)
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.message ?: "Đăng nhập thất bại") }
                }
        }
    }

    private fun register() {
        val s = _state.value
        when {
            s.fullName.isBlank() -> { _state.update { it.copy(error = "Vui lòng nhập họ tên") }; return }
            s.email.isBlank() || !s.email.contains("@") -> {
                _state.update { it.copy(error = "Email không hợp lệ") }; return
            }
            s.password.length < 6 -> { _state.update { it.copy(error = "Mật khẩu phải ít nhất 6 ký tự") }; return }
        }
        val cleanEmail = s.email.replace("\n", "").replace("\r", "").replace(" ", "").trim()
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            authRepository.register(cleanEmail, s.password, s.fullName.trim())
                .onSuccess {
                    _state.update { it.copy(isLoading = false) }
                    _effect.send(AuthEffect.NavigateToOtp(cleanEmail))
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.message ?: "Đăng ký thất bại") }
                }
        }
    }

    private fun verifyOtp(email: String) {
        val otp = _state.value.otp
        if (otp.length !in 6..8) {
            _state.update { it.copy(error = "Mã OTP phải có 6 đến 8 chữ số") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            authRepository.verifyOtp(email, otp)
                .onSuccess {
                    _state.update { it.copy(isLoading = false) }
                    _effect.send(AuthEffect.NavigateToHome)
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.message ?: "OTP không đúng") }
                }
        }
    }
}
