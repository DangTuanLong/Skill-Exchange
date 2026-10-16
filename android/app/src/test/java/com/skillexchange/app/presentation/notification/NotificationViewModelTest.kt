package com.skillexchange.app.presentation.notification

import com.skillexchange.app.domain.model.notification.AppNotification
import com.skillexchange.app.domain.model.notification.NotificationListResult
import com.skillexchange.app.domain.model.notification.NotificationType
import com.skillexchange.app.domain.repository.INotificationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fixedToday = LocalDate.of(2026, 10, 10)

    private val fakeNotifications = listOf(
        AppNotification(
            id = "n_1",
            userId = "user_me",
            type = NotificationType.NEW_REQUEST,
            rawType = "NEW_REQUEST",
            title = "Yêu cầu mới",
            body = "Nam muốn trao đổi Kotlin",
            entityId = "ex_100",
            data = "{}",
            isRead = false,
            createdAt = "2026-10-10T09:00:00"
        ),
        AppNotification(
            id = "n_2",
            userId = "user_me",
            type = NotificationType.APPOINTMENT_REMINDER,
            rawType = "APPOINTMENT_REMINDER",
            title = "Nhắc lịch hẹn",
            body = "Lịch hẹn lúc 10:00",
            entityId = "ex_100",
            data = "{}",
            isRead = false,
            createdAt = "2026-10-09T18:00:00"
        ),
        AppNotification(
            id = "n_3",
            userId = "user_me",
            type = NotificationType.RATING_REMINDER,
            rawType = "RATING_REMINDER",
            title = "Đánh giá trao đổi",
            body = "Hãy đánh giá phiên trao đổi",
            entityId = "ex_99",
            data = "{}",
            isRead = true,
            createdAt = "2026-10-05T12:00:00"
        )
    )

    private var markAsReadCalls = mutableListOf<String>()
    private var markAllAsReadCalled = false

    private val fakeRepository = object : INotificationRepository {
        override suspend fun getNotifications(page: Int, limit: Int): Result<NotificationListResult> {
            return Result.success(
                NotificationListResult(
                    notifications = fakeNotifications,
                    unreadCount = fakeNotifications.count { !it.isRead }.toLong(),
                    total = fakeNotifications.size.toLong()
                )
            )
        }

        override suspend fun markAsRead(notificationId: String): Result<Unit> {
            markAsReadCalls.add(notificationId)
            return Result.success(Unit)
        }

        override suspend fun markAllAsRead(): Result<Unit> {
            markAllAsReadCalled = true
            return Result.success(Unit)
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        markAsReadCalls.clear()
        markAllAsReadCalled = false
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): NotificationViewModel {
        return NotificationViewModel(
            notificationRepository = fakeRepository
        ).apply {
            nowProvider = { fixedToday }
        }
    }

    @Test
    fun `init loads notifications and groups by day correctly`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals(3, state.notifications.size)
        assertEquals(2L, state.unreadCount)

        // Check grouping: Hôm nay, Hôm qua, 05/10/2026
        assertEquals(3, state.groups.size)
        assertEquals("Hôm nay", state.groups[0].title)
        assertEquals(1, state.groups[0].items.size)
        assertEquals("n_1", state.groups[0].items[0].id)

        assertEquals("Hôm qua", state.groups[1].title)
        assertEquals(1, state.groups[1].items.size)
        assertEquals("n_2", state.groups[1].items[0].id)

        assertEquals("05/10/2026", state.groups[2].title)
        assertEquals(1, state.groups[2].items.size)
        assertEquals("n_3", state.groups[2].items[0].id)
    }

    @Test
    fun `NotificationClicked optimistically marks as read and emits NavigateToEntity`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val emittedEffects = mutableListOf<NotificationEffect>()
        val job = launch {
            viewModel.effect.collect { emittedEffects.add(it) }
        }

        val clickedItem = viewModel.state.value.notifications.first { it.id == "n_1" }
        assertFalse(clickedItem.isRead)

        viewModel.onIntent(NotificationIntent.NotificationClicked(clickedItem))
        advanceUntilIdle()

        // UI state immediately shows read
        val updatedItem = viewModel.state.value.notifications.first { it.id == "n_1" }
        assertTrue(updatedItem.isRead)
        assertEquals(1L, viewModel.state.value.unreadCount)

        // Repository call executed
        assertEquals(listOf("n_1"), markAsReadCalls)

        // Navigation effect emitted
        assertEquals(1, emittedEffects.size)
        val effect = emittedEffects.first()
        assertTrue(effect is NotificationEffect.NavigateToEntity)
        assertEquals("NEW_REQUEST", (effect as NotificationEffect.NavigateToEntity).type)
        assertEquals("ex_100", effect.entityId)

        job.cancel()
    }

    @Test
    fun `NotificationClicked emits ShowSnackbar when entityId is null`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val emittedEffects = mutableListOf<NotificationEffect>()
        val job = launch {
            viewModel.effect.collect { emittedEffects.add(it) }
        }

        val notificationWithoutEntity = AppNotification(
            id = "n_null_entity",
            userId = "user_me",
            type = NotificationType.OTHER,
            rawType = "SYSTEM_BROADCAST",
            title = "Bảo trì",
            body = "Hệ thống bảo trì",
            entityId = null,
            data = "{}",
            isRead = true,
            createdAt = "2026-10-10T10:00:00"
        )

        viewModel.onIntent(NotificationIntent.NotificationClicked(notificationWithoutEntity))
        advanceUntilIdle()

        assertEquals(1, emittedEffects.size)
        val effect = emittedEffects.first()
        assertTrue(effect is NotificationEffect.ShowSnackbar)

        job.cancel()
    }

    @Test
    fun `MarkAllAsRead optimistically marks all notifications as read`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(2L, viewModel.state.value.unreadCount)

        viewModel.onIntent(NotificationIntent.MarkAllAsRead)
        advanceUntilIdle()

        assertEquals(0L, viewModel.state.value.unreadCount)
        assertTrue(viewModel.state.value.notifications.all { it.isRead })
        assertTrue(markAllAsReadCalled)
    }
}
