package com.skillexchange.app.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skillexchange.app.domain.model.Profile
import com.skillexchange.app.domain.model.SkillCategory
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

// ─── State ────────────────────────────────────────────────────────────
data class ProfileUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    // Profile fields
    val fullName: String = "",
    val bio: String = "",
    val city: String = "",
    val avatarUrl: String = "",
    val savedProfile: Profile? = null,
    // Skills
    val categories: List<SkillCategory> = emptyList(),
    val mySkills: List<UserSkill> = emptyList(),
    val selectedTab: Int = 0,         // 0 = HAVE, 1 = WANT
    val selectedCategoryId: Int? = null,
    val proficiencyLevel: Int = 3,
    // UI
    val error: String? = null,
    val successMessage: String? = null,
    val step: ProfileStep = ProfileStep.PROFILE_INFO
)

enum class ProfileStep { PROFILE_INFO, SKILL_SELECTION, DONE }

// ─── Intent ───────────────────────────────────────────────────────────
sealed class ProfileIntent {
    data class FullNameChanged(val value: String) : ProfileIntent()
    data class BioChanged(val value: String) : ProfileIntent()
    data class CityChanged(val value: String) : ProfileIntent()
    data class AvatarUrlChanged(val value: String) : ProfileIntent()
    object SaveProfile : ProfileIntent()
    object LoadData : ProfileIntent()
    data class SelectCategory(val id: Int?) : ProfileIntent()
    data class SelectTab(val tab: Int) : ProfileIntent()
    data class AddSkill(val skillId: Int) : ProfileIntent()
    data class RemoveSkill(val userSkillId: String) : ProfileIntent()
    data class SetProficiency(val level: Int) : ProfileIntent()
    object GoToSkillStep : ProfileIntent()
    object GoToDone : ProfileIntent()
}

// ─── Effect ───────────────────────────────────────────────────────────
sealed class ProfileEffect {
    object NavigateToHome : ProfileEffect()
    object NavigateToSkillSelection : ProfileEffect()
    data class ShowSnackbar(val message: String) : ProfileEffect()
}

class ProfileViewModel(
    private val profileRepo: IProfileRepository,
    private val skillRepo: ISkillRepository,
    private val tokenManager: com.skillexchange.app.core.security.TokenManager? = null
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state = _state.asStateFlow()

    private val _effect = Channel<ProfileEffect>()
    val effect = _effect.receiveAsFlow()

    init { onIntent(ProfileIntent.LoadData) }

    fun onIntent(intent: ProfileIntent) {
        when (intent) {
            is ProfileIntent.LoadData         -> loadData()
            is ProfileIntent.FullNameChanged  -> _state.update { it.copy(fullName = intent.value, error = null) }
            is ProfileIntent.BioChanged       -> _state.update { it.copy(bio = intent.value) }
            is ProfileIntent.CityChanged      -> _state.update { it.copy(city = intent.value) }
            is ProfileIntent.AvatarUrlChanged -> _state.update { it.copy(avatarUrl = intent.value) }
            is ProfileIntent.SaveProfile      -> saveProfile()
            is ProfileIntent.SelectCategory   -> _state.update { it.copy(selectedCategoryId = intent.id) }
            is ProfileIntent.SelectTab        -> _state.update { it.copy(selectedTab = intent.tab) }
            is ProfileIntent.AddSkill         -> addSkill(intent.skillId)
            is ProfileIntent.RemoveSkill      -> removeSkill(intent.userSkillId)
            is ProfileIntent.SetProficiency   -> _state.update { it.copy(proficiencyLevel = intent.level) }
            is ProfileIntent.GoToSkillStep    -> _state.update { it.copy(step = ProfileStep.SKILL_SELECTION) }
            is ProfileIntent.GoToDone         -> _state.update { it.copy(step = ProfileStep.DONE) }
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            // Load profile + categories concurrently
            launch {
                profileRepo.getMyProfile()
                    .onSuccess { p ->
                        _state.update { it.copy(
                            savedProfile = p, fullName = p.fullName,
                            bio = p.bio ?: "", city = p.city ?: "",
                            avatarUrl = p.avatarUrl ?: ""
                        )}
                        checkAndUpdateProfileCompletion()
                    }
            }
            launch {
                skillRepo.getCategories().onSuccess { cats ->
                    _state.update { it.copy(categories = cats) }
                }
            }
            launch {
                skillRepo.getUserSkills("me").onSuccess { skills ->
                    _state.update { it.copy(mySkills = skills) }
                    checkAndUpdateProfileCompletion()
                }
            }
            _state.update { it.copy(isLoading = false) }
        }
    }

    private fun saveProfile() {
        val s = _state.value
        if (s.fullName.isBlank()) {
            _state.update { it.copy(error = "Tên không được để trống") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            profileRepo.updateProfile(
                fullName = s.fullName,
                bio = s.bio.ifBlank { null },
                city = s.city.ifBlank { null },
                avatarUrl = s.avatarUrl.ifBlank { null }
            )
                .onSuccess { profile ->
                    _state.update { it.copy(isSaving = false, savedProfile = profile) }
                    checkAndUpdateProfileCompletion()
                    _effect.send(ProfileEffect.ShowSnackbar("Lưu hồ sơ thành công!"))
                    _effect.send(ProfileEffect.NavigateToSkillSelection)
                }
                .onFailure { e ->
                    _state.update { it.copy(isSaving = false, error = e.message) }
                }
        }
    }

    private fun addSkill(skillId: Int) {
        val s = _state.value
        val type = if (s.selectedTab == 0) "HAVE" else "WANT"
        viewModelScope.launch {
            skillRepo.addUserSkill(skillId, type, s.proficiencyLevel, null)
                .onSuccess { skill ->
                    val updatedSkills = _state.value.mySkills + skill
                    _state.update { it.copy(mySkills = updatedSkills) }
                    checkAndUpdateProfileCompletion()
                    _effect.send(ProfileEffect.ShowSnackbar("Đã thêm kỹ năng!"))
                }
                .onFailure { e ->
                    _effect.send(ProfileEffect.ShowSnackbar(e.message ?: "Lỗi thêm kỹ năng"))
                }
        }
    }

    private fun removeSkill(userSkillId: String) {
        viewModelScope.launch {
            skillRepo.removeUserSkill(userSkillId)
                .onSuccess {
                    val updatedSkills = _state.value.mySkills.filter { sk -> sk.id != userSkillId }
                    _state.update { it.copy(mySkills = updatedSkills) }
                    checkAndUpdateProfileCompletion()
                }
        }
    }

    private fun checkAndUpdateProfileCompletion() {
        val s = _state.value
        val isCompleted = s.fullName.isNotBlank() && s.mySkills.isNotEmpty()
        tokenManager?.saveProfileCompleted(isCompleted)
    }
}
