package com.skillexchange.app.domain.repository

import com.skillexchange.app.data.remote.exchange.CreateExchangeRequestDto
import com.skillexchange.app.domain.model.exchange.ExchangeRequest

interface IExchangeRepository {
    suspend fun createExchangeRequest(dto: CreateExchangeRequestDto): Result<ExchangeRequest>
    suspend fun getIncomingRequests(): Result<List<ExchangeRequest>>
    suspend fun getOutgoingRequests(): Result<List<ExchangeRequest>>
    suspend fun getExchangeRequest(id: String): Result<ExchangeRequest>
    suspend fun acceptRequest(id: String): Result<ExchangeRequest>
    suspend fun rejectRequest(id: String): Result<ExchangeRequest>
    suspend fun cancelRequest(id: String, reason: String? = null): Result<ExchangeRequest>
    suspend fun completeRequest(id: String): Result<ExchangeRequest>
}
