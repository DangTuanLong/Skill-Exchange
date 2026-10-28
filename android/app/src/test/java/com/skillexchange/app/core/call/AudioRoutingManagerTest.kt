package com.skillexchange.app.core.call

import org.junit.Assert.*
import org.junit.Test

class AudioRoutingManagerTest {

    @Test
    fun testDefaultDevice_VoiceCall_DefaultsToEarpiece() {
        val manager = AudioRoutingManager(
            isVideo = false,
            initialAvailableDevices = setOf(AppAudioDevice.EARPIECE, AppAudioDevice.SPEAKER)
        )

        assertEquals(AppAudioDevice.EARPIECE, manager.selectedDevice.value)
    }

    @Test
    fun testDefaultDevice_VideoCall_DefaultsToSpeaker() {
        val manager = AudioRoutingManager(
            isVideo = true,
            initialAvailableDevices = setOf(AppAudioDevice.EARPIECE, AppAudioDevice.SPEAKER)
        )

        assertEquals(AppAudioDevice.SPEAKER, manager.selectedDevice.value)
    }

    @Test
    fun testDefaultDevice_WiredHeadsetPluggedIn_DefaultsToWired() {
        val manager = AudioRoutingManager(
            isVideo = false,
            initialAvailableDevices = setOf(AppAudioDevice.EARPIECE, AppAudioDevice.SPEAKER, AppAudioDevice.WIRED)
        )

        assertEquals(AppAudioDevice.WIRED, manager.selectedDevice.value)
    }

    @Test
    fun testDeviceSwitchingMidCall_Success() {
        val manager = AudioRoutingManager(
            isVideo = false,
            initialAvailableDevices = setOf(AppAudioDevice.EARPIECE, AppAudioDevice.SPEAKER)
        )

        assertEquals(AppAudioDevice.EARPIECE, manager.selectedDevice.value)

        // Người dùng đổi sang Loa ngoài giữa cuộc gọi
        val switchSuccess = manager.selectDevice(AppAudioDevice.SPEAKER)
        assertTrue(switchSuccess)
        assertEquals(AppAudioDevice.SPEAKER, manager.selectedDevice.value)

        // Người dùng đổi lại Loa trong
        val switchBack = manager.selectDevice(AppAudioDevice.EARPIECE)
        assertTrue(switchBack)
        assertEquals(AppAudioDevice.EARPIECE, manager.selectedDevice.value)
    }

    @Test
    fun testToggleSpeakerphone() {
        val manager = AudioRoutingManager(
            isVideo = false,
            initialAvailableDevices = setOf(AppAudioDevice.EARPIECE, AppAudioDevice.SPEAKER)
        )

        assertEquals(AppAudioDevice.EARPIECE, manager.selectedDevice.value)

        manager.toggleSpeakerphone()
        assertEquals(AppAudioDevice.SPEAKER, manager.selectedDevice.value)

        manager.toggleSpeakerphone()
        assertEquals(AppAudioDevice.EARPIECE, manager.selectedDevice.value)
    }

    @Test
    fun testWiredHeadset_PluggedInAndUnplugged_FallsBackSafely() {
        val manager = AudioRoutingManager(
            isVideo = false,
            initialAvailableDevices = setOf(AppAudioDevice.EARPIECE, AppAudioDevice.SPEAKER)
        )

        assertEquals(AppAudioDevice.EARPIECE, manager.selectedDevice.value)

        // 1. Cắm tai nghe có dây vào
        manager.setAvailableDevices(setOf(AppAudioDevice.EARPIECE, AppAudioDevice.SPEAKER, AppAudioDevice.WIRED))
        assertEquals(AppAudioDevice.WIRED, manager.selectedDevice.value)

        // 2. Rút tai nghe có dây ra ("thiết bị bị rút")
        manager.setAvailableDevices(setOf(AppAudioDevice.EARPIECE, AppAudioDevice.SPEAKER))
        // Fallback về loa trong cho cuộc gọi thoại
        assertEquals(AppAudioDevice.EARPIECE, manager.selectedDevice.value)
    }

    @Test
    fun testWiredHeadset_Unplugged_InVideoCall_FallsBackToSpeaker() {
        val manager = AudioRoutingManager(
            isVideo = true,
            initialAvailableDevices = setOf(AppAudioDevice.EARPIECE, AppAudioDevice.SPEAKER, AppAudioDevice.WIRED)
        )

        assertEquals(AppAudioDevice.WIRED, manager.selectedDevice.value)

        // Rút tai nghe có dây
        manager.setAvailableDevices(setOf(AppAudioDevice.EARPIECE, AppAudioDevice.SPEAKER))
        // Video call fallback về loa ngoài
        assertEquals(AppAudioDevice.SPEAKER, manager.selectedDevice.value)
    }

    @Test
    fun testBluetooth_WithoutPermission_Denied() {
        val manager = AudioRoutingManager(
            isVideo = false,
            initialAvailableDevices = setOf(AppAudioDevice.EARPIECE, AppAudioDevice.SPEAKER, AppAudioDevice.BLUETOOTH),
            initialHasBluetoothPermission = false
        )

        // Không tự động chọn Bluetooth nếu thiếu quyền
        assertEquals(AppAudioDevice.EARPIECE, manager.selectedDevice.value)

        // Cố gắng chọn Bluetooth khi chưa cấp quyền -> thất bại an toàn
        val selectResult = manager.selectDevice(AppAudioDevice.BLUETOOTH)
        assertFalse(selectResult)
        assertEquals(AppAudioDevice.EARPIECE, manager.selectedDevice.value)
    }

    @Test
    fun testBluetooth_GrantedPermission_AllowsSelection() {
        val manager = AudioRoutingManager(
            isVideo = false,
            initialAvailableDevices = setOf(AppAudioDevice.EARPIECE, AppAudioDevice.SPEAKER, AppAudioDevice.BLUETOOTH),
            initialHasBluetoothPermission = false
        )

        // Cấp quyền Bluetooth sau đó
        manager.setBluetoothPermission(true)
        assertTrue(manager.hasBluetoothPermission.value)

        val selectResult = manager.selectDevice(AppAudioDevice.BLUETOOTH)
        assertTrue(selectResult)
        assertEquals(AppAudioDevice.BLUETOOTH, manager.selectedDevice.value)
    }

    @Test
    fun testBluetooth_PermissionRevokedOrDisconnected_FallsBackSafely() {
        val manager = AudioRoutingManager(
            isVideo = false,
            initialAvailableDevices = setOf(AppAudioDevice.EARPIECE, AppAudioDevice.SPEAKER, AppAudioDevice.BLUETOOTH),
            initialHasBluetoothPermission = true
        )

        manager.selectDevice(AppAudioDevice.BLUETOOTH)
        assertEquals(AppAudioDevice.BLUETOOTH, manager.selectedDevice.value)

        // Tai nghe Bluetooth ngắt kết nối
        manager.setAvailableDevices(setOf(AppAudioDevice.EARPIECE, AppAudioDevice.SPEAKER))
        assertEquals(AppAudioDevice.EARPIECE, manager.selectedDevice.value)
    }

    @Test
    fun testAudioFocus_LossTransient_InterruptsCallAndMutes() {
        val manager = AudioRoutingManager(isVideo = false)

        assertFalse(manager.isMutedByAudioFocus.value)
        assertFalse(manager.isInterruptedBySystemCall.value)

        // Cuộc gọi SIM (PSTN) đến -> mất audio focus tạm thời
        manager.onAudioFocusChange(AudioRoutingManager.AUDIOFOCUS_LOSS_TRANSIENT)
        assertTrue(manager.isMutedByAudioFocus.value)
        assertTrue(manager.isInterruptedBySystemCall.value)

        // Cuộc gọi SIM kết thúc -> khôi phục audio focus
        manager.onAudioFocusChange(AudioRoutingManager.AUDIOFOCUS_GAIN)
        assertFalse(manager.isMutedByAudioFocus.value)
        assertFalse(manager.isInterruptedBySystemCall.value)
    }

    @Test
    fun testAudioFocus_PermanentLoss_MutesMic() {
        val manager = AudioRoutingManager(isVideo = false)

        manager.onAudioFocusChange(AudioRoutingManager.AUDIOFOCUS_LOSS)
        assertTrue(manager.isMutedByAudioFocus.value)
        assertFalse(manager.isInterruptedBySystemCall.value) // Mất vĩnh viễn không phải do chen ngang cuộc gọi
    }
}
