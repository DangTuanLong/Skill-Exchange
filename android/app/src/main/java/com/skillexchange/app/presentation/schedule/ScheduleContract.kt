package com.skillexchange.app.presentation.schedule

import com.skillexchange.app.domain.model.AvailabilityWindow
import com.skillexchange.app.domain.model.exchange.ExchangeRequest
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

data class ScheduleState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val currentUserId: String = "",
    val selectedDate: LocalDate = LocalDate.now(),
    val currentWeekStart: LocalDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
    val confirmedExchanges: List<ExchangeRequest> = emptyList(),
    val myAvailability: List<AvailabilityWindow> = emptyList()
) {
    /**
     * 7 ngày trong tuần hiện tại (từ Thứ 2 đến Chủ nhật)
     */
    val daysInCurrentWeek: List<LocalDate>
        get() = (0L..6L).map { currentWeekStart.plusDays(it) }

    /**
     * Tập hợp các ngày có ít nhất một buổi hẹn đã xác nhận (ACCEPTED)
     */
    val datesWithBookings: Set<LocalDate>
        get() = confirmedExchanges.mapNotNull { exchange ->
            try {
                Instant.parse(exchange.scheduledAt).atZone(ZoneId.systemDefault()).toLocalDate()
            } catch (e: Exception) {
                null
            }
        }.toSet()

    /**
     * Danh sách các buổi hẹn đã chốt rơi vào ngày được chọn, sắp xếp theo giờ bắt đầu
     */
    val sessionsOnSelectedDate: List<ExchangeRequest>
        get() = confirmedExchanges.filter { exchange ->
            try {
                val exchangeDate = Instant.parse(exchange.scheduledAt).atZone(ZoneId.systemDefault()).toLocalDate()
                exchangeDate == selectedDate
            } catch (e: Exception) {
                false
            }
        }.sortedBy { it.scheduledAt }

    /**
     * Khung giờ rảnh cá nhân đã thiết lập tương ứng với thứ của ngày được chọn
     */
    val openAvailabilityOnSelectedDate: List<AvailabilityWindow>
        get() {
            val dayKey = when (selectedDate.dayOfWeek) {
                DayOfWeek.MONDAY -> "MON"
                DayOfWeek.TUESDAY -> "TUE"
                DayOfWeek.WEDNESDAY -> "WED"
                DayOfWeek.THURSDAY -> "THU"
                DayOfWeek.FRIDAY -> "FRI"
                DayOfWeek.SATURDAY -> "SAT"
                DayOfWeek.SUNDAY -> "SUN"
            }
            return myAvailability
                .filter { it.day.uppercase() == dayKey }
                .sortedBy { it.from }
        }
}

sealed class ScheduleIntent {
    object LoadData : ScheduleIntent()
    object Refresh : ScheduleIntent()
    data class SelectDate(val date: LocalDate) : ScheduleIntent()
    data class ChangeWeek(val offsetWeeks: Long) : ScheduleIntent()
    object JumpToToday : ScheduleIntent()
}

sealed class ScheduleEffect {
    data class NavigateToDetail(val exchangeId: String) : ScheduleEffect()
    data class NavigateToChat(val chatId: String) : ScheduleEffect()
    data class ShowSnackbar(val message: String) : ScheduleEffect()
}
