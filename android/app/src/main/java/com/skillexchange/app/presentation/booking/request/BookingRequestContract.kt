package com.skillexchange.app.presentation.booking.request

import com.skillexchange.app.domain.model.Profile
import com.skillexchange.app.domain.model.UserSkill
import com.skillexchange.app.domain.model.exchange.MeetingMode

data class BookingRequestState(
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val receiverProfile: Profile? = null,
    val validTeachSkills: List<UserSkill> = emptyList(), // Current user's HAVE that receiver WANTs
    val validLearnSkills: List<UserSkill> = emptyList(), // Receiver's HAVE that current user WANTs
    val selectedSkillOfferedId: Int? = null,
    val selectedSkillWantedId: Int? = null,
    val scheduledDateEpochMs: Long = System.currentTimeMillis() + 86400000L, // Default tomorrow
    val scheduledHour: Int = 10,
    val scheduledMinute: Int = 0,
    val durationMinutes: Int = 60,
    val meetingMode: MeetingMode = MeetingMode.UNDECIDED,
    val message: String = "",
    val error: String? = null
) {
    val hasValidPair: Boolean
        get() = validTeachSkills.isNotEmpty() && validLearnSkills.isNotEmpty()

    val canSubmit: Boolean
        get() = hasValidPair &&
                selectedSkillOfferedId != null &&
                selectedSkillWantedId != null &&
                !isSubmitting &&
                !isLoading
}

sealed class BookingRequestIntent {
    data class LoadData(val receiverId: String) : BookingRequestIntent()
    data class SelectSkillOffered(val skillId: Int) : BookingRequestIntent()
    data class SelectSkillWanted(val skillId: Int) : BookingRequestIntent()
    data class SetScheduledDate(val epochMs: Long) : BookingRequestIntent()
    data class SetScheduledTime(val hour: Int, val minute: Int) : BookingRequestIntent()
    data class SetDuration(val minutes: Int) : BookingRequestIntent()
    data class SetMeetingMode(val mode: MeetingMode) : BookingRequestIntent()
    data class SetMessage(val message: String) : BookingRequestIntent()
    object SubmitRequest : BookingRequestIntent()
}

sealed class BookingRequestEffect {
    data class NavigateToDetail(val exchangeId: String, val receiverId: String) : BookingRequestEffect()
    object NavigateToSkillSelection : BookingRequestEffect()
    data class ShowSnackbar(val message: String) : BookingRequestEffect()
}
