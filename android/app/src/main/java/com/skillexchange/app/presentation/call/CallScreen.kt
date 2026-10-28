package com.skillexchange.app.presentation.call

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.skillexchange.app.core.ui.theme.Brand500
import com.skillexchange.app.domain.model.call.CallUiStatus
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
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

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            showPermissionRationaleDialog = true
        }
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
                                CallUiStatus.BUSY, CallUiStatus.REJECTED, CallUiStatus.MISSED, CallUiStatus.ERROR -> Color(0xFFF87171)
                                CallUiStatus.CONNECTED -> Color(0xFF34D399)
                                else -> Color(0xFF38BDF8)
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (!state.errorMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.errorMessage ?: "",
                            color = Color(0xFFFCA5A5),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // 2. Avatar trung tâm (120dp)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(160.dp)
                ) {
                    // Vòng viền phát sóng âm khi CONNECTED
                    if (state.uiStatus == CallUiStatus.CONNECTED) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .border(2.dp, Color(0x4438BDF8), CircleShape)
                        )
                    }

                    if (!state.partnerAvatar.isNullOrBlank()) {
                        AsyncImage(
                            model = state.partnerAvatar,
                            contentDescription = "Avatar ${state.partnerName}",
                            modifier = Modifier
                                .size(120.dp)
                                .clip(CircleShape)
                                .border(3.dp, Brand500, CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF334155))
                                .border(3.dp, Brand500, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Avatar mặc định",
                                modifier = Modifier.size(64.dp),
                                tint = Color(0xFF94A3B8)
                            )
                        }
                    }
                }

                // 3. Floating Call Controls Bar ở đáy
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.canRetry) {
                        Button(
                            onClick = { viewModel.onIntent(CallIntent.Retry) },
                            colors = ButtonDefaults.buttonColors(containerColor = Brand500),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
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

                        // Nút Bật/Tắt Loa ngoài
                        IconButton(
                            onClick = { viewModel.onIntent(CallIntent.ToggleSpeaker) },
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(if (state.isSpeakerOn) Color(0xFF38BDF8) else Color(0xFF334155))
                        ) {
                            Icon(
                                imageVector = if (state.isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.PhoneInTalk,
                                contentDescription = if (state.isSpeakerOn) "Tắt loa ngoài" else "Bật loa ngoài",
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
}
