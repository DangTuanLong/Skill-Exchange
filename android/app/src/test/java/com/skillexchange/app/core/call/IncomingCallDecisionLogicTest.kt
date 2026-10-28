package com.skillexchange.app.core.call

import org.junit.Assert.assertEquals
import org.junit.Test

class IncomingCallDecisionLogicTest {

    @Test
    fun decidePushAction_WhenNotLoggedIn_ReturnsIgnore() {
        val result = IncomingCallDecisionLogic.decidePushAction(
            isBusy = false,
            isLoggedIn = false,
            isForeground = false
        )
        assertEquals(IncomingPushAction.IGNORE, result)
    }

    @Test
    fun decidePushAction_WhenBusy_ReturnsRejectBusy() {
        val result = IncomingCallDecisionLogic.decidePushAction(
            isBusy = true,
            isLoggedIn = true,
            isForeground = false
        )
        assertEquals(IncomingPushAction.REJECT_BUSY, result)
    }

    @Test
    fun decidePushAction_WhenLoggedInAndForeground_ReturnsShowInAppDialog() {
        val result = IncomingCallDecisionLogic.decidePushAction(
            isBusy = false,
            isLoggedIn = true,
            isForeground = true
        )
        assertEquals(IncomingPushAction.SHOW_IN_APP_DIALOG, result)
    }

    @Test
    fun decidePushAction_WhenLoggedInAndBackground_ReturnsShowSystemNotification() {
        val result = IncomingCallDecisionLogic.decidePushAction(
            isBusy = false,
            isLoggedIn = true,
            isForeground = false
        )
        assertEquals(IncomingPushAction.SHOW_SYSTEM_NOTIFICATION, result)
    }

    @Test
    fun decideNotificationType_WithPermission_ReturnsFullScreenIntent() {
        val result = IncomingCallDecisionLogic.decideNotificationType(hasFullScreenPermission = true)
        assertEquals(NotificationDisplayType.FULL_SCREEN_INTENT, result)
    }

    @Test
    fun decideNotificationType_WithoutPermission_ReturnsHeadsUpNotification() {
        val result = IncomingCallDecisionLogic.decideNotificationType(hasFullScreenPermission = false)
        assertEquals(NotificationDisplayType.HEADS_UP_NOTIFICATION, result)
    }

    @Test
    fun checkAnswerEligibility_ValidReceiverAndRinging_ReturnsEligible() {
        val result = IncomingCallDecisionLogic.checkAnswerEligibility(
            currentUserId = "user-b",
            receiverId = "user-b",
            status = "RINGING"
        )
        assertEquals(CallAnswerEligibility.ELIGIBLE, result)
    }

    @Test
    fun checkAnswerEligibility_ValidReceiverAndDialing_ReturnsEligible() {
        val result = IncomingCallDecisionLogic.checkAnswerEligibility(
            currentUserId = "user-b",
            receiverId = "user-b",
            status = "DIALING"
        )
        assertEquals(CallAnswerEligibility.ELIGIBLE, result)
    }

    @Test
    fun checkAnswerEligibility_DifferentUser_ReturnsNotReceiver() {
        val result = IncomingCallDecisionLogic.checkAnswerEligibility(
            currentUserId = "user-c",
            receiverId = "user-b",
            status = "RINGING"
        )
        assertEquals(CallAnswerEligibility.NOT_RECEIVER, result)
    }

    @Test
    fun checkAnswerEligibility_EndedStatus_ReturnsAlreadyTerminated() {
        val result = IncomingCallDecisionLogic.checkAnswerEligibility(
            currentUserId = "user-b",
            receiverId = "user-b",
            status = "ENDED"
        )
        assertEquals(CallAnswerEligibility.ALREADY_TERMINATED, result)
    }

    @Test
    fun checkAnswerEligibility_MissedStatus_ReturnsAlreadyTerminated() {
        val result = IncomingCallDecisionLogic.checkAnswerEligibility(
            currentUserId = "user-b",
            receiverId = "user-b",
            status = "MISSED"
        )
        assertEquals(CallAnswerEligibility.ALREADY_TERMINATED, result)
    }

    @Test
    fun checkAnswerEligibility_RejectedStatus_ReturnsAlreadyTerminated() {
        val result = IncomingCallDecisionLogic.checkAnswerEligibility(
            currentUserId = "user-b",
            receiverId = "user-b",
            status = "REJECTED"
        )
        assertEquals(CallAnswerEligibility.ALREADY_TERMINATED, result)
    }

    @Test
    fun decideFgsType_WithRecordAudio_ReturnsPhoneCallAndMicrophone() {
        val result = IncomingCallDecisionLogic.decideFgsType(hasRecordAudioPermission = true)
        assertEquals(SafeFgsType.PHONE_CALL_AND_MICROPHONE, result)
    }

    @Test
    fun decideFgsType_WithoutRecordAudio_ReturnsPhoneCallOnly() {
        val result = IncomingCallDecisionLogic.decideFgsType(hasRecordAudioPermission = false)
        assertEquals(SafeFgsType.PHONE_CALL_ONLY, result)
    }
}
