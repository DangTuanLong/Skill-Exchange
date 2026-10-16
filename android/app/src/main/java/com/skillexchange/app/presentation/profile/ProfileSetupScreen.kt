package com.skillexchange.app.presentation.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.skillexchange.app.R
import com.skillexchange.app.core.ui.theme.*
import com.skillexchange.app.presentation.auth.AuthTextField
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSetupScreen(
    onNavigateToSkillSelection: () -> Unit = {},
    onNavigateToHome: () -> Unit = {},
    viewModel: ProfileViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    var visible by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null) {
                    val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                    val ext = when {
                        mimeType.contains("png") -> "png"
                        mimeType.contains("webp") -> "webp"
                        else -> "jpg"
                    }
                    viewModel.onIntent(ProfileIntent.UploadAvatar(bytes, "avatar.$ext", mimeType))
                }
            } catch (e: Exception) {
                // Handled in ViewModel
            }
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null) {
                    var fileName = "document.txt"
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1 && cursor.moveToFirst()) {
                            fileName = cursor.getString(nameIndex)
                        }
                    }
                    viewModel.onIntent(ProfileIntent.UploadDocument(bytes, fileName))
                }
            } catch (e: Exception) {
                // Handled in ViewModel
            }
        }
    }

    LaunchedEffect(Unit) { visible = true }
    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is ProfileEffect.NavigateToSkillSelection -> onNavigateToSkillSelection()
                is ProfileEffect.NavigateToHome           -> onNavigateToHome()
                is ProfileEffect.OpenDocumentPicker       -> documentPickerLauncher.launch("*/*")
                is ProfileEffect.ShowSnackbar             -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    // Default preset avatar URLs
    val presetAvatars = remember {
        listOf(
            "https://api.dicebear.com/7.x/bottts/svg?seed=SkillExchange1",
            "https://api.dicebear.com/7.x/bottts/svg?seed=SkillExchange2",
            "https://api.dicebear.com/7.x/bottts/svg?seed=SkillExchange3",
            "https://api.dicebear.com/7.x/avataaars/png?seed=Alex",
            "https://api.dicebear.com/7.x/avataaars/png?seed=Sarah",
            "https://api.dicebear.com/7.x/avataaars/png?seed=David"
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFFF8FAFF)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            AnimatedVisibility(visible = visible, enter = fadeIn() + slideInVertically { -40 }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Thông tin cá nhân",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryLight
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            AnimatedVisibility(visible = visible, enter = fadeIn() + slideInVertically { 60 }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Avatar display & selection
                    Box(
                        modifier = Modifier
                            .size(92.dp)
                            .clickable(enabled = !state.isUploadingAvatar) {
                                galleryLauncher.launch("image/*")
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEFF6FF))
                                .border(2.dp, Brand500, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (state.avatarUrl.isNotBlank()) {
                                AsyncImage(
                                    model = state.avatarUrl,
                                    contentDescription = "Avatar",
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Default Avatar",
                                    tint = Brand500,
                                    modifier = Modifier.size(48.dp)
                                )
                            }

                            if (state.isUploadingAvatar) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.4f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(28.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                }
                            }
                        }

                        // Badge icon camera/edit
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Brand500)
                                .border(2.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Tải ảnh lên",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        enabled = !state.isUploadingAvatar,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Brand500
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (state.isUploadingAvatar) "Đang tải ảnh lên..." else "Tải ảnh từ thiết bị",
                            fontSize = 12.sp,
                            color = Brand500,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Text("Hoặc chọn avatar có sẵn", fontSize = 13.sp, color = TextSecondaryLight)

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(presetAvatars) { avatar ->
                            val isSelected = state.avatarUrl == avatar
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFF1F5F9))
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) Brand500 else Color(0xFFCBD5E1),
                                        shape = CircleShape
                                    )
                                    .clickable { viewModel.onIntent(ProfileIntent.AvatarUrlChanged(avatar)) },
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = avatar,
                                    contentDescription = "Avatar Preset",
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }

                    AuthTextField(
                        value = state.fullName,
                        onValueChange = { viewModel.onIntent(ProfileIntent.FullNameChanged(it)) },
                        label = "Họ và tên *",
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.clearFocus() })
                    )

                    OutlinedTextField(
                        value = state.bio,
                        onValueChange = { viewModel.onIntent(ProfileIntent.BioChanged(it)) },
                        label = { Text("Giới thiệu bản thân") },
                        placeholder = { Text("VD: Mình là developer thích học tiếng Nhật...", color = Color(0xFFCBD5E1)) },
                        minLines = 3,
                        maxLines = 5,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Brand500, unfocusedBorderColor = Color(0xFFCBD5E1),
                            focusedLabelColor = Brand500, unfocusedContainerColor = Color(0xFFF8FAFF),
                            focusedContainerColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                    ) {
                        OutlinedButton(
                            onClick = {
                                focusManager.clearFocus()
                                viewModel.onIntent(ProfileIntent.RequestDocumentUpload)
                            },
                            enabled = !state.isExtractingDocument,
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (!state.isExtractingDocument) Brand500 else Color(0xFFCBD5E1)
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            if (state.isExtractingDocument) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Brand500
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Đang tải...", fontSize = 13.sp, color = Brand500)
                            } else {
                                Text(
                                    "Tải tài liệu 📄",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Brand500
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                focusManager.clearFocus()
                                viewModel.onIntent(ProfileIntent.RequestSkillExtraction)
                            },
                            enabled = !state.isExtractingSkills && state.bio.isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (!state.isExtractingSkills && state.bio.isNotBlank()) Brand500 else Color(0xFFCBD5E1)
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            if (state.isExtractingSkills) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Brand500
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Đang phân tích...", fontSize = 13.sp, color = Brand500)
                            } else {
                                Text(
                                    "Gợi ý từ Bio 🤖",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (state.bio.isNotBlank()) Brand500 else Color(0xFF94A3B8)
                                )
                            }
                        }
                    }

                    AuthTextField(
                        value = state.city,
                        onValueChange = { viewModel.onIntent(ProfileIntent.CityChanged(it)) },
                        label = "Thành phố (VD: Hà Nội, TP.HCM)",
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                    )

                    // ── Lịch rảnh hàng tuần (Availability Editor) ─────────────
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFF8FAFC))
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(14.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                tint = Brand500,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))

                            Text(
                                "Lịch rảnh hàng tuần",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = TextPrimaryLight
                            )
                        }

                        Text(
                            "Chọn ngày và khung giờ bạn có thể trao đổi kỹ năng",
                            fontSize = 12.sp,
                            color = TextSecondaryLight
                        )

                        // Day chips
                        val days = remember {
                            listOf(
                                "MON" to "T2", "TUE" to "T3", "WED" to "T4",
                                "THU" to "T5", "FRI" to "T6", "SAT" to "T7", "SUN" to "CN"
                            )
                        }
                        var selectedDay by remember { mutableStateOf("MON") }
                        var fromTime by remember { mutableStateOf("18:00") }
                        var toTime by remember { mutableStateOf("21:00") }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(days) { (dayKey, dayLabel) ->
                                val isSelected = selectedDay == dayKey
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) Brand500 else Color.White)
                                        .border(
                                            1.dp,
                                            if (isSelected) Brand500 else Color(0xFFCBD5E1),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { selectedDay = dayKey }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = dayLabel,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else TextPrimaryLight
                                    )
                                }
                            }
                        }

                        // Time range input + Add button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = fromTime,
                                onValueChange = { if (it.length <= 5) fromTime = it },
                                label = { Text("Từ (HH:mm)", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White
                                )
                            )
                            OutlinedTextField(
                                value = toTime,
                                onValueChange = { if (it.length <= 5) toTime = it },
                                label = { Text("Đến (HH:mm)", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White
                                )
                            )
                            FilledTonalButton(
                                onClick = {
                                    focusManager.clearFocus()
                                    viewModel.onIntent(
                                        ProfileIntent.AddAvailabilityWindow(
                                            com.skillexchange.app.domain.model.AvailabilityWindow(
                                                day = selectedDay,
                                                from = fromTime.trim(),
                                                to = toTime.trim()
                                            )
                                        )
                                    )
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color(0xFFEFF6FF),
                                    contentColor = Brand600
                                )
                            ) {
                                Text("+ Thêm", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }

                        // Display selected availability chips
                        if (state.availability.isNotEmpty()) {
                            Text(
                                "Khung giờ đã chọn:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextSecondaryLight
                            )
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val dayLabelsMap = remember {
                                    mapOf("MON" to "T2", "TUE" to "T3", "WED" to "T4", "THU" to "T5", "FRI" to "T6", "SAT" to "T7", "SUN" to "CN")
                                }
                                state.availability.forEach { window ->
                                    val label = "${dayLabelsMap[window.day] ?: window.day}: ${window.from}-${window.to}"
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFFEFF6FF))
                                            .border(1.dp, Color(0xFFBAE6FD), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Brand700)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Xoá",
                                            tint = Brand500,
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clickable {
                                                    viewModel.onIntent(ProfileIntent.RemoveAvailabilityWindow(window))
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }



                    if (state.error != null) {
                        Text(
                            state.error!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Save + Continue button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth().height(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (!state.isSaving)
                                    Brush.horizontalGradient(listOf(GradientStart, Brand500, GradientMid))
                                else Brush.horizontalGradient(listOf(Color(0xFFCBD5E1), Color(0xFFCBD5E1)))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                viewModel.onIntent(ProfileIntent.SaveProfile)
                            },
                            enabled = !state.isSaving,
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent
                            ),
                            elevation = ButtonDefaults.buttonElevation(0.dp)
                        ) {
                            if (state.isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = Color.White, strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    "Quản lý kỹ năng",
                                    fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White
                                )
                            }
                        }
                    }

                    TextButton(
                        onClick = onNavigateToHome,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Trở về trang chủ", color = TextSecondaryLight, fontSize = 14.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (state.showSkillExtractionSheet) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.onIntent(ProfileIntent.DismissSkillExtractionSheet) },
            containerColor = Color.White,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Kỹ năng được phát hiện",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryLight
                        )
                        Text(
                            text = "Hãy kiểm tra lại trước khi thêm",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B),
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                    IconButton(onClick = { viewModel.onIntent(ProfileIntent.DismissSkillExtractionSheet) }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Đóng", tint = Color(0xFF64748B))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                val toAddCount = state.extractedSkills.count { it.isSelected && !it.isAlreadyAdded }

                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.extractedSkills, key = { it.skill.skillId }) { item ->
                        val (badgeText, badgeBg, badgeTextColor) = when (item.skill.matchType) {
                            "EXACT" -> Triple("Chính xác", Color(0xFFECFDF5), Color(0xFF059669))
                            "ALIAS" -> Triple("Từ đồng nghĩa", Color(0xFFEFF6FF), Color(0xFF2563EB))
                            else -> Triple("Gần đúng", Color(0xFFFFFBEB), Color(0xFFD97706))
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (item.isAlreadyAdded) Color(0xFFF8FAFC) else Color.White
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (item.isSelected && !item.isAlreadyAdded) Brand500 else Color(0xFFE2E8F0)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = item.isSelected,
                                    onCheckedChange = { viewModel.onIntent(ProfileIntent.ToggleExtractedSkillSelection(item.skill.skillId)) },
                                    enabled = !item.isAlreadyAdded,
                                    colors = CheckboxDefaults.colors(checkedColor = Brand500)
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = item.skill.skillName,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 15.sp,
                                            color = if (item.isAlreadyAdded) Color(0xFF94A3B8) else TextPrimaryLight
                                        )

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(badgeBg)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = badgeText,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = badgeTextColor
                                            )
                                        }
                                    }

                                    if (item.skill.categoryName != null) {
                                        Text(
                                            text = item.skill.categoryName,
                                            fontSize = 12.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }

                                    if (item.isAlreadyAdded) {
                                        Text(
                                            text = "(Đã có trong danh sách kỹ năng)",
                                            fontSize = 11.sp,
                                            color = Color(0xFF94A3B8),
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "Thành thạo: ${item.level}/5",
                                                fontSize = 12.sp,
                                                color = TextSecondaryLight
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                (1..5).forEach { lvl ->
                                                    val isLvlSelected = item.level == lvl
                                                    Box(
                                                        modifier = Modifier
                                                            .size(24.dp)
                                                            .clip(CircleShape)
                                                            .background(if (isLvlSelected) Brand500 else Color(0xFFF1F5F9))
                                                            .clickable {
                                                                viewModel.onIntent(ProfileIntent.SetExtractedSkillLevel(item.skill.skillId, lvl))
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = "$lvl",
                                                            fontSize = 11.sp,
                                                            fontWeight = if (isLvlSelected) FontWeight.Bold else FontWeight.Normal,
                                                            color = if (isLvlSelected) Color.White else Color(0xFF475569)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.onIntent(ProfileIntent.DismissSkillExtractionSheet) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Hủy", color = Color(0xFF64748B))
                    }

                    Button(
                        onClick = { viewModel.onIntent(ProfileIntent.ConfirmAddExtractedSkills) },
                        modifier = Modifier.weight(1.5f),
                        enabled = toAddCount > 0,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Brand500)
                    ) {
                        Text(
                            text = if (toAddCount > 0) "Thêm vào hồ sơ ($toAddCount)" else "Thêm vào hồ sơ",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (state.showDocumentPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.onIntent(ProfileIntent.DismissDocumentPrivacyDialog) },
            title = {
                Text(
                    text = "🔒 Bảo mật & Quyền riêng tư",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = TextPrimaryLight
                )
            },
            text = {
                Text(
                    text = "Tệp của bạn sẽ được gửi lên máy chủ để đọc trong bộ nhớ và trích xuất từ khóa kỹ năng. Hệ thống cam kết không lưu trữ tệp, không ghi log nội dung cá nhân và dữ liệu sẽ tự động hủy ngay sau khi xử lý.",
                    fontSize = 14.sp,
                    color = TextSecondaryLight,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.onIntent(ProfileIntent.ConfirmDocumentPrivacyDialog) },
                    colors = ButtonDefaults.buttonColors(containerColor = Brand500),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Tiếp tục chọn tệp", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.onIntent(ProfileIntent.DismissDocumentPrivacyDialog) }
                ) {
                    Text("Hủy", color = Color(0xFF64748B))
                }
            }
        )
    }
}
