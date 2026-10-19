package com.skillexchange.app.data.repository

import com.skillexchange.app.data.remote.rating.RatingRemoteDataSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class RatingRepositoryTest {

    private fun createMockClient(statusCode: HttpStatusCode, responseJson: String): HttpClient {
        return HttpClient(MockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            engine {
                addHandler {
                    respond(
                        content = responseJson,
                        status = statusCode,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            }
        }
    }

    private val sampleRatingJson = """
    {
        "id": "rate-1",
        "exchange_id": "ex-100",
        "reviewer_id": "user-A",
        "reviewer_name": "Người đánh giá A",
        "reviewer_avatar_url": null,
        "reviewee_id": "user-B",
        "score": 5,
        "comment": "Dạy rất nhiệt tình và dễ hiểu!",
        "created_at": "2026-10-19T08:00:00Z"
    }
    """.trimIndent()

    private fun wrapData(json: String): String = """{"success":true,"data":$json}"""

    @Test
    fun createRating_Success_ReturnsRating() = runTest {
        val client = createMockClient(HttpStatusCode.Created, wrapData(sampleRatingJson))
        val repo = RatingRepositoryImpl(RatingRemoteDataSource(client))

        val result = repo.createRating("ex-100", 5, "Dạy rất nhiệt tình và dễ hiểu!")

        assertTrue(result.isSuccess)
        val rating = result.getOrNull()
        assertNotNull(rating)
        assertEquals("rate-1", rating?.id)
        assertEquals(5, rating?.score)
        assertEquals("Dạy rất nhiệt tình và dễ hiểu!", rating?.comment)
        assertEquals("user-A", rating?.reviewerId)
    }

    @Test
    fun createRating_Error_ReturnsFailure() = runTest {
        val errorJson = """{"success":false,"code":"ALREADY_RATED","message":"Bạn đã đánh giá buổi trao đổi này rồi"}"""
        val client = createMockClient(HttpStatusCode.Conflict, errorJson)
        val repo = RatingRepositoryImpl(RatingRemoteDataSource(client))

        val result = repo.createRating("ex-100", 5, "Dạy tốt")

        assertTrue(result.isFailure)
    }

    @Test
    fun getUserRatings_Success_ReturnsList() = runTest {
        val client = createMockClient(HttpStatusCode.OK, wrapData("[$sampleRatingJson]"))
        val repo = RatingRepositoryImpl(RatingRemoteDataSource(client))

        val result = repo.getUserRatings("user-B")

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.size)
        assertEquals(5, result.getOrNull()?.first()?.score)
    }

    @Test
    fun getUserReputation_Success_ReturnsReputation() = runTest {
        val repJson = """
        {
            "score": 4.8,
            "badge": "Xuất sắc",
            "total_exchanges": 10,
            "completion_rate": 0.95,
            "rating_count": 8,
            "avg_rating": 4.9
        }
        """.trimIndent()
        val client = createMockClient(HttpStatusCode.OK, wrapData(repJson))
        val repo = RatingRepositoryImpl(RatingRemoteDataSource(client))

        val result = repo.getUserReputation("user-B")

        assertTrue(result.isSuccess)
        val rep = result.getOrNull()
        assertNotNull(rep)
        assertEquals(4.8, rep?.score ?: 0.0, 0.01)
        assertEquals("Xuất sắc", rep?.badge)
        assertEquals(10, rep?.totalExchanges)
        assertEquals(0.95, rep?.completionRate ?: 0.0, 0.01)
    }

    @Test
    fun getMyExchangeRating_Success_ReturnsStatus() = runTest {
        val statusJson = """
        {
            "has_rated": true,
            "rating": $sampleRatingJson
        }
        """.trimIndent()
        val client = createMockClient(HttpStatusCode.OK, wrapData(statusJson))
        val repo = RatingRepositoryImpl(RatingRemoteDataSource(client))

        val result = repo.getMyExchangeRating("ex-100")

        assertTrue(result.isSuccess)
        val status = result.getOrNull()
        assertNotNull(status)
        assertTrue(status?.hasRated == true)
        assertEquals(5, status?.rating?.score)
    }
}
