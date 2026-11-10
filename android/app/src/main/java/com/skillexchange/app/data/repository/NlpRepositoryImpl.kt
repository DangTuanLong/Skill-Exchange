package com.skillexchange.app.data.repository

import com.skillexchange.app.core.security.TokenManager
import com.skillexchange.app.data.remote.nlp.ExtractedSkillDto
import com.skillexchange.app.data.remote.nlp.NlpApiErrorDto
import com.skillexchange.app.data.remote.nlp.NlpRemoteDataSource
import com.skillexchange.app.domain.repository.DocumentExtractionResult
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
        try {
            val response = remoteDataSource.extractSkills(token, text)
            if (!response.success) {
                throw RuntimeException("Gợi ý kỹ năng thất bại")
            }
            response.data
        } catch (e: ResponseException) {
            throw RuntimeException(parseErrorMessage(e, "Lỗi phân tích kỹ năng"))
        }
    }

    override suspend fun extractSkillsFromDocument(
        fileBytes: ByteArray,
        fileName: String
    ): Result<List<ExtractedSkillDto>> =
        extractSkillsFromDocumentWithWarning(fileBytes, fileName).map { it.skills }

    override suspend fun extractSkillsFromDocumentWithWarning(
        fileBytes: ByteArray,
        fileName: String
    ): Result<DocumentExtractionResult> = runCatching {
        val token = tokenManager.getAccessToken() ?: throw IllegalStateException("Chưa đăng nhập")
        try {
            val response = remoteDataSource.extractSkillsFromDocument(token, fileBytes, fileName)
            if (!response.success) {
                throw RuntimeException("Gợi ý kỹ năng từ tài liệu thất bại")
            }
            DocumentExtractionResult(
                skills = response.data,
                warning = response.warning
            )
        } catch (e: ResponseException) {
            throw RuntimeException(parseErrorMessage(e, "Lỗi tải tài liệu"))
        }
    }

    private suspend fun parseErrorMessage(e: ResponseException, prefix: String): String {
        val errorBody = try {
            e.response.body<String>()
        } catch (_: Exception) {
            ""
        }
        val parsedError = try {
            json.decodeFromString<NlpApiErrorDto>(errorBody)
        } catch (_: Exception) {
            null
        }
        return parsedError?.message ?: "$prefix: ${e.response.status.value}"
    }
}
