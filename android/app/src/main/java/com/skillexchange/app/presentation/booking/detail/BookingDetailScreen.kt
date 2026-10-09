package com.skillexchange.app.presentation.booking.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.skillexchange.app.core.ui.theme.Brand500
import com.skillexchange.app.core.ui.theme.TextPrimaryLight
import com.skillexchange.app.core.ui.theme.TextSecondaryLight
import com.skillexchange.app.domain.model.exchange.ExchangeStatus
import org.koin.androidx.compose.koinViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingDetailScreen(
    onNavigateBack: () -> Unit,
    onNavigateToRating: ((String) -> Unit)? = null,
    onNavigateToChat: ((String) -> Unit)? = null,
    viewModel: BookingDetailViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                if (viewModel.exchangeId.isNotBlank()) {
                    viewModel.loadDetail(viewModel.exchangeId)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is BookingDetailEffect.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
                is BookingDetailEffect.NavigateBack -> {
                    onNavigateBack()
                }
            }
        }
    }

    if (state.showCancelDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.onIntent(BookingDetailIntent.DismissCancelDialog) },
            title = {
                Text(
                    "Xác nhận hủy lịch hẹn",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Lịch hẹn này đã được chấp nhận. Bạn có chắc chắn muốn hủy không?",
                        fontSize = 14.sp,
                        color = TextPrimaryLight
                    )
                    OutlinedTextField(
                        value = state.cancelReason,
                        onValueChange = { viewModel.onIntent(BookingDetailIntent.UpdateCancelReason(it)) },
                        label = { Text("Lý do hủy (tùy chọn)") },
                        placeholder = { Text("Nhập lý do...") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.onIntent(BookingDetailIntent.ConfirmCancel) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    enabled = !state.isActionLoading
                ) {
                    if (state.isActionLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("XÁC NHẬN HỦY", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.onIntent(BookingDetailIntent.DismissCancelDialog) },
                    enabled = !state.isActionLoading
                ) {
                    Text("ĐÓNG")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chi tiết lịch hẹn", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFFF8FAFC)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Brand500
                )
            } else if (state.exchange != null) {
                val exchange = state.exchange!!

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Thẻ đối tác + Chip trạng thái
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    val otherAvatar = exchange.otherUserAvatar(state.currentUserId)
                                    val otherName = exchange.otherUserName(state.currentUserId)

                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFEFF6FF)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!otherAvatar.isNullOrBlank()) {
                                            AsyncImage(
                                                model = otherAvatar,
                                                contentDescription = otherName,
                                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Icon(Icons.Default.Person, contentDescription = null, tint = Brand500)
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column {
                                        Text(
                                            text = otherName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = TextPrimaryLight
                                        )
                                        Text(
                                            text = if (state.isSender) "Người nhận yêu cầu" else "Người gửi yêu cầu",
                                            fontSize = 12.sp,
                                            color = TextSecondaryLight
                                        )
                                    }
                                }

                                // Status Chip cập nhật tại chỗ
                                StatusChip(
                                    status = exchange.status,
                                    isWaitingForOther = state.isWaitingForOtherToConfirm
                                )
                            }
                        }
                    }

                    // 2. Nội dung trao đổi kỹ năng
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                "Nội dung trao đổi 🤝",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = TextPrimaryLight
                            )

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFF1F5F9),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceAround
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Bạn dạy", fontSize = 12.sp, color = TextSecondaryLight)
                                        Text(
                                            exchange.teachSkillName(state.currentUserId),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = Brand500
                                        )
                                    }

                                    Icon(
                                        Icons.Default.SwapHoriz,
                                        contentDescription = null,
                                        tint = TextSecondaryLight,
                                        modifier = Modifier.size(28.dp)
                                    )

                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Bạn học", fontSize = 12.sp, color = TextSecondaryLight)
                                        Text(
                                            exchange.learnSkillName(state.currentUserId),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = Color(0xFF10B981)
                                        )
                                    }
                                }
                            }

                            // Thời gian & Địa điểm
                            val formattedTime = try {
                                val instant = Instant.parse(exchange.scheduledAt)
                                val formatter = DateTimeFormatter.ofPattern("HH:mm - dd/MM/yyyy")
                                    .withZone(ZoneId.systemDefault())
                                formatter.format(instant)
                            } catch (e: Exception) {
                                exchange.scheduledAt
                            }

                            DetailInfoRow(
                                icon = Icons.Default.CalendarToday,
                                title = "Thời gian",
                                value = "$formattedTime (${exchange.durationMinutes} phút)"
                            )

                            DetailInfoRow(
                                icon = Icons.Default.LocationOn,
                                title = "Hình thức",
                                value = exchange.meetingMode.displayName()
                            )

                            if (!exchange.message.isNullOrBlank()) {
                                DetailInfoRow(
                                    icon = Icons.Default.ChatBubbleOutline,
                                    title = "Lời nhắn",
                                    value = exchange.message
                                )
                            }

                            if (!exchange.cancellationReason.isNullOrBlank()) {
                                DetailInfoRow(
                                    icon = Icons.Default.Cancel,
                                    title = "Lý do hủy",
                                    value = exchange.cancellationReason,
                                    valueColor = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    // 3. Khối hành động theo bảng UI_SPEC §4.12
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "Thao tác",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = TextPrimaryLight
                            )

                            when (exchange.status) {
                                ExchangeStatus.PENDING -> {
                                    if (state.isReceiver) {
                                        // Receiver: Từ chối / Chấp nhận
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = { viewModel.onIntent(BookingDetailIntent.RejectRequest) },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp),
                                                shape = RoundedCornerShape(12.dp),
                                                enabled = !state.isActionLoading,
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    contentColor = MaterialTheme.colorScheme.error
                                                )
                                            ) {
                                                Text("Từ chối", fontWeight = FontWeight.SemiBold)
                                            }

                                            Button(
                                                onClick = { viewModel.onIntent(BookingDetailIntent.AcceptRequest) },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp),
                                                shape = RoundedCornerShape(12.dp),
                                                enabled = !state.isActionLoading,
                                                colors = ButtonDefaults.buttonColors(containerColor = Brand500)
                                            ) {
                                                Text("Chấp nhận", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    } else {
                                        // Sender: Hủy yêu cầu
                                        OutlinedButton(
                                            onClick = { viewModel.onIntent(BookingDetailIntent.CancelPendingRequest) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            enabled = !state.isActionLoading,
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = MaterialTheme.colorScheme.error
                                            )
                                        ) {
                                            Text("Hủy yêu cầu trao đổi", fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }

                                ExchangeStatus.ACCEPTED -> {
                                    // Nhắn tin (TASK-030)
                                    OutlinedButton(
                                        onClick = {
                                            state.exchange?.let { ex ->
                                                onNavigateToChat?.invoke(ex.id)
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Brand500)
                                    ) {
                                        Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Nhắn tin trao đổi", fontWeight = FontWeight.SemiBold)
                                    }

                                    // Xác nhận hoàn thành
                                    if (state.hasUserConfirmed) {
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            color = Color(0xFFF3E8FF),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(14.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Icons.Default.HourglassEmpty,
                                                    contentDescription = null,
                                                    tint = Color(0xFF7E22CE)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    "Bạn đã xác nhận. Đang chờ đối phương xác nhận hoàn thành.",
                                                    fontSize = 13.sp,
                                                    color = Color(0xFF7E22CE),
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    } else {
                                        Button(
                                            onClick = { viewModel.onIntent(BookingDetailIntent.ConfirmCompletion) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            enabled = !state.isActionLoading,
                                            colors = ButtonDefaults.buttonColors(containerColor = Brand500)
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Xác nhận đã hoàn thành", fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    // Hủy lịch hẹn sau khi ACCEPTED (mở confirmation dialog)
                                    TextButton(
                                        onClick = { viewModel.onIntent(BookingDetailIntent.OpenCancelDialog) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp),
                                        enabled = !state.isActionLoading,
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        )
                                    ) {
                                        Text("Hủy lịch hẹn trao đổi", fontWeight = FontWeight.SemiBold)
                                    }
                                }

                                ExchangeStatus.COMPLETED -> {
                                    if (state.hasRated) {
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFFDCFCE7))
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(14.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = Color(0xFF15803D),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    "Đã đánh giá",
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF15803D),
                                                    fontSize = 15.sp
                                                )
                                            }
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                state.exchange?.let { ex ->
                                                    onNavigateToRating?.invoke(ex.id)
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Brand500)
                                        ) {
                                            Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Đánh giá ${state.otherPartyName}", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                ExchangeStatus.REJECTED,
                                ExchangeStatus.CANCELLED -> {
                                    Text(
                                        "Lịch hẹn đã kết thúc và không còn hành động khả dụng.",
                                        fontSize = 13.sp,
                                        color = TextSecondaryLight
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusChip(
    status: ExchangeStatus,
    isWaitingForOther: Boolean,
    modifier: Modifier = Modifier
) {
    val (text, bgColor, textColor) = when {
        status == ExchangeStatus.PENDING -> Triple(
            "Chờ phản hồi",
            Color(0xFFFEF3C7),
            Color(0xFFB45309)
        )
        status == ExchangeStatus.ACCEPTED && isWaitingForOther -> Triple(
            "Chờ đối phương",
            Color(0xFFF3E8FF),
            Color(0xFF7E22CE)
        )
        status == ExchangeStatus.ACCEPTED -> Triple(
            "Đã chấp nhận",
            Color(0xFFD1FAE5),
            Color(0xFF047857)
        )
        status == ExchangeStatus.COMPLETED -> Triple(
            "Đã hoàn thành",
            Color(0xFFDCFCE7),
            Color(0xFF15803D)
        )
        status == ExchangeStatus.REJECTED -> Triple(
            "Bị từ chối",
            Color(0xFFFEE2E2),
            Color(0xFFB91C1C)
        )
        status == ExchangeStatus.CANCELLED -> Triple(
            "Đã hủy",
            Color(0xFFF1F5F9),
            Color(0xFF475569)
        )
        else -> Triple("Không xác định", Color(0xFFF1F5F9), Color(0xFF475569))
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun DetailInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    valueColor: Color = TextPrimaryLight
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Brand500,
            modifier = Modifier
                .size(20.dp)
                .padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(title, fontSize = 12.sp, color = TextSecondaryLight)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = valueColor)
        }
    }
}
