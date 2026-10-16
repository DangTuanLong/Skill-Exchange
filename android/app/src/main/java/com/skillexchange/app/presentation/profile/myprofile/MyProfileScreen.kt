package com.skillexchange.app.presentation.profile.myprofile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.skillexchange.app.core.ui.theme.*
import com.skillexchange.app.domain.model.SkillType
import com.skillexchange.app.domain.model.UserSkill
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyProfileScreen(
    onNavigateToEditProfile: () -> Unit = {},
    onNavigateToManageSkills: () -> Unit = {},
    onNavigateToLogin: () -> Unit = {},
    onNavigateToDashboard: () -> Unit = {},
    viewModel: MyProfileViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showLogoutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                MyProfileEffect.NavigateToEditProfile -> onNavigateToEditProfile()
                MyProfileEffect.NavigateToManageSkills -> onNavigateToManageSkills()
                MyProfileEffect.NavigateToLogin -> onNavigateToLogin()
                is MyProfileEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Hồ sơ của tôi",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimaryLight
                    )
                },
                actions = {
                    IconButton(
                        onClick = { showLogoutDialog = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Đăng xuất",
                            tint = Color(0xFFEF4444)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFFF8FAFC)
    ) { innerPadding ->
        if (state.isLoading && state.profile == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Brand500)
            }
        } else if (state.error != null && state.profile == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = state.error ?: "Đã có lỗi xảy ra",
                        color = Color(0xFFEF4444),
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = { viewModel.onIntent(MyProfileIntent.Refresh) },
                        colors = ButtonDefaults.buttonColors(containerColor = Brand500)
                    ) {
                        Text("Thử lại")
                    }
                }
            }
        } else {
            val profile = state.profile
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── 1. Header Card (Avatar, Tên, Thành phố, Uy tín, Nút Chỉnh sửa) ─────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Avatar
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE2E8F0)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!profile?.avatarUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = profile?.avatarUrl,
                                    contentDescription = "Ảnh đại diện của tôi",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Ảnh đại diện mặc định",
                                    modifier = Modifier.size(52.dp),
                                    tint = TextSecondaryLight
                                )
                            }
                        }

                        // Full name & City
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = profile?.fullName?.ifBlank { "Chưa đặt tên" } ?: "Chưa đặt tên",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimaryLight,
                                textAlign = TextAlign.Center
                            )
                            if (!profile?.city.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = "Thành phố",
                                        modifier = Modifier.size(16.dp),
                                        tint = TextSecondaryLight
                                    )
                                    Text(
                                        text = profile?.city ?: "",
                                        fontSize = 13.sp,
                                        color = TextSecondaryLight
                                    )
                                }
                            }
                        }

                        // Reputation Badge & Score
                        val reputation = state.reputation
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val badgeText = reputation?.badge ?: "Thành viên mới"
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xFFEFF6FF),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = Color(0xFFF59E0B),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = badgeText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Brand600
                                    )
                                }
                            }

                            if (reputation?.score != null && reputation.score > 0) {
                                Text(
                                    text = "•  ${String.format("%.1f", reputation.score)} điểm",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextSecondaryLight
                                )
                            }
                        }

                        // Bio
                        if (!profile?.bio.isNullOrBlank()) {
                            Text(
                                text = profile?.bio ?: "",
                                fontSize = 13.sp,
                                color = TextSecondaryLight,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Nút phụ "Chỉnh sửa hồ sơ"
                        OutlinedButton(
                            onClick = { viewModel.onIntent(MyProfileIntent.EditProfile) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, Brand500),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Brand500)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Chỉnh sửa hồ sơ",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                // ── 2. Tóm tắt Kỹ năng HAVE & WANT kèm "Quản lý kỹ năng" ───────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Kỹ năng của tôi",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimaryLight
                            )
                            TextButton(
                                onClick = { viewModel.onIntent(MyProfileIntent.ManageSkills) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Quản lý kỹ năng",
                                    color = Brand500,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        // HAVE Skills
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "KỸ NĂNG TÔI CÓ (HAVE) • ${state.haveSkills.size}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Brand600,
                                letterSpacing = 0.5.sp
                            )
                            if (state.haveSkills.isNotEmpty()) {
                                state.haveSkills.forEach { skill ->
                                    MySkillItemRow(skill)
                                }
                            } else {
                                Text(
                                    text = "Chưa có kỹ năng chia sẻ nào",
                                    fontSize = 13.sp,
                                    color = TextSecondaryLight,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = Color(0xFFF1F5F9))

                        // WANT Skills
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "KỸ NĂNG MUỐN HỌC (WANT) • ${state.wantSkills.size}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF059669),
                                letterSpacing = 0.5.sp
                            )
                            if (state.wantSkills.isNotEmpty()) {
                                state.wantSkills.forEach { skill ->
                                    MySkillItemRow(skill)
                                }
                            } else {
                                Text(
                                    text = "Chưa có kỹ năng muốn học nào",
                                    fontSize = 13.sp,
                                    color = TextSecondaryLight,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                // ── 4. Lịch rảnh hàng tuần ─────────────────────────────────────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                tint = Brand500,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Lịch rảnh hàng tuần",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimaryLight
                            )
                        }

                        val availability = state.availability
                        if (availability.isNotEmpty()) {
                            val dayOrder = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
                            val dayLabels = mapOf(
                                "MON" to "Thứ 2", "TUE" to "Thứ 3", "WED" to "Thứ 4",
                                "THU" to "Thứ 5", "FRI" to "Thứ 6", "SAT" to "Thứ 7", "SUN" to "Chủ nhật"
                            )
                            val sorted = availability.sortedWith(
                                compareBy({ dayOrder.indexOf(it.day.uppercase()).let { idx -> if (idx >= 0) idx else 99 } }, { it.from })
                            )

                            sorted.forEach { window ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFF8FAFC))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = dayLabels[window.day.uppercase()] ?: window.day,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = Brand600
                                    )
                                    Text(
                                        text = "${window.from} - ${window.to}",
                                        fontSize = 13.sp,
                                        color = TextSecondaryLight
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "Chưa thiết lập lịch rảnh. Bấm \"Chỉnh sửa hồ sơ\" để cài đặt.",
                                fontSize = 13.sp,
                                color = TextSecondaryLight
                            )
                        }
                    }
                }

                // ── 5. Thống kê hoạt động (Dashboard) ──────────────────────────────
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToDashboard() },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Thống kê & Bảng điều khiển",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "Xem chi tiết ↗",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        // Preview indicators
                        val rep = state.reputation
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatBox(
                                label = "Buổi trao đổi",
                                value = "${rep?.totalExchanges ?: 0}",
                                modifier = Modifier.weight(1f)
                            )
                            StatBox(
                                label = "Hoàn thành",
                                value = if (rep != null) "${(rep.completionRate * 100).toInt()}%" else "100%",
                                modifier = Modifier.weight(1f)
                            )
                            StatBox(
                                label = "Đánh giá",
                                value = if (rep?.avgRating != null) "${String.format("%.1f", rep.avgRating)}★" else "Mới",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Text(
                            text = "Xem biểu đồ radar kỹ năng 6 trục, phân tích 6 tháng gần nhất, gợi ý mở rộng kết nối và top 10 bảng xếp hạng.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = {
                Text(
                    text = "Xác nhận đăng xuất",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "Bạn có chắc chắn muốn đăng xuất khỏi tài khoản không?",
                    fontSize = 14.sp,
                    color = TextSecondaryLight
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.onIntent(MyProfileIntent.Logout)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Đăng xuất", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Hủy", color = TextSecondaryLight)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun StatBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF8FAFC),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Brand600
            )
            Text(
                text = label,
                fontSize = 11.sp,
                color = TextSecondaryLight,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MySkillItemRow(skill: UserSkill) {
    val isHave = skill.type == SkillType.HAVE
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isHave) Color(0xFFF0F7FF) else Color(0xFFF0FDF4))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = skill.skillName,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimaryLight
            )
            if (skill.categoryName.isNotBlank()) {
                Text(
                    text = skill.categoryName,
                    fontSize = 11.sp,
                    color = TextSecondaryLight
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (isHave) Brand500 else Color(0xFF10B981)
        ) {
            Text(
                text = "Cấp ${skill.proficiencyLevel}/5",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}
