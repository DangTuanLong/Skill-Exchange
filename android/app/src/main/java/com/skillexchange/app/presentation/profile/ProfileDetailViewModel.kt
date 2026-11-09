package com.skillexchange.app.presentation.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skillexchange.app.core.security.TokenManager
import com.skillexchange.app.domain.model.Profile
import com.skillexchange.app.domain.model.SkillType
import com.skillexchange.app.domain.model.UserSkill
import com.skillexchange.app.domain.repository.IProfileRepository
import com.skillexchange.app.domain.repository.ISkillRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileDetailUiState(
    val isLoading: Boolean = false,
    val isAddingSkill: Boolean = false,
    val profile: Profile? = null,
    val skills: List<UserSkill> = emptyList(),
    val hasValidPair: Boolean = false,
    val isOwnProfile: Boolean = false,
    val noValidPairReason: String? = null,
    val suggestedSkills: List<UserSkill> = emptyList(),
    val reputation: com.skillexchange.app.domain.model.rating.Reputation? = null,
    val reviews: List<com.skillexchange.app.domain.model.rating.Rating> = emptyList(),
    val showAllReviewsBottomSheet: Boolean = false,
    val relationshipStatus: String? = null, // "ACTIVE", "COMPLETED", null (DEC-028, TASK-083)
    val completedExchangeCount: Int = 0,
    val recentlyRejected: Boolean = false,
    val error: String? = null
) {
    val suggestedSkill: UserSkill?
        get() = suggestedSkills.firstOrNull()
}

sealed class ProfileDetailIntent {
    data class LoadUserProfile(val userId: String) : ProfileDetailIntent()
    object RequestExchange : ProfileDetailIntent()
    data class AddSuggestedSkillAndMatch(val skill: UserSkill) : ProfileDetailIntent()
    object OpenAllReviews : ProfileDetailIntent()
    object DismissAllReviews : ProfileDetailIntent()
}

sealed class ProfileDetailEffect {
    data class NavigateToBooking(val receiverId: String) : ProfileDetailEffect()
    data class ShowSnackbar(val message: String) : ProfileDetailEffect()
}

class ProfileDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val profileRepository: IProfileRepository,
    private val skillRepository: ISkillRepository,
    private val tokenManager: TokenManager,
    private val ratingRepository: com.skillexchange.app.domain.repository.IRatingRepository,
    private val exchangeRepository: com.skillexchange.app.domain.repository.IExchangeRepository? = null
) : ViewModel() {

    private val targetUserId: String? = savedStateHandle["userId"]
    val hasInitialTargetUser: Boolean = targetUserId != null

    private val _state = MutableStateFlow(ProfileDetailUiState())
    val state = _state.asStateFlow()

    private val _effect = Channel<ProfileDetailEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    init {
        targetUserId?.let { loadUserProfile(it) }
    }

    fun onIntent(intent: ProfileDetailIntent) {
        when (intent) {
            is ProfileDetailIntent.LoadUserProfile -> loadUserProfile(intent.userId)
            is ProfileDetailIntent.RequestExchange -> {
                targetUserId?.let { userId ->
                    viewModelScope.launch {
                        _effect.send(ProfileDetailEffect.NavigateToBooking(userId))
                    }
                }
            }
            is ProfileDetailIntent.AddSuggestedSkillAndMatch -> addSuggestedSkill(intent.skill)
            ProfileDetailIntent.OpenAllReviews -> _state.update { it.copy(showAllReviewsBottomSheet = true) }
            ProfileDetailIntent.DismissAllReviews -> _state.update { it.copy(showAllReviewsBottomSheet = false) }
        }
    }

    private fun addSuggestedSkill(skill: UserSkill) {
        val userId = targetUserId ?: return
        viewModelScope.launch {
            _state.update { it.copy(isAddingSkill = true) }
            val targetLevel = skill.proficiencyLevel.coerceIn(1, 5)
            skillRepository.addUserSkill(skill.skillId, "WANT", targetLevel, null)
                .onSuccess {
                    _effect.send(ProfileDetailEffect.ShowSnackbar("Đã thêm \"${skill.skillName}\" vào danh sách muốn học!"))
                    loadUserProfile(userId)
                }
                .onFailure { err ->
                    _state.update { it.copy(isAddingSkill = false) }
                    _effect.send(ProfileDetailEffect.ShowSnackbar(err.message ?: "Không thể thêm kỹ năng"))
                }
        }
    }

    fun loadUserProfile(userId: String) {
        val currentUserId = tokenManager.getUserId()
        val isOwn = currentUserId == userId

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, isOwnProfile = isOwn) }

            val (profileRes, targetSkillsRes, mySkillsRes, reputationRes, reviewsRes, validPairsRes) = coroutineScope {
                val profileDeferred = async { profileRepository.getUserProfile(userId) }
                val targetSkillsDeferred = async { skillRepository.getUserSkills(userId) }
                val mySkillsDeferred = async { skillRepository.getUserSkills("me") }
                val reputationDeferred = async { ratingRepository.getUserReputation(userId) }
                val reviewsDeferred = async { ratingRepository.getUserRatings(userId) }
                val validPairsDeferred = async {
                    if (!isOwn) exchangeRepository?.getValidPairs(userId)?.getOrNull() else null
                }
                 sextuple(
                    profileDeferred.await(),
                    targetSkillsDeferred.await(),
                    mySkillsDeferred.await(),
                    reputationDeferred.await(),
                    reviewsDeferred.await(),
                    validPairsDeferred.await()
                )
            }

            val profile = profileRes.getOrNull()
            val targetSkills = targetSkillsRes.getOrDefault(emptyList())
            val mySkills = mySkillsRes.getOrDefault(emptyList())
            val reputation = reputationRes.getOrNull()
            val reviews = reviewsRes.getOrDefault(emptyList())

            val myHaves = mySkills.filter { it.type == SkillType.HAVE }
            val myWants = mySkills.filter { it.type == SkillType.WANT }
            val targetHaves = targetSkills.filter { it.type == SkillType.HAVE }
            val targetWants = targetSkills.filter { it.type == SkillType.WANT }

            val validTeach = myHaves.any { have ->
                val want = targetWants.find { it.skillId == have.skillId }
                want != null && have.proficiencyLevel >= want.proficiencyLevel
            }
            val validLearn = targetHaves.any { have ->
                val want = myWants.find { it.skillId == have.skillId }
                want != null && have.proficiencyLevel >= want.proficiencyLevel
            }

            // Tiềm năng 1 chiều: Mình dạy được cho đối phương, nhưng đối phương dạy kỹ năng mình chưa có trong WANT
            val suggestedSkills = if (!isOwn && validTeach && !validLearn) {
                targetHaves.filter { targetHave ->
                    myWants.none { myWant -> myWant.skillId == targetHave.skillId }
                }
            } else {
                emptyList()
            }

            val validPairs = validPairsRes?.pairs ?: emptyList()
            val recentlyRejected = validPairsRes?.recentlyRejected ?: false
            val relStatus = validPairsRes?.relationshipStatus
            val completedCount = validPairsRes?.completedExchangeCount ?: 0

            val availablePairs = validPairs.filter { !it.isActive }

            val hasValidPair = when {
                isOwn -> false
                !validTeach || !validLearn -> false
                validPairsRes != null && validPairs.isNotEmpty() -> availablePairs.isNotEmpty()
                validPairsRes != null && validPairs.isEmpty() -> false
                else -> !isOwn && validTeach && validLearn
            }

            val reason = when {
                isOwn -> "Đây là hồ sơ cá nhân của bạn"
                validTeach && validLearn && validPairsRes != null && validPairs.isNotEmpty() && availablePairs.isEmpty() ->
                    "Tất cả cặp kỹ năng phù hợp đang có yêu cầu trao đổi đang xử lý."
                !hasValidPair && suggestedSkills.isNotEmpty() -> null
                !hasValidPair -> "Chưa có cặp kỹ năng phù hợp hai chiều để trao đổi"
                else -> null
            }

            val effectiveRelStatus = relStatus
                ?: if (validPairs.any { it.isActive }) "ACTIVE"
                   else if (completedCount > 0 || validPairs.any { it.completedCount > 0 }) "COMPLETED"
                   else null

            val effectiveCompletedCount = maxOf(
                completedCount,
                validPairs.maxOfOrNull { it.completedCount } ?: 0
            )

            _state.update {
                it.copy(
                    isLoading = false,
                    isAddingSkill = false,
                    profile = profile,
                    skills = targetSkills,
                    hasValidPair = hasValidPair,
                    noValidPairReason = reason,
                    suggestedSkills = suggestedSkills,
                    reputation = reputation,
                    reviews = reviews,
                    relationshipStatus = effectiveRelStatus,
                    completedExchangeCount = effectiveCompletedCount,
                    recentlyRejected = recentlyRejected,
                    error = if (profile == null) (profileRes.exceptionOrNull()?.message ?: "Lỗi tải hồ sơ") else null
                )
            }
        }
    }

    private data class Sextuple<A, B, C, D, E, F>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
        val fifth: E,
        val sixth: F
    )

    private fun <A, B, C, D, E, F> sextuple(a: A, b: B, c: C, d: D, e: E, f: F) =
        Sextuple(a, b, c, d, e, f)
}
