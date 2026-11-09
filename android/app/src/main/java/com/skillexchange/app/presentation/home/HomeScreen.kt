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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.skillexchange.app.presentation.discovery.PotentialMatchCard
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
    val state by viewModel.state.collectAsStateWithLifecycle()
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

    DisposableEffect(Unit) {
        onDispose {
            viewModel.onIntent(HomeIntent.ClearPendingUndo)
        }
    }

    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is HomeEffect.ShowSnackbar -> {
                    val result = snackbarHostState.showSnackbar(
                        message = effect.message,
                        actionLabel = effect.actionLabel,
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.onIntent(HomeIntent.UndoAddSkill)
                    } else {
                        viewModel.onIntent(HomeIntent.ClearPendingUndo)
                    }
                }
                is HomeEffect.NavigateToProfileDetail -> {
                    onNavigateToProfileDetail(effect.userId)
                }
            }
        }
    }

    // Confirmation dialog for adding potential skill to WANT (TASK-082)
    state.pendingSkillToAdd?.let { skill ->
        AlertDialog(
            onDismissRequest = { viewModel.onIntent(HomeIntent.DismissAddSkillDialog) },
            title = {
                Text(
                    text = "Thêm kỹ năng mong muốn",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "Bạn có muốn thêm kỹ năng \"${skill.skillName}\" vào danh sách mong muốn học không?\n(Mục tiêu: 1/5, có thể chỉnh sau trong Quản lý kỹ năng)",
                    fontSize = 14.sp,
                    color = TextSecondaryLight
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.onIntent(HomeIntent.ConfirmAddSkill) },
                    colors = ButtonDefaults.buttonColors(containerColor = Brand500)
                ) {
                    Text("Thêm kỹ năng")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onIntent(HomeIntent.DismissAddSkillDialog) }) {
                    Text("Hủy")
                }
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFFF8FAFF)
    ) { _ ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // ── Item 1: Condensed TopBar with Search, Đổi vòng tròn, Tin nhắn ──
            item(key = "home_top_bar", contentType = "top_bar") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                        .background(Brush.verticalGradient(listOf(Brand600, Brand500)))
                        .statusBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. Search Bar taking majority of width
                        OutlinedTextField(
                            value = state.query,
                            onValueChange = { viewModel.onIntent(HomeIntent.QueryChanged(it)) },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            placeholder = {
                                Text(
                                    "Tìm kiếm đối tác, kỹ năng...",
                                    fontSize = 13.sp,
                                    color = Color(0xFF94A3B8),
                                    maxLines = 1
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = "Tìm kiếm",
                                    tint = Brand500,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            trailingIcon = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(end = 4.dp)
                                ) {
                                    if (state.query.isNotEmpty()) {
                                        IconButton(
                                            onClick = {
                                                viewModel.onIntent(HomeIntent.QueryChanged(""))
                                                viewModel.onIntent(HomeIntent.PerformSearch)
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Clear,
                                                contentDescription = "Clear",
                                                tint = TextSecondaryLight,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { viewModel.onIntent(HomeIntent.ToggleFilterSheet) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        BadgedBox(
                                            badge = {
                                                if (state.activeFilterCount > 0) {
                                                    Badge(containerColor = Brand500) {
                                                        Text(
                                                            "${state.activeFilterCount}",
                                                            color = Color.White,
                                                            fontSize = 9.sp
                                                        )
                                                    }
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Tune,
                                                contentDescription = "Bộ lọc",
                                                tint = if (state.activeFilterCount > 0) Brand500 else TextSecondaryLight,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                focusManager.clearFocus()
                                viewModel.onIntent(HomeIntent.PerformSearch)
                            })
                        )

                        // 2. Đổi vòng tròn button
                        IconButton(
                            onClick = onNavigateToSkillGraph,
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Autorenew,
                                contentDescription = "Đổi vòng tròn",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // 3. Tin nhắn button
                        IconButton(
                            onClick = onNavigateToChat,
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f))
                        ) {
                            BadgedBox(
                                badge = {
                                    if (state.unreadChatCount > 0) {
                                        Badge(
                                            containerColor = Color(0xFFEF4444),
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
                                    imageVector = Icons.AutoMirrored.Filled.Chat,
                                    contentDescription = "Tin nhắn",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ── Item 2: Category Chips (DEC-014: Multi-select OR, selected categories moved to front) ──
            if (state.categories.isNotEmpty()) {
                item(key = "home_category_chips", contentType = "category_chips") {
                    Spacer(modifier = Modifier.height(14.dp))
                    val selectedIds = state.selectedCategoryIds
                    val orderedCategories = remember(state.categories, selectedIds) {
                        state.categories.sortedWith(
                            compareByDescending<SkillCategory> { it.id in selectedIds }
                                .thenBy { it.name }
                        )
                    }
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                        item(key = "all", contentType = "CategoryChip") {
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
                        items(orderedCategories, key = { it.id }, contentType = { "CategoryChip" }) { cat ->
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

            // ── Item 4: Tab Toggle (Matching vs All Users) ───────────────────
            item(key = "home_tab_toggle", contentType = "tab_toggle") {
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
                item(key = "home_loading", contentType = "loading") {
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
                item(key = "home_error", contentType = "error") {
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
                if (state.selectedTab == HomeTab.MATCHING && state.potentialMatches.isNotEmpty()) {
                    // Header for potential 1-way matches (TASK-082)
                    item(key = "home_potential_header", contentType = "potential_header") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                ) {
                                    Text(
                                        text = "Gợi ý một chiều",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Text(
                                    text = "Cơ hội kết nối mới",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimaryLight
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Đối tác cần kỹ năng bạn có, nhưng dạy kỹ năng bạn chưa thêm vào mong muốn. Thêm vào mong muốn để mở trao đổi 2 chiều!",
                                fontSize = 13.sp,
                                color = TextSecondaryLight
                            )
                        }
                    }

                    items(state.potentialMatches, key = { "potential_${it.userId}" }, contentType = { "PotentialCard" }) { potential ->
                        Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                            PotentialMatchCard(
                                match = potential,
                                onAddWantSkill = { skill ->
                                    viewModel.onIntent(HomeIntent.RequestAddPotentialSkill(skill))
                                },
                                onClick = { onNavigateToProfileDetail(potential.userId) }
                            )
                        }
                    }
                } else {
                    item(key = "home_empty", contentType = "empty") {
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
                                text = if (state.selectedTab == HomeTab.MATCHING)
                                    "Hiện không còn gợi ý mới phù hợp"
                                else
                                    "Không tìm thấy đối tác phù hợp",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = TextPrimaryLight
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (state.selectedTab == HomeTab.MATCHING)
                                    "Hãy tìm kiếm thêm đối tác theo tên hoặc cập nhật danh sách kỹ năng của bạn!"
                                else
                                    "Thử thay đổi từ khóa hoặc đặt lại bộ lọc tìm kiếm.",
                                fontSize = 13.sp,
                                color = TextSecondaryLight
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            if (state.selectedTab == HomeTab.MATCHING) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = { viewModel.onIntent(HomeIntent.SwitchTab(HomeTab.ALL_USERS)) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Brand500)
                                    ) {
                                        Text("Tìm kiếm đối tác")
                                    }
                                    OutlinedButton(
                                        onClick = onNavigateToProfileSetup
                                    ) {
                                        Text("Cập nhật kỹ năng", color = Brand500)
                                    }
                                }
                            } else {
                                OutlinedButton(onClick = { viewModel.onIntent(HomeIntent.ResetFilters) }) {
                                    Text("Xóa bộ lọc", color = Brand500)
                                }
                            }
                        }
                    }
                }
            } else {
                item(key = "home_top_spacer", contentType = "spacer") {
                    Spacer(modifier = Modifier.height(8.dp))
                }
                items(state.displayedUsers, key = { it.userId }, contentType = { "UserCard" }) { user ->
                    Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                        UserCardItem(
                            user = user,
                            onClick = { onNavigateToProfileDetail(user.userId) },
                            showRelationshipChip = (state.selectedTab == HomeTab.ALL_USERS)
                        )
                    }
                }

                if (state.isLoadingMore) {
                    item(key = "home_loading_more", contentType = "loading_more") {
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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        FILTER_CITIES.forEach { city ->
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
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha },
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
                        .background(Color(0xFFE2E8F0))
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(width = 140.dp, height = 16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFE2E8F0))
                    )
                    Box(
                        modifier = Modifier
                            .size(width = 80.dp, height = 12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFE2E8F0))
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFE2E8F0))
            )
        }
    }
}

private val FILTER_CITIES = listOf("Tất cả", "Hà Nội", "TP.HCM", "Đà Nẵng", "Cần Thơ", "Hải Phòng")
