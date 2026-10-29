package com.skillexchange.app.presentation.chat.detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneMissed
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skillexchange.app.domain.model.chat.ChatMessage
import com.skillexchange.app.presentation.chat.detail.SystemCallFormatter

@Composable
fun SystemCallMessageItem(
    message: ChatMessage,
    currentUserId: String,
    isExchangeAccepted: Boolean,
    onCallAgain: (isVideo: Boolean) -> Unit,
    onCannotCallAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSuccess = SystemCallFormatter.isSuccessful(message.callStatus)
    val title = SystemCallFormatter.formatTitle(message, currentUserId)
    val timeStr = SystemCallFormatter.formatTime(message.createdAt)
    val isVideo = message.callType.equals("VIDEO", ignoreCase = true)

    val iconColor = if (isSuccess) Color(0xFF16A34A) else Color(0xFFDC2626)
    val iconBgColor = if (isSuccess) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)

    val iconVector = when {
        isVideo -> Icons.Default.Videocam
        isSuccess -> Icons.Default.Phone
        else -> Icons.Default.PhoneMissed
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFF8FAFC))
                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                .clickable {
                    if (isExchangeAccepted) {
                        onCallAgain(isVideo)
                    } else {
                        onCannotCallAgain()
                    }
                }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon tròn biểu thị cuộc gọi
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(iconBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1E293B)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = timeStr,
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                    if (isExchangeAccepted) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "• Nhấn để gọi lại",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF2563EB)
                        )
                    }
                }
            }
        }
    }
}

