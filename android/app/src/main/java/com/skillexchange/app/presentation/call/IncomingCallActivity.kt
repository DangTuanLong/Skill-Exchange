package com.skillexchange.app.presentation.call

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.skillexchange.app.MainActivity
import com.skillexchange.app.core.notification.IncomingCallNotificationHelper
import com.skillexchange.app.core.service.CallForegroundService
import com.skillexchange.app.core.ui.theme.SkillExchangeTheme
import com.skillexchange.app.domain.repository.ICallRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class IncomingCallActivity : ComponentActivity() {

    private val tag = "IncomingCallActivity"
    private val callRepository: ICallRepository by inject()

    private var callCancelledReceiver: BroadcastReceiver? = null

    companion object {
        const val EXTRA_SESSION_ID = "extra_session_id"
        const val EXTRA_CALLER_ID = "extra_caller_id"
        const val EXTRA_CALLER_NAME = "extra_caller_name"
        const val EXTRA_CALL_TYPE = "extra_call_type"
        const val EXTRA_EXCHANGE_ID = "extra_exchange_id"
        const val EXTRA_AUTO_ANSWER = "extra_auto_answer"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Cấu hình hiển thị màn hình khóa (Android 8.1+ và cũ hơn)
        configureLockScreenFlags()

        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: ""
        val callerId = intent.getStringExtra(EXTRA_CALLER_ID) ?: ""
        val callerName = intent.getStringExtra(EXTRA_CALLER_NAME)?.ifBlank { "Đối tác" } ?: "Đối tác"
        val callType = intent.getStringExtra(EXTRA_CALL_TYPE) ?: "VOICE"
        val exchangeId = intent.getStringExtra(EXTRA_EXCHANGE_ID) ?: ""
        val autoAnswer = intent.getBooleanExtra(EXTRA_AUTO_ANSWER, false)

        if (sessionId.isBlank()) {
            Log.e(tag, "sessionId rỗng, tự động đóng IncomingCallActivity")
            finish()
            return
        }

        // Lắng nghe broadcast CALL_CANCELLED từ phía người gọi hủy
        registerCallCancelledReceiver(sessionId)

        setContent {
            SkillExchangeTheme {
                IncomingCallContent(
                    sessionId = sessionId,
                    callerName = callerName,
                    callType = callType,
                    exchangeId = exchangeId,
                    autoAnswer = autoAnswer,
                    onReject = {
                        handleRejectCall(sessionId)
                    },
                    onAnswerSuccess = {
                        handleAnswerSuccess(sessionId, callerName, exchangeId, callType)
                    },
                    onTimeout = {
                        handleTimeout(sessionId)
                    }
                )
            }
        }
    }

    private fun configureLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun registerCallCancelledReceiver(currentSessionId: String) {
        callCancelledReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == IncomingCallNotificationHelper.ACTION_CALL_CANCELLED_BROADCAST) {
                    val incomingSessionId = intent.getStringExtra(EXTRA_SESSION_ID)
                    if (incomingSessionId == currentSessionId && currentSessionId.isNotBlank()) {
                        Log.d(tag, "Nhận được broadcast CALL_CANCELLED khớp sessionId=$currentSessionId, đóng IncomingCallActivity")
                        Toast.makeText(this@IncomingCallActivity, "Người gọi đã hủy cuộc gọi", Toast.LENGTH_SHORT).show()
                        IncomingCallNotificationHelper.cancelIncomingCallNotification(this@IncomingCallActivity)
                        finish()
                    }
                }
            }
        }
        val filter = IntentFilter(IncomingCallNotificationHelper.ACTION_CALL_CANCELLED_BROADCAST)
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            callCancelledReceiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun handleRejectCall(sessionId: String) {
        Log.d(tag, "Người dùng bấm Từ chối trên IncomingCallActivity: $sessionId")
        IncomingCallNotificationHelper.cancelIncomingCallNotification(this)
        // Sử dụng Application CoroutineScope tiêm qua Koin để tránh rò rỉ hoặc bị hủy theo vòng đời Activity
        val applicationScope: kotlinx.coroutines.CoroutineScope = org.koin.core.context.GlobalContext.get().get()
        applicationScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                callRepository.rejectCall(sessionId)
            } catch (e: Exception) {
                Log.e(tag, "Lỗi khi gọi rejectCall: ${e.message}")
            }
        }
        finish()
    }

    private fun handleTimeout(sessionId: String) {
        Log.d(tag, "Đã hết 30s đổ chuông mà người dùng không nhấc máy: $sessionId")
        IncomingCallNotificationHelper.cancelIncomingCallNotification(this)
        finish()
    }

    private fun handleAnswerSuccess(
        sessionId: String,
        callerName: String,
        exchangeId: String,
        callType: String
    ) {
        Log.d(tag, "Trả lời thành công, khởi động ForegroundService và điều hướng tới CallScreen")
        IncomingCallNotificationHelper.cancelIncomingCallNotification(this)

        // Cấp quyền nội bộ cho sessionId trước khi điều hướng sang MainActivity -> CallScreen
        com.skillexchange.app.core.call.InternalCallAuth.authorize(sessionId)

        // Khởi động Foreground Service giữ kết nối âm thanh cuộc gọi
        CallForegroundService.startService(
            context = this,
            sessionId = sessionId,
            callerName = callerName,
            exchangeId = exchangeId,
            callType = callType
        )

        // Mở MainActivity kèm deep link điều hướng vào màn hình CallScreen
        val callIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("type", "CALL")
            putExtra("entityId", exchangeId)
            putExtra("sessionId", sessionId)
            putExtra("isVideo", callType.equals("VIDEO", ignoreCase = true))
        }
        startActivity(callIntent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        callCancelledReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {}
        }
        // Không hủy thông báo nếu Activity bị hủy do đổi cấu hình (xoay màn hình, gập/mở máy)
        if (!isChangingConfigurations) {
            IncomingCallNotificationHelper.cancelIncomingCallNotification(this)
        }
    }
}

@Composable
fun IncomingCallContent(
    sessionId: String,
    callerName: String,
    callType: String,
    exchangeId: String,
    autoAnswer: Boolean,
    onReject: () -> Unit,
    onAnswerSuccess: () -> Unit,
    onTimeout: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val callRepository: ICallRepository = org.koin.compose.koinInject()

    var remainingSeconds by remember { mutableStateOf(30) }
    var isConnecting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showPermissionRationale by remember { mutableStateOf(false) }

    // Launcher xin quyền RECORD_AUDIO runtime
    val recordAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d("IncomingCallContent", "Người dùng đã cấp quyền RECORD_AUDIO, tiến hành kết nối phòng")
            performJoinCall(sessionId, callRepository, coroutineScope, onConnecting = { isConnecting = true }, onSuccess = onAnswerSuccess, onError = { errorMessage = it; isConnecting = false })
        } else {
            Log.w("IncomingCallContent", "Người dùng từ chối quyền RECORD_AUDIO")
            showPermissionRationale = true
        }
    }

    // Đếm ngược 30 giây đổ chuông (Ringing Timeout)
    LaunchedEffect(Unit) {
        while (remainingSeconds > 0) {
            delay(1000L)
            remainingSeconds--
        }
        onTimeout()
    }

    // Xử lý cờ autoAnswer khi mở từ notification action [Trả lời]
    LaunchedEffect(autoAnswer) {
        if (autoAnswer && !isConnecting) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                performJoinCall(sessionId, callRepository, coroutineScope, onConnecting = { isConnecting = true }, onSuccess = onAnswerSuccess, onError = { errorMessage = it; isConnecting = false })
            } else {
                recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    // Animation nhịp đập (pulse) cho Avatar
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "avatarPulse"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF121212)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header: Loại cuộc gọi & đồng hồ đếm ngược
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 48.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (callType.equals("VIDEO", ignoreCase = true)) Icons.Filled.Videocam else Icons.Filled.Phone,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (callType.equals("VIDEO", ignoreCase = true)) "Cuộc gọi video đến" else "Cuộc gọi thoại đến",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.LightGray
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Đang đổ chuông (${remainingSeconds}s)...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            }

            // Body: Avatar với hiệu ứng nhịp đập & Tên đối tác
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(160.dp)
                ) {
                    // Vòng tròn hào quang nhịp đập
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .scale(pulseScale)
                            .background(Color(0xFF2E7D32).copy(alpha = 0.35f), shape = CircleShape)
                    )
                    // Avatar chính
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .background(Color(0xFF1B5E20), shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = callerName.take(1).uppercase(),
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = callerName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }

                if (isConnecting) {
                    Spacer(modifier = Modifier.height(16.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = Color(0xFF4CAF50)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Đang kết nối phòng...",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray
                    )
                }
            }

            // Footer: 2 nút tròn lớn [Từ chối] và [Trả lời]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Nút [Từ chối] màu đỏ
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onReject,
                        enabled = !isConnecting,
                        modifier = Modifier
                            .size(72.dp)
                            .background(Color(0xFFD32F2F), shape = CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CallEnd,
                            contentDescription = "Từ chối",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Từ chối", color = Color.White, style = MaterialTheme.typography.labelLarge)
                }

                // Nút [Trả lời] màu xanh lá
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = {
                            if (!isConnecting) {
                                val hasPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED

                                if (hasPermission) {
                                    performJoinCall(sessionId, callRepository, coroutineScope, onConnecting = { isConnecting = true }, onSuccess = onAnswerSuccess, onError = { errorMessage = it; isConnecting = false })
                                } else {
                                    recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        },
                        enabled = !isConnecting,
                        modifier = Modifier
                            .size(72.dp)
                            .background(Color(0xFF2E7D32), shape = CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Call,
                            contentDescription = "Trả lời",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Trả lời", color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }

    // Dialog giải thích quyền RECORD_AUDIO nếu người dùng từ chối
    if (showPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showPermissionRationale = false },
            title = { Text("Yêu cầu quyền Micro") },
            text = {
                Text("Ứng dụng cần quyền sử dụng Micro để bạn có thể đàm thoại với đối tác trong cuộc gọi. Vui lòng cấp quyền để tiếp tục.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionRationale = false
                    recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }) {
                    Text("Thử lại")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionRationale = false }) {
                    Text("Đóng")
                }
            }
        )
    }
}

private fun performJoinCall(
    sessionId: String,
    callRepository: ICallRepository,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onConnecting: () -> Unit,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    onConnecting()
    coroutineScope.launch {
        // 1. Xác minh phiên gọi với server và lấy token
        val joinResult = callRepository.joinCall(sessionId)
        joinResult.onSuccess {
            // 2. Xác nhận đã kết nối với server
            val notifyResult = callRepository.notifyConnected(sessionId)
            notifyResult.onSuccess {
                onSuccess()
            }.onFailure { err ->
                Log.w("IncomingCall", "notifyConnected thất bại: ${err.message}")
                onError(err.message ?: "Cuộc gọi đã kết thúc")
            }
        }.onFailure { err ->
            Log.w("IncomingCall", "joinCall thất bại: ${err.message}")
            onError(err.message ?: "Cuộc gọi đã kết thúc hoặc không còn khả dụng")
        }
    }
}
