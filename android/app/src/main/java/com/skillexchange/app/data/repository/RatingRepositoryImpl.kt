package com.skillexchange.app.data.repository

import com.skillexchange.app.data.remote.rating.CreateRatingRequestDto
import com.skillexchange.app.data.remote.rating.RatingDto
import com.skillexchange.app.data.remote.rating.RatingRemoteDataSource
import com.skillexchange.app.data.remote.rating.ReputationDto
import com.skillexchange.app.domain.model.rating.Rating
import com.skillexchange.app.domain.model.rating.Reputation
import com.skillexchange.app.domain.model.rating.UserExchangeRatingStatus
import com.skillexchange.app.domain.repository.IRatingRepository

class RatingRepositoryImpl(
    private val remoteDataSource: RatingRemoteDataSource
) : IRatingRepository {

    override suspend fun createRating(exchangeId: String, score: Int, comment: String?): Result<Rating> = runCatching {
        val dto = remoteDataSource.createRating(
            CreateRatingRequestDto(
                exchangeId = exchangeId,
                score = score,
                comment = comment
            )
        )
        dto.toDomain()
    }

    override suspend fun getUserRatings(userId: String): Result<List<Rating>> = runCatching {
        val dtos = remoteDataSource.getUserRatings(userId)
        dtos.map { it.toDomain() }
    }

    override suspend fun getUserReputation(userId: String): Result<Reputation> = runCatching {
        val dto = remoteDataSource.getUserReputation(userId)
        dto.toDomain()
    }

    override suspend fun getMyExchangeRating(exchangeId: String): Result<UserExchangeRatingStatus> = runCatching {
        val dto = remoteDataSource.getMyExchangeRating(exchangeId)
        UserExchangeRatingStatus(
            hasRated = dto.hasRated,
            rating = dto.rating?.toDomain()
        )
    }

    private fun RatingDto.toDomain() = Rating(
        id = id,
        exchangeId = exchangeId,
        reviewerId = reviewerId,
        reviewerName = reviewerName,
        reviewerAvatarUrl = reviewerAvatarUrl,
        revieweeId = revieweeId,
        score = score,
        comment = comment,
        createdAt = createdAt
    )

    private fun ReputationDto.toDomain() = Reputation(
        score = score,
        badge = badge,
        totalExchanges = totalExchanges,
        completionRate = completionRate,
        ratingCount = ratingCount,
        avgRating = avgRating
    )
}
