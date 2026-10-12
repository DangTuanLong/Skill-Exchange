package com.skillexchange.app.presentation.notification

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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skillexchange.app.core.ui.theme.*
import com.skillexchange.app.domain.model.notification.AppNotification
import com.skillexchange.app.domain.model.notification.NotificationType
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEntity: (type: String, entityId: String) -> Unit,
    viewModel: NotificationViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is NotificationEffect.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
                is NotificationEffect.NavigateToEntity -> {
                    onNavigateToEntity(effect.type, effect.entityId)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Thông báo",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        if (state.unreadCount > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Badge(
                                containerColor = Brand500,
                                contentColor = Color.White
                            ) {
                                Text(
                                    text = state.unreadCount.toString(),
                                    modifier = Modifier.semantics {
                                        contentDescription = "${state.unreadCount} thông báo chưa đọc"
                                    }
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Quay lại"
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.onIntent(NotificationIntent.MarkAllAsRead) },
                        enabled = state.unreadCount > 0,
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Text(
                            text = "Đọc hết",
                            fontWeight = FontWeight.Medium,
                            color = if (state.unreadCount > 0) Brand500 else TextSecondaryLight.copy(alpha = 0.5f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LightSurface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = LightBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                state.isLoading && state.notifications.isEmpty() -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Brand500
                    )
                }

                state.error != null && state.notifications.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = "Lỗi",
                            modifier = Modifier.size(56.dp),
                            tint = Error
                        )
                        Text(
                            text = state.error ?: "Đã có lỗi xảy ra",
                            fontSize = 16.sp,
                            color = TextSecondaryLight,
                            fontWeight = FontWeight.Medium
                        )
                        Button(
                            onClick = { viewModel.onIntent(NotificationIntent.LoadNotifications) },
                            colors = ButtonDefaults.buttonColors(containerColor = Brand500),
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text(text = "Thử lại", color = Color.White)
                        }
                    }
                }

                state.groups.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsNone,
                            contentDescription = "Chưa có thông báo",
                            modifier = Modifier.size(64.dp),
                            tint = TextSecondaryLight.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "Chưa có thông báo nào",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimaryLight
                        )
                        Text(
                            text = "Các thông báo về trao đổi, tin nhắn và lịch hẹn sẽ hiển thị ở đây",
                            fontSize = 14.sp,
                            color = TextSecondaryLight,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.groups.forEach { group ->
                            item(key = "header_${group.title}") {
                                Text(
                                    text = group.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondaryLight,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp, start = 4.dp)
                                )
                            }

                            items(
                                items = group.items,
                                key = { it.id }
                            ) { notification ->
                                NotificationItemCard(
                                    notification = notification,
                                    onClick = {
                                        viewModel.onIntent(NotificationIntent.NotificationClicked(notification))
                                    }
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
fun NotificationItemCard(
    notification: AppNotification,
    onClick: () -> Unit
) {
    val (icon, iconBg, iconTint) = getNotificationVisuals(notification.type)
    val formattedTime = remember(notification.createdAt) {
        formatNotificationTime(notification.createdAt)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (notification.isRead) LightSurface else Brand500.copy(alpha = 0.06f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (notification.isRead) 1.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icon Badge
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = notification.rawType,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Text Content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = notification.title,
                        fontSize = 15.sp,
                        fontWeight = if (notification.isRead) FontWeight.SemiBold else FontWeight.Bold,
                        color = TextPrimaryLight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = formattedTime,
                        fontSize = 12.sp,
                        color = TextSecondaryLight
                    )
                }

                Text(
                    text = notification.body,
                    fontSize = 13.sp,
                    color = if (notification.isRead) TextSecondaryLight else TextPrimaryLight,
                    lineHeight = 18.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Unread Indicator Dot
            if (!notification.isRead) {
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Brand500)
                        .semantics { contentDescription = "Chưa đọc" }
                )
            }
        }
    }
}

private fun getNotificationVisuals(type: NotificationType): Triple<ImageVector, Color, Color> {
    return when (type) {
        NotificationType.NEW_MESSAGE -> Triple(
            Icons.Default.Chat,
            Brand500.copy(alpha = 0.15f),
            Brand600
        )
        NotificationType.NEW_REQUEST -> Triple(
            Icons.Default.SwapHoriz,
            Accent500.copy(alpha = 0.15f),
            Accent600
        )
        NotificationType.REQUEST_ACCEPTED -> Triple(
            Icons.Default.CheckCircle,
            Success.copy(alpha = 0.15f),
            Success
        )
        NotificationType.REQUEST_REJECTED -> Triple(
            Icons.Default.Cancel,
            Error.copy(alpha = 0.15f),
            Error
        )
        NotificationType.APPOINTMENT_REMINDER -> Triple(
            Icons.Default.Alarm,
            Warning.copy(alpha = 0.15f),
            Warning
        )
        NotificationType.RATING_REMINDER -> Triple(
            Icons.Default.Star,
            StarFilled.copy(alpha = 0.2f),
            HighlightDark
        )
        NotificationType.OTHER -> Triple(
            Icons.Default.Notifications,
            Brand500.copy(alpha = 0.15f),
            Brand500
        )
    }
}

private fun formatNotificationTime(dateStr: String): String {
    return runCatching {
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        if (dateStr.contains("T")) {
            if (dateStr.endsWith("Z") || dateStr.contains("+")) {
                ZonedDateTime.parse(dateStr).format(timeFormatter)
            } else {
                LocalDateTime.parse(dateStr).format(timeFormatter)
            }
        } else {
            dateStr.substringAfter(" ", "").take(5)
        }
    }.getOrDefault("")
}
