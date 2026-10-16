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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skillexchange.app.core.ui.theme.Brand500
import com.skillexchange.app.data.remote.stats.RadarAxisDto
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun RadarChart(
    axes: List<RadarAxisDto>,
    modifier: Modifier = Modifier
) {
    if (axes.isEmpty()) return

    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val axisLineColor = MaterialTheme.colorScheme.outline
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val brandColor = Brand500
    val fillColor = brandColor.copy(alpha = 0.3f)
    val surfaceColor = MaterialTheme.colorScheme.surface

    Box(
        modifier = modifier
            .height(280.dp)
            .padding(16.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
            val radius = min(size.width, size.height) / 2f - 40.dp.toPx()
            val numAxes = axes.size
            val angleStep = (2 * Math.PI / numAxes).toFloat()

            // 1. Vẽ 5 vòng đa giác đồng tâm (levels 1..5)
            for (level in 1..5) {
                val levelRadius = radius * (level / 5f)
                val path = Path()
                for (i in 0 until numAxes) {
                    val angle = (-Math.PI / 2 + i * angleStep).toFloat()
                    val x = center.x + levelRadius * cos(angle)
                    val y = center.y + levelRadius * sin(angle)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                drawPath(path, color = gridColor, style = Stroke(width = 1.dp.toPx()))
            }

            // 2. Vẽ các đường trục từ tâm và nhãn văn bản
            val textPaint = Paint().apply {
                color = textColor
                textSize = 12.sp.toPx()
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
                isFakeBoldText = true
            }

            for (i in 0 until numAxes) {
                val angle = (-Math.PI / 2 + i * angleStep).toFloat()
                val endX = center.x + radius * cos(angle)
                val endY = center.y + radius * sin(angle)

                drawLine(
                    color = axisLineColor,
                    start = center,
                    end = androidx.compose.ui.geometry.Offset(endX, endY),
                    strokeWidth = 1.dp.toPx()
                )

                // Nhãn danh mục ngoài đỉnh
                val labelRadius = radius + 24.dp.toPx()
                val labelX = center.x + labelRadius * cos(angle)
                val labelY = center.y + labelRadius * sin(angle) + 4.dp.toPx()
                val label = axes[i].categoryName

                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    labelX,
                    labelY,
                    textPaint
                )
            }

            // 3. Vẽ vùng dữ liệu của người dùng
            val dataPath = Path()
            val points = mutableListOf<androidx.compose.ui.geometry.Offset>()

            for (i in 0 until numAxes) {
                val score = axes[i].score.coerceIn(0.0, 5.0)
                val dataRadius = radius * (score / 5f).toFloat()
                val angle = (-Math.PI / 2 + i * angleStep).toFloat()
                val x = center.x + dataRadius * cos(angle)
                val y = center.y + dataRadius * sin(angle)
                val point = androidx.compose.ui.geometry.Offset(x, y)
                points.add(point)

                if (i == 0) dataPath.moveTo(x, y) else dataPath.lineTo(x, y)
            }
            dataPath.close()

            // Tô diện tích bán trong suốt
            drawPath(dataPath, color = fillColor)
            // Viền đậm
            drawPath(dataPath, color = brandColor, style = Stroke(width = 2.5f.dp.toPx()))

            // Vẽ điểm tròn tại mỗi đỉnh có score > 0
            for (p in points) {
                drawCircle(color = brandColor, radius = 4.dp.toPx(), center = p)
                drawCircle(color = surfaceColor, radius = 2.dp.toPx(), center = p)
            }
        }
    }
}
