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
import kotlinx.coroutines.channels.Channel
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
    private val ratingRepository: com.skillexchange.app.domain.repository.IRatingRepository
) : ViewModel() {

    private val targetUserId: String? = savedStateHandle["userId"]

    private val _state = MutableStateFlow(ProfileDetailUiState())
    val state = _state.asStateFlow()

    private val _effect = Channel<ProfileDetailEffect>()
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

            val profileRes = profileRepository.getUserProfile(userId)
            val profile = profileRes.getOrNull()

            val targetSkillsRes = skillRepository.getUserSkills(userId)
            val targetSkills = targetSkillsRes.getOrDefault(emptyList())

            val mySkillsRes = skillRepository.getUserSkills("me")
            val mySkills = mySkillsRes.getOrDefault(emptyList())

            val reputationRes = ratingRepository.getUserReputation(userId)
            val reputation = reputationRes.getOrNull()

            val reviewsRes = ratingRepository.getUserRatings(userId)
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
            val hasValidPair = !isOwn && validTeach && validLearn

            // Tiềm năng 1 chiều: Mình dạy được cho đối phương, nhưng đối phương dạy kỹ năng mình chưa có trong WANT
            val suggestedSkills = if (!isOwn && validTeach && !validLearn) {
                targetHaves.filter { targetHave ->
                    myWants.none { myWant -> myWant.skillId == targetHave.skillId }
                }
            } else {
                emptyList()
            }

            val reason = when {
                isOwn -> "Đây là hồ sơ cá nhân của bạn"
                !hasValidPair && suggestedSkills.isNotEmpty() -> null
                !hasValidPair -> "Chưa có cặp kỹ năng phù hợp hai chiều để trao đổi"
                else -> null
            }

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
                    error = if (profile == null) (profileRes.exceptionOrNull()?.message ?: "Lỗi tải hồ sơ") else null
                )
            }
        }
    }
}
