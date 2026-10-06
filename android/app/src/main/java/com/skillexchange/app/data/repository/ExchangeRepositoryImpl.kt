package com.skillexchange.app.data.repository

import com.skillexchange.app.data.remote.exchange.CreateExchangeRequestDto
import com.skillexchange.app.data.remote.exchange.ExchangeRemoteDataSource
import com.skillexchange.app.domain.model.exchange.ExchangeRequest
import com.skillexchange.app.domain.model.exchange.toDomain
import com.skillexchange.app.domain.repository.IExchangeRepository

class ExchangeRepositoryImpl(
    private val remoteDataSource: ExchangeRemoteDataSource
) : IExchangeRepository {

    override suspend fun createExchangeRequest(dto: CreateExchangeRequestDto): Result<ExchangeRequest> =
        runCatching {
            remoteDataSource.createExchangeRequest(dto).toDomain()
        }

    override suspend fun getIncomingRequests(): Result<List<ExchangeRequest>> =
        runCatching {
            remoteDataSource.getIncomingRequests().map { it.toDomain() }
        }

    override suspend fun getOutgoingRequests(): Result<List<ExchangeRequest>> =
        runCatching {
            remoteDataSource.getOutgoingRequests().map { it.toDomain() }
        }

    override suspend fun getExchangeRequest(id: String): Result<ExchangeRequest> =
        runCatching {
            remoteDataSource.getExchangeRequest(id).toDomain()
        }

    override suspend fun acceptRequest(id: String): Result<ExchangeRequest> =
        runCatching {
            remoteDataSource.acceptRequest(id).toDomain()
        }

    override suspend fun rejectRequest(id: String): Result<ExchangeRequest> =
        runCatching {
            remoteDataSource.rejectRequest(id).toDomain()
        }

    override suspend fun cancelRequest(id: String, reason: String?): Result<ExchangeRequest> =
        runCatching {
            remoteDataSource.cancelRequest(id, reason).toDomain()
        }

    override suspend fun completeRequest(id: String): Result<ExchangeRequest> =
        runCatching {
            remoteDataSource.completeRequest(id).toDomain()
        }
}
