package com.skillexchange.app.data.remote.call

import com.skillexchange.app.core.common.Constants
import com.skillexchange.app.core.network.ApiErrorDto
import com.skillexchange.app.core.network.ApiException
import com.skillexchange.app.data.remote.auth.ApiWrapper
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

class CallRemoteDataSource(private val client: HttpClient) {

    private suspend inline fun <reified T> handleResponse(response: HttpResponse): T {
        if (response.status.isSuccess()) {
            val body = response.body<ApiWrapper<T>>()
            return body.data ?: throw Exception(body.message ?: "Dữ liệu trả về rỗng")
        } else {
            val errorBody = runCatching { response.body<ApiErrorDto>() }.getOrNull()
            throw ApiException.fromResponse(response.status.value, errorBody)
        }
    }

    suspend fun initiateCall(exchangeId: String, callType: String): CallTokenResponse {
        val response = client.post("${Constants.BASE_URL}/api/calls/token") {
            contentType(ContentType.Application.Json)
            setBody(CallTokenRequest(exchangeId = exchangeId, callType = callType))
        }
        return handleResponse(response)
    }

    suspend fun joinCall(sessionId: String): CallTokenResponse {
        val response = client.post("${Constants.BASE_URL}/api/calls/token/join") {
            contentType(ContentType.Application.Json)
            setBody(CallActionRequest(sessionId = sessionId))
        }
        return handleResponse(response)
    }

    suspend fun notifyConnected(sessionId: String): CallSessionDto {
        val response = client.post("${Constants.BASE_URL}/api/calls/connected") {
            contentType(ContentType.Application.Json)
            setBody(CallActionRequest(sessionId = sessionId))
        }
        return handleResponse(response)
    }

    suspend fun rejectCall(sessionId: String): CallSessionDto {
        val response = client.post("${Constants.BASE_URL}/api/calls/reject") {
            contentType(ContentType.Application.Json)
            setBody(CallActionRequest(sessionId = sessionId))
        }
        return handleResponse(response)
    }

    suspend fun endCall(sessionId: String): CallEndResponse {
        val response = client.post("${Constants.BASE_URL}/api/calls/end") {
            contentType(ContentType.Application.Json)
            setBody(CallEndRequest(sessionId = sessionId))
        }
        return handleResponse(response)
    }
}
