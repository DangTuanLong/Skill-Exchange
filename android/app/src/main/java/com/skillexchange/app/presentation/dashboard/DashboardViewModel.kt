package com.skillexchange.app.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skillexchange.app.domain.repository.IStatsRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val statsRepository: IStatsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    private val _effect = Channel<DashboardEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    init {
        loadData()
    }

    fun onIntent(intent: DashboardIntent) {
        when (intent) {
            is DashboardIntent.LoadData -> loadData()
            is DashboardIntent.Refresh -> refreshData()
            is DashboardIntent.ClickLeaderboardUser -> {
                viewModelScope.launch {
                    _effect.send(DashboardEffect.NavigateToProfile(intent.userId))
                }
            }
            is DashboardIntent.ClickSkillGap -> {
                viewModelScope.launch {
                    _effect.send(DashboardEffect.ShowSnackbar("Gợi ý: Tìm đối tác dạy ${intent.skillName} ở tab Khám phá!"))
                }
            }
        }
    }

    private fun loadData() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val statsDeferred = async { statsRepository.getUserStats() }
            val leaderboardDeferred = async { statsRepository.getLeaderboard() }

            val statsResult = statsDeferred.await()
            val leaderboardResult = leaderboardDeferred.await()

            if (statsResult.isSuccess && leaderboardResult.isSuccess) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        userStats = statsResult.getOrNull(),
                        leaderboard = leaderboardResult.getOrDefault(emptyList()),
                        error = null
                    )
                }
            } else {
                val errorMsg = statsResult.exceptionOrNull()?.message
                    ?: leaderboardResult.exceptionOrNull()?.message
                    ?: "Không thể tải dữ liệu thống kê"
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = errorMsg
                    )
                }
            }
        }
    }

    private fun refreshData() {
        _state.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            val statsDeferred = async { statsRepository.getUserStats() }
            val leaderboardDeferred = async { statsRepository.getLeaderboard() }

            val statsResult = statsDeferred.await()
            val leaderboardResult = leaderboardDeferred.await()

            if (statsResult.isSuccess && leaderboardResult.isSuccess) {
                _state.update {
                    it.copy(
                        isRefreshing = false,
                        userStats = statsResult.getOrNull(),
                        leaderboard = leaderboardResult.getOrDefault(emptyList()),
                        error = null
                    )
                }
            } else {
                _state.update { it.copy(isRefreshing = false) }
                _effect.send(DashboardEffect.ShowSnackbar("Cập nhật thất bại. Vui lòng thử lại."))
            }
        }
    }
}
