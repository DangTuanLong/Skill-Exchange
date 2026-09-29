package com.skillexchange.app.presentation.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skillexchange.app.R
import com.skillexchange.app.core.ui.theme.*
import kotlinx.coroutines.launch

data class OnboardingPage(
    val emoji: String,
    val title: String,
    val description: String,
    val gradientColors: List<Color>
)

private val pages = listOf(
    OnboardingPage(
        emoji = "🤝",
        title = "Trao đổi kỹ năng",
        description = "Bạn có kỹ năng gì? Hãy chia sẻ với người khác và học lại điều bạn muốn — hoàn toàn miễn phí.",
        gradientColors = listOf(Color(0xFF0EA5E9), Color(0xFF06B6D4))
    ),
    OnboardingPage(
        emoji = "🔍",
        title = "Tìm người phù hợp",
        description = "AI gợi ý đối tác học phù hợp nhất dựa trên kỹ năng và mục tiêu của bạn.",
        gradientColors = listOf(Color(0xFF14B8A6), Color(0xFF10B981))
    ),
    OnboardingPage(
        emoji = "⭐",
        title = "Đánh giá & Phát triển",
        description = "Theo dõi hành trình học tập, đánh giá buổi trao đổi và xây dựng uy tín cộng đồng.",
        gradientColors = listOf(Color(0xFF6366F1), Color(0xFF0EA5E9))
    )
)

@Composable
fun OnboardingScreen(
    onFinish: () -> Unit
) {
    var currentPage by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Skip button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onFinish) {
                    Text("Bỏ qua", color = TextSecondary, fontSize = 14.sp)
                }
            }

            // Page content
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(300)) + slideInHorizontally { it / 3 },
                exit = fadeOut(tween(200)) + slideOutHorizontally { -it / 3 },
                modifier = Modifier.weight(1f)
            ) {
                OnboardingPageContent(page = pages[currentPage])
            }

            // Bottom section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
                    .padding(bottom = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                // Dot indicators
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pages.indices.forEach { index ->
                        val width by animateDpAsState(
                            targetValue = if (index == currentPage) 28.dp else 8.dp,
                            animationSpec = tween(300), label = "dot"
                        )
                        Box(
                            modifier = Modifier
                                .height(8.dp)
                                .width(width)
                                .clip(CircleShape)
                                .background(
                                    if (index == currentPage) Brand500 else DarkSurface3
                                )
                                .clickable { currentPage = index }
                        )
                    }
                }

                // Action button
                val isLast = currentPage == pages.size - 1
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(GradientStart, Brand500, GradientMid)
                            )
                        )
                        .clickable {
                            if (isLast) onFinish()
                            else currentPage++
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isLast) "Bắt đầu ngay →" else "Tiếp theo →",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Logo
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = "Logo",
            modifier = Modifier
                .size(100.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
                .padding(10.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Emoji icon badge
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(page.gradientColors)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(page.emoji, fontSize = 38.sp)
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = page.title,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = page.description,
            fontSize = 16.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}
