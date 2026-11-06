package com.skillexchange.app.domain.repository

import com.skillexchange.app.data.remote.reschedule.CreateRescheduleProposalDto
import com.skillexchange.app.domain.model.reschedule.RescheduleProposal

interface IRescheduleRepository {
    suspend fun createProposal(exchangeId: String, dto: CreateRescheduleProposalDto): Result<RescheduleProposal>
    suspend fun createProposal(exchangeId: String, newScheduledAt: String, reason: String? = null): Result<RescheduleProposal> =
        createProposal(exchangeId, CreateRescheduleProposalDto(newScheduledAt = newScheduledAt, reason = reason))
    suspend fun getCurrentProposal(exchangeId: String): Result<RescheduleProposal?>
    suspend fun acceptProposal(proposalId: String): Result<RescheduleProposal>
    suspend fun rejectProposal(proposalId: String): Result<RescheduleProposal>
    suspend fun cancelProposal(proposalId: String): Result<RescheduleProposal>
}

