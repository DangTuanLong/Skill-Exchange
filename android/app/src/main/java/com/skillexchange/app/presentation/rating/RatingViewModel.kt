package com.skillexchange.app.presentation.rating

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skillexchange.app.core.security.TokenManager
import com.skillexchange.app.domain.repository.IExchangeRepository
import com.skillexchange.app.domain.repository.IRatingRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RatingViewModel(
    private val ratingRepository: IRatingRepository,
    private val exchangeRepository: IExchangeRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _state = MutableStateFlow(RatingState())
    val state: StateFlow<RatingState> = _state.asStateFlow()

    private val _effect = Channel<RatingEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    fun onIntent(intent: RatingIntent) {
        when (intent) {
            is RatingIntent.LoadExchange -> loadExchange(intent.exchangeId)
            is RatingIntent.SelectScore -> selectScore(intent.score)
            is RatingIntent.UpdateComment -> updateComment(intent.comment)
            RatingIntent.SubmitRating -> submitRating()
        }
    }

    private fun loadExchange(exchangeId: String) {
        _state.update { it.copy(isLoading = true, exchangeId = exchangeId, error = null) }
        viewModelScope.launch {
            exchangeRepository.getExchangeRequest(exchangeId).fold(
                onSuccess = { exchange ->
                    val myUserId = tokenManager.getUserId() ?: ""
                    val isSender = exchange.senderId == myUserId
                    val otherName = if (isSender) exchange.receiverName ?: "Đối tác" else exchange.senderName ?: "Đối tác"
                    val otherAvatar = if (isSender) exchange.receiverAvatarUrl else exchange.senderAvatarUrl
                    val title = "Bạn dạy ${exchange.skillOfferedName} ↔ Học ${exchange.skillWantedName}"

                    _state.update {
                        it.copy(
                            isLoading = false,
                            otherUserName = otherName,
                            otherUserAvatarUrl = otherAvatar,
                            exchangeTitle = title,
                            scheduledDate = exchange.scheduledAt
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Không thể tải thông tin phiên trao đổi"
                        )
                    }
                }
            )
        }
    }

    private fun selectScore(score: Int) {
        if (score in 1..5) {
            _state.update { it.copy(score = score) }
        }
    }

    private fun updateComment(comment: String) {
        if (comment.length <= 200) {
            _state.update { it.copy(comment = comment) }
        }
    }

    private fun submitRating() {
        val currentState = _state.value
        if (currentState.isSubmitting) return

        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            ratingRepository.createRating(
                exchangeId = currentState.exchangeId,
                score = currentState.score,
                comment = currentState.comment.takeIf { it.isNotBlank() }
            ).fold(
                onSuccess = {
                    _state.update { it.copy(isSubmitting = false, isSuccess = true) }
                    _effect.send(RatingEffect.ShowSnackbar("Cảm ơn bạn đã gửi đánh giá!"))
                    _effect.send(RatingEffect.NavigateBack)
                },
                onFailure = { error ->
                    _state.update { it.copy(isSubmitting = false, error = error.message) }
                    _effect.send(RatingEffect.ShowSnackbar(error.message ?: "Gửi đánh giá thất bại"))
                }
            )
        }
    }
}
