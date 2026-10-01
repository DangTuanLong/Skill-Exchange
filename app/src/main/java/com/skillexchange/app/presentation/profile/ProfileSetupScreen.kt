package com.skillexchange.app.presentation.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skillexchange.app.R
import com.skillexchange.app.core.ui.theme.*
import com.skillexchange.app.presentation.auth.AuthTextField
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@Composable
fun ProfileSetupScreen(
    onNavigateToHome: () -> Unit,
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
                is ProfileEffect.NavigateToHome -> onNavigateToHome()
                is ProfileEffect.ShowSnackbar   -> snackbarHostState.showSnackbar(effect.message)
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp)
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(56.dp))

            AnimatedVisibility(visible = visible, enter = fadeIn() + slideInVertically { -40 }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = "Logo",
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Tạo hồ sơ của bạn",
                        fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextPrimaryLight)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Cho mọi người biết bạn là ai",
                        fontSize = 14.sp, color = TextSecondaryLight, textAlign = TextAlign.Center)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            AnimatedVisibility(visible = visible, enter = fadeIn() + slideInVertically { 60 }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
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
                        Text(state.error!!, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
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
                                CircularProgressIndicator(modifier = Modifier.size(22.dp),
                                    color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Text("Lưu & Tiếp theo →",
                                    fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
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
        }
    }
}
