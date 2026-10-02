package com.skillexchange.app.presentation.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skillexchange.app.R
import com.skillexchange.app.core.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@Composable
fun OtpVerificationScreen(
    email: String,
    onNavigateToHome: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: AuthViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { visible = true }
    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is AuthEffect.NavigateToHome -> onNavigateToHome()
                is AuthEffect.ShowError      -> snackbarHostState.showSnackbar(effect.message)
                else -> {}
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
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(64.dp))

            AnimatedVisibility(visible = visible, enter = fadeIn() + slideInVertically { -40 }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = "Logo",
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        "Xác thực OTP",
                        fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextPrimaryLight
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Mã xác thực đã được gửi đến",
                        fontSize = 14.sp, color = TextSecondaryLight
                    )
                    Text(
                        email,
                        fontSize = 14.sp, color = Brand500, fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // ── OTP Card ──────────────────────────────────────────────
            AnimatedVisibility(visible = visible, enter = fadeIn() + slideInVertically { 60 }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    OtpInputField(
                        otp = state.otp,
                        onOtpChange = { viewModel.onIntent(AuthIntent.OtpChanged(it)) }
                    )

                    if (state.error != null) {
                        Text(
                            state.error!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Confirm button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth().height(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (!state.isLoading && state.otp.length >= 6)
                                    Brush.horizontalGradient(listOf(GradientStart, Brand500, GradientMid))
                                else Brush.horizontalGradient(listOf(Color(0xFFCBD5E1), Color(0xFFCBD5E1)))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = { viewModel.onIntent(AuthIntent.SubmitOtp(email)) },
                            enabled = !state.isLoading && state.otp.length >= 6,
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent
                            ),
                            elevation = ButtonDefaults.buttonElevation(0.dp)
                        ) {
                            if (state.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = Color.White, strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    "Xác nhận",
                                    fontWeight = FontWeight.Bold, fontSize = 16.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // Back / Resend row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = onNavigateBack) {
                            Text("← Quay lại", color = TextSecondaryLight, fontSize = 14.sp)
                        }
                        TextButton(onClick = { /* TODO: resend */ }) {
                            Text("Gửi lại mã", color = Brand500, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 6–8 ô nhập OTP — BasicTextField transparent overlay, tự focus khi vào màn hình.
 * Light Theme: ô nền xám nhẹ, border xanh khi focus.
 */
@Composable
fun OtpInputField(
    otp: String,
    onOtpChange: (String) -> Unit,
    maxLength: Int = 8
) {
    val focusRequester = remember { FocusRequester() }

    Box(
        modifier = Modifier.fillMaxWidth().height(64.dp)
    ) {
        // Visual boxes
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            repeat(maxLength) { index ->
                val char = otp.getOrNull(index)?.toString() ?: ""
                val isFocused = index == otp.length

                Box(
                    modifier = Modifier
                        .weight(1f).fillMaxHeight()
                        .background(
                            if (char.isNotEmpty()) Color(0xFFEFF6FF) else Color(0xFFF8FAFF),
                            RoundedCornerShape(10.dp)
                        )
                        .border(
                            width = if (isFocused) 2.dp else 1.dp,
                            color = when {
                                isFocused       -> Brand500
                                char.isNotEmpty() -> Brand600
                                else            -> Color(0xFFCBD5E1)
                            },
                            shape = RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = char,
                        color = TextPrimaryLight,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Invisible overlay để nhận keyboard input
        BasicTextField(
            value = otp,
            onValueChange = { new ->
                if (new.length <= maxLength && new.all { it.isDigit() }) onOtpChange(new)
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            cursorBrush = SolidColor(Color.Transparent),
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .alpha(0f)
        )
    }

    LaunchedEffect(Unit) {
        delay(400)
        runCatching { focusRequester.requestFocus() }
    }
}
