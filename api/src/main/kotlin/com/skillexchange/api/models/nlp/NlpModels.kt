package com.skillexchange.api.models.nlp

import kotlinx.serialization.Serializable

@Serializable
data class ExtractSkillsRequest(
    val text: String
)

@Serializable
data class ExtractedSkillDto(
    val skillId: Int,
    val skillName: String,
    val categoryId: Int? = null,
    val categoryName: String? = null,
    val confidence: Double,
    val matchType: String, // "EXACT", "ALIAS", "FUZZY"
    val matchedTerm: String
)

@Serializable
data class ExtractSkillsResponse(
    val success: Boolean = true,
    val data: List<ExtractedSkillDto>
)
