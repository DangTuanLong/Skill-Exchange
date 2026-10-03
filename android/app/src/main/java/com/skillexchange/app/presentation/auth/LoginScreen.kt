package com.skillexchange.app.presentation.auth

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skillexchange.app.R
import com.skillexchange.app.core.ui.theme.*
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@Composable
fun LoginScreen(
    onNavigateToRegister: () -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToProfileSetup: () -> Unit = onNavigateToHome,
    viewModel: AuthViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { visible = true }
    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is AuthEffect.NavigateToHome -> onNavigateToHome()
                is AuthEffect.NavigateToProfileSetup -> onNavigateToProfileSetup()
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp)
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(64.dp))

            // ── Logo + Title ───────────────────────────────────────────
            AnimatedVisibility(visible = visible, enter = fadeIn() + slideInVertically { -40 }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = "SkillExchange Logo",
                        modifier = Modifier.size(88.dp)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        "Chào mừng trở lại!",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryLight
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Đăng nhập để tiếp tục",
                        fontSize = 15.sp,
                        color = TextSecondaryLight,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // ── Form Card ──────────────────────────────────────────────
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
                        value = state.email,
                        onValueChange = { viewModel.onIntent(AuthIntent.EmailChanged(it)) },
                        label = "Email",
                        leadingIcon = { Icon(Icons.Default.Email, null, tint = Brand500) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email, imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        )
                    )

                    AuthTextField(
                        value = state.password,
                        onValueChange = { viewModel.onIntent(AuthIntent.PasswordChanged(it)) },
                        label = "Mật khẩu",
                        leadingIcon = { Icon(Icons.Default.Lock, null, tint = Brand500) },
                        visualTransformation = if (state.passwordVisible)
                            VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { viewModel.onIntent(AuthIntent.TogglePasswordVisibility) }) {
                                Icon(
                                    if (state.passwordVisible) Icons.Default.Visibility
                                    else Icons.Default.VisibilityOff,
                                    null, tint = TextSecondaryLight
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password, imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            viewModel.onIntent(AuthIntent.SubmitLogin)
                        })
                    )

                    if (state.error != null) {
                        Text(
                            state.error!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Gradient button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (!state.isLoading)
                                    Brush.horizontalGradient(listOf(GradientStart, Brand500, GradientMid))
                                else Brush.horizontalGradient(listOf(Color(0xFFCBD5E1), Color(0xFFCBD5E1)))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                viewModel.onIntent(AuthIntent.SubmitLogin)
                            },
                            enabled = !state.isLoading,
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
                                    "Đăng nhập",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Chưa có tài khoản?", color = TextSecondaryLight, fontSize = 14.sp)
                TextButton(onClick = onNavigateToRegister) {
                    Text("Đăng ký ngay", color = Brand500, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
