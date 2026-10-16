package com.skillexchange.app.presentation.profile.myprofile

import com.skillexchange.app.domain.model.AvailabilityWindow
import com.skillexchange.app.domain.model.Profile
import com.skillexchange.app.domain.model.SkillType
import com.skillexchange.app.domain.model.UserSkill
import com.skillexchange.app.domain.model.rating.Reputation

data class MyProfileUiState(
    val isLoading: Boolean = false,
    val profile: Profile? = null,
    val reputation: Reputation? = null,
    val skills: List<UserSkill> = emptyList(),
    val error: String? = null
) {
    val haveSkills: List<UserSkill>
        get() = skills.filter { it.type == SkillType.HAVE }

    val wantSkills: List<UserSkill>
        get() = skills.filter { it.type == SkillType.WANT }

    val availability: List<AvailabilityWindow>
        get() = profile?.availability ?: emptyList()
}

sealed class MyProfileIntent {
    object LoadData : MyProfileIntent()
    object Refresh : MyProfileIntent()
    object EditProfile : MyProfileIntent()
    object ManageSkills : MyProfileIntent()
    object Logout : MyProfileIntent()
}

sealed class MyProfileEffect {
    object NavigateToEditProfile : MyProfileEffect()
    object NavigateToManageSkills : MyProfileEffect()
    object NavigateToLogin : MyProfileEffect()
    data class ShowSnackbar(val message: String) : MyProfileEffect()
}
