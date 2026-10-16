package com.skillexchange.app.data.remote.nlp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ExtractSkillsRequest(
    val text: String
)

@Serializable
data class ExtractedSkillDto(
    @SerialName("skillId")
    val skillId: Int,
    @SerialName("skillName")
    val skillName: String,
    @SerialName("categoryId")
    val categoryId: Int? = null,
    @SerialName("categoryName")
    val categoryName: String? = null,
    val confidence: Double,
    val matchType: String,
    val matchedTerm: String
)

@Serializable
data class ExtractSkillsResponse(
    val success: Boolean = true,
    val data: List<ExtractedSkillDto>
)
