package com.skillexchange.app.data.remote.exchange

import com.skillexchange.app.core.common.Constants
import com.skillexchange.app.data.remote.auth.ApiWrapper
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

class ExchangeRemoteDataSource(private val client: HttpClient) {

    private suspend inline fun <reified T> handleResponse(response: HttpResponse): T {
        if (response.status.isSuccess()) {
            val body = response.body<ApiWrapper<T>>()
            return body.data ?: throw Exception(body.message ?: "Dữ liệu trả về rỗng")
        } else {
            val errorBody = runCatching { response.body<com.skillexchange.app.core.network.ApiErrorDto>() }.getOrNull()
            throw com.skillexchange.app.core.network.ApiException.fromResponse(response.status.value, errorBody)
        }
    }

    suspend fun createExchangeRequest(dto: CreateExchangeRequestDto): ExchangeRequestDto {
        val response = client.post("${Constants.BASE_URL}/api/exchange-requests") {
            contentType(ContentType.Application.Json)
            setBody(dto)
        }
        return handleResponse(response)
    }

    suspend fun getIncomingRequests(): List<ExchangeRequestDto> {
        val response = client.get("${Constants.BASE_URL}/api/exchange-requests/incoming")
        return handleResponse(response)
    }

    suspend fun getOutgoingRequests(): List<ExchangeRequestDto> {
        val response = client.get("${Constants.BASE_URL}/api/exchange-requests/outgoing")
        return handleResponse(response)
    }

    suspend fun getExchangeRequest(id: String): ExchangeRequestDto {
        val response = client.get("${Constants.BASE_URL}/api/exchange-requests/$id")
        return handleResponse(response)
    }

    suspend fun acceptRequest(id: String): ExchangeRequestDto {
        val response = client.put("${Constants.BASE_URL}/api/exchange-requests/$id/accept")
        return handleResponse(response)
    }

    suspend fun rejectRequest(id: String): ExchangeRequestDto {
        val response = client.put("${Constants.BASE_URL}/api/exchange-requests/$id/reject")
        return handleResponse(response)
    }

    suspend fun cancelRequest(id: String, reason: String? = null): ExchangeRequestDto {
        val response = client.put("${Constants.BASE_URL}/api/exchange-requests/$id/cancel") {
            contentType(ContentType.Application.Json)
            setBody(CancelExchangeRequestDto(reason = reason))
        }
        return handleResponse(response)
    }

    suspend fun completeRequest(id: String): ExchangeRequestDto {
        val response = client.put("${Constants.BASE_URL}/api/exchange-requests/$id/complete")
        return handleResponse(response)
    }
}
