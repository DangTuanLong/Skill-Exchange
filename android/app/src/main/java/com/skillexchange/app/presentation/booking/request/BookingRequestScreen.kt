package com.skillexchange.app.presentation.booking.request

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.skillexchange.app.core.ui.theme.Brand500
import com.skillexchange.app.core.ui.theme.TextPrimaryLight
import com.skillexchange.app.core.ui.theme.TextSecondaryLight
import com.skillexchange.app.domain.model.exchange.MeetingMode
import com.skillexchange.app.domain.model.exchange.ValidSkillPair
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingRequestScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (exchangeId: String, receiverId: String) -> Unit,
    onNavigateToSkillSelection: () -> Unit,
    viewModel: BookingRequestViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is BookingRequestEffect.NavigateToDetail -> {
                    onNavigateToDetail(effect.exchangeId, effect.receiverId)
                }
                is BookingRequestEffect.NavigateToSkillSelection -> {
                    onNavigateToSkillSelection()
                }
                is BookingRequestEffect.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tạo yêu cầu trao đổi", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            if (state.hasValidPair) {
                Surface(
                    color = Color.White,
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Button(
                            onClick = { viewModel.onIntent(BookingRequestIntent.SubmitRequest) },
                            enabled = state.canSubmit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Brand500,
                                disabledContainerColor = Brand500.copy(alpha = 0.4f)
                            )
                        ) {
                            if (state.isSubmitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    "GỬI YÊU CẦU",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
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
            } else if (!state.hasValidPair) {
                // Màn hình giải thích khi không có cặp kỹ năng hợp lệ (DEC-028, TASK-083)
                val isAllActive = state.pairs.isNotEmpty() && state.pairs.none { !it.isActive }
                NoValidPairContent(
                    receiverName = state.receiverProfile?.fullName ?: "Đối tác",
                    isAllActive = isAllActive,
                    onNavigateToSkillSelection = onNavigateToSkillSelection
                )
            } else {
                // Form tạo yêu cầu khi có cặp kỹ năng hợp lệ
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Cảnh báo mềm khi đối phương từng từ chối/hủy gần đây (DEC-028, TASK-083)
                    if (state.recentlyRejected) {
                        SoftWarningBanner(
                            message = "Yêu cầu trao đổi gần nhất với người này đã bị từ chối/hủy. Hãy gửi tin nhắn trao đổi rõ ràng hơn để tăng cơ hội kết nối nhé!"
                        )
                    }

                    // 1. Tóm tắt thông tin đối tác
                    ReceiverSummaryCard(
                        fullName = state.receiverProfile?.fullName ?: "Đối tác",
                        avatarUrl = state.receiverProfile?.avatarUrl,
                        city = state.receiverProfile?.city
                    )

                    // 2. Chọn cặp kỹ năng trao đổi (DEC-028, TASK-083)
                    if (state.pairs.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "Cặp kỹ năng trao đổi",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = TextPrimaryLight
                                )
                                Text(
                                    "Chọn cặp kỹ năng (Bạn dạy ↔ Bạn học):",
                                    fontSize = 13.sp,
                                    color = TextSecondaryLight,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                                )
                                state.pairs.forEach { pair ->
                                    val selected = pair.skillOfferedId == state.selectedSkillOfferedId &&
                                            pair.skillWantedId == state.selectedSkillWantedId
                                    PairSelectableRow(
                                        pair = pair,
                                        selected = selected,
                                        onClick = {
                                            if (!pair.isActive) {
                                                viewModel.onIntent(
                                                    BookingRequestIntent.SelectPair(
                                                        pair.skillOfferedId,
                                                        pair.skillWantedId
                                                    )
                                                )
                                            }
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }
                    } else {
                        // Fallback theo danh sách teach/learn riêng lẻ nếu không có data cặp
                        // Kỹ năng bạn dạy (User's HAVE)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "Kỹ năng bạn sẽ dạy",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = TextPrimaryLight
                                )
                                Text(
                                    "Chọn từ danh sách kỹ năng bạn có mà đối phương muốn học:",
                                    fontSize = 13.sp,
                                    color = TextSecondaryLight,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                                )
                                state.validTeachSkills.forEach { skill ->
                                    val selected = skill.skillId == state.selectedSkillOfferedId
                                    SkillSelectableRow(
                                        skillName = skill.skillName,
                                        proficiencyLevel = skill.proficiencyLevel,
                                        selected = selected,
                                        onClick = {
                                            viewModel.onIntent(BookingRequestIntent.SelectSkillOffered(skill.skillId))
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }

                        // Kỹ năng bạn học (Receiver's HAVE)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "Kỹ năng bạn muốn học",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = TextPrimaryLight
                                )
                                Text(
                                    "Chọn từ danh sách kỹ năng đối phương có mà bạn muốn học:",
                                    fontSize = 13.sp,
                                    color = TextSecondaryLight,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                                )
                                state.validLearnSkills.forEach { skill ->
                                    val selected = skill.skillId == state.selectedSkillWantedId
                                    SkillSelectableRow(
                                        skillName = skill.skillName,
                                        proficiencyLevel = skill.proficiencyLevel,
                                        selected = selected,
                                        onClick = {
                                            viewModel.onIntent(BookingRequestIntent.SelectSkillWanted(skill.skillId))
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }
                    }

                    // 4. Lịch hẹn (Ngày & Giờ)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Thời gian trao đổi",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = TextPrimaryLight
                            )
                            Text(
                                "Lịch hẹn bắt buộc phải ở tương lai:",
                                fontSize = 13.sp,
                                color = TextSecondaryLight,
                                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Chọn ngày
                                val cal = Calendar.getInstance().apply {
                                    timeInMillis = state.scheduledDateEpochMs
                                }
                                val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
                                val dateStr = dateFormatter.format(Date(state.scheduledDateEpochMs))

                                OutlinedCard(
                                    onClick = {
                                        val nowCal = Calendar.getInstance()
                                        val dialog = DatePickerDialog(
                                            context,
                                            { _, y, m, d ->
                                                val selectedCal = Calendar.getInstance().apply {
                                                    set(Calendar.YEAR, y)
                                                    set(Calendar.MONTH, m)
                                                    set(Calendar.DAY_OF_MONTH, d)
                                                }
                                                viewModel.onIntent(BookingRequestIntent.SetScheduledDate(selectedCal.timeInMillis))
                                            },
                                            cal.get(Calendar.YEAR),
                                            cal.get(Calendar.MONTH),
                                            cal.get(Calendar.DAY_OF_MONTH)
                                        )
                                        dialog.datePicker.minDate = nowCal.timeInMillis
                                        dialog.show()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = Brand500)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text("Ngày", fontSize = 11.sp, color = TextSecondaryLight)
                                            Text(dateStr, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        }
                                    }
                                }

                                // Chọn giờ
                                val timeStr = String.format("%02d:%02d", state.scheduledHour, state.scheduledMinute)
                                OutlinedCard(
                                    onClick = {
                                        TimePickerDialog(
                                            context,
                                            { _, h, m ->
                                                viewModel.onIntent(BookingRequestIntent.SetScheduledTime(h, m))
                                            },
                                            state.scheduledHour,
                                            state.scheduledMinute,
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
                                            .padding(horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.AccessTime, contentDescription = null, tint = Brand500)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text("Giờ", fontSize = 11.sp, color = TextSecondaryLight)
                                            Text(timeStr, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 5. Thời lượng (30/60/90/120)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Thời lượng buổi trao đổi",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = TextPrimaryLight
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(30, 60, 90, 120).forEach { mins ->
                                    val isSelected = state.durationMinutes == mins
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { viewModel.onIntent(BookingRequestIntent.SetDuration(mins)) },
                                        label = { Text("$mins phút") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Brand500.copy(alpha = 0.15f),
                                            selectedLabelColor = Brand500
                                        ),
                                        modifier = Modifier.height(48.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 6. Hình thức gặp
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Hình thức gặp",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = TextPrimaryLight
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    MeetingMode.ONLINE to "Online",
                                    MeetingMode.IN_PERSON to "Trực tiếp",
                                    MeetingMode.UNDECIDED to "Linh hoạt"
                                ).forEach { (mode, label) ->
                                    val isSelected = state.meetingMode == mode
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { viewModel.onIntent(BookingRequestIntent.SetMeetingMode(mode)) },
                                        label = { Text(label) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Brand500.copy(alpha = 0.15f),
                                            selectedLabelColor = Brand500
                                        ),
                                        modifier = Modifier.height(48.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 7. Lời nhắn tùy chọn
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Lời nhắn (tùy chọn)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = TextPrimaryLight
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = state.message,
                                onValueChange = { viewModel.onIntent(BookingRequestIntent.SetMessage(it)) },
                                placeholder = { Text("Chào bạn, mình rất muốn trao đổi kỹ năng...") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3,
                                maxLines = 5,
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(60.dp))
                }
            }
        }
    }
}

@Composable
private fun ReceiverSummaryCard(
    fullName: String,
    avatarUrl: String?,
    city: String?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEFF6FF)),
                contentAlignment = Alignment.Center
            ) {
                if (!avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = fullName,
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
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = fullName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = TextPrimaryLight
                )
                if (!city.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Địa điểm",
                            modifier = Modifier.size(14.dp),
                            tint = TextSecondaryLight
                        )
                        Text(
                            text = city,
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
private fun SkillSelectableRow(
    skillName: String,
    proficiencyLevel: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Color(0xFFEFF6FF) else Color(0xFFF8FAFC),
        border = if (selected) androidx.compose.foundation.BorderStroke(1.5.dp, Brand500) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = skillName,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 15.sp,
                    color = if (selected) Brand500 else TextPrimaryLight
                )
                Text(
                    text = "Trình độ: $proficiencyLevel/5",
                    fontSize = 12.sp,
                    color = TextSecondaryLight
                )
            }

            if (selected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Đã chọn",
                    tint = Brand500,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun SoftWarningBanner(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Cảnh báo nhẹ",
                tint = Color(0xFFD97706),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = message,
                fontSize = 13.sp,
                color = Color(0xFF92400E),
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun PairSelectableRow(
    pair: ValidSkillPair,
    selected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = when {
        pair.isActive -> Color(0xFFE2E8F0)
        selected -> Brand500
        else -> Color(0xFFE2E8F0)
    }
    val backgroundColor = when {
        pair.isActive -> Color(0xFFF1F5F9)
        selected -> Brand500.copy(alpha = 0.08f)
        else -> Color.White
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable(enabled = !pair.isActive, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Dạy: ${pair.skillOfferedName} ↔ Học: ${pair.skillWantedName}",
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 14.sp,
                color = if (pair.isActive) Color(0xFF94A3B8) else TextPrimaryLight
            )
            if (pair.isActive) {
                Text(
                    text = "Đã có yêu cầu đang mở",
                    fontSize = 12.sp,
                    color = Color(0xFFEF4444),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 2.dp)
                )
            } else if (pair.completedCount > 0) {
                Text(
                    text = "Đã trao đổi ${pair.completedCount} lần (có thể trao đổi tiếp)",
                    fontSize = 12.sp,
                    color = Color(0xFF15803D),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        if (selected && !pair.isActive) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "Đã chọn",
                tint = Brand500,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun NoValidPairContent(
    receiverName: String,
    isAllActive: Boolean = false,
    onNavigateToSkillSelection: () -> Unit
) {
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
                .background(Color(0xFFFEF3C7)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = Color(0xFFD97706),
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = if (isAllActive) "Tất cả cặp kỹ năng đang có yêu cầu" else "Chưa có cặp kỹ năng phù hợp hai chiều",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = TextPrimaryLight
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = if (isAllActive) {
                "Tất cả cặp kỹ năng phù hợp giữa bạn và $receiverName hiện đều đang có yêu cầu trao đổi đang xử lý (chờ phản hồi hoặc đã chấp nhận). Vui lòng kiểm tra lại tại tab Trao đổi hoặc quay lại sau."
            } else {
                "Để gửi yêu cầu trao đổi với $receiverName, bạn cần có kỹ năng đối phương muốn học, đồng thời đối phương có kỹ năng bạn muốn học (với mức độ thành thạo đạt yêu cầu)."
            },
            fontSize = 14.sp,
            color = TextSecondaryLight,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onNavigateToSkillSelection,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Brand500)
        ) {
            Text(
                "CẬP NHẬT KỸ NĂNG CỦA TÔI",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color.White
            )
        }
    }
}
