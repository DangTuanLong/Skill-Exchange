package com.skillexchange.app.presentation.schedule

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.skillexchange.app.core.ui.theme.*
import com.skillexchange.app.domain.model.AvailabilityWindow
import com.skillexchange.app.domain.model.exchange.ExchangeRequest
import com.skillexchange.app.domain.model.exchange.MeetingMode
import org.koin.androidx.compose.koinViewModel
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (exchangeId: String) -> Unit,
    onNavigateToChat: (exchangeId: String) -> Unit,
    viewModel: ScheduleViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is ScheduleEffect.NavigateToDetail -> onNavigateToDetail(effect.exchangeId)
                is ScheduleEffect.NavigateToChat -> onNavigateToChat(effect.chatId)
                is ScheduleEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Thời gian biểu của tôi",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Quay lại"
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.onIntent(ScheduleIntent.JumpToToday) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Today,
                            contentDescription = null,
                            tint = Brand600,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Hôm nay",
                            fontWeight = FontWeight.Bold,
                            color = Brand600,
                            fontSize = 13.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                ),
                modifier = Modifier.statusBarsPadding()
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFFF8FAFC)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ── Bộ điều hướng tuần (Month & Week Navigator) ───────────
            WeekHeader(
                currentWeekStart = state.currentWeekStart,
                onPreviousWeek = { viewModel.onIntent(ScheduleIntent.ChangeWeek(-1)) },
                onNextWeek = { viewModel.onIntent(ScheduleIntent.ChangeWeek(1)) }
            )

            // ── Dải băng 7 ngày trong tuần (Horizontal Weekly Strip) ───
            WeeklyStrip(
                days = state.daysInCurrentWeek,
                selectedDate = state.selectedDate,
                datesWithBookings = state.datesWithBookings,
                onSelectDate = { viewModel.onIntent(ScheduleIntent.SelectDate(it)) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── Dòng thời gian chi tiết trong ngày (Daily Agenda Timeline) ─
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                when {
                    state.isLoading -> {
                        ScheduleLoadingShimmer()
                    }
                    state.error != null -> {
                        ScheduleErrorState(
                            message = state.error!!,
                            onRetry = { viewModel.onIntent(ScheduleIntent.LoadData) }
                        )
                    }
                    else -> {
                        ScheduleDailyContent(
                            selectedDate = state.selectedDate,
                            sessions = state.sessionsOnSelectedDate,
                            availability = state.openAvailabilityOnSelectedDate,
                            currentUserId = state.currentUserId,
                            onNavigateToDetail = onNavigateToDetail,
                            onNavigateToChat = onNavigateToChat
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekHeader(
    currentWeekStart: LocalDate,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit
) {
    val monthYearFormatter = remember {
        DateTimeFormatter.ofPattern("'Tháng' M, yyyy", Locale.forLanguageTag("vi-VN"))
    }
    val monthYearText = monthYearFormatter.format(currentWeekStart)

    Surface(
        color = Color.White,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = monthYearText,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = TextPrimaryLight
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onPreviousWeek,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "Tuần trước",
                        tint = Brand600
                    )
                }
                IconButton(
                    onClick = onNextWeek,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Tuần tới",
                        tint = Brand600
                    )
                }
            }
        }
    }
}

@Composable
private fun WeeklyStrip(
    days: List<LocalDate>,
    selectedDate: LocalDate,
    datesWithBookings: Set<LocalDate>,
    onSelectDate: (LocalDate) -> Unit
) {
    val dayLabels = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
    val today = remember { LocalDate.now() }

    Surface(
        color = Color.White,
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            days.forEachIndexed { index, date ->
                val isSelected = date == selectedDate
                val isToday = date == today
                val hasBookings = date in datesWithBookings
                val dayLabel = dayLabels.getOrElse(index) { "T${date.dayOfWeek.value + 1}" }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when {
                                isSelected -> Brand500
                                isToday -> Color(0xFFEFF6FF)
                                else -> Color.Transparent
                            }
                        )
                        .border(
                            width = if (isToday && !isSelected) 1.dp else 0.dp,
                            color = if (isToday && !isSelected) Brand500 else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { onSelectDate(date) }
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = dayLabel,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.White else TextSecondaryLight
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "${date.dayOfMonth}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color.White else TextPrimaryLight
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Chỉ báo chấm tròn nếu ngày đó có lịch hẹn
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    hasBookings && isSelected -> Color.White
                                    hasBookings -> Brand500
                                    else -> Color.Transparent
                                }
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduleDailyContent(
    selectedDate: LocalDate,
    sessions: List<ExchangeRequest>,
    availability: List<AvailabilityWindow>,
    currentUserId: String,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToChat: (String) -> Unit
) {
    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("EEEE, 'ngày' dd/MM/yyyy", Locale.forLanguageTag("vi-VN"))
    }
    val dateHeading = dateFormatter.format(selectedDate).replaceFirstChar { it.uppercase() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
    ) {
        // Tiêu đề ngày được chọn
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = null,
                    tint = Brand600,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = dateHeading,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = TextPrimaryLight
                )
            }
        }

        // Danh sách các buổi hẹn đã chốt trong ngày
        if (sessions.isNotEmpty()) {
            item {
                Text(
                    text = "Lịch hẹn đã chốt (${sessions.size})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Brand700
                )
            }

            items(sessions, key = { it.id }) { exchange ->
                ConfirmedSessionCard(
                    exchange = exchange,
                    currentUserId = currentUserId,
                    onNavigateToDetail = { onNavigateToDetail(exchange.id) },
                    onNavigateToChat = { onNavigateToChat(exchange.id) }
                )
            }
        }

        // Khung rảnh cá nhân đã thiết lập cho ngày này
        if (availability.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Khung rảnh cá nhân của bạn",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF0284C7)
                )
            }

            items(availability) { window ->
                OpenAvailabilityCard(window = window)
            }
        }

        // Trạng thái trống (Không có lịch hẹn và không có khung rảnh)
        if (sessions.isEmpty() && availability.isEmpty()) {
            item {
                EmptyDayCard()
            }
        } else if (sessions.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Brand500,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Hôm nay chưa có buổi hẹn nào được xếp. Bạn có thể nhận thêm lời mời học trong các khung rảnh bên dưới.",
                            fontSize = 13.sp,
                            color = TextSecondaryLight
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmedSessionCard(
    exchange: ExchangeRequest,
    currentUserId: String,
    onNavigateToDetail: () -> Unit,
    onNavigateToChat: () -> Unit
) {
    val otherName = exchange.otherUserName(currentUserId)
    val otherAvatar = exchange.otherUserAvatar(currentUserId)

    val timeInterval = remember(exchange.scheduledAt, exchange.durationMinutes) {
        try {
            val instant = Instant.parse(exchange.scheduledAt)
            val startTime = instant.atZone(ZoneId.systemDefault()).toLocalTime()
            val endTime = startTime.plusMinutes(exchange.durationMinutes.toLong())
            val formatter = DateTimeFormatter.ofPattern("HH:mm")
            "${formatter.format(startTime)} - ${formatter.format(endTime)}"
        } catch (e: Exception) {
            exchange.scheduledAt
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onNavigateToDetail),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Dải viền màu nhấn bên trái
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(IntrinsicSize.Max)
                    .background(
                        if (exchange.meetingMode == MeetingMode.ONLINE) Brand500 else Color(0xFF10B981)
                    )
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Hàng thời gian & Hình thức gặp
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = Brand600,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "$timeInterval (${exchange.durationMinutes} phút)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Brand700
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (exchange.meetingMode == MeetingMode.ONLINE) Color(0xFFEFF6FF) else Color(0xFFECFDF5)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (exchange.meetingMode == MeetingMode.ONLINE) Icons.Default.Videocam else Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = if (exchange.meetingMode == MeetingMode.ONLINE) Brand600 else Color(0xFF059669),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = exchange.meetingMode.displayName(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (exchange.meetingMode == MeetingMode.ONLINE) Brand700 else Color(0xFF047857)
                            )
                        }
                    }
                }

                // Thông tin đối tác
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEFF6FF)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!otherAvatar.isNullOrBlank()) {
                            AsyncImage(
                                model = otherAvatar,
                                contentDescription = otherName,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = Brand500,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = otherName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = TextPrimaryLight,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Đối tác trao đổi",
                            fontSize = 11.sp,
                            color = TextSecondaryLight
                        )
                    }
                }

                // Cặp kỹ năng trao đổi
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF8FAFC),
                    border = borderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Text(
                            text = "Dạy: ${exchange.teachSkillName(currentUserId)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Brand600
                        )
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = null,
                            tint = TextSecondaryLight,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Học: ${exchange.learnSkillName(currentUserId)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF10B981)
                        )
                    }
                }

                // Nút thao tác nhanh
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onNavigateToChat,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Brand600),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Nhắn tin", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    FilledTonalButton(
                        onClick = onNavigateToDetail,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFFEFF6FF),
                            contentColor = Brand600
                        ),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Chi tiết", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OpenAvailabilityCard(window: AvailabilityWindow) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F9FF)),
        border = borderStroke(1.dp, Color(0xFFBAE6FD))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = null,
                    tint = Color(0xFF0284C7),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "${window.from} - ${window.to}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF0369A1)
                    )
                    Text(
                        text = "Khung rảnh định kỳ (Chưa có lịch hẹn)",
                        fontSize = 12.sp,
                        color = TextSecondaryLight
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFFE0F2FE)
            ) {
                Text(
                    text = "Còn trống",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0284C7),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyDayCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color(0xFFEFF6FF)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null,
                tint = Brand500,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Không có lịch hẹn nào",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimaryLight
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Bạn không có buổi trao đổi nào trong ngày này.",
            fontSize = 13.sp,
            color = TextSecondaryLight
        )
    }
}

@Composable
private fun ScheduleErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = message,
            fontSize = 14.sp,
            color = TextPrimaryLight
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = Brand500)
        ) {
            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Thử lại", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ScheduleLoadingShimmer() {
    val transition = rememberInfiniteTransition(label = "ScheduleShimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFE2E8F0).copy(alpha = alpha))
            )
        }
    }
}

private fun borderStroke(width: androidx.compose.ui.unit.Dp, color: Color) =
    androidx.compose.foundation.BorderStroke(width, color)
