package com.skillexchange.app.data.remote.reschedule

import com.skillexchange.app.core.common.Constants
import com.skillexchange.app.core.network.ApiErrorDto
import com.skillexchange.app.core.network.ApiException
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

class RescheduleRemoteDataSource(private val client: HttpClient) {

    private suspend inline fun <reified T> handleResponse(response: HttpResponse): T {
        if (response.status.isSuccess()) {
            val body = response.body<ApiWrapper<T>>()
            return body.data ?: throw Exception(body.message ?: "Dữ liệu trả về rỗng")
        } else {
            val errorBody = runCatching { response.body<ApiErrorDto>() }.getOrNull()
            throw ApiException.fromResponse(response.status.value, errorBody)
        }
    }

    private suspend inline fun <reified T> handleNullableResponse(response: HttpResponse): T? {
        if (response.status.isSuccess()) {
            val body = response.body<ApiWrapper<T>>()
            return body.data
        } else {
            val errorBody = runCatching { response.body<ApiErrorDto>() }.getOrNull()
            throw ApiException.fromResponse(response.status.value, errorBody)
        }
    }

    suspend fun createProposal(exchangeId: String, dto: CreateRescheduleProposalDto): RescheduleProposalDto {
        val response = client.post("${Constants.BASE_URL}/api/exchange-requests/$exchangeId/reschedule") {
            contentType(ContentType.Application.Json)
            setBody(dto)
        }
        return handleResponse(response)
    }

    suspend fun getCurrentProposal(exchangeId: String): RescheduleProposalDto? {
        val response = client.get("${Constants.BASE_URL}/api/exchange-requests/$exchangeId/reschedule/current")
        return handleNullableResponse(response)
    }

    suspend fun acceptProposal(proposalId: String): RescheduleProposalDto {
        val response = client.put("${Constants.BASE_URL}/api/reschedule-proposals/$proposalId/accept")
        return handleResponse(response)
    }

    suspend fun rejectProposal(proposalId: String): RescheduleProposalDto {
        val response = client.put("${Constants.BASE_URL}/api/reschedule-proposals/$proposalId/reject")
        return handleResponse(response)
    }

    suspend fun cancelProposal(proposalId: String): RescheduleProposalDto {
        val response = client.put("${Constants.BASE_URL}/api/reschedule-proposals/$proposalId/cancel")
        return handleResponse(response)
    }
}

