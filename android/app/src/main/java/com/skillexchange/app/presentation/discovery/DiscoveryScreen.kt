package com.skillexchange.app.presentation.discovery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.skillexchange.app.core.ui.theme.*
import com.skillexchange.app.domain.model.SkillType
import com.skillexchange.app.domain.model.UserDiscovery
import com.skillexchange.app.domain.model.UserSkill
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryScreen(
    onNavigateToProfileDetail: (String) -> Unit = {},
    viewModel: DiscoveryViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is DiscoveryEffect.ShowSnackbar            -> snackbarHostState.showSnackbar(effect.message)
                is DiscoveryEffect.NavigateToProfileDetail -> onNavigateToProfileDetail(effect.userId)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFFF8FAFF)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Header Bar & Search Box
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Khám phá kỹ năng 🔍",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryLight
                        )
                        Text(
                            text = "Tìm kiếm đối tác kết nối & trao đổi",
                            fontSize = 13.sp,
                            color = TextSecondaryLight
                        )
                    }
                    IconButton(
                        onClick = { viewModel.onIntent(DiscoveryIntent.ToggleFilterSheet) },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (state.selectedCity != null || state.minProficiency != null || state.selectedType != null) Color(0xFFEFF6FF) else Color(0xFFF1F5F9))
                    ) {
                        BadgedBox(
                            badge = {
                                if (state.selectedCity != null || state.minProficiency != null || state.selectedType != null) {
                                    Badge(containerColor = Brand500)
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Filter",
                                tint = if (state.selectedCity != null || state.minProficiency != null || state.selectedType != null) Brand500 else TextPrimaryLight
                            )
                        }
                    }
                }

                // Search Input Field
                OutlinedTextField(
                    value = state.query,
                    onValueChange = {
                        viewModel.onIntent(DiscoveryIntent.QueryChanged(it))
                        viewModel.onIntent(DiscoveryIntent.PerformSearch)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Tìm tên, kỹ năng (Android, Guitar, Tiếng Anh...)", fontSize = 14.sp, color = Color(0xFF94A3B8)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Brand500) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = {
                                viewModel.onIntent(DiscoveryIntent.QueryChanged(""))
                                viewModel.onIntent(DiscoveryIntent.PerformSearch)
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextSecondaryLight)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Brand500,
                        unfocusedBorderColor = Color(0xFFE2E8F0),
                        focusedContainerColor = Color(0xFFF8FAFF),
                        unfocusedContainerColor = Color(0xFFF8FAFF)
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        focusManager.clearFocus()
                        viewModel.onIntent(DiscoveryIntent.PerformSearch)
                    })
                )

                // Category Filter Chips Row
                if (state.categories.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            FilterChip(
                                selected = state.selectedCategoryId == null,
                                onClick = { viewModel.onIntent(DiscoveryIntent.CategorySelected(null)) },
                                label = { Text("Tất cả") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Brand500,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                        items(state.categories) { cat ->
                            val isSelected = state.selectedCategoryId == cat.id
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.onIntent(DiscoveryIntent.CategorySelected(cat.id)) },
                                label = { Text("${cat.icon} ${cat.name}") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Brand500,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
            }

            // Results List
            Box(modifier = Modifier.fillMaxSize()) {
                if (state.isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = Brand500,
                        trackColor = Color(0xFFE2E8F0)
                    )
                }

                if (state.usersList.isEmpty() && !state.isLoading) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = null,
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Không tìm thấy người dùng phù hợp",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = TextPrimaryLight
                        )
                        Text(
                            text = "Thử thay đổi từ khóa hoặc bộ lọc tìm kiếm",
                            fontSize = 13.sp,
                            color = TextSecondaryLight
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(onClick = { viewModel.onIntent(DiscoveryIntent.ResetFilters) }) {
                            Text("Xóa bộ lọc", color = Brand500)
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(state.usersList) { user ->
                            UserCardItem(
                                user = user,
                                onClick = { onNavigateToProfileDetail(user.userId) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Filter Bottom Sheet
    if (state.isFilterOpen) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.onIntent(DiscoveryIntent.ToggleFilterSheet) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Bộ lọc nâng cao", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimaryLight)
                    TextButton(onClick = { viewModel.onIntent(DiscoveryIntent.ResetFilters) }) {
                        Text("Đặt lại", color = Brand500, fontSize = 14.sp)
                    }
                }

                // City filter
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Thành phố", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryLight)
                    val cities = listOf("Tất cả", "Hà Nội", "TP.HCM", "Đà Nẵng", "Cần Thơ", "Hải Phòng")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(cities) { city ->
                            val selected = (state.selectedCity == null && city == "Tất cả") || state.selectedCity == city
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    viewModel.onIntent(DiscoveryIntent.CitySelected(if (city == "Tất cả") null else city))
                                },
                                label = { Text(city) }
                            )
                        }
                    }
                }

                // Skill Type Filter (HAVE / WANT)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Loại kỹ năng", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryLight)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.selectedType == null,
                            onClick = { viewModel.onIntent(DiscoveryIntent.TypeSelected(null)) },
                            label = { Text("Tất cả") }
                        )
                        FilterChip(
                            selected = state.selectedType == "HAVE",
                            onClick = { viewModel.onIntent(DiscoveryIntent.TypeSelected("HAVE")) },
                            label = { Text("Kỹ năng có (HAVE)") }
                        )
                        FilterChip(
                            selected = state.selectedType == "WANT",
                            onClick = { viewModel.onIntent(DiscoveryIntent.TypeSelected("WANT")) },
                            label = { Text("Muốn học (WANT)") }
                        )
                    }
                }

                // Minimum Proficiency Slider Filter
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Mức độ thành thạo tối thiểu", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryLight)
                        Text(
                            text = if (state.minProficiency != null) "Level ${state.minProficiency}+" else "Tất cả",
                            fontSize = 13.sp, color = Brand500, fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = (state.minProficiency ?: 1).toFloat(),
                        onValueChange = { viewModel.onIntent(DiscoveryIntent.MinProficiencySelected(it.toInt())) },
                        valueRange = 1f..5f,
                        steps = 3,
                        colors = SliderDefaults.colors(thumbColor = Brand500, activeTrackColor = Brand500)
                    )
                }

                // Apply button
                Button(
                    onClick = {
                        viewModel.onIntent(DiscoveryIntent.PerformSearch)
                        viewModel.onIntent(DiscoveryIntent.ToggleFilterSheet)
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Brand500)
                ) {
                    Text("Áp dụng bộ lọc", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun UserCardItem(
    user: UserDiscovery,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // User Top Info Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEFF6FF)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!user.avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = user.avatarUrl,
                            contentDescription = user.fullName,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = user.fullName.take(1).uppercase(),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Brand500
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user.fullName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryLight
                    )
                    if (!user.city.isNullOrBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = Brand500,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = user.city,
                                fontSize = 12.sp,
                                color = TextSecondaryLight
                            )
                        }
                    }
                }
            }

            if (!user.bio.isNullOrBlank()) {
                Text(
                    text = user.bio,
                    fontSize = 13.sp,
                    color = TextSecondaryLight,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Skills breakdown chips
            val haveSkills = user.skills.filter { it.type == SkillType.HAVE }
            val wantSkills = user.skills.filter { it.type == SkillType.WANT }

            if (haveSkills.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Có thể chia sẻ:", fontSize = 11.sp, color = Brand600, fontWeight = FontWeight.Bold)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        haveSkills.take(4).forEach { s ->
                            SkillBadgeChip(s.skillName, s.proficiencyLevel, isHave = true)
                        }
                    }
                }
            }

            if (wantSkills.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Muốn học hỏi:", fontSize = 11.sp, color = Color(0xFF16A34A), fontWeight = FontWeight.Bold)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        wantSkills.take(4).forEach { s ->
                            SkillBadgeChip(s.skillName, s.proficiencyLevel, isHave = false)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SkillBadgeChip(name: String, level: Int, isHave: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isHave) Color(0xFFEFF6FF) else Color(0xFFF0FDF4))
            .border(0.5.dp, if (isHave) Brand500 else Color(0xFF16A34A), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = name,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (isHave) Brand600 else Color(0xFF15803D)
            )
            if (isHave) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Lvl $level",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Brand500
                )
            }
        }
    }
}
