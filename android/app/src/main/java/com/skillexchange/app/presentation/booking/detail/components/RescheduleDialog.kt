package com.skillexchange.app.presentation.booking.detail.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skillexchange.app.core.ui.theme.Brand500
import com.skillexchange.app.core.ui.theme.TextPrimaryLight
import com.skillexchange.app.core.ui.theme.TextSecondaryLight
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*

@Composable
fun RescheduleDialog(
    onDismiss: () -> Unit,
    onConfirm: (newScheduledAtIso: String, reason: String?) -> Unit,
    isLoading: Boolean = false
) {
    val context = LocalContext.current

    // Khởi tạo thời gian mặc định: Hiện tại + 2 giờ
    val defaultCal = remember {
        Calendar.getInstance().apply {
            add(Calendar.HOUR_OF_DAY, 2)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    var scheduledDateEpochMs by remember { mutableLongStateOf(defaultCal.timeInMillis) }
    var scheduledHour by remember { mutableIntStateOf(defaultCal.get(Calendar.HOUR_OF_DAY)) }
    var scheduledMinute by remember { mutableIntStateOf(defaultCal.get(Calendar.MINUTE)) }
    var reason by remember { mutableStateOf("") }

    val minAllowedTimeMs = remember { System.currentTimeMillis() + 15 * 60 * 1000L }

    // Tính epoch millis của thời điểm đã chọn
    val selectedEpochMs = remember(scheduledDateEpochMs, scheduledHour, scheduledMinute) {
        val cal = Calendar.getInstance().apply {
            timeInMillis = scheduledDateEpochMs
            set(Calendar.HOUR_OF_DAY, scheduledHour)
            set(Calendar.MINUTE, scheduledMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        cal.timeInMillis
    }

    val isTimeValid = selectedEpochMs > minAllowedTimeMs
    val isReasonValid = reason.length <= 200

    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    val dateStr = dateFormatter.format(Date(scheduledDateEpochMs))
    val timeStr = String.format(Locale.getDefault(), "%02d:%02d", scheduledHour, scheduledMinute)

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.EditCalendar,
                    contentDescription = null,
                    tint = Brand500,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Đề xuất giờ khác",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = TextPrimaryLight
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "Chọn thời gian mới cho buổi trao đổi. Thời gian mới phải cách hiện tại ít nhất 15 phút.",
                    fontSize = 13.sp,
                    color = TextSecondaryLight
                )

                // Chọn Ngày & Giờ
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Thẻ chọn ngày
                    OutlinedCard(
                        onClick = {
                            val cal = Calendar.getInstance().apply { timeInMillis = scheduledDateEpochMs }
                            val nowCal = Calendar.getInstance()
                            val datePicker = DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    val sel = Calendar.getInstance().apply {
                                        set(Calendar.YEAR, y)
                                        set(Calendar.MONTH, m)
                                        set(Calendar.DAY_OF_MONTH, d)
                                    }
                                    scheduledDateEpochMs = sel.timeInMillis
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            )
                            datePicker.datePicker.minDate = nowCal.timeInMillis
                            datePicker.show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = Brand500, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text("Ngày mới", fontSize = 10.sp, color = TextSecondaryLight)
                                Text(dateStr, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        }
                    }

                    // Thẻ chọn giờ
                    OutlinedCard(
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, h, m ->
                                    scheduledHour = h
                                    scheduledMinute = m
                                },
                                scheduledHour,
                                scheduledMinute,
                                true
                            ).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AccessTime, contentDescription = null, tint = Brand500, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text("Giờ mới", fontSize = 10.sp, color = TextSecondaryLight)
                                Text(timeStr, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        }
                    }
                }

                // Cảnh báo nếu thời gian <= now + 15 phút
                if (!isTimeValid) {
                    Text(
                        "Thời gian mới phải cách thời điểm hiện tại ít nhất 15 phút.",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Ô nhập lý do (tùy chọn, tối đa 200 ký tự text thuần)
                Column {
                    OutlinedTextField(
                        value = reason,
                        onValueChange = {
                            if (it.length <= 250) {
                                reason = it
                            }
                        },
                        label = { Text("Lý do đổi lịch (tùy chọn)") },
                        placeholder = { Text("Ví dụ: Bận đột xuất, dời sang chiều nhé...") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        isError = !isReasonValid,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, end = 4.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = "${reason.length} / 200",
                            fontSize = 11.sp,
                            color = if (isReasonValid) TextSecondaryLight else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isTimeValid && isReasonValid && !isLoading) {
                        val instant = Instant.ofEpochMilli(selectedEpochMs)
                        val formatter = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss'Z'")
                            .withZone(ZoneOffset.UTC)
                        val isoString = formatter.format(instant)
                        val trimmedReason = reason.trim().takeIf { it.isNotEmpty() }
                        onConfirm(isoString, trimmedReason)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Brand500),
                enabled = isTimeValid && isReasonValid && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("GỬI ĐỀ XUẤT", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("ĐÓNG")
            }
        }
    )
}
