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
    val isUploadingAvatar: Boolean = false,
    // Profile fields
    val fullName: String = "",
    val bio: String = "",
    val city: String = "",
    val avatarUrl: String = "",
    val savedProfile: Profile? = null,
    val availability: List<com.skillexchange.app.domain.model.AvailabilityWindow> = emptyList(),
    // Skills
    val categories: List<SkillCategory> = emptyList(),
    val mySkills: List<UserSkill> = emptyList(),
    val selectedTab: Int = 0,         // 0 = HAVE, 1 = WANT
    val selectedCategoryId: Int? = null,
    val proficiencyLevel: Int = 3,
    val selectedSkillIdForEdit: String? = null,
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
    data class UploadAvatar(val fileBytes: ByteArray, val fileName: String, val mimeType: String) : ProfileIntent()
    data class AddAvailabilityWindow(val window: com.skillexchange.app.domain.model.AvailabilityWindow) : ProfileIntent()
    data class RemoveAvailabilityWindow(val window: com.skillexchange.app.domain.model.AvailabilityWindow) : ProfileIntent()
    object SaveProfile : ProfileIntent()
    object LoadData : ProfileIntent()
    data class SelectCategory(val id: Int?) : ProfileIntent()
    data class SelectTab(val tab: Int) : ProfileIntent()
    data class AddSkill(val skillId: Int) : ProfileIntent()
    data class RemoveSkill(val userSkillId: String) : ProfileIntent()
    data class SetProficiency(val level: Int) : ProfileIntent()
    data class SelectSkillForEdit(val userSkillId: String?) : ProfileIntent()
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
            is ProfileIntent.UploadAvatar     -> uploadAvatar(intent.fileBytes, intent.fileName, intent.mimeType)
            is ProfileIntent.AddAvailabilityWindow -> addAvailabilityWindow(intent.window)
            is ProfileIntent.RemoveAvailabilityWindow -> removeAvailabilityWindow(intent.window)
            is ProfileIntent.SaveProfile      -> saveProfile()
            is ProfileIntent.SelectCategory   -> _state.update { it.copy(selectedCategoryId = intent.id) }
            is ProfileIntent.SelectTab        -> _state.update { it.copy(selectedTab = intent.tab, selectedSkillIdForEdit = null) }
            is ProfileIntent.AddSkill         -> addSkill(intent.skillId)
            is ProfileIntent.RemoveSkill      -> removeSkill(intent.userSkillId)
            is ProfileIntent.SetProficiency   -> updateProficiency(intent.level)
            is ProfileIntent.SelectSkillForEdit -> selectSkillForEdit(intent.userSkillId)
            is ProfileIntent.GoToSkillStep    -> _state.update { it.copy(step = ProfileStep.SKILL_SELECTION) }
            is ProfileIntent.GoToDone         -> _state.update { it.copy(step = ProfileStep.DONE) }
        }
    }

    private fun uploadAvatar(fileBytes: ByteArray, fileName: String, mimeType: String) {
        if (fileBytes.size > 2 * 1024 * 1024) {
            viewModelScope.launch {
                _effect.send(ProfileEffect.ShowSnackbar("Kích thước ảnh vượt quá giới hạn 2MB"))
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isUploadingAvatar = true) }
            profileRepo.uploadAvatar(fileBytes, fileName, mimeType)
                .onSuccess { newUrl ->
                    _state.update { it.copy(avatarUrl = newUrl, isUploadingAvatar = false) }
                    _effect.send(ProfileEffect.ShowSnackbar("Cập nhật ảnh đại diện thành công!"))
                }
                .onFailure { e ->
                    _state.update { it.copy(isUploadingAvatar = false) }
                    val raw = e.message ?: ""
                    val msg = when {
                        raw.contains("413") || raw.contains("2MB") ->
                            "Kích thước ảnh vượt quá giới hạn 2MB"
                        raw.contains("415") || raw.contains("hỗ trợ") ->
                            "Định dạng ảnh không được hỗ trợ (chỉ chấp nhận JPEG, PNG, WebP)"
                        raw.contains("429") || raw.contains("quá nhiều") ->
                            "Bạn đã tải lên quá nhiều lần. Vui lòng thử lại sau 1 phút."
                        else -> raw.ifBlank { "Tải ảnh đại diện thất bại" }
                    }
                    _effect.send(ProfileEffect.ShowSnackbar(msg))
                }
        }
    }

    private fun addAvailabilityWindow(window: com.skillexchange.app.domain.model.AvailabilityWindow) {
        val timeRegex = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")
        if (!timeRegex.matches(window.from) || !timeRegex.matches(window.to)) {
            viewModelScope.launch { _effect.send(ProfileEffect.ShowSnackbar("Định dạng giờ không hợp lệ (HH:mm)")) }
            return
        }
        if (window.from >= window.to) {
            viewModelScope.launch { _effect.send(ProfileEffect.ShowSnackbar("Giờ bắt đầu phải trước giờ kết thúc")) }
            return
        }
        val overlaps = _state.value.availability.any {
            it.day.equals(window.day, ignoreCase = true) &&
                maxOf(it.from, window.from) < minOf(it.to, window.to)
        }
        if (overlaps) {
            viewModelScope.launch { _effect.send(ProfileEffect.ShowSnackbar("Khung giờ bị trùng với lịch đã có trong ngày")) }
            return
        }
        _state.update { it.copy(availability = it.availability + window) }
    }

    private fun removeAvailabilityWindow(window: com.skillexchange.app.domain.model.AvailabilityWindow) {
        _state.update { it.copy(availability = it.availability - window) }
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
                            avatarUrl = p.avatarUrl ?: "",
                            availability = p.availability
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
                avatarUrl = s.avatarUrl.ifBlank { null },
                availability = s.availability
            )
                .onSuccess { profile ->
                    _state.update { it.copy(isSaving = false, savedProfile = profile, availability = profile.availability) }
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
                    // Replace existing skill with same skillId and type to avoid duplicates
                    val updatedSkills = _state.value.mySkills
                        .filterNot { it.skillId == skill.skillId && it.type == skill.type } + skill
                    _state.update { it.copy(mySkills = updatedSkills, selectedSkillIdForEdit = skill.id) }
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
                    val currentEditId = _state.value.selectedSkillIdForEdit
                    val nextEditId = if (currentEditId == userSkillId) null else currentEditId
                    _state.update { it.copy(mySkills = updatedSkills, selectedSkillIdForEdit = nextEditId) }
                    checkAndUpdateProfileCompletion()
                }
                .onFailure { error ->
                    _effect.send(ProfileEffect.ShowSnackbar(error.message ?: "Không thể xóa kỹ năng"))
                }
        }
    }

    private fun selectSkillForEdit(userSkillId: String?) {
        val skill = _state.value.mySkills.find { it.id == userSkillId }
        _state.update {
            it.copy(
                selectedSkillIdForEdit = userSkillId,
                proficiencyLevel = skill?.proficiencyLevel ?: it.proficiencyLevel
            )
        }
    }

    private fun updateProficiency(level: Int) {
        _state.update { it.copy(proficiencyLevel = level) }
        val editId = _state.value.selectedSkillIdForEdit ?: return
        val targetSkill = _state.value.mySkills.find { it.id == editId } ?: return

        viewModelScope.launch {
            skillRepo.addUserSkill(
                skillId = targetSkill.skillId,
                type = targetSkill.type.name,
                proficiencyLevel = level,
                note = targetSkill.note
            ).onSuccess { updated ->
                val updatedSkills = _state.value.mySkills
                    .filterNot { it.id == targetSkill.id } + updated
                _state.update { it.copy(mySkills = updatedSkills, selectedSkillIdForEdit = updated.id) }
            }
        }
    }

    private fun checkAndUpdateProfileCompletion() {
        val s = _state.value
        val isCompleted = s.fullName.isNotBlank() && s.mySkills.isNotEmpty()
        tokenManager?.saveProfileCompleted(isCompleted)
    }
}
