package com.skillexchange.app.data.repository

import com.skillexchange.app.core.security.TokenManager
import com.skillexchange.app.data.remote.nlp.ExtractedSkillDto
import com.skillexchange.app.data.remote.nlp.NlpApiErrorDto
import com.skillexchange.app.data.remote.nlp.NlpRemoteDataSource
import com.skillexchange.app.domain.repository.INlpRepository
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import kotlinx.serialization.json.Json

class NlpRepositoryImpl(
    private val remoteDataSource: NlpRemoteDataSource,
    private val tokenManager: TokenManager
) : INlpRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun extractSkills(text: String): Result<List<ExtractedSkillDto>> = runCatching {
        val token = tokenManager.getAccessToken() ?: throw IllegalStateException("Chưa đăng nhập")
        val response = remoteDataSource.extractSkills(token, text)
        if (!response.success) {
            throw RuntimeException("Gợi ý kỹ năng thất bại")
        }
        response.data
    }

    override suspend fun extractSkillsFromDocument(fileBytes: ByteArray, fileName: String): Result<List<ExtractedSkillDto>> = runCatching {
        val token = tokenManager.getAccessToken() ?: throw IllegalStateException("Chưa đăng nhập")
        try {
            val response = remoteDataSource.extractSkillsFromDocument(token, fileBytes, fileName)
            if (!response.success) {
                throw RuntimeException("Gợi ý kỹ năng từ tài liệu thất bại")
            }
            response.data
        } catch (e: ResponseException) {
            val errorBody = e.response.body<String>()
            val parsedError = try {
                json.decodeFromString<NlpApiErrorDto>(errorBody)
            } catch (ex: Exception) {
                null
            }
            throw RuntimeException(parsedError?.message ?: "Lỗi tải tài liệu: ${e.response.status.value}")
        }
    }
}
