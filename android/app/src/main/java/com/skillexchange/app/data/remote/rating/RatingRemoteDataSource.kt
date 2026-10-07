package com.skillexchange.app.data.remote.rating

import com.skillexchange.app.core.common.Constants
import com.skillexchange.app.core.network.ApiErrorDto
import com.skillexchange.app.core.network.ApiException
import com.skillexchange.app.data.remote.auth.ApiWrapper
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

class RatingRemoteDataSource(private val client: HttpClient) {

    private suspend inline fun <reified T> handleResponse(response: HttpResponse): T {
        if (response.status.isSuccess()) {
            val body = response.body<ApiWrapper<T>>()
            return body.data ?: throw Exception(body.message ?: "Dữ liệu trả về rỗng")
        } else {
            val errorBody = runCatching { response.body<ApiErrorDto>() }.getOrNull()
            throw ApiException.fromResponse(response.status.value, errorBody)
        }
    }

    suspend fun createRating(dto: CreateRatingRequestDto): RatingDto {
        val response = client.post("${Constants.BASE_URL}/api/ratings") {
            contentType(ContentType.Application.Json)
            setBody(dto)
        }
        return handleResponse(response)
    }

    suspend fun getUserRatings(userId: String): List<RatingDto> {
        val response = client.get("${Constants.BASE_URL}/api/ratings/user/$userId")
        return handleResponse(response)
    }

    suspend fun getUserReputation(userId: String): ReputationDto {
        val response = client.get("${Constants.BASE_URL}/api/reputation/$userId")
        return handleResponse(response)
    }

    suspend fun getMyExchangeRating(exchangeId: String): UserExchangeRatingStatusDto {
        val response = client.get("${Constants.BASE_URL}/api/ratings/exchange/$exchangeId/mine")
        return handleResponse(response)
    }
}
