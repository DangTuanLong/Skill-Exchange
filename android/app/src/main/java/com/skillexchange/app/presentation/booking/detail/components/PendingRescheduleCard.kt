package com.skillexchange.app.presentation.booking.detail.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skillexchange.app.core.ui.theme.Brand500
import com.skillexchange.app.core.ui.theme.TextPrimaryLight
import com.skillexchange.app.core.ui.theme.TextSecondaryLight
import com.skillexchange.app.domain.model.reschedule.RescheduleProposal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val RESCHEDULE_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm - dd/MM/yyyy")

@Composable
fun PendingRescheduleCard(
    proposal: RescheduleProposal,
    isProposer: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onCancel: () -> Unit,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    val formattedTime = remember(proposal.newScheduledAt) {
        try {
            val instant = Instant.parse(proposal.newScheduledAt)
            RESCHEDULE_TIME_FORMATTER
                .withZone(ZoneId.systemDefault())
                .format(instant)
        } catch (_: Exception) {
            proposal.newScheduledAt
        }
    }

    val cardBg = if (isProposer) Color(0xFFEFF6FF) else Color(0xFFFFFBEB)
    val cardBorder = if (isProposer) Color(0xFFBFDBFE) else Color(0xFFFDE68A)
    val iconColor = if (isProposer) Color(0xFF1D4ED8) else Color(0xFFB45309)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, cardBorder),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Icon + Tiêu đề + Badge Lượt {n}/3
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isProposer) Icons.Default.HourglassTop else Icons.Default.Schedule,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isProposer) "Bạn đã đề xuất đổi lịch hẹn" else "Đối tác đề xuất đổi lịch hẹn",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextPrimaryLight
                    )
                }

                Surface(
                    color = if (isProposer) Color(0xFFDBEAFE) else Color(0xFFFEF3C7),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Lượt ${proposal.proposalNumber}/3",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isProposer) Color(0xFF1E40AF) else Color(0xFF92400E),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Nội dung chi tiết: Giờ mới & Lý do
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Thời gian mới: $formattedTime",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Brand500
                )

                if (!proposal.reason.isNullOrBlank()) {
                    Text(
                        text = "Lý do: ${proposal.reason}",
                        fontSize = 13.sp,
                        color = TextSecondaryLight
                    )
                }

                if (isProposer) {
                    Text(
                        text = "Đang chờ đối phương xác nhận.",
                        fontSize = 12.sp,
                        color = TextSecondaryLight
                    )
                }
            }

            // Nút hành động
            if (isProposer) {
                // Người đề xuất: Nút Hủy đề xuất
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isLoading,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Hủy đề xuất", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            } else {
                // Người nhận: [Từ chối] (phụ) & [Đồng ý đổi] (chính)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onReject,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isLoading,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Text("Từ chối", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }

                    Button(
                        onClick = onAccept,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = Brand500)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text("Đồng ý đổi", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
