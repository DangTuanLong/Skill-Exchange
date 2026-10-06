package com.skillexchange.app.presentation.booking.request

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skillexchange.app.data.remote.exchange.CreateExchangeRequestDto
import com.skillexchange.app.domain.model.SkillType
import com.skillexchange.app.domain.model.UserSkill
import com.skillexchange.app.domain.repository.IExchangeRepository
import com.skillexchange.app.domain.repository.IProfileRepository
import com.skillexchange.app.domain.repository.ISkillRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class BookingRequestViewModel(
    savedStateHandle: SavedStateHandle,
    private val profileRepository: IProfileRepository,
    private val skillRepository: ISkillRepository,
    private val exchangeRepository: IExchangeRepository
) : ViewModel() {

    val receiverId: String = savedStateHandle["receiverId"] ?: ""

    private val _state = MutableStateFlow(BookingRequestState())
    val state = _state.asStateFlow()

    private val _effect = Channel<BookingRequestEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    init {
        if (receiverId.isNotBlank()) {
            loadData(receiverId)
        }
    }

    fun onIntent(intent: BookingRequestIntent) {
        when (intent) {
            is BookingRequestIntent.LoadData -> loadData(intent.receiverId)
            is BookingRequestIntent.SelectSkillOffered -> _state.update { it.copy(selectedSkillOfferedId = intent.skillId) }
            is BookingRequestIntent.SelectSkillWanted -> _state.update { it.copy(selectedSkillWantedId = intent.skillId) }
            is BookingRequestIntent.SetScheduledDate -> _state.update { it.copy(scheduledDateEpochMs = intent.epochMs) }
            is BookingRequestIntent.SetScheduledTime -> _state.update { it.copy(scheduledHour = intent.hour, scheduledMinute = intent.minute) }
            is BookingRequestIntent.SetDuration -> _state.update { it.copy(durationMinutes = intent.minutes) }
            is BookingRequestIntent.SetMeetingMode -> _state.update { it.copy(meetingMode = intent.mode) }
            is BookingRequestIntent.SetMessage -> _state.update { it.copy(message = intent.message) }
            is BookingRequestIntent.SubmitRequest -> submitRequest()
        }
    }

    fun loadData(targetReceiverId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            // 1. Tải hồ sơ đối tác
            val profileRes = profileRepository.getUserProfile(targetReceiverId)
            val profile = profileRes.getOrNull()

            // 2. Tải kỹ năng của tôi và kỹ năng của đối tác
            val mySkillsRes = skillRepository.getUserSkills("me")
            val receiverSkillsRes = skillRepository.getUserSkills(targetReceiverId)

            val mySkills = mySkillsRes.getOrDefault(emptyList())
            val receiverSkills = receiverSkillsRes.getOrDefault(emptyList())

            // 3. Tính toán cặp kỹ năng hợp lệ theo BUSINESS_RULES.md §3:
            // - Bạn dạy X: Bạn có HAVE X >= đối tác target WANT X
            // - Đối tác dạy Y: Đối tác có HAVE Y >= bạn target WANT Y
            val myHaves = mySkills.filter { it.type == SkillType.HAVE }
            val myWants = mySkills.filter { it.type == SkillType.WANT }
            val receiverHaves = receiverSkills.filter { it.type == SkillType.HAVE }
            val receiverWants = receiverSkills.filter { it.type == SkillType.WANT }

            val validTeach = myHaves.filter { have ->
                val matchingWant = receiverWants.find { it.skillId == have.skillId }
                matchingWant != null && have.proficiencyLevel >= matchingWant.proficiencyLevel
            }

            val validLearn = receiverHaves.filter { have ->
                val matchingWant = myWants.find { it.skillId == have.skillId }
                matchingWant != null && have.proficiencyLevel >= matchingWant.proficiencyLevel
            }

            _state.update {
                it.copy(
                    isLoading = false,
                    receiverProfile = profile,
                    validTeachSkills = validTeach,
                    validLearnSkills = validLearn,
                    selectedSkillOfferedId = validTeach.firstOrNull()?.skillId,
                    selectedSkillWantedId = validLearn.firstOrNull()?.skillId,
                    error = if (profile == null) "Không thể tải thông tin người dùng" else null
                )
            }
        }
    }

    private fun submitRequest() {
        val currentState = _state.value
        if (!currentState.canSubmit) return

        val skillOfferedId = currentState.selectedSkillOfferedId ?: return
        val skillWantedId = currentState.selectedSkillWantedId ?: return

        // Tính toán ISO-8601 string cho scheduledAt và kiểm tra tương lai
        val scheduledInstant = try {
            val localDate = Instant.ofEpochMilli(currentState.scheduledDateEpochMs)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            val localDateTime = localDate.atTime(currentState.scheduledHour, currentState.scheduledMinute)
            localDateTime.atZone(ZoneId.systemDefault()).toInstant()
        } catch (e: Exception) {
            _state.update { it.copy(error = "Thời gian không hợp lệ") }
            return
        }

        if (scheduledInstant.isBefore(Instant.now())) {
            val msg = "Thời gian trao đổi phải ở tương lai"
            _state.update { it.copy(error = msg) }
            viewModelScope.launch { _effect.send(BookingRequestEffect.ShowSnackbar(msg)) }
            return
        }

        val scheduledAtIso = DateTimeFormatter.ISO_INSTANT.format(scheduledInstant)

        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null) }

            val reqDto = CreateExchangeRequestDto(
                receiverId = receiverId,
                skillOfferedId = skillOfferedId,
                skillWantedId = skillWantedId,
                scheduledAt = scheduledAtIso,
                durationMinutes = currentState.durationMinutes,
                meetingMode = currentState.meetingMode.name,
                message = currentState.message.takeIf { it.isNotBlank() }
            )

            exchangeRepository.createExchangeRequest(reqDto)
                .onSuccess { exchange ->
                    _state.update { it.copy(isSubmitting = false) }
                    _effect.send(BookingRequestEffect.NavigateToDetail(exchange.id, receiverId))
                }
                .onFailure { e ->
                    val errorMsg = e.message ?: "Gửi yêu cầu thất bại"
                    _state.update { it.copy(isSubmitting = false, error = errorMsg) }
                    _effect.send(BookingRequestEffect.ShowSnackbar(errorMsg))
                }
        }
    }
}
