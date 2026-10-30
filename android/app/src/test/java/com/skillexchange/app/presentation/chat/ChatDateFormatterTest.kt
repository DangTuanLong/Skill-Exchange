package com.skillexchange.app.presentation.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class ChatDateFormatterTest {

    private val vietnamZone = ZoneId.of("Asia/Ho_Chi_Minh")

    @Test
    fun `isSameDay returns false across Vietnam midnight boundary 23_59 and 00_01`() {
        // Day 1: 2026-10-30 23:59:59 Vietnam Time (UTC: 2026-10-30 16:59:59)
        val ldt1 = LocalDateTime.of(2026, 10, 30, 23, 59, 59)
        val time1 = ldt1.atZone(vietnamZone).toInstant().toEpochMilli()

        // Day 2: 2026-10-31 00:00:01 Vietnam Time (UTC: 2026-10-30 17:00:01)
        val ldt2 = LocalDateTime.of(2026, 10, 31, 0, 0, 1)
        val time2 = ldt2.atZone(vietnamZone).toInstant().toEpochMilli()

        // In UTC, both times are on 2026-10-30, but in Asia/Ho_Chi_Minh they are different days!
        val sameDayVietnam = ChatDateFormatter.isSameDay(time1, time2, vietnamZone)
        assertFalse("23:59 on Oct 30 and 00:00:01 on Oct 31 must be different days in Vietnam", sameDayVietnam)

        // Verify that naive UTC division would have failed
        val naiveUtcSameDay = (time1 / 86_400_000L) == (time2 / 86_400_000L)
        assertTrue("Naive UTC division falsely treats 23:59 and 00:01 as same day", naiveUtcSameDay)
    }

    @Test
    fun `isSameDay returns true for 00_01 and 23_59 on the same Vietnam day`() {
        val ldtStart = LocalDateTime.of(2026, 10, 30, 0, 1, 0)
        val timeStart = ldtStart.atZone(vietnamZone).toInstant().toEpochMilli()

        val ldtEnd = LocalDateTime.of(2026, 10, 30, 23, 59, 0)
        val timeEnd = ldtEnd.atZone(vietnamZone).toInstant().toEpochMilli()

        assertTrue(ChatDateFormatter.isSameDay(timeStart, timeEnd, vietnamZone))
    }

    @Test
    fun `formatMessageTime formats correctly in Vietnam timezone`() {
        val ldt = LocalDateTime.of(2026, 10, 30, 8, 5, 0)
        val time = ldt.atZone(vietnamZone).toInstant().toEpochMilli()

        val formatted = ChatDateFormatter.formatMessageTime(time, vietnamZone)
        assertEquals("08:05", formatted)
    }

    @Test
    fun `getDaySeparatorText handles today, yesterday, and older dates correctly across boundaries`() {
        val now = LocalDateTime.of(2026, 10, 31, 0, 1, 0).atZone(vietnamZone).toInstant()

        // Just 2 minutes earlier: Oct 30 at 23:59 -> Yesterday!
        val yesterdayTime = LocalDateTime.of(2026, 10, 30, 23, 59, 0).atZone(vietnamZone).toInstant().toEpochMilli()
        assertEquals("Hôm qua", ChatDateFormatter.getDaySeparatorText(yesterdayTime, now, vietnamZone))

        // Same day: Oct 31 at 00:00:30 -> Today!
        val todayTime = LocalDateTime.of(2026, 10, 31, 0, 0, 30).atZone(vietnamZone).toInstant().toEpochMilli()
        assertEquals("Hôm nay", ChatDateFormatter.getDaySeparatorText(todayTime, now, vietnamZone))

        // Older date: Oct 25 -> dd/MM/yyyy
        val olderTime = LocalDateTime.of(2026, 10, 25, 14, 0, 0).atZone(vietnamZone).toInstant().toEpochMilli()
        assertEquals("25/10/2026", ChatDateFormatter.getDaySeparatorText(olderTime, now, vietnamZone))
    }

    @Test
    fun `formatChatListTime handles today, same year, and previous year`() {
        val now = LocalDateTime.of(2026, 10, 30, 12, 0, 0).atZone(vietnamZone).toInstant()

        // Today: HH:mm
        val todayTime = LocalDateTime.of(2026, 10, 30, 9, 30, 0).atZone(vietnamZone).toInstant().toEpochMilli()
        assertEquals("09:30", ChatDateFormatter.formatChatListTime(todayTime, now, vietnamZone))

        // Different day this year: dd/MM
        val thisYearTime = LocalDateTime.of(2026, 8, 15, 10, 0, 0).atZone(vietnamZone).toInstant().toEpochMilli()
        assertEquals("15/08", ChatDateFormatter.formatChatListTime(thisYearTime, now, vietnamZone))

        // Previous year: dd/MM/yyyy
        val lastYearTime = LocalDateTime.of(2025, 12, 20, 10, 0, 0).atZone(vietnamZone).toInstant().toEpochMilli()
        assertEquals("20/12/2025", ChatDateFormatter.formatChatListTime(lastYearTime, now, vietnamZone))
    }
}

