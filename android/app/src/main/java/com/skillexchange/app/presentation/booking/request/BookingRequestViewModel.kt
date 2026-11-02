package com.skillexchange.app.presentation.booking.request

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skillexchange.app.core.network.ApiException
import com.skillexchange.app.data.remote.exchange.CreateExchangeRequestDto
import com.skillexchange.app.domain.model.SkillType
import com.skillexchange.app.domain.model.UserSkill
import com.skillexchange.app.domain.model.exchange.ValidSkillPair
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
            is BookingRequestIntent.SelectPair -> {
                _state.update {
                    it.copy(
                        selectedSkillOfferedId = intent.skillOfferedId,
                        selectedSkillWantedId = intent.skillWantedId
                    )
                }
            }
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

            // 3. Tải cặp kỹ năng hợp lệ và cảnh báo mềm từ GET valid-pairs (DEC-028, TASK-083)
            val validPairsRes = exchangeRepository.getValidPairs(targetReceiverId)
            val pairsResult = validPairsRes.getOrNull()

            val rawPairs = pairsResult?.pairs ?: emptyList()
            val recentlyRejected = pairsResult?.recentlyRejected ?: false

            val finalPairs = if (rawPairs.isNotEmpty() || validPairsRes.isSuccess) {
                rawPairs
            } else {
                // Fallback nếu API valid-pairs thất bại hoặc chưa mock trong test cũ
                validTeach.flatMap { teach ->
                    validLearn.map { learn ->
                        ValidSkillPair(
                            skillOfferedId = teach.skillId,
                            skillOfferedName = teach.skillName,
                            skillWantedId = learn.skillId,
                            skillWantedName = learn.skillName,
                            isActive = false,
                            completedCount = 0
                        )
                    }
                }
            }

            // 4. Chọn cặp mặc định: cặp chênh lệch trình độ tốt nhất theo ProficiencyFit (DEC-028, BUSINESS_RULES.md §2)
            fun fitScore(d: Int): Double = when {
                d < 0 -> 0.0
                d in 0..2 -> 1.0
                d == 3 -> 0.7
                else -> 0.5
            }

            val availablePairs = finalPairs.filter { !it.isActive }
            val bestPair = availablePairs.maxWithOrNull(
                compareBy<ValidSkillPair> { pair ->
                    val teachHave = myHaves.find { it.skillId == pair.skillOfferedId }?.proficiencyLevel ?: 1
                    val teachWant = receiverWants.find { it.skillId == pair.skillOfferedId }?.proficiencyLevel ?: 1
                    val dTeach = teachHave - teachWant

                    val learnHave = receiverHaves.find { it.skillId == pair.skillWantedId }?.proficiencyLevel ?: 1
                    val learnWant = myWants.find { it.skillId == pair.skillWantedId }?.proficiencyLevel ?: 1
                    val dLearn = learnHave - learnWant

                    fitScore(dTeach) + fitScore(dLearn)
                }.thenByDescending { it.completedCount }
            )

            _state.update {
                it.copy(
                    isLoading = false,
                    receiverProfile = profile,
                    validTeachSkills = validTeach,
                    validLearnSkills = validLearn,
                    pairs = finalPairs,
                    recentlyRejected = recentlyRejected,
                    selectedSkillOfferedId = bestPair?.skillOfferedId ?: validTeach.firstOrNull()?.skillId,
                    selectedSkillWantedId = bestPair?.skillWantedId ?: validLearn.firstOrNull()?.skillId,
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
                    val isConflict = (e is ApiException && (e.code == ApiException.CODE_PAIR_ALREADY_ACTIVE || e.code == ApiException.CODE_PENDING_LIMIT_REACHED)) ||
                                     e.message?.contains("PAIR_ALREADY_ACTIVE") == true ||
                                     e.message?.contains("PENDING_LIMIT_REACHED") == true

                    val errorMsg = if (e is ApiException) {
                        when (e.code) {
                            ApiException.CODE_PAIR_ALREADY_ACTIVE ->
                                "Cặp kỹ năng này đang có yêu cầu trao đổi hoạt động."
                            ApiException.CODE_PENDING_LIMIT_REACHED ->
                                "Bạn đang có 3 yêu cầu chờ phản hồi với người này."
                            else -> e.message
                        }
                    } else if (e.message?.contains("PAIR_ALREADY_ACTIVE") == true) {
                        "Cặp kỹ năng này đang có yêu cầu trao đổi hoạt động."
                    } else if (e.message?.contains("PENDING_LIMIT_REACHED") == true) {
                        "Bạn đang có 3 yêu cầu chờ phản hồi với người này."
                    } else {
                        e.message ?: "Gửi yêu cầu thất bại"
                    }

                    _state.update { it.copy(isSubmitting = false, error = errorMsg) }
                    _effect.send(BookingRequestEffect.ShowSnackbar(errorMsg))

                    // Tự động tải lại danh sách cặp khi gặp xung đột để cập nhật cặp active
                    if (isConflict) {
                        loadData(receiverId)
                    }
                }
        }
    }
}
