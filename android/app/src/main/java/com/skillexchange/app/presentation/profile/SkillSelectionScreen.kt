package com.skillexchange.app.presentation.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skillexchange.app.core.ui.theme.*
import com.skillexchange.app.domain.model.SkillType
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@Composable
fun SkillSelectionScreen(
    onNavigateToHome: () -> Unit,
    viewModel: ProfileViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is ProfileEffect.ShowSnackbar   -> snackbarHostState.showSnackbar(effect.message)
                is ProfileEffect.NavigateToHome -> onNavigateToHome()
                else -> {}
            }
        }
    }

    val selectedCat = state.categories.find { it.id == state.selectedCategoryId }
        ?: state.categories.firstOrNull()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFFF8FAFF),
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 16.dp)
                    .height(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.horizontalGradient(listOf(GradientStart, Brand500, GradientMid)))
                    .clickable { onNavigateToHome() },
                contentAlignment = Alignment.Center
            ) {
                Text("Hoàn thành →", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Kỹ năng của tôi",
                    fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimaryLight)
                Text("Chọn kỹ năng bạn có hoặc muốn học",
                    fontSize = 14.sp, color = TextSecondaryLight)
                Spacer(modifier = Modifier.height(8.dp))
            }

            // HAVE / WANT tab
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFE0F2FE))
                        .padding(4.dp)
                ) {
                    listOf("Kỹ năng tôi có", "Kỹ năng muốn học").forEachIndexed { index, label ->
                        val selected = state.selectedTab == index
                        Box(
                            modifier = Modifier
                                .weight(1f).height(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) Brand500 else Color.Transparent)
                                .clickable { viewModel.onIntent(ProfileIntent.SelectTab(index)) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, color = if (selected) Color.White else TextSecondaryLight,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp)
                        }
                    }
                }
            }

            // My skills (current tab)
            val tabType = if (state.selectedTab == 0) SkillType.HAVE else SkillType.WANT
            val myTabSkills = state.mySkills.filter { it.type == tabType }
            if (myTabSkills.isNotEmpty()) {
                item {
                    Text("Đã chọn (${myTabSkills.size})",
                        fontSize = 13.sp, color = TextSecondaryLight, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    myTabSkills.forEach { skill ->
                        val isSelectedForEdit = state.selectedSkillIdForEdit == skill.id
                        UserSkillChip(
                            name = skill.skillName,
                            level = skill.proficiencyLevel,
                            type = skill.type,
                            isSelected = isSelectedForEdit,
                            onClick = { viewModel.onIntent(ProfileIntent.SelectSkillForEdit(skill.id)) },
                            onRemove = { viewModel.onIntent(ProfileIntent.RemoveSkill(skill.id)) }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }

            // Proficiency slider (only for HAVE)
            if (state.selectedTab == 0) {
                item {
                    val editingSkill = state.mySkills.find { it.id == state.selectedSkillIdForEdit }
                    ProficiencySlider(
                        level = state.proficiencyLevel,
                        editingSkillName = editingSkill?.skillName,
                        onLevelChange = { viewModel.onIntent(ProfileIntent.SetProficiency(it)) }
                    )
                }
            }

            // Category picker
            item {
                Text("Danh mục", fontSize = 14.sp, color = TextSecondaryLight, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.categories) { cat ->
                        val isSelected = (state.selectedCategoryId ?: state.categories.firstOrNull()?.id) == cat.id
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSelected) Brand500 else Color.White)
                                .border(1.dp, if (isSelected) Brand500 else Color(0xFFCBD5E1), RoundedCornerShape(20.dp))
                                .clickable { viewModel.onIntent(ProfileIntent.SelectCategory(cat.id)) }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(formatCategoryLabel(cat.icon, cat.name),
                                color = if (isSelected) Color.White else TextPrimaryLight,
                                fontSize = 13.sp)
                        }
                    }
                }
            }

            // Skills in selected category
            if (selectedCat != null) {
                item {
                    Text(selectedCat.name, fontSize = 15.sp,
                        fontWeight = FontWeight.Bold, color = TextPrimaryLight)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                items(selectedCat.skills.chunked(2)) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { skill ->
                            val alreadyAdded = state.mySkills.any {
                                it.skillId == skill.id && it.type == tabType
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (alreadyAdded) Color(0xFFEFF6FF) else Color.White)
                                    .border(
                                        1.dp,
                                        if (alreadyAdded) Brand500 else Color(0xFFCBD5E1),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        if (!alreadyAdded) {
                                            viewModel.onIntent(ProfileIntent.AddSkill(skill.id))
                                        } else {
                                            val existingUserSkill = state.mySkills.find { it.skillId == skill.id && it.type == tabType }
                                            viewModel.onIntent(ProfileIntent.SelectSkillForEdit(existingUserSkill?.id))
                                        }
                                    }
                                    .padding(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(skill.name, color = TextPrimaryLight, fontSize = 13.sp,
                                        modifier = Modifier.weight(1f))
                                    if (alreadyAdded) {
                                        Icon(Icons.Default.Check, null,
                                            tint = Brand500, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                        if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

private fun formatCategoryLabel(icon: String, name: String): String {
    return if (icon.isBlank() || icon.matches(Regex("^[a-zA-Z0-9_-]+$"))) {
        name
    } else {
        "$icon $name"
    }
}

@Composable
private fun UserSkillChip(
    name: String,
    level: Int,
    type: SkillType,
    isSelected: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (type == SkillType.HAVE) Color(0xFFEFF6FF) else Color(0xFFF0FDF4))
            .border(
                width = if (isSelected) 1.5.dp else 0.dp,
                color = if (isSelected) Brand500 else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, color = TextPrimaryLight, fontSize = 13.sp, modifier = Modifier.weight(1f))
        if (type == SkillType.HAVE) {
            repeat(5) { i ->
                Box(modifier = Modifier.size(8.dp).clip(CircleShape)
                    .background(if (i < level) Brand500 else Color(0xFFCBD5E1)))
                Spacer(modifier = Modifier.width(2.dp))
            }
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Xóa kỹ năng",
                tint = TextSecondaryLight,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ProficiencySlider(
    level: Int,
    editingSkillName: String?,
    onLevelChange: (Int) -> Unit
) {
    val labels = listOf("Mới học", "Cơ bản", "Trung bình", "Thành thạo", "Chuyên gia")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Mức độ thành thạo", fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold, color = TextPrimaryLight)
                if (editingSkillName != null) {
                    Text("Đang chỉnh: $editingSkillName", fontSize = 12.sp, color = Brand500, fontWeight = FontWeight.Medium)
                }
            }
            Text(labels.getOrNull(level - 1) ?: "",
                fontSize = 13.sp, color = Brand500, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Slider(
            value = level.toFloat(),
            onValueChange = { onLevelChange(it.toInt()) },
            valueRange = 1f..5f,
            steps = 3,
            colors = SliderDefaults.colors(
                thumbColor = Brand500,
                activeTrackColor = Brand500,
                inactiveTrackColor = Color(0xFFCBD5E1)
            )
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("1", fontSize = 11.sp, color = TextSecondaryLight)
            Text("5", fontSize = 11.sp, color = TextSecondaryLight)
        }
    }
}
