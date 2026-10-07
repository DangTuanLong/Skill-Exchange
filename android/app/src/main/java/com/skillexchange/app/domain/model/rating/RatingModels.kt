package com.skillexchange.app.domain.model.rating

data class Rating(
    val id: String,
    val exchangeId: String,
    val reviewerId: String,
    val reviewerName: String?,
    val reviewerAvatarUrl: String?,
    val revieweeId: String,
    val score: Int,
    val comment: String?,
    val createdAt: String
)

data class Reputation(
    val score: Double?,
    val badge: String?,
    val totalExchanges: Int,
    val completionRate: Double,
    val ratingCount: Int,
    val avgRating: Double?
)

data class UserExchangeRatingStatus(
    val hasRated: Boolean,
    val rating: Rating?
)
