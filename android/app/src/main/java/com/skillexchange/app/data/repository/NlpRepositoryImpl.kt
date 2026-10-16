package com.skillexchange.app.data.repository

import com.skillexchange.app.core.security.TokenManager
import com.skillexchange.app.data.remote.nlp.ExtractedSkillDto
import com.skillexchange.app.data.remote.nlp.NlpRemoteDataSource
import com.skillexchange.app.domain.repository.INlpRepository

class NlpRepositoryImpl(
    private val remoteDataSource: NlpRemoteDataSource,
    private val tokenManager: TokenManager
) : INlpRepository {

    override suspend fun extractSkills(text: String): Result<List<ExtractedSkillDto>> = runCatching {
        val token = tokenManager.getAccessToken() ?: throw IllegalStateException("Chưa đăng nhập")
        val response = remoteDataSource.extractSkills(token, text)
        if (!response.success) {
            throw RuntimeException("Gợi ý kỹ năng thất bại")
        }
        response.data
    }
}
