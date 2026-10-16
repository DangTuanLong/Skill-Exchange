package com.skillexchange.app.domain.repository

import com.skillexchange.app.data.remote.nlp.ExtractedSkillDto

interface INlpRepository {
    suspend fun extractSkills(text: String): Result<List<ExtractedSkillDto>>
    suspend fun extractSkillsFromDocument(fileBytes: ByteArray, fileName: String): Result<List<ExtractedSkillDto>>
}
