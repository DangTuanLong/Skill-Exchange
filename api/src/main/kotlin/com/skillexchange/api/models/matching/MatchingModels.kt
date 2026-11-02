package com.skillexchange.api.models.matching

import com.skillexchange.api.models.profile.AvailabilityWindowDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class UserSkillData(
    val skillId: Int,
    val skillName: String,
    val type: String, // "HAVE" hoặc "WANT"
    val proficiencyLevel: Int // 1..5
)

data class UserMatchingProfile(
    val userId: String,
    val fullName: String,
    val avatarUrl: String? = null,
    val city: String? = null,
    val availability: List<AvailabilityWindowDto> = emptyList(),
    val skills: List<UserSkillData> = emptyList()
)

@Serializable
data class MatchingScoreBreakdown(
    @SerialName("skill_match") val skillMatch: Double,
    @SerialName("proficiency_fit") val proficiencyFit: Double,
    @SerialName("location_bonus") val locationBonus: Double,
    @SerialName("availability_overlap") val availabilityOverlap: Double,
    @SerialName("available_pairs_count") val availablePairsCount: Int = 0
)

@Serializable
data class MatchedSkillDto(
    @SerialName("skill_id") val skillId: Int,
    @SerialName("skill_name") val skillName: String,
    @SerialName("teacher_level") val teacherLevel: Int,
    @SerialName("learner_level") val learnerLevel: Int
)

@Serializable
data class MatchedSkillsBreakdown(
    @SerialName("they_teach_you") val theyTeachYou: List<MatchedSkillDto> = emptyList(),
    @SerialName("you_teach_them") val youTeachThem: List<MatchedSkillDto> = emptyList()
)

@Serializable
data class MatchingScoreResponse(
    @SerialName("target_user_id") val targetUserId: String,
    @SerialName("total_score") val totalScore: Double,
    val percentage: Int,
    val breakdown: MatchingScoreBreakdown,
    @SerialName("matched_skills") val matchedSkills: MatchedSkillsBreakdown
)

@Serializable
data class MatchingSuggestionDto(
    @SerialName("user_id") val userId: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val city: String? = null,
    @SerialName("total_score") val totalScore: Double,
    val percentage: Int,
    val breakdown: MatchingScoreBreakdown,
    @SerialName("matched_skills") val matchedSkills: MatchedSkillsBreakdown,
    @SerialName("available_pairs_count") val availablePairsCount: Int = 0
)

@Serializable
data class MatchingSuggestionsResponse(
    val items: List<MatchingSuggestionDto>,
    @SerialName("next_cursor") val nextCursor: String? = null
)

@Serializable
data class PotentialMatchSkillDto(
    @SerialName("skill_id") val skillId: Int,
    @SerialName("skill_name") val skillName: String,
    @SerialName("proficiency_level") val proficiencyLevel: Int
)

@Serializable
data class PotentialMatchDto(
    @SerialName("user_id") val userId: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val city: String? = null,
    @SerialName("can_teach_skill") val canTeachSkill: PotentialMatchSkillDto,
    @SerialName("suggested_want_skill") val suggestedWantSkill: PotentialMatchSkillDto,
    @SerialName("potential_score") val potentialScore: Int
)

@Serializable
data class PotentialSuggestionsResponse(
    val items: List<PotentialMatchDto>,
    @SerialName("next_cursor") val nextCursor: String? = null
)
