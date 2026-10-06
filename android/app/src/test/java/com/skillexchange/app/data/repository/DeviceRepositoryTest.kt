package com.skillexchange.app.data.repository

import com.skillexchange.app.data.remote.device.DeviceRemoteDataSource
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceRepositoryTest {

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

    @Test
    fun registerDevice_Success_ReturnsSuccess() = runTest {
        val json = """{"success":true,"data":{"token":"token-123","message":"OK"}}"""
        val client = createMockClient(HttpStatusCode.OK, json)
        val repository = DeviceRepositoryImpl(DeviceRemoteDataSource(client))

        val result = repository.registerDevice("token-123", "Pixel 8")

        assertTrue(result.isSuccess)
    }

    @Test
    fun registerDevice_Error_ReturnsFailure() = runTest {
        val json = """{"success":false,"message":"Lỗi server","code":500}"""
        val client = createMockClient(HttpStatusCode.InternalServerError, json)
        val repository = DeviceRepositoryImpl(DeviceRemoteDataSource(client))

        val result = repository.registerDevice("token-123")

        assertTrue(result.isFailure)
        assertEquals("Lỗi server", result.exceptionOrNull()?.message)
    }

    @Test
    fun unregisterDevice_Success_ReturnsSuccess() = runTest {
        val json = """{"success":true,"data":{"token":"token-123","message":"Deleted"}}"""
        val client = createMockClient(HttpStatusCode.OK, json)
        val repository = DeviceRepositoryImpl(DeviceRemoteDataSource(client))

        val result = repository.unregisterDevice("token-123")

        assertTrue(result.isSuccess)
    }
}
