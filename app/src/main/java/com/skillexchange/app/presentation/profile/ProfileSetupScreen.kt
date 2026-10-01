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
import androidx.compose.material.icons.Icons
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

    LaunchedEffect(Unit) { visible = true }
    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is ProfileEffect.NavigateToSkillSelection -> onNavigateToSkillSelection()
                is ProfileEffect.NavigateToHome           -> onNavigateToHome()
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
            Spacer(modifier = Modifier.height(36.dp))

            AnimatedVisibility(visible = visible, enter = fadeIn() + slideInVertically { -40 }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = "Logo",
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Tạo hồ sơ của bạn",
                        fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimaryLight
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Bước 1/2: Thông tin cá nhân",
                        fontSize = 14.sp, color = Brand500, fontWeight = FontWeight.SemiBold
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
                    }

                    Text("Chọn ảnh đại diện", fontSize = 13.sp, color = TextSecondaryLight)

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

                    AuthTextField(
                        value = state.city,
                        onValueChange = { viewModel.onIntent(ProfileIntent.CityChanged(it)) },
                        label = "Thành phố (VD: Hà Nội, TP.HCM)",
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                    )

                    if (state.error != null) {
                        Text(
                            state.error!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

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
                                    "Lưu & Tiếp theo →",
                                    fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White
                                )
                            }
                        }
                    }

                    TextButton(
                        onClick = onNavigateToHome,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Bỏ qua, thiết lập sau", color = TextSecondaryLight, fontSize = 14.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
