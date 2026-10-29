package com.skillexchange.app.presentation.chat.detail

import com.skillexchange.app.domain.model.chat.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemCallFormatterTest {

    @Test
    fun formatDuration_FormatsCorrectly() {
        assertEquals("00:00", SystemCallFormatter.formatDuration(0))
        assertEquals("00:00", SystemCallFormatter.formatDuration(-5))
        assertEquals("00:45", SystemCallFormatter.formatDuration(45))
        assertEquals("02:05", SystemCallFormatter.formatDuration(125))
        assertEquals("01:01:05", SystemCallFormatter.formatDuration(3665))
    }

    @Test
    fun isSuccessful_IdentifiesTerminalStatuses() {
        assertTrue(SystemCallFormatter.isSuccessful("COMPLETED"))
        assertTrue(SystemCallFormatter.isSuccessful("completed"))
        assertTrue(SystemCallFormatter.isSuccessful("ENDED"))
        assertTrue(SystemCallFormatter.isSuccessful("ended"))

        assertFalse(SystemCallFormatter.isSuccessful("MISSED"))
        assertFalse(SystemCallFormatter.isSuccessful("REJECTED"))
        assertFalse(SystemCallFormatter.isSuccessful("BUSY"))
        assertFalse(SystemCallFormatter.isSuccessful(null))
    }

    @Test
    fun formatTitle_CallerPerspective() {
        val myUserId = "user-caller"

        val msgCompleted = ChatMessage(
            id = "m1", chatId = "c1", senderId = myUserId, content = "fallback",
            type = "SYSTEM_CALL", callType = "VOICE", callStatus = "COMPLETED",
            durationSeconds = 125, callerId = myUserId, receiverId = "user-receiver"
        )
        assertEquals("Cuộc gọi thoại đi (02:05)", SystemCallFormatter.formatTitle(msgCompleted, myUserId))

        val msgVideoCompleted = ChatMessage(
            id = "m2", chatId = "c1", senderId = myUserId, content = "fallback",
            type = "SYSTEM_CALL", callType = "VIDEO", callStatus = "ENDED",
            durationSeconds = 60, callerId = myUserId, receiverId = "user-receiver"
        )
        assertEquals("Cuộc gọi video đi (01:00)", SystemCallFormatter.formatTitle(msgVideoCompleted, myUserId))

        val msgMissed = ChatMessage(
            id = "m3", chatId = "c1", senderId = myUserId, content = "fallback",
            type = "SYSTEM_CALL", callType = "VOICE", callStatus = "MISSED",
            durationSeconds = 0, callerId = myUserId, receiverId = "user-receiver"
        )
        assertEquals("Cuộc gọi đi không trả lời", SystemCallFormatter.formatTitle(msgMissed, myUserId))

        val msgRejected = ChatMessage(
            id = "m4", chatId = "c1", senderId = myUserId, content = "fallback",
            type = "SYSTEM_CALL", callType = "VOICE", callStatus = "REJECTED",
            durationSeconds = 0, callerId = myUserId, receiverId = "user-receiver"
        )
        assertEquals("Cuộc gọi đi bị từ chối", SystemCallFormatter.formatTitle(msgRejected, myUserId))

        val msgBusy = ChatMessage(
            id = "m5", chatId = "c1", senderId = myUserId, content = "fallback",
            type = "SYSTEM_CALL", callType = "VOICE", callStatus = "BUSY",
            durationSeconds = 0, callerId = myUserId, receiverId = "user-receiver"
        )
        assertEquals("Đối phương đang bận", SystemCallFormatter.formatTitle(msgBusy, myUserId))
    }

    @Test
    fun formatTitle_ReceiverPerspective() {
        val myUserId = "user-receiver"
        val callerId = "user-caller"

        val msgCompleted = ChatMessage(
            id = "m1", chatId = "c1", senderId = callerId, content = "fallback",
            type = "SYSTEM_CALL", callType = "VOICE", callStatus = "COMPLETED",
            durationSeconds = 125, callerId = callerId, receiverId = myUserId
        )
        assertEquals("Cuộc gọi thoại đến (02:05)", SystemCallFormatter.formatTitle(msgCompleted, myUserId))

        val msgVideoCompleted = ChatMessage(
            id = "m2", chatId = "c1", senderId = callerId, content = "fallback",
            type = "SYSTEM_CALL", callType = "VIDEO", callStatus = "ENDED",
            durationSeconds = 3600, callerId = callerId, receiverId = myUserId
        )
        assertEquals("Cuộc gọi video đến (01:00:00)", SystemCallFormatter.formatTitle(msgVideoCompleted, myUserId))

        val msgMissed = ChatMessage(
            id = "m3", chatId = "c1", senderId = callerId, content = "fallback",
            type = "SYSTEM_CALL", callType = "VOICE", callStatus = "MISSED",
            durationSeconds = 0, callerId = callerId, receiverId = myUserId
        )
        assertEquals("Cuộc gọi nhỡ", SystemCallFormatter.formatTitle(msgMissed, myUserId))

        val msgRejected = ChatMessage(
            id = "m4", chatId = "c1", senderId = callerId, content = "fallback",
            type = "SYSTEM_CALL", callType = "VOICE", callStatus = "REJECTED",
            durationSeconds = 0, callerId = callerId, receiverId = myUserId
        )
        assertEquals("Đã từ chối cuộc gọi", SystemCallFormatter.formatTitle(msgRejected, myUserId))

        val msgBusy = ChatMessage(
            id = "m5", chatId = "c1", senderId = callerId, content = "fallback",
            type = "SYSTEM_CALL", callType = "VOICE", callStatus = "BUSY",
            durationSeconds = 0, callerId = callerId, receiverId = myUserId
        )
        assertEquals("Cuộc gọi nhỡ (Máy bận)", SystemCallFormatter.formatTitle(msgBusy, myUserId))
    }
}

