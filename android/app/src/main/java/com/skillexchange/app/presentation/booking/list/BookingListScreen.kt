package com.skillexchange.app.presentation.booking.list

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.skillexchange.app.core.ui.theme.Brand500
import com.skillexchange.app.core.ui.theme.TextPrimaryLight
import com.skillexchange.app.core.ui.theme.TextSecondaryLight
import com.skillexchange.app.domain.model.exchange.ExchangeRequest
import com.skillexchange.app.presentation.booking.detail.StatusChip
import org.koin.androidx.compose.koinViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingListScreen(
    onNavigateToDetail: (exchangeId: String) -> Unit,
    viewModel: BookingListViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Lưu lại scroll state cho từng tab để giữ vị trí cuộn khi quay lại từ detail
    val incomingListState = rememberLazyListState()
    val outgoingListState = rememberLazyListState()
    val historyListState = rememberLazyListState()

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is BookingListEffect.NavigateToDetail -> onNavigateToDetail(effect.exchangeId)
                is BookingListEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Lịch hẹn trao đổi",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.onIntent(BookingListIntent.Refresh) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Làm mới", tint = Brand500)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
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
            // 3 Tabs có Badge số đếm
            TabRow(
                selectedTabIndex = state.selectedTab.ordinal,
                containerColor = Color.White,
                contentColor = Brand500,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[state.selectedTab.ordinal]),
                        color = Brand500
                    )
                }
            ) {
                BookingTab.values().forEach { tab ->
                    val count = when (tab) {
                        BookingTab.INCOMING -> state.incomingList.size
                        BookingTab.OUTGOING -> state.outgoingList.size
                        BookingTab.HISTORY -> state.historyList.size
                    }
                    val isSelected = state.selectedTab == tab

                    Tab(
                        selected = isSelected,
                        onClick = { viewModel.onIntent(BookingListIntent.SelectTab(tab)) },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = tab.title,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Brand500 else TextSecondaryLight
                                )
                                if (count > 0) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isSelected) Brand500 else Color(0xFFE2E8F0),
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "$count",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) Color.White else TextSecondaryLight
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.height(48.dp)
                    )
                }
            }

            // Nội dung theo 4 trạng thái
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                when {
                    state.isLoading -> {
                        // 1. Loading Skeleton Shimmer
                        BookingListSkeleton()
                    }

                    state.error != null && state.currentList.isEmpty() -> {
                        // 2. Lỗi + nút Thử lại
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = state.error ?: "Đã có lỗi xảy ra",
                                fontSize = 15.sp,
                                color = TextPrimaryLight,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.onIntent(BookingListIntent.LoadData) },
                                colors = ButtonDefaults.buttonColors(containerColor = Brand500)
                            ) {
                                Text("Thử lại", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    state.currentList.isEmpty() -> {
                        // 3. Trạng thái rỗng
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEFF6FF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.DateRange,
                                    contentDescription = null,
                                    tint = Brand500,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Chưa có lịch hẹn nào",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimaryLight
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = when (state.selectedTab) {
                                    BookingTab.INCOMING -> "Bạn chưa nhận được yêu cầu trao đổi nào."
                                    BookingTab.OUTGOING -> "Bạn chưa gửi yêu cầu trao đổi nào."
                                    BookingTab.HISTORY -> "Chưa có lịch hẹn hoàn thành hoặc đã kết thúc."
                                },
                                fontSize = 13.sp,
                                color = TextSecondaryLight
                            )
                        }
                    }

                    else -> {
                        // 4. Danh sách nội dung (Thẻ không có nút hành động)
                        val currentScrollState = when (state.selectedTab) {
                            BookingTab.INCOMING -> incomingListState
                            BookingTab.OUTGOING -> outgoingListState
                            BookingTab.HISTORY -> historyListState
                        }

                        LazyColumn(
                            state = currentScrollState,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(state.currentList, key = { it.id }) { exchange ->
                                BookingCard(
                                    exchange = exchange,
                                    currentUserId = state.currentUserId,
                                    onClick = { onNavigateToDetail(exchange.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookingCard(
    exchange: ExchangeRequest,
    currentUserId: String,
    onClick: () -> Unit
) {
    val otherAvatar = exchange.otherUserAvatar(currentUserId)
    val otherName = exchange.otherUserName(currentUserId)

    val formattedTime = try {
        val instant = Instant.parse(exchange.scheduledAt)
        val formatter = DateTimeFormatter.ofPattern("HH:mm - dd/MM/yyyy")
            .withZone(ZoneId.systemDefault())
        formatter.format(instant)
    } catch (e: Exception) {
        exchange.scheduledAt
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Avatar, Tên đối tác và Status Chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
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
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = Brand500,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = otherName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextPrimaryLight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                StatusChip(
                    status = exchange.status,
                    isWaitingForOther = exchange.isWaitingForOtherToConfirm(currentUserId)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Trao đổi: Dạy X ↔ Học Y
            Surface(
                color = Color(0xFFF8FAFC),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Dạy: ",
                        fontSize = 13.sp,
                        color = TextSecondaryLight
                    )
                    Text(
                        text = exchange.teachSkillName(currentUserId),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = Brand500
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        Icons.Default.SwapHoriz,
                        contentDescription = null,
                        tint = TextSecondaryLight,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Học: ",
                        fontSize = 13.sp,
                        color = TextSecondaryLight
                    )
                    Text(
                        text = exchange.learnSkillName(currentUserId),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = Color(0xFF10B981)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Thời gian
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.CalendarToday,
                    contentDescription = null,
                    tint = TextSecondaryLight,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = formattedTime,
                    fontSize = 12.sp,
                    color = TextSecondaryLight
                )
            }

            // Message preview nếu có
            if (!exchange.message.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "\"${exchange.message}\"",
                    fontSize = 12.sp,
                    color = TextSecondaryLight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Xem → không có nút hành động
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Xem chi tiết",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Brand500
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Brand500,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun BookingListSkeleton() {
    val shimmerTransition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by shimmerTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFFE2E8F0),
            Color(0xFFF1F5F9),
            Color(0xFFE2E8F0)
        ),
        start = Offset.Zero,
        end = Offset(x = translateAnim, y = translateAnim)
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(3) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(shimmerBrush)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Box(
                                modifier = Modifier
                                    .width(120.dp)
                                    .height(16.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(shimmerBrush)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .width(70.dp)
                                .height(22.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(shimmerBrush)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(shimmerBrush)
                    )

                    Box(
                        modifier = Modifier
                            .width(160.dp)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(shimmerBrush)
                    )
                }
            }
        }
    }
}
