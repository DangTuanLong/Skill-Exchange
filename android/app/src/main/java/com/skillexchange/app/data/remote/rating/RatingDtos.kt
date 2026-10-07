package com.skillexchange.app.data.remote.rating

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateRatingRequestDto(
    @SerialName("exchange_id") val exchangeId: String,
    val score: Int,
    val comment: String? = null
)

@Serializable
data class RatingDto(
    val id: String,
    @SerialName("exchange_id") val exchangeId: String,
    @SerialName("reviewer_id") val reviewerId: String,
    @SerialName("reviewer_name") val reviewerName: String? = null,
    @SerialName("reviewer_avatar_url") val reviewerAvatarUrl: String? = null,
    @SerialName("reviewee_id") val revieweeId: String,
    val score: Int,
    val comment: String? = null,
    @SerialName("created_at") val createdAt: String
)

@Serializable
data class ReputationDto(
    val score: Double? = null,
    val badge: String? = null,
    @SerialName("total_exchanges") val totalExchanges: Int = 0,
    @SerialName("completion_rate") val completionRate: Double = 0.0,
    @SerialName("rating_count") val ratingCount: Int = 0,
    @SerialName("avg_rating") val avgRating: Double? = null
)

@Serializable
data class UserExchangeRatingStatusDto(
    @SerialName("has_rated") val hasRated: Boolean,
    val rating: RatingDto? = null
)
