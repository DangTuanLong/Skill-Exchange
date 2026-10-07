package com.skillexchange.api.models.rating

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateRatingRequest(
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
    val score: Double?,
    val badge: String?,
    @SerialName("total_exchanges") val totalExchanges: Int,
    @SerialName("completion_rate") val completionRate: Double,
    @SerialName("rating_count") val ratingCount: Int,
    @SerialName("avg_rating") val avgRating: Double? = null
)

@Serializable
data class UserExchangeRatingStatusDto(
    @SerialName("has_rated") val hasRated: Boolean,
    val rating: RatingDto? = null
)
