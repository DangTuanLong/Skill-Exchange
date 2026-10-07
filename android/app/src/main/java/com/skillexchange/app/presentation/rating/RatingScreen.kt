package com.skillexchange.app.presentation.rating

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.skillexchange.app.core.ui.theme.Brand500
import com.skillexchange.app.core.ui.theme.TextPrimaryLight
import com.skillexchange.app.core.ui.theme.TextSecondaryLight
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RatingScreen(
    exchangeId: String,
    onNavigateBack: () -> Unit,
    viewModel: RatingViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = androidx.compose.runtime.remember { SnackbarHostState() }

    LaunchedEffect(exchangeId) {
        viewModel.onIntent(RatingIntent.LoadExchange(exchangeId))
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is RatingEffect.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
                RatingEffect.NavigateBack -> {
                    onNavigateBack()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Đánh giá phiên trao đổi", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Quay lại"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Brand500)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // Partner Avatar & Info Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (!state.otherUserAvatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = state.otherUserAvatarUrl,
                                contentDescription = "Ảnh đại diện ${state.otherUserName}",
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(Brand500),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = state.otherUserName.take(1).uppercase(),
                                    color = Color.White,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = state.otherUserName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryLight
                        )

                        if (state.exchangeTitle.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = state.exchangeTitle,
                                fontSize = 13.sp,
                                color = TextSecondaryLight,
                                textAlign = TextAlign.Center
                            )
                        }

                        if (state.scheduledDate.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Thời gian: ${state.scheduledDate.take(10)}",
                                fontSize = 12.sp,
                                color = TextSecondaryLight
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Bạn đánh giá trải nghiệm thế nào?",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimaryLight
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 5 Tappable Stars
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    (1..5).forEach { starIndex ->
                        IconButton(
                            onClick = {
                                if (!state.isSubmitting) {
                                    viewModel.onIntent(RatingIntent.SelectScore(starIndex))
                                }
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = if (starIndex <= state.score) Icons.Default.Star else Icons.Outlined.StarOutline,
                                contentDescription = "Chọn $starIndex sao",
                                tint = if (starIndex <= state.score) Color(0xFFF59E0B) else Color(0xFFCBD5E1),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Score Text Label
                Text(
                    text = "${state.score} sao — ${state.scoreLabel}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFB45309)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Comment Text Field
                OutlinedTextField(
                    value = state.comment,
                    onValueChange = {
                        if (!state.isSubmitting) {
                            viewModel.onIntent(RatingIntent.UpdateComment(it))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nhận xét (không bắt buộc)") },
                    placeholder = { Text("Chia sẻ cảm nhận của bạn về buổi trao đổi...") },
                    minLines = 3,
                    maxLines = 5,
                    enabled = !state.isSubmitting,
                    shape = RoundedCornerShape(12.dp),
                    supportingText = {
                        Text(
                            text = "${state.comment.length}/200",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End,
                            fontSize = 12.sp,
                            color = if (state.comment.length > 200) MaterialTheme.colorScheme.error else TextSecondaryLight
                        )
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Primary CTA: "GỬI ĐÁNH GIÁ"
                Button(
                    onClick = { viewModel.onIntent(RatingIntent.SubmitRating) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    enabled = !state.isSubmitting,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Brand500)
                ) {
                    if (state.isSubmitting) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "GỬI ĐÁNH GIÁ",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Secondary CTA: "Đánh giá sau"
                TextButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    enabled = !state.isSubmitting
                ) {
                    Text(
                        text = "Đánh giá sau",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondaryLight
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
