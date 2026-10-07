package com.skillexchange.app.presentation.rating

data class RatingState(
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val exchangeId: String = "",
    val otherUserName: String = "",
    val otherUserAvatarUrl: String? = null,
    val exchangeTitle: String = "",
    val scheduledDate: String = "",
    val score: Int = 5,
    val comment: String = "",
    val error: String? = null,
    val isSuccess: Boolean = false
) {
    val scoreLabel: String
        get() = when (score) {
            1 -> "Tệ"
            2 -> "Không tốt"
            3 -> "Bình thường"
            4 -> "Tốt"
            5 -> "Tuyệt vời!"
            else -> ""
        }
}

sealed class RatingIntent {
    data class LoadExchange(val exchangeId: String) : RatingIntent()
    data class SelectScore(val score: Int) : RatingIntent()
    data class UpdateComment(val comment: String) : RatingIntent()
    object SubmitRating : RatingIntent()
}

sealed class RatingEffect {
    data class ShowSnackbar(val message: String) : RatingEffect()
    object NavigateBack : RatingEffect()
}
