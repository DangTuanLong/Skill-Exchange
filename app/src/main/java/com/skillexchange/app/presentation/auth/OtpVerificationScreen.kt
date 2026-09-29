package com.skillexchange.app.presentation.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skillexchange.app.core.ui.theme.Brand500
import com.skillexchange.app.core.ui.theme.Brand600
import com.skillexchange.app.core.ui.theme.DarkBackground
import com.skillexchange.app.core.ui.theme.DarkSurface
import com.skillexchange.app.core.ui.theme.DarkSurface2
import com.skillexchange.app.core.ui.theme.DarkSurface3
import com.skillexchange.app.core.ui.theme.TextSecondary
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
        containerColor = DarkBackground
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF0E1520), DarkBackground)
                    )
                )
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(80.dp))

                AnimatedVisibility(visible = visible, enter = fadeIn() + slideInVertically { -40 }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // OTP Icon
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .background(
                                    Brush.linearGradient(listOf(Brand500, Brand600)),
                                    RoundedCornerShape(24.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✉", fontSize = 36.sp)
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        Text(
                            "Xác thực OTP",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Mã xác thực OTP đã được gửi đến",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Text(
                            email,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Brand500,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))

                // ── OTP Input Boxes ─────────────────────────────────
                AnimatedVisibility(visible = visible, enter = fadeIn() + slideInVertically { 60 }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkSurface, RoundedCornerShape(24.dp))
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        // 8 OTP digit boxes
                        OtpInputField(
                            otp = state.otp,
                            otpLength = 8,
                            onOtpChange = { viewModel.onIntent(AuthIntent.OtpChanged(it)) }
                        )

                        if (state.error != null) {
                            Text(
                                text = state.error!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Button(
                            onClick = { viewModel.onIntent(AuthIntent.SubmitOtp(email)) },
                            enabled = !state.isLoading && state.otp.length in 6..8,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Brand500,
                                disabledContainerColor = DarkSurface2
                            )
                        ) {
                            if (state.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Xác nhận", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            }
                        }

                        // Resend / Back
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(onClick = onNavigateBack) {
                                Text("← Quay lại", color = TextSecondary, fontSize = 14.sp)
                            }
                            TextButton(onClick = { /* TODO: resend OTP */ }) {
                                Text("Gửi lại mã", color = Brand500, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 8-ô nhập OTP — BasicTextField transparent overlay toàn hàng,
 * tap bất kỳ đâu trên OTP area là keyboard hiện lên.
 */
@Composable
fun OtpInputField(
    otp: String,
    otpLength: Int = 8,
    onOtpChange: (String) -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
    ) {
        // Visual boxes on top
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            repeat(otpLength) { index ->
                val char = otp.getOrNull(index)?.toString() ?: ""
                val isFocused = index == otp.length

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(DarkSurface2, RoundedCornerShape(10.dp))
                        .border(
                            width = if (isFocused) 2.dp else 1.dp,
                            color = when {
                                isFocused   -> Brand500
                                char.isNotEmpty() -> Brand600
                                else        -> DarkSurface3
                            },
                            shape = RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = char,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Invisible BasicTextField — đặt TRÊN visual boxes để nhận input
        BasicTextField(
            value = otp,
            onValueChange = { new ->
                if (new.length <= otpLength && new.all { it.isDigit() }) onOtpChange(new)
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            cursorBrush = SolidColor(Color.Transparent),
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .alpha(0f)
        )
    }

    // Auto-request focus khi screen hiện
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(400)
        runCatching { focusRequester.requestFocus() }
    }
}


