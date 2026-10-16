package com.skillexchange.app.presentation.dashboard.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skillexchange.app.core.ui.theme.Brand500
import com.skillexchange.app.data.remote.stats.MonthlySessionDto
import kotlin.math.max

@Composable
fun MonthlyBarChart(
    monthlySessions: List<MonthlySessionDto>,
    modifier: Modifier = Modifier
) {
    if (monthlySessions.isEmpty()) return

    val barColor = Brand500
    val emptyBarColor = MaterialTheme.colorScheme.outlineVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val countTextColor = MaterialTheme.colorScheme.onSurface.toArgb()

    Box(
        modifier = modifier
            .height(200.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val totalWidth = size.width
            val totalHeight = size.height

            val bottomLabelHeight = 24.dp.toPx()
            val topLabelHeight = 20.dp.toPx()
            val chartAreaHeight = totalHeight - bottomLabelHeight - topLabelHeight
            val baselineY = topLabelHeight + chartAreaHeight

            val numBars = monthlySessions.size
            val slotWidth = totalWidth / numBars
            val barWidth = slotWidth * 0.45f

            val maxVal = max(4, monthlySessions.maxOf { it.count })

            // 1. Vẽ đường trục cơ sở (baseline)
            drawLine(
                color = gridColor,
                start = Offset(0f, baselineY),
                end = Offset(totalWidth, baselineY),
                strokeWidth = 1.dp.toPx()
            )

            // Đường chia giữa (50% max)
            val midY = topLabelHeight + chartAreaHeight / 2f
            drawLine(
                color = gridColor.copy(alpha = 0.5f),
                start = Offset(0f, midY),
                end = Offset(totalWidth, midY),
                strokeWidth = 0.8f.dp.toPx()
            )

            val labelPaint = Paint().apply {
                color = textColor
                textSize = 11.sp.toPx()
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }

            val countPaint = Paint().apply {
                color = countTextColor
                textSize = 11.sp.toPx()
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
                isFakeBoldText = true
            }

            // 2. Vẽ từng cột
            for (i in 0 until numBars) {
                val item = monthlySessions[i]
                val centerX = (i + 0.5f) * slotWidth

                val barHeight = if (item.count > 0) {
                    (item.count.toFloat() / maxVal) * chartAreaHeight
                } else {
                    4.dp.toPx()
                }

                val topLeft = Offset(centerX - barWidth / 2f, baselineY - barHeight)
                val barSize = Size(barWidth, barHeight)

                drawRoundRect(
                    color = if (item.count > 0) barColor else emptyBarColor,
                    topLeft = topLeft,
                    size = barSize,
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )

                // Vẽ số lượng phía trên đầu cột (nếu có phiên)
                if (item.count > 0) {
                    drawContext.canvas.nativeCanvas.drawText(
                        item.count.toString(),
                        centerX,
                        topLeft.y - 4.dp.toPx(),
                        countPaint
                    )
                }

                // Vẽ nhãn tháng phía dưới đường cơ sở
                val monthLabel = item.label.substringBefore("/") // Ví dụ "05"
                drawContext.canvas.nativeCanvas.drawText(
                    "T$monthLabel",
                    centerX,
                    baselineY + 16.dp.toPx(),
                    labelPaint
                )
            }
        }
    }
}
