package com.skillexchange.app.presentation.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skillexchange.app.core.security.TokenManager
import com.skillexchange.app.domain.model.exchange.ExchangeStatus
import com.skillexchange.app.domain.repository.IExchangeRepository
import com.skillexchange.app.domain.repository.IProfileRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

class ScheduleViewModel(
    private val exchangeRepository: IExchangeRepository,
    private val profileRepository: IProfileRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _state = MutableStateFlow(
        ScheduleState(currentUserId = tokenManager.getUserId() ?: "")
    )
    val state = _state.asStateFlow()

    private val _effect = Channel<ScheduleEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    init {
        loadData(isRefresh = false)
    }

    fun onIntent(intent: ScheduleIntent) {
        when (intent) {
            is ScheduleIntent.LoadData -> loadData(isRefresh = false)
            is ScheduleIntent.Refresh -> loadData(isRefresh = true)
            is ScheduleIntent.SelectDate -> selectDate(intent.date)
            is ScheduleIntent.ChangeWeek -> changeWeek(intent.offsetWeeks)
            is ScheduleIntent.JumpToToday -> jumpToToday()
        }
    }

    fun loadData(isRefresh: Boolean = false) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoading = !isRefresh,
                    isRefreshing = isRefresh,
                    error = null,
                    currentUserId = tokenManager.getUserId() ?: it.currentUserId
                )
            }

            val incomingDeferred = async { exchangeRepository.getIncomingRequests() }
            val outgoingDeferred = async { exchangeRepository.getOutgoingRequests() }
            val profileDeferred = async { profileRepository.getMyProfile() }

            val incomingRes = incomingDeferred.await()
            val outgoingRes = outgoingDeferred.await()
            val profileRes = profileDeferred.await()

            if (incomingRes.isFailure && outgoingRes.isFailure) {
                val errorMsg = incomingRes.exceptionOrNull()?.message
                    ?: outgoingRes.exceptionOrNull()?.message
                    ?: "Không thể tải danh sách lịch hẹn"
                _state.update { it.copy(isLoading = false, isRefreshing = false, error = errorMsg) }
                _effect.send(ScheduleEffect.ShowSnackbar(errorMsg))
                return@launch
            }

            val allIncoming = incomingRes.getOrDefault(emptyList())
            val allOutgoing = outgoingRes.getOrDefault(emptyList())

            // Lọc chỉ các buổi trao đổi đã chốt (ACCEPTED), hợp nhất 2 chiều không trùng ID
            val confirmed = (allIncoming + allOutgoing)
                .filter { it.status == ExchangeStatus.ACCEPTED }
                .distinctBy { it.id }

            val myProfile = profileRes.getOrNull()
            val availability = myProfile?.availability ?: emptyList()

            _state.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    confirmedExchanges = confirmed,
                    myAvailability = availability
                )
            }
        }
    }

    private fun selectDate(date: LocalDate) {
        val weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        _state.update {
            it.copy(
                selectedDate = date,
                currentWeekStart = weekStart
            )
        }
    }

    private fun changeWeek(offsetWeeks: Long) {
        _state.update { current ->
            val newWeekStart = current.currentWeekStart.plusWeeks(offsetWeeks)
            // Giữ nguyên thứ tương ứng trong tuần mới
            val dayDiff = current.selectedDate.dayOfWeek.value - DayOfWeek.MONDAY.value
            val newSelectedDate = newWeekStart.plusDays(dayDiff.toLong())
            current.copy(
                currentWeekStart = newWeekStart,
                selectedDate = newSelectedDate
            )
        }
    }

    private fun jumpToToday() {
        val today = LocalDate.now()
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        _state.update {
            it.copy(
                selectedDate = today,
                currentWeekStart = weekStart
            )
        }
    }
}
