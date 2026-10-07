package com.skillexchange.app.domain.repository

import com.skillexchange.app.domain.model.rating.Rating
import com.skillexchange.app.domain.model.rating.Reputation
import com.skillexchange.app.domain.model.rating.UserExchangeRatingStatus

interface IRatingRepository {
    suspend fun createRating(exchangeId: String, score: Int, comment: String?): Result<Rating>
    suspend fun getUserRatings(userId: String): Result<List<Rating>>
    suspend fun getUserReputation(userId: String): Result<Reputation>
    suspend fun getMyExchangeRating(exchangeId: String): Result<UserExchangeRatingStatus>
}
