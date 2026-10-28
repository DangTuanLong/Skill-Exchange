package com.skillexchange.app.presentation.call

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import coil3.compose.AsyncImage
import com.skillexchange.app.core.call.AppAudioDevice
import com.skillexchange.app.core.ui.theme.Brand500
import com.skillexchange.app.domain.model.call.CallUiStatus
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallScreen(
    onNavigateBack: () -> Unit,
    viewModel: CallViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showPermissionRationaleDialog by remember { mutableStateOf(false) }
    var showAudioDeviceSheet by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            showPermissionRationaleDialog = true
        }
    }

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.onIntent(CallIntent.BluetoothPermissionResult(isGranted))
    }

    LaunchedEffect(Unit) {
        val hasRecordAudio = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PermissionChecker.PERMISSION_GRANTED
        if (!hasRecordAudio) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is CallSideEffect.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
                is CallSideEffect.RequestBluetoothPermission -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    } else {
                        viewModel.onIntent(CallIntent.BluetoothPermissionResult(true))
                    }
                }
                is CallSideEffect.NavigateBack -> {
                    onNavigateBack()
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFF0F172A) // Nền tối chuyên nghiệp cho Call Screen
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0F172A),
                            Color(0xFF1E293B),
                            Color(0xFF0F172A)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 1. Header & Cảnh báo quyền riêng tư (UI_SPEC §4.20)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        color = Color(0x3338BDF8),
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x6638BDF8))
                    ) {
                        Text(
                            text = "🔒 Cuộc gọi đi qua máy chủ trung gian của LiveKit, ứng dụng không ghi âm.",
                            color = Color(0xFFBAE6FD),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = if (state.isVideo) "CUỘC GỌI VIDEO" else "CUỘC GỌI THOẠI",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = state.partnerName,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    if (state.skillName.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Kỹ năng: ${state.skillName}",
                            color = Color(0xFFCBD5E1),
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Hiển thị trạng thái hoặc thời lượng
                    if (state.uiStatus == CallUiStatus.CONNECTED) {
                        Surface(
                            color = Color(0x2210B981),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981))
                        ) {
                            Text(
                                text = state.formattedDuration,
                                color = Color(0xFF34D399),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                            )
                        }
                    } else {
                        Text(
                            text = state.uiStatus.label,
                            color = when (state.uiStatus) {
                                CallUiStatus.RINGING, CallUiStatus.DIALING -> Color(0xFF38BDF8)
                                CallUiStatus.BUSY, CallUiStatus.MISSED, CallUiStatus.REJECTED -> Color(0xFFF87171)
                                CallUiStatus.ENDED -> Color(0xFF94A3B8)
                                CallUiStatus.ERROR -> Color(0xFFEF4444)
                                else -> Color.White
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Thông báo gián đoạn Audio Focus (Cuộc gọi hệ thống chen vào)
                    if (state.isInterruptedBySystemCall) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = Color(0x33F59E0B),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x88F59E0B))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.PhoneCallback, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Cuộc gọi bị tạm dừng do có cuộc gọi hệ thống", color = Color(0xFFFEF3C7), fontSize = 12.sp)
                            }
                        }
                    } else if (state.isMutedByAudioFocus) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = Color(0x33F59E0B),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x88F59E0B))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.MicOff, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Micro tạm ngắt do ứng dụng khác chiếm âm thanh", color = Color(0xFFFEF3C7), fontSize = 12.sp)
                            }
                        }
                    }
                }

                // 2. Avatar lớn ở trung tâm kèm hiệu ứng sóng âm khi kết nối
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(200.dp)
                ) {
                    if (state.uiStatus == CallUiStatus.CONNECTED) {
                        // Vòng hào quang pulsing giả lập sóng âm
                        Box(
                            modifier = Modifier
                                .size(170.dp)
                                .clip(CircleShape)
                                .background(Color(0x2238BDF8))
                        )
                        Box(
                            modifier = Modifier
                                .size(150.dp)
                                .clip(CircleShape)
                                .background(Color(0x3338BDF8))
                        )
                    }

                    if (state.partnerAvatar != null) {
                        AsyncImage(
                            model = state.partnerAvatar,
                            contentDescription = state.partnerName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(120.dp)
                                .clip(CircleShape)
                                .border(3.dp, Color(0xFF38BDF8), CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF334155))
                                .border(3.dp, Color(0xFF38BDF8), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = state.partnerName.take(1).uppercase(),
                                color = Color.White,
                                fontSize = 44.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // 3. Thông báo lỗi hoặc nút Thử lại (nếu có)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.errorMessage != null && state.uiStatus != CallUiStatus.CONNECTED) {
                        Text(
                            text = state.errorMessage ?: "",
                            color = Color(0xFFF87171),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }

                    if (state.canRetry) {
                        Button(
                            onClick = { viewModel.onIntent(CallIntent.Retry) },
                            colors = ButtonDefaults.buttonColors(containerColor = Brand500),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Thử gọi lại", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Nút Tắt/Bật Micro
                        IconButton(
                            onClick = { viewModel.onIntent(CallIntent.ToggleMute) },
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(if (state.isMuted) Color(0xFFEF4444) else Color(0xFF334155))
                        ) {
                            Icon(
                                imageVector = if (state.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = if (state.isMuted) "Bật micro" else "Tắt micro",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // Nút Kết thúc cuộc gọi (Nút tròn đỏ lớn chính giữa)
                        IconButton(
                            onClick = { viewModel.onIntent(CallIntent.EndCall) },
                            enabled = !state.isActionLoading,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFDC2626))
                        ) {
                            if (state.isActionLoading) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CallEnd,
                                    contentDescription = "Kết thúc cuộc gọi",
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        // Nút Thiết bị âm thanh (Loa ngoài / Loa trong / Tai nghe / Bluetooth)
                        val audioIcon = when (state.selectedAudioDevice) {
                            AppAudioDevice.SPEAKER -> Icons.Default.VolumeUp
                            AppAudioDevice.EARPIECE -> Icons.Default.PhoneInTalk
                            AppAudioDevice.WIRED -> Icons.Default.Headphones
                            AppAudioDevice.BLUETOOTH -> Icons.Default.BluetoothAudio
                        }
                        val isAudioHighlight = state.selectedAudioDevice == AppAudioDevice.SPEAKER || state.selectedAudioDevice == AppAudioDevice.BLUETOOTH

                        IconButton(
                            onClick = {
                                if (state.availableAudioDevices.size > 2) {
                                    showAudioDeviceSheet = true
                                } else {
                                    viewModel.onIntent(CallIntent.ToggleSpeaker)
                                }
                            },
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(if (isAudioHighlight) Color(0xFF38BDF8) else Color(0xFF334155))
                        ) {
                            Icon(
                                imageVector = audioIcon,
                                contentDescription = state.selectedAudioDevice.displayName,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPermissionRationaleDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionRationaleDialog = false },
            title = { Text("Quyền sử dụng Microphone") },
            text = { Text("Ứng dụng cần quyền Microphone để đàm thoại trong cuộc gọi. Vui lòng cấp quyền trong Cài đặt của thiết bị.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionRationaleDialog = false
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                ) {
                    Text("Mở Cài đặt")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionRationaleDialog = false }) {
                    Text("Đóng")
                }
            }
        )
    }

    if (showAudioDeviceSheet) {
        AlertDialog(
            onDismissRequest = { showAudioDeviceSheet = false },
            title = { Text("Thiết bị âm thanh", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    state.availableAudioDevices.forEach { device ->
                        val isSelected = device == state.selectedAudioDevice
                        val icon = when (device) {
                            AppAudioDevice.SPEAKER -> Icons.Default.VolumeUp
                            AppAudioDevice.EARPIECE -> Icons.Default.PhoneInTalk
                            AppAudioDevice.WIRED -> Icons.Default.Headphones
                            AppAudioDevice.BLUETOOTH -> Icons.Default.BluetoothAudio
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.onIntent(CallIntent.SelectAudioDevice(device))
                                    showAudioDeviceSheet = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (isSelected) Brand500 else Color(0xFF64748B),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = device.displayName,
                                color = if (isSelected) Brand500 else Color.Unspecified,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Đang chọn",
                                    tint = Brand500,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAudioDeviceSheet = false }) {
                    Text("Đóng")
                }
            }
        )
    }
}
