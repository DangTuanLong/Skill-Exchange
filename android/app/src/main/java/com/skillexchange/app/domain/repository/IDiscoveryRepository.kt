package com.skillexchange.app.domain.repository

import com.skillexchange.app.domain.model.UserDiscovery

data class DiscoverySearchResult(
    val users: List<UserDiscovery>,
    val nextCursor: String? = null
)

interface IDiscoveryRepository {
    suspend fun searchUsers(
        query: String?,
        categoryIds: List<Int>?,
        city: String?,
        minProficiency: Int?,
        maxProficiency: Int? = null,
        type: String?,
        limit: Int = 20,
        lastId: String? = null
    ): Result<DiscoverySearchResult>
}
