package com.skillexchange.app.domain.repository

import com.skillexchange.app.domain.model.UserDiscovery

interface IDiscoveryRepository {
    suspend fun searchUsers(
        query: String?,
        categoryId: Int?,
        city: String?,
        minProficiency: Int?,
        type: String?,
        limit: Int = 20,
        offset: Int = 0
    ): Result<List<UserDiscovery>>
}
