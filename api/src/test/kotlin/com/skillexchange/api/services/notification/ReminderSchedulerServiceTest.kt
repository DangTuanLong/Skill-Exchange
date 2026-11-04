package com.skillexchange.api.services.notification

import com.skillexchange.api.services.call.CallService
import com.skillexchange.api.services.exchange.RescheduleService
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertTrue

class ReminderSchedulerServiceTest {

    private val notificationService = mockk<NotificationService>(relaxed = true)
    private val callService = mockk<CallService>(relaxed = true)
    private val rescheduleService = mockk<RescheduleService>(relaxed = true)
    private val fixedNow = LocalDateTime.of(2026, 11, 4, 10, 0, 0)

    private val reminderSchedulerService = ReminderSchedulerService(
        notificationService = notificationService,
        timeProvider = { fixedNow },
        callService = callService,
        rescheduleService = rescheduleService
    )

    @Test
    fun `processReminders - calls cleanupExpiredCalls and cleanupExpiredProposals`() {
        val response = reminderSchedulerService.processReminders()

        assertTrue(response.success)
        // Verify callService.cleanupExpiredCalls() was invoked
        verify(exactly = 1) { callService.cleanupExpiredCalls() }
        // Verify rescheduleService.cleanupExpiredProposals(now) was invoked
        verify(exactly = 1) { rescheduleService.cleanupExpiredProposals(fixedNow) }
    }
}

