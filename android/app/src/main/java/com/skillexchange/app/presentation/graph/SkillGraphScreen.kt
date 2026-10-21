package com.skillexchange.app.presentation.graph

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skillexchange.app.core.ui.theme.Brand500
import com.skillexchange.app.core.ui.theme.Brand700
import com.skillexchange.app.domain.repository.GraphUserNode
import com.skillexchange.app.domain.repository.SuggestedLoop
import org.koin.androidx.compose.koinViewModel
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillGraphScreen(
    onNavigateBack: () -> Unit,
    onNavigateToProfile: (String) -> Unit,
    onNavigateToBooking: (String) -> Unit,
    viewModel: SkillGraphViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sơ đồ trao đổi vòng tròn") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Quay lại"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.handleIntent(SkillGraphIntent.LoadGraph) }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Làm mới"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = Brand500)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Đang tìm kiếm các vòng trao đổi tiềm năng...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                uiState.error != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Không thể tải sơ đồ",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.error ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.handleIntent(SkillGraphIntent.LoadGraph) },
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("Thử lại")
                        }
                    }
                }

                uiState.currentLoops.isEmpty() && uiState.loops2.isEmpty() && uiState.loops3Plus.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Chưa tìm thấy vòng trao đổi nào",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Hãy thêm nhiều kỹ năng bạn CÓ (HAVE) và MUỐN HỌC (WANT) để thuật toán tìm ra chu trình kết nối liên thông phù hợp.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = { viewModel.handleIntent(SkillGraphIntent.LoadGraph) },
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("Quét lại")
                        }
                    }
                }

                else -> {
                    SkillGraphContent(
                        uiState = uiState,
                        onTabSelected = { viewModel.handleIntent(SkillGraphIntent.SelectTab(it)) },
                        onLoopSelected = { viewModel.handleIntent(SkillGraphIntent.SelectLoop(it)) },
                        onNodeSelected = { viewModel.handleIntent(SkillGraphIntent.SelectNode(it)) },
                        onNavigateToProfile = onNavigateToProfile,
                        onNavigateToBooking = onNavigateToBooking
                    )
                }
            }
        }
    }
}

@Composable
private fun SkillGraphContent(
    uiState: SkillGraphUiState,
    onTabSelected: (GraphLoopTab) -> Unit,
    onLoopSelected: (Int) -> Unit,
    onNodeSelected: (GraphUserNode) -> Unit,
    onNavigateToProfile: (String) -> Unit,
    onNavigateToBooking: (String) -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = 24.dp)
    ) {
        // 1. Disclaimer Card nổi bật theo yêu cầu đề bài
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = Brand500,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = uiState.disclaimer,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 2. TabRow tách riêng Vòng 3-4 người và Song phương 2 người
        TabRow(
            selectedTabIndex = if (uiState.selectedTab == GraphLoopTab.MULTI_WAY) 0 else 1,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Tab(
                selected = uiState.selectedTab == GraphLoopTab.MULTI_WAY,
                onClick = { onTabSelected(GraphLoopTab.MULTI_WAY) },
                icon = { Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp)) },
                text = { Text("Vòng 3–4 người (${uiState.loops3Plus.size})") },
                modifier = Modifier.height(48.dp)
            )
            Tab(
                selected = uiState.selectedTab == GraphLoopTab.TWO_WAY,
                onClick = { onTabSelected(GraphLoopTab.TWO_WAY) },
                icon = { Icon(Icons.Default.Handshake, contentDescription = null, modifier = Modifier.size(18.dp)) },
                text = { Text("Song phương 2 người (${uiState.loops2.size})") },
                modifier = Modifier.height(48.dp)
            )
        }

        // 3. Nếu danh sách tab hiện tại rỗng
        if (uiState.currentLoops.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (uiState.selectedTab == GraphLoopTab.MULTI_WAY)
                        "Chưa có vòng trao đổi 3–4 người nào"
                    else "Chưa có vòng trao đổi song phương nào",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Column
        }

        // 4. Bộ chọn chu trình (nếu có nhiều hơn 1 chu trình trong tab)
        if (uiState.currentLoops.size > 1) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(uiState.currentLoops) { index, _ ->
                    FilterChip(
                        selected = uiState.selectedLoopIndex == index,
                        onClick = { onLoopSelected(index) },
                        label = { Text("Gợi ý #${index + 1}") }
                    )
                }
            }
        }

        val activeLoop = uiState.currentLoop
        if (activeLoop != null) {
            // 5. Canvas vẽ đồ thị chỉ đọc (Read-only Canvas)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Sơ đồ luồng trao đổi",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    CanvasGraph(
                        loop = activeLoop,
                        onNodeClick = onNodeSelected,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                    )
                }
            }

            // 6. Chi tiết từng chặng trao đổi trong chu trình
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Hành trình trao đổi ${activeLoop.cycleLength} bên",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    activeLoop.edges.forEachIndexed { index, edge ->
                        val fromUser = activeLoop.nodes.find { it.userId == edge.fromUserId }
                        val toUser = activeLoop.nodes.find { it.userId == edge.toUserId }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(Brand500, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${fromUser?.fullName ?: "Đối tác"} → ${toUser?.fullName ?: "Đối tác"}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Dạy ${edge.skillName} (Cấp ${edge.haveLevel} → Cấp ${edge.wantLevel})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Nút hành động nhanh
                            if (fromUser != null) {
                                OutlinedButton(
                                    onClick = { onNavigateToProfile(fromUser.userId) },
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text("Hồ sơ", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    // Nếu là vòng song phương 2 người, có thêm CTA gửi yêu cầu trao đổi 1-1
                    if (activeLoop.cycleLength == 2) {
                        val otherUser = activeLoop.nodes.find { it.userId != activeLoop.nodes.firstOrNull()?.userId }
                        if (otherUser != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { onNavigateToBooking(otherUser.userId) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Text("Gửi yêu cầu trao đổi với ${otherUser.fullName}")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Canvas vẽ các Node theo đường tròn và các Mũi tên có hướng biểu diễn chu trình.
 */
@Composable
private fun CanvasGraph(
    loop: SuggestedLoop,
    onNodeClick: (GraphUserNode) -> Unit,
    modifier: Modifier = Modifier
) {
    val nodeCount = loop.nodes.size
    val primaryColor = Brand500
    val secondaryColor = Brand700
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()

    Canvas(
        modifier = modifier.pointerInput(loop) {
            detectTapGestures { offset ->
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                val graphRadius = (size.width.coerceAtMost(size.height) / 2f) * 0.72f
                val nodeRadius = 32f

                for (i in 0 until nodeCount) {
                    val angle = -Math.PI / 2 + (2 * Math.PI * i / nodeCount)
                    val cx = centerX + graphRadius * cos(angle).toFloat()
                    val cy = centerY + graphRadius * sin(angle).toFloat()
                    val dist = sqrt((offset.x - cx) * (offset.x - cx) + (offset.y - cy) * (offset.y - cy))
                    if (dist <= nodeRadius * 1.5f) {
                        onNodeClick(loop.nodes[i])
                        break
                    }
                }
            }
        }
    ) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val graphRadius = (size.width.coerceAtMost(size.height) / 2f) * 0.72f
        val nodeRadius = 30f

        // 1. Tính tọa độ các đỉnh (Nodes)
        val nodePositions = mutableListOf<Offset>()
        for (i in 0 until nodeCount) {
            val angle = -Math.PI / 2 + (2 * Math.PI * i / nodeCount)
            val cx = centerX + graphRadius * cos(angle).toFloat()
            val cy = centerY + graphRadius * sin(angle).toFloat()
            nodePositions.add(Offset(cx, cy))
        }

        // 2. Vẽ các cạnh có hướng (Directed Edges with Arrows)
        for (i in 0 until nodeCount) {
            val fromIndex = i
            val toIndex = (i + 1) % nodeCount

            val start = nodePositions[fromIndex]
            val end = nodePositions[toIndex]

            val dx = end.x - start.x
            val dy = end.y - start.y
            val length = sqrt(dx * dx + dy * dy)
            if (length == 0f) continue

            val unitX = dx / length
            val unitY = dy / length

            // Thụt đầu và cuối cạnh một khoảng nodeRadius để không đè lên vòng tròn Node
            val edgeStart = Offset(start.x + unitX * nodeRadius, start.y + unitY * nodeRadius)
            val edgeEnd = Offset(end.x - unitX * nodeRadius, end.y - unitY * nodeRadius)

            // Vẽ đường nối
            drawLine(
                color = primaryColor,
                start = edgeStart,
                end = edgeEnd,
                strokeWidth = 4f
            )

            // Vẽ đầu mũi tên (Arrowhead)
            val arrowLength = 20f
            val arrowAngle = Math.PI / 6 // 30 độ
            val angle = atan2(unitY.toDouble(), unitX.toDouble())

            val x1 = edgeEnd.x - arrowLength * cos(angle - arrowAngle).toFloat()
            val y1 = edgeEnd.y - arrowLength * sin(angle - arrowAngle).toFloat()
            val x2 = edgeEnd.x - arrowLength * cos(angle + arrowAngle).toFloat()
            val y2 = edgeEnd.y - arrowLength * sin(angle + arrowAngle).toFloat()

            val arrowPath = Path().apply {
                moveTo(edgeEnd.x, edgeEnd.y)
                lineTo(x1, y1)
                lineTo(x2, y2)
                close()
            }
            drawPath(path = arrowPath, color = primaryColor)

            // Vẽ tên kỹ năng tại điểm giữa của cạnh
            val midX = (edgeStart.x + edgeEnd.x) / 2f
            val midY = (edgeStart.y + edgeEnd.y) / 2f
            val edge = loop.edges.getOrNull(i)
            if (edge != null) {
                drawContext.canvas.nativeCanvas.apply {
                    val paint = Paint().apply {
                        color = textColor
                        textSize = 28f
                        isAntiAlias = true
                        textAlign = Paint.Align.CENTER
                        isFakeBoldText = true
                    }
                    drawText(edge.skillName, midX, midY - 10f, paint)
                }
            }
        }

        // 3. Vẽ các Node hình tròn
        for (i in 0 until nodeCount) {
            val pos = nodePositions[i]
            val user = loop.nodes[i]

            // Vòng tròn node
            drawCircle(
                color = Color.White,
                radius = nodeRadius,
                center = pos
            )
            drawCircle(
                color = primaryColor,
                radius = nodeRadius,
                center = pos,
                style = Stroke(width = 4f)
            )

            // Viết chữ viết tắt (Initials) trong node
            val initials = user.fullName.split(" ")
                .filter { it.isNotBlank() }
                .takeLast(2)
                .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                .joinToString("")
                .ifBlank { "U" }

            drawContext.canvas.nativeCanvas.apply {
                val paint = Paint().apply {
                    color = primaryColor.toArgb()
                    textSize = 28f
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                    isFakeBoldText = true
                }
                drawText(initials, pos.x, pos.y + 10f, paint)

                // Tên người dùng dưới node
                val namePaint = Paint().apply {
                    color = textColor
                    textSize = 24f
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                }
                val shortName = user.fullName.split(" ").lastOrNull() ?: user.fullName
                drawText(shortName, pos.x, pos.y + nodeRadius + 22f, namePaint)
            }
        }
    }
}
