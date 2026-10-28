package com.skillexchange.app.core.call

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Các loại thiết bị âm thanh hỗ trợ trong cuộc gọi.
 */
enum class AppAudioDevice(val displayName: String) {
    SPEAKER("Loa ngoài"),
    EARPIECE("Loa trong"),
    WIRED("Tai nghe có dây"),
    BLUETOOTH("Tai nghe Bluetooth")
}

/**
 * Trình quản lý định tuyến âm thanh (AudioRoutingManager) thuần logic.
 * Không phụ thuộc vào android.* context, đảm bảo 100% JVM Unit Testable.
 *
 * Quy tắc:
 * - Mặc định thoại (isVideo = false): Ưu tiên EARPIECE (loa trong).
 * - Mặc định video (isVideo = true): Ưu tiên SPEAKER (loa ngoài).
 * - Tự động định tuyến khi cắm/rút tai nghe (WIRED, BLUETOOTH).
 * - Fallback an toàn về thiết bị mặc định khi thiết bị đang dùng bị rút/ngắt kết nối.
 * - Kiểm soát quyền BLUETOOTH_CONNECT (Android 12+): Xử lý từ chối an toàn mà không crash.
 * - Xử lý Audio Focus khi có cuộc gọi hệ thống hoặc ứng dụng khác chiếm quyền.
 */
class AudioRoutingManager(
    val isVideo: Boolean,
    initialAvailableDevices: Set<AppAudioDevice> = setOf(AppAudioDevice.EARPIECE, AppAudioDevice.SPEAKER),
    initialHasBluetoothPermission: Boolean = false
) {
    companion object {
        const val AUDIOFOCUS_GAIN = 1
        const val AUDIOFOCUS_LOSS = -1
        const val AUDIOFOCUS_LOSS_TRANSIENT = -2
        const val AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK = -3
    }

    private val _availableDevices = MutableStateFlow(initialAvailableDevices)
    val availableDevices: StateFlow<Set<AppAudioDevice>> = _availableDevices.asStateFlow()

    private val _hasBluetoothPermission = MutableStateFlow(initialHasBluetoothPermission)
    val hasBluetoothPermission: StateFlow<Boolean> = _hasBluetoothPermission.asStateFlow()

    // Người dùng chủ động chọn thiết bị nào hay không
    private var userExplicitSelection: AppAudioDevice? = null

    // Thiết bị âm thanh đang được chọn
    private val _selectedDevice = MutableStateFlow(determineInitialDevice(initialAvailableDevices, initialHasBluetoothPermission))
    val selectedDevice: StateFlow<AppAudioDevice> = _selectedDevice.asStateFlow()

    // Trạng thái mic bị mute do mất Audio Focus (cuộc gọi SIM / app khác chiếm)
    private val _isMutedByAudioFocus = MutableStateFlow(false)
    val isMutedByAudioFocus: StateFlow<Boolean> = _isMutedByAudioFocus.asStateFlow()

    // Trạng thái cuộc gọi bị gián đoạn do cuộc gọi hệ thống (PSTN / SIM)
    private val _isInterruptedBySystemCall = MutableStateFlow(false)
    val isInterruptedBySystemCall: StateFlow<Boolean> = _isInterruptedBySystemCall.asStateFlow()

    private fun determineInitialDevice(
        devices: Set<AppAudioDevice>,
        bluetoothPermission: Boolean
    ): AppAudioDevice {
        // Nếu có tai nghe cắm sẵn, ưu tiên tai nghe
        if (devices.contains(AppAudioDevice.WIRED)) return AppAudioDevice.WIRED
        if (devices.contains(AppAudioDevice.BLUETOOTH) && bluetoothPermission) return AppAudioDevice.BLUETOOTH

        // Ngược lại theo mặc định cuộc gọi: thoại = loa trong, video = loa ngoài
        val defaultDevice = if (isVideo) AppAudioDevice.SPEAKER else AppAudioDevice.EARPIECE
        return if (devices.contains(defaultDevice)) {
            defaultDevice
        } else {
            devices.firstOrNull() ?: AppAudioDevice.SPEAKER
        }
    }

    /**
     * Cập nhật danh sách thiết bị âm thanh đang khả dụng từ hệ thống.
     * Xử lý trường hợp thiết bị mới cắm vào hoặc thiết bị đang nghe bị rút ra.
     */
    fun setAvailableDevices(newDevices: Set<AppAudioDevice>) {
        val previousDevices = _availableDevices.value
        _availableDevices.value = newDevices

        val currentSelected = _selectedDevice.value

        // 1. Kiểm tra nếu thiết bị đang dùng BỊ RÚT / NGẮT KẾT NỐI
        if (!newDevices.contains(currentSelected)) {
            userExplicitSelection = null
            fallbackToAvailableDevice(newDevices)
            return
        }

        // 2. Kiểm tra nếu có thiết bị mới vừa cắm vào (ví dụ vừa cắm tai nghe có dây)
        val newlyAdded = newDevices - previousDevices
        if (newlyAdded.contains(AppAudioDevice.WIRED)) {
            // Tự động chuyển sang tai nghe có dây khi người dùng cắm vào
            _selectedDevice.value = AppAudioDevice.WIRED
            userExplicitSelection = null
            return
        }
        if (newlyAdded.contains(AppAudioDevice.BLUETOOTH) && _hasBluetoothPermission.value) {
            // Tự động chuyển sang bluetooth nếu người dùng đã cấp quyền
            _selectedDevice.value = AppAudioDevice.BLUETOOTH
            userExplicitSelection = null
            return
        }

        // 3. Nếu người dùng chưa có lựa chọn thủ công, giữ đúng chuẩn ưu tiên
        if (userExplicitSelection == null) {
            _selectedDevice.value = determineInitialDevice(newDevices, _hasBluetoothPermission.value)
        }
    }

    /**
     * Người dùng chủ động đổi thiết bị giữa cuộc gọi.
     * Trả về true nếu chuyển thành công, false nếu thiết bị không hợp lệ hoặc thiếu quyền Bluetooth.
     */
    fun selectDevice(device: AppAudioDevice): Boolean {
        if (!_availableDevices.value.contains(device)) {
            return false
        }

        if (device == AppAudioDevice.BLUETOOTH && !_hasBluetoothPermission.value) {
            // Thiếu quyền BLUETOOTH_CONNECT trên Android 12+
            return false
        }

        userExplicitSelection = device
        _selectedDevice.value = device
        return true
    }

    /**
     * Chuyển đổi nhanh giữa Loa ngoài và Loa trong (hoặc thiết bị rảnh tay kế tiếp).
     */
    fun toggleSpeakerphone(): AppAudioDevice {
        val target = if (_selectedDevice.value == AppAudioDevice.SPEAKER) {
            if (_availableDevices.value.contains(AppAudioDevice.EARPIECE)) {
                AppAudioDevice.EARPIECE
            } else if (_availableDevices.value.contains(AppAudioDevice.WIRED)) {
                AppAudioDevice.WIRED
            } else {
                AppAudioDevice.SPEAKER
            }
        } else {
            AppAudioDevice.SPEAKER
        }
        selectDevice(target)
        return _selectedDevice.value
    }

    /**
     * Cập nhật trạng thái quyền BLUETOOTH_CONNECT.
     */
    fun setBluetoothPermission(granted: Boolean) {
        _hasBluetoothPermission.value = granted
        if (granted) {
            // Nếu có thiết bị Bluetooth trong danh sách, cho phép chọn
            if (_availableDevices.value.contains(AppAudioDevice.BLUETOOTH) && userExplicitSelection == AppAudioDevice.BLUETOOTH) {
                _selectedDevice.value = AppAudioDevice.BLUETOOTH
            }
        } else {
            // Nếu quyền bị từ chối và thiết bị đang chọn là BLUETOOTH, fallback an toàn
            if (_selectedDevice.value == AppAudioDevice.BLUETOOTH) {
                userExplicitSelection = null
                fallbackToAvailableDevice(_availableDevices.value)
            }
        }
    }

    /**
     * Xử lý thay đổi Audio Focus (AudioManager.OnAudioFocusChangeListener).
     */
    fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AUDIOFOCUS_LOSS -> {
                // Mất Audio Focus vĩnh viễn (ứng dụng khác phát media lâu dài)
                _isMutedByAudioFocus.value = true
                _isInterruptedBySystemCall.value = false
            }
            AUDIOFOCUS_LOSS_TRANSIENT, AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Mất Audio Focus tạm thời (cuộc gọi SIM / cuộc gọi hệ thống chen vào)
                _isMutedByAudioFocus.value = true
                _isInterruptedBySystemCall.value = true
            }
            AUDIOFOCUS_GAIN -> {
                // Khôi phục lại Audio Focus sau khi cuộc gọi SIM kết thúc
                _isMutedByAudioFocus.value = false
                _isInterruptedBySystemCall.value = false
            }
        }
    }

    private fun fallbackToAvailableDevice(devices: Set<AppAudioDevice>) {
        val defaultDevice = if (isVideo) AppAudioDevice.SPEAKER else AppAudioDevice.EARPIECE
        val fallback = when {
            devices.contains(AppAudioDevice.WIRED) -> AppAudioDevice.WIRED
            devices.contains(AppAudioDevice.BLUETOOTH) && _hasBluetoothPermission.value -> AppAudioDevice.BLUETOOTH
            devices.contains(defaultDevice) -> defaultDevice
            devices.contains(AppAudioDevice.SPEAKER) -> AppAudioDevice.SPEAKER
            devices.contains(AppAudioDevice.EARPIECE) -> AppAudioDevice.EARPIECE
            else -> devices.firstOrNull() ?: AppAudioDevice.SPEAKER
        }
        _selectedDevice.value = fallback
    }
}
