package com.skillexchange.app.data.repository

import com.skillexchange.app.data.remote.reschedule.CreateRescheduleProposalDto
import com.skillexchange.app.data.remote.reschedule.RescheduleRemoteDataSource
import com.skillexchange.app.domain.model.reschedule.RescheduleProposal
import com.skillexchange.app.domain.model.reschedule.toDomain
import com.skillexchange.app.domain.repository.IRescheduleRepository

class RescheduleRepositoryImpl(
    private val remoteDataSource: RescheduleRemoteDataSource
) : IRescheduleRepository {

    override suspend fun createProposal(
        exchangeId: String,
        dto: CreateRescheduleProposalDto
    ): Result<RescheduleProposal> = runCatching {
        remoteDataSource.createProposal(exchangeId, dto).toDomain()
    }

    override suspend fun getCurrentProposal(exchangeId: String): Result<RescheduleProposal?> = runCatching {
        remoteDataSource.getCurrentProposal(exchangeId)?.toDomain()
    }

    override suspend fun acceptProposal(proposalId: String): Result<RescheduleProposal> = runCatching {
        remoteDataSource.acceptProposal(proposalId).toDomain()
    }

    override suspend fun rejectProposal(proposalId: String): Result<RescheduleProposal> = runCatching {
        remoteDataSource.rejectProposal(proposalId).toDomain()
    }

    override suspend fun cancelProposal(proposalId: String): Result<RescheduleProposal> = runCatching {
        remoteDataSource.cancelProposal(proposalId).toDomain()
    }
}

