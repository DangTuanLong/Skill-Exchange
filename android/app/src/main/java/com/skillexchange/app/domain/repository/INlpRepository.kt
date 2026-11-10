package com.skillexchange.app.domain.repository

import com.skillexchange.app.data.remote.nlp.ExtractedSkillDto

data class DocumentExtractionResult(
    val skills: List<ExtractedSkillDto>,
    val warning: String? = null
)

interface INlpRepository {
    suspend fun extractSkills(text: String): Result<List<ExtractedSkillDto>>
    suspend fun extractSkillsFromDocument(fileBytes: ByteArray, fileName: String): Result<List<ExtractedSkillDto>>
    suspend fun extractSkillsFromDocumentWithWarning(
        fileBytes: ByteArray,
        fileName: String
    ): Result<DocumentExtractionResult> = extractSkillsFromDocument(fileBytes, fileName).map {
        DocumentExtractionResult(skills = it, warning = null)
    }
}
