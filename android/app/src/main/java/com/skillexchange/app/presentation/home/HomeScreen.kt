package com.skillexchange.app.presentation.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skillexchange.app.R
import com.skillexchange.app.core.ui.theme.*
import com.skillexchange.app.core.ui.util.CategoryIconHelper
import com.skillexchange.app.domain.model.SkillCategory
import com.skillexchange.app.presentation.discovery.UserCardItem
import com.skillexchange.app.presentation.discovery.formatCategoryLabel
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    onNavigateToProfileDetail: (String) -> Unit = {},
    onNavigateToSkillGraph: () -> Unit = {},
    onNavigateToProfileSetup: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToChat: () -> Unit = {},
    onNavigateToDiscovery: () -> Unit = {},
    viewModel: HomeViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()

    // Detect reaching end of list for cursor load-more (Tất cả đối tác)
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItemIndex >= totalItems - 2
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && state.nextCursor != null && !state.isLoading && !state.isLoadingMore && state.selectedTab == HomeTab.ALL_USERS) {
            viewModel.onIntent(HomeIntent.LoadMore)
        }
    }

    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is HomeEffect.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
                is HomeEffect.NavigateToProfileDetail -> {
                    onNavigateToProfileDetail(effect.userId)
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFFF8FAFF)
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // ── Item 1: Header Banner Section ────────────────────────────────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                        .background(Brush.verticalGradient(listOf(Brand600, Brand500)))
                        .padding(horizontal = 24.dp, vertical = 24.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                                    contentDescription = "Logo",
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                                Column {
                                    Text(
                                        text = "Xin chào",
                                        fontSize = 14.sp,
                                        color = Color.White.copy(alpha = 0.85f)
                                    )
                                    Text(
                                        text = "SkillExchange",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }

                            IconButton(
                                onClick = onNavigateToChat,
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f))
                            ) {
                                BadgedBox(
                                    badge = {
                                        if (state.unreadChatCount > 0) {
                                            Badge(
                                                containerColor = Brand500,
                                                contentColor = Color.White
                                            ) {
                                                Text(
                                                    text = if (state.unreadChatCount > 99) "99+" else state.unreadChatCount.toString(),
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Chat,
                                        contentDescription = "Tin nhắn",
                                        tint = Color.White
                                    )
                                }
                            }
                        }

                        // Banner Card Inside Header
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Trao đổi kỹ năng hai chiều",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Tìm bạn học cùng tiến bộ: Lập trình, Ngoại ngữ, Thiết kế, Âm nhạc...",
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.9f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Item 2: Quick Actions Section ────────────────────────────────
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigateToProfileSetup() },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF16A34A).copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(20.dp))
                            }
                            Column {
                                Text(
                                    text = "Hồ sơ của tôi",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = TextPrimaryLight
                                )
                                Text(
                                    text = "Kỹ năng & Lịch rảnh",
                                    fontSize = 11.sp,
                                    color = TextSecondaryLight
                                )
                            }
                        }
                    }

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigateToSkillGraph() },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Brand500.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Autorenew, contentDescription = null, tint = Brand500, modifier = Modifier.size(20.dp))
                            }
                            Column {
                                Text(
                                    text = "Đổi vòng tròn",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = TextPrimaryLight
                                )
                                Text(
                                    text = "Chu trình A → B → C",
                                    fontSize = 11.sp,
                                    color = TextSecondaryLight
                                )
                            }
                        }
                    }
                }
            }

            // ── Item 3: Search Bar & Filter Section ───────────────────────────
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = state.query,
                            onValueChange = { viewModel.onIntent(HomeIntent.QueryChanged(it)) },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Tìm tên, kỹ năng (Kotlin, Tiếng Anh...)", fontSize = 13.sp, color = Color(0xFF94A3B8)) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Brand500) },
                            trailingIcon = {
                                if (state.query.isNotEmpty()) {
                                    IconButton(onClick = {
                                        viewModel.onIntent(HomeIntent.QueryChanged(""))
                                        viewModel.onIntent(HomeIntent.PerformSearch)
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
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                focusManager.clearFocus()
                                viewModel.onIntent(HomeIntent.PerformSearch)
                            })
                        )

                        IconButton(
                            onClick = { viewModel.onIntent(HomeIntent.ToggleFilterSheet) },
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (state.activeFilterCount > 0) Color(0xFFEFF6FF) else Color.White)
                                .border(1.dp, if (state.activeFilterCount > 0) Brand500 else Color(0xFFE2E8F0), RoundedCornerShape(14.dp))
                        ) {
                            BadgedBox(
                                badge = {
                                    if (state.activeFilterCount > 0) {
                                        Badge(containerColor = Brand500) {
                                            Text("${state.activeFilterCount}", color = Color.White, fontSize = 10.sp)
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Bộ lọc",
                                    tint = if (state.activeFilterCount > 0) Brand500 else TextPrimaryLight
                                )
                            }
                        }
                    }

                    // Category Chips (DEC-014: Multi-select OR, selected categories moved to front)
                    if (state.categories.isNotEmpty()) {
                        val selectedIds = state.selectedCategoryIds
                        val orderedCategories = remember(state.categories, selectedIds) {
                            state.categories.sortedWith(
                                compareByDescending<SkillCategory> { it.id in selectedIds }
                                    .thenBy { it.name }
                            )
                        }
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            item {
                                FilterChip(
                                    selected = state.selectedCategoryIds.isEmpty(),
                                    onClick = { viewModel.onIntent(HomeIntent.ClearCategories) },
                                    label = { Text("Tất cả", fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Brand500,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                            items(orderedCategories, key = { it.id }) { cat ->
                                val isSelected = state.selectedCategoryIds.contains(cat.id)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.onIntent(HomeIntent.ToggleCategory(cat.id)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = CategoryIconHelper.getCategoryIcon(cat.icon, cat.name),
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    label = { Text(cat.name, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Brand500,
                                        selectedLabelColor = Color.White,
                                        selectedLeadingIconColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // ── Item 4: Tab Toggle (Matching vs All Users) ───────────────────
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TabToggleButton(
                        text = "Gợi ý phù hợp",
                        icon = Icons.Default.Star,
                        isSelected = state.selectedTab == HomeTab.MATCHING,
                        onClick = { viewModel.onIntent(HomeIntent.SwitchTab(HomeTab.MATCHING)) },
                        modifier = Modifier.weight(1f)
                    )
                    TabToggleButton(
                        text = "Tất cả đối tác",
                        isSelected = state.selectedTab == HomeTab.ALL_USERS,
                        onClick = { viewModel.onIntent(HomeIntent.SwitchTab(HomeTab.ALL_USERS)) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ── Item 5: Loading / States / Candidate Items ───────────────────
            if (state.isLoading) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        repeat(3) {
                            ShimmerCandidateCard()
                        }
                    }
                }
            } else if (state.error != null && state.displayedUsers.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = state.error ?: "Đã xảy ra lỗi kết nối",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimaryLight
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.onIntent(HomeIntent.Retry) },
                            colors = ButtonDefaults.buttonColors(containerColor = Brand500)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Thử lại")
                        }
                    }
                }
            } else if (state.displayedUsers.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = null,
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (state.selectedTab == HomeTab.MATCHING) "Chưa có gợi ý phù hợp" else "Không tìm thấy đối tác phù hợp",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = TextPrimaryLight
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (state.selectedTab == HomeTab.MATCHING)
                                "Hãy cập nhật kỹ năng CÓ và MUỐN trong hồ sơ, hoặc chuyển sang 'Tất cả đối tác' để tìm kiếm."
                            else
                                "Thử thay đổi từ khóa hoặc đặt lại bộ lọc tìm kiếm.",
                            fontSize = 13.sp,
                            color = TextSecondaryLight
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        if (state.selectedTab == HomeTab.MATCHING) {
                            OutlinedButton(onClick = { viewModel.onIntent(HomeIntent.SwitchTab(HomeTab.ALL_USERS)) }) {
                                Text("Xem tất cả đối tác", color = Brand500)
                            }
                        } else {
                            OutlinedButton(onClick = { viewModel.onIntent(HomeIntent.ResetFilters) }) {
                                Text("Xóa bộ lọc", color = Brand500)
                            }
                        }
                    }
                }
            } else {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }
                items(state.displayedUsers, key = { it.userId }) { user ->
                    Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                        UserCardItem(
                            user = user,
                            onClick = { onNavigateToProfileDetail(user.userId) }
                        )
                    }
                }

                if (state.isLoadingMore) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Brand500,
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Filter Bottom Sheet (DEC-014: shared selectedCategoryIds) ────────────
    if (state.isFilterOpen) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.onIntent(HomeIntent.ToggleFilterSheet) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Bộ lọc đối tác", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimaryLight)
                    TextButton(onClick = { viewModel.onIntent(HomeIntent.ResetFilters) }) {
                        Text("Đặt lại", color = Brand500, fontSize = 14.sp)
                    }
                }

                // Categories multi-select in Bottom Sheet
                if (state.categories.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Danh mục kỹ năng", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryLight)
                            if (state.selectedCategoryIds.isNotEmpty()) {
                                TextButton(
                                    onClick = { viewModel.onIntent(HomeIntent.ClearCategories) },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("Xóa chọn", fontSize = 12.sp, color = Brand500)
                                }
                            }
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            state.categories.forEach { cat ->
                                val isSelected = state.selectedCategoryIds.contains(cat.id)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.onIntent(HomeIntent.ToggleCategory(cat.id)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = CategoryIconHelper.getCategoryIcon(cat.icon, cat.name),
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    label = { Text(cat.name, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Brand500,
                                        selectedLabelColor = Color.White,
                                        selectedLeadingIconColor = Color.White
                                    )
                                )
                            }
                        }
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
                                    viewModel.onIntent(HomeIntent.CitySelected(if (city == "Tất cả") null else city))
                                },
                                label = { Text(city, fontSize = 12.sp) }
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
                            onClick = { viewModel.onIntent(HomeIntent.TypeSelected(null)) },
                            label = { Text("Tất cả", fontSize = 12.sp) }
                        )
                        FilterChip(
                            selected = state.selectedType == "HAVE",
                            onClick = { viewModel.onIntent(HomeIntent.TypeSelected("HAVE")) },
                            label = { Text("Kỹ năng CÓ", fontSize = 12.sp) }
                        )
                        FilterChip(
                            selected = state.selectedType == "WANT",
                            onClick = { viewModel.onIntent(HomeIntent.TypeSelected("WANT")) },
                            label = { Text("Muốn học", fontSize = 12.sp) }
                        )
                    }
                }

                // Minimum Proficiency Slider Filter
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Trình độ tối thiểu", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryLight)
                        Text(
                            text = if (state.minProficiency != null) "Cấp độ ${state.minProficiency}+" else "Tất cả",
                            fontSize = 13.sp, color = Brand500, fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = (state.minProficiency ?: 1).toFloat(),
                        onValueChange = { viewModel.onIntent(HomeIntent.MinProficiencySelected(it.toInt())) },
                        valueRange = 1f..5f,
                        steps = 3,
                        colors = SliderDefaults.colors(thumbColor = Brand500, activeTrackColor = Brand500)
                    )
                }

                // Apply button
                Button(
                    onClick = {
                        viewModel.onIntent(HomeIntent.PerformSearch)
                        viewModel.onIntent(HomeIntent.ToggleFilterSheet)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
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
private fun TabToggleButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Brand500 else Color.White)
            .border(1.dp, if (isSelected) Brand500 else Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (isSelected) Color.White else Brand500
                )
            }
            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color.White else TextPrimaryLight
            )
        }
    }
}

@Composable
private fun ShimmerCandidateCard() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color.Gray.copy(alpha = alpha))
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(width = 140.dp, height = 16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Gray.copy(alpha = alpha))
                    )
                    Box(
                        modifier = Modifier
                            .size(width = 80.dp, height = 12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Gray.copy(alpha = alpha))
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Gray.copy(alpha = alpha))
            )
        }
    }
}
