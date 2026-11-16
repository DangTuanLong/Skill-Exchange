package com.skillexchange.api.models.nlp

import kotlinx.serialization.Serializable

@Serializable
data class ExtractSkillsRequest(
    val text: String
)

@Serializable
data class ExtractSkillsDocumentRequest(
    val text: String,
    val fileName: String = "document.txt"
)

@Serializable
data class ExtractedSkillDto(
    val skillId: Int,
    val skillName: String,
    val categoryId: Int? = null,
    val categoryName: String? = null,
    val confidence: Double,
    val matchType: String, // "EXACT", "ALIAS", "FUZZY", "AI_GEMINI"
    val matchedTerm: String,
    val frequency: Int = 1,
    val score: Double = confidence,
    val reason: String? = null,
    val suggestedLevel: Int = 2,
    val skillType: String = "HAVE",
    val experienceYears: Double = 0.0,
    val evidence: String? = null
)

@Serializable
data class ExtractSkillsResponse(
    val success: Boolean = true,
    val data: List<ExtractedSkillDto>,
    val warning: String? = null,
    val profileSummary: String? = null
)
