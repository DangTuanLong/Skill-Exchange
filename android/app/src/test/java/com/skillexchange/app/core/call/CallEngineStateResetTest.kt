package com.skillexchange.app.core.call

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CallEngineStateResetTest {

    @Test
    fun twoConsecutiveCalls_ResetsStateCleanly_NoStatePollution() {
        val engine = FakeCallEngine(autoConnect = true)

        // ── Cuộc gọi 1 ──
        engine.connect(
            url = "wss://test.livekit.cloud",
            roomName = "room-call-1",
            token = "token-1",
            isVideo = false
        )
        // Người dùng tắt mic và bật loa ngoài trong cuộc gọi 1
        engine.setMicrophoneMuted(true)
        engine.selectAudioDevice(AppAudioDevice.SPEAKER)

        assertEquals(CallEngineStatus.CONNECTED, engine.status.value)
        assertFalse("Mic phải bị mute", !engine.isMuted.value)
        assertEquals(AppAudioDevice.SPEAKER, engine.selectedAudioDevice.value)
        assertEquals(true, engine.isSpeakerphoneOn.value)

        // Kết thúc cuộc gọi 1
        engine.disconnect()
        assertEquals(CallEngineStatus.DISCONNECTED, engine.status.value)

        // Kiểm tra sau khi disconnect, trạng thái đã được reset về mặc định
        assertFalse("Mic phải được reset về unmuted", engine.isMuted.value)
        assertFalse("Loa ngoài phải được reset về false", engine.isSpeakerphoneOn.value)
        assertEquals(AppAudioDevice.EARPIECE, engine.selectedAudioDevice.value)
        assertFalse(engine.isMutedByAudioFocus.value)
        assertFalse(engine.isInterruptedBySystemCall.value)

        // ── Cuộc gọi 2 ──
        engine.connect(
            url = "wss://test.livekit.cloud",
            roomName = "room-call-2",
            token = "token-2",
            isVideo = false
        )

        // Trạng thái cuộc gọi 2 phải sạch, không bị rò rỉ từ cuộc gọi 1
        assertEquals(CallEngineStatus.CONNECTED, engine.status.value)
        assertFalse("Cuộc gọi 2 không được kế thừa trạng thái mute của cuộc gọi 1", engine.isMuted.value)
        assertFalse("Cuộc gọi 2 không được kế thừa trạng thái loa ngoài", engine.isSpeakerphoneOn.value)
        assertEquals(AppAudioDevice.EARPIECE, engine.selectedAudioDevice.value)

        // Kết thúc cuộc gọi 2
        engine.disconnect()
        assertFalse(engine.isMuted.value)
        assertEquals(CallEngineStatus.DISCONNECTED, engine.status.value)
    }

    @Test
    fun connectVideoCall_InitializesSpeakerphoneCorrectly() {
        val engine = FakeCallEngine(autoConnect = true)

        engine.connect(
            url = "wss://test.livekit.cloud",
            roomName = "room-video",
            token = "token-video",
            isVideo = true
        )

        // Cuộc gọi video mặc định bật loa ngoài
        assertEquals(AppAudioDevice.SPEAKER, engine.selectedAudioDevice.value)
        assertEquals(true, engine.isSpeakerphoneOn.value)
        assertFalse(engine.isMuted.value)

        engine.disconnect()
        // Sau khi ngắt, trở lại mặc định
        assertEquals(AppAudioDevice.EARPIECE, engine.selectedAudioDevice.value)
        assertFalse(engine.isSpeakerphoneOn.value)
    }
}

