package com.example.carrotamap.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.content.Context
import com.example.carrotamap.XiaogeVehicleData
import com.example.carrotamap.XYZData
import kotlin.math.abs
import kotlin.math.ln
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay

/**
 * 车辆和车道可视化弹窗组件 - 优化版
 * 绘制4条车道线（2个车道），当前车辆，前车，曲率弯曲，盲区高亮
 * 并显示核心数据信息
 * 只有用户类型3（赞助者）或4（铁粉）才自动显示
 */
@Composable
fun VehicleLaneVisualization(
    data: XiaogeVehicleData?,
    userType: Int,
    showDialog: Boolean, // 改为必需参数，由外部控制
    onDismiss: () -> Unit, // 改为必需参数，添加关闭回调
    amapRoadType: Int? = null // 🆕 额外传入高德 RoadType，方便展示原始道路类型
) {
    // 只有用户类型3或4才允许显示弹窗
    if (userType != 3 && userType != 4) {
        return
    }
    
    if (showDialog) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            )
        ) {
            val context = LocalContext.current
            val density = LocalDensity.current
            val configuration = LocalConfiguration.current
            val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val screenWidth = context.resources.displayMetrics.widthPixels
            val dialogWidth = with(density) { (screenWidth * 0.9f).toDp() }  // 宽度为屏幕的90%
            
            // 🆕 数据更新频率控制：限制为10Hz（每100ms更新一次）
            var displayData by remember { mutableStateOf(data) }
            LaunchedEffect(data) {
                delay(100) // 限制为10Hz
                displayData = data
            }
            
            // 🆕 数据一致性检查：计算数据年龄和延迟
            // 注意：由于网络延迟和数据处理时间，正常延迟可能在1000-2000ms范围内
            // 只有当延迟超过2000ms时才认为数据异常
            // 🔧 修复：Python 端已经发送毫秒级时间戳（time.time() * 1000），不需要再乘以 1000
            val currentTime = System.currentTimeMillis()
            val dataTimestamp = (displayData?.timestamp ?: 0.0)  // 已经是毫秒，不需要再乘
            val dataAge = currentTime - dataTimestamp.toLong()
            val isDataStale = dataAge > 2000 // 超过2000ms认为数据延迟（提高阈值）
            
            // 🆕 优化：预加载车辆图片资源，防止重复加载
            val carBitmap: ImageBitmap? = remember(context) {
                runCatching {
                    var resId = context.resources.getIdentifier("car", "drawable", context.packageName)
                    if (resId == 0) {
                        resId = context.resources.getIdentifier("car", "mipmap", context.packageName)
                    }
                    if (resId != 0) {
                        ImageBitmap.imageResource(context.resources, resId)
                    } else {
                        null
                    }
                }.getOrNull()
            }
            
            Card(
                modifier = Modifier
                    .width(dialogWidth)
                    .wrapContentHeight()
                    .padding(0.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Transparent
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF0F172A), // 深蓝黑色
                                    Color(0xFF1E293B), // 中蓝黑色
                                    Color(0xFF0F172A)  // 深蓝黑色
                                )
                            )
                        )
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (isLandscape) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 320.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                LaneVisualizationCard(
                                    data = displayData,
                                    carBitmap = carBitmap,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                )
                                
                                VehicleDataPanel(
                                    data = displayData,
                                    dataAge = dataAge,
                                    isDataStale = isDataStale,
                                    amapRoadType = amapRoadType,
                                    onClose = onDismiss,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                )
                            }
                        } else {
                            VehicleDataPanel(
                                data = displayData,
                                dataAge = dataAge,
                                isDataStale = isDataStale,
                                amapRoadType = amapRoadType,
                                onClose = onDismiss,
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            LaneVisualizationCard(
                                data = displayData,
                                carBitmap = carBitmap,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(300.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}


/**
 * 车道可视化卡�? */
@Composable
private fun LaneVisualizationCard(
    data: XiaogeVehicleData?,
    carBitmap: ImageBitmap?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E293B).copy(alpha = 0.6f)
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            drawLaneVisualization(data, carBitmap, context)
        }
    }
}

/**
 * 绘制车道可视化（优化版）
 * @param context Context 用于读取 SharedPreferences 获取超车模式（仅在 drawCurrentVehicle 中使用）
 */
private fun DrawScope.drawLaneVisualization(
    data: XiaogeVehicleData?, 
    carBitmap: ImageBitmap?,
    context: Context? = null
) {
    val width = size.width
    val height = size.height
    
    // 绘制道路背景渐变
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF475569).copy(alpha = 0.3f),
                Color(0xFF334155).copy(alpha = 0.5f),
                Color(0xFF1E293B).copy(alpha = 0.7f)
            )
        )
    )
    
    // 计算车道参数
    val laneWidth = width / 3.5f
    val centerX = width / 2f
    
    // 获取数据
    val curvature = data?.modelV2?.curvature
    val curvatureRate = curvature?.maxOrientationRate ?: 0f
    val curvatureDirection = curvature?.direction ?: 0
    
    // 绘制盲区高亮（需要随曲率弯曲）
    drawLaneBackgrounds(
        leftBlindspot = data?.carState?.leftBlindspot == true,
        rightBlindspot = data?.carState?.rightBlindspot == true,
        laneWidth = laneWidth,
        centerX = centerX,
        width = width,
        height = height,
        curvatureRate = curvatureRate,
        curvatureDirection = curvatureDirection
    )
    
    // 绘制距离标记
    drawDistanceMarkers(centerX, laneWidth * 1.5f)
    
    // 🎯 绘制路缘线（在车道线之前绘制，作为背景）
    drawRoadEdges(
        data = data,
        centerX = centerX,
        width = width
    )
    
    // 🎯 动态绘制车道线（使用透视投影和实际 laneLines 数据）
    drawDynamicLaneLines(
        data = data,
        centerX = centerX,
        laneWidth = laneWidth,
        width = width,
        curvatureRate = curvatureRate,
        curvatureDirection = curvatureDirection
    )
    
    // 🎯 绘制车辆引导路径（使用 position 数据，从车辆车头开始）
    // 注意：在车道线之后、车辆之前绘制，确保正确的层级关系
    val isActive = data?.systemState?.active == true
    val lead0 = data?.modelV2?.lead0
    val leadDistance = if (lead0 != null && lead0.prob > 0.5f && lead0.x > 0f) lead0.x else null
    drawGuidancePath(
        data = data,
        centerX = centerX,
        laneWidth = laneWidth,
        isActive = isActive,
        width = width,
        leadDistance = leadDistance
    )
    
    // 绘制前车（使用车辆图片）
    val leadOne = data?.radarState?.leadOne
    if (lead0 != null && lead0.prob > 0.5f && lead0.x > 0f) {
        val leadSpeedKmh = (leadOne?.vLead ?: 0f) * 3.6f
        val leadDistance = lead0.x
        drawLeadVehicle(
            leadDistance = leadDistance,
            leadSpeedKmh = leadSpeedKmh,
            centerX = centerX,
            laneWidth = laneWidth,
            curvatureRate = curvatureRate,
            curvatureDirection = curvatureDirection,
            width = width,
            vRel = leadOne?.vRel ?: 0f,
            carBitmap = carBitmap
        )
    }
    
    // 绘制当前车辆（最后绘制，确保在最上层）
    val vEgoKmh = (data?.carState?.vEgo ?: 0f) * 3.6f
    drawCurrentVehicle(centerX, laneWidth, carBitmap, vEgoKmh, data, context)
}

/**
 * 🎯 动态绘制车道线（使用透视投影和实际 laneLines 数据）
 * 根据 openpilot 规范：
 * - 索引0: 最左侧车道线（左侧相邻车道的左边界）
 * - 索引1: 左车道线（当前车道左边界）
 * - 索引2: 右车道线（当前车道右边界）
 * - 索引3: 最右侧车道线（右侧相邻车道的右边界）
 * 
 * 颜色规则：
 * - 白色：普通车道线
 * - 黄色：特殊车道线（当 leftLaneLine >= 20 或 rightLaneLine >= 20 时）
 * 
 * 透明度：根据置信度计算（220 * prob / 255），阈值 0.3
 */
private fun DrawScope.drawDynamicLaneLines(
    data: XiaogeVehicleData?,
    centerX: Float,
    laneWidth: Float,
    width: Float,
    curvatureRate: Float,
    curvatureDirection: Int
) {
    val laneLines = data?.modelV2?.laneLines ?: emptyList()
    val laneLineProbs = data?.modelV2?.laneLineProbs ?: emptyList()
    
    if (laneLines.isEmpty() || laneLineProbs.isEmpty()) {
        // 没有车道线数据，不绘制
        return
    }
    
    // 置信度阈值：低于此值不绘制（与 openpilot UI 一致）
    val confidenceThreshold = 0.3f
    
    // 获取车道线类型（用于颜色判断）
    val leftLaneLine = data?.carState?.leftLaneLine ?: 0  // 左车道线类型
    val rightLaneLine = data?.carState?.rightLaneLine ?: 0  // 右车道线类型
    
    /**
     * 根据置信度计算透明度（参考 openpilot: 220 * prob）
     * 转换为 0-1 范围：220 / 255 ≈ 0.86
     */
    fun calculateAlpha(prob: Float): Float {
        return if (prob >= confidenceThreshold) {
            (220f / 255f) * prob.coerceIn(0f, 1f)  // 最大透明度约 0.86
        } else {
            0f
        }
    }
    
    /**
     * 根据车道线类型和索引获取颜色
     * - 白色：普通车道线
     * - 黄色：特殊车道线（leftLaneLine >= 20 或 rightLaneLine >= 20）
     */
    fun getLaneLineColor(index: Int): Color {
        val isSpecial = when (index) {
            0, 1 -> leftLaneLine >= 20  // 左侧车道线
            2, 3 -> rightLaneLine >= 20  // 右侧车道线
            else -> false
        }
        return if (isSpecial) {
            Color(0xFFFFFF00)  // 黄色
        } else {
            Color(0xFFFFFFFF)  // 白色
        }
    }
    
    /**
     * 绘制单条车道线（使用透视投影）
     */
    fun drawLaneLineByIndex(index: Int, laneLine: XYZData, prob: Float) {
        if (prob < confidenceThreshold || laneLine.x.isEmpty() || laneLine.y.isEmpty()) {
            return
        }
        
        val alpha = calculateAlpha(prob)
        val color = getLaneLineColor(index).copy(alpha = alpha)
        
        // 使用透视投影绘制车道线
        val path = Path()
        var firstPoint = true
        
        // 遍历车道线的每个点
        for (i in laneLine.x.indices) {
            val x = laneLine.x[i]  // 距离（米）
            val y = laneLine.y[i]   // 横向偏移（米），左侧为正，右侧为负
            val z = if (i < laneLine.z.size) laneLine.z[i] else 0f  // 高度（米），通常为0
            
            // 使用透视投影转换为屏幕坐标
            val screenPoint = com.example.carrotamap.ui.components.PerspectiveProjection.mapToScreen(
                x = x,
                y = y,
                z = z,
                screenWidth = width,
                screenHeight = size.height,
                centerX = centerX
            )
            
            if (screenPoint != null) {
                if (firstPoint) {
                    path.moveTo(screenPoint.x, screenPoint.y)
                    firstPoint = false
                } else {
                    path.lineTo(screenPoint.x, screenPoint.y)
                }
            }
        }
        
        // 绘制车道线
        if (!firstPoint) {
            drawPath(
                path = path,
                color = color,
                style = Stroke(
                    width = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            )
        }
    }
    
    // 绘制4条车道线
    for (i in 0 until minOf(4, laneLines.size, laneLineProbs.size)) {
        val laneLine = laneLines[i]
        val prob = laneLineProbs[i]
        drawLaneLineByIndex(i, laneLine, prob)
    }
}

/**
 * 🎯 绘制路缘线（使用透视投影和实际 roadEdges 数据）
 * 根据标准差设置颜色和透明度
 */
private fun DrawScope.drawRoadEdges(
    data: XiaogeVehicleData?,
    centerX: Float,
    width: Float
) {
    val roadEdges = data?.modelV2?.roadEdges ?: emptyList()
    val roadEdgeStds = data?.modelV2?.roadEdgeStds ?: emptyList()
    
    if (roadEdges.isEmpty()) {
        return
    }
    
    /**
     * 根据标准差计算颜色和透明度
     * 标准差越大，颜色越红（表示不确定性高）
     */
    fun getRoadEdgeColor(std: Float): Color {
        val alpha = (0.3f + (1f - std.coerceIn(0f, 1f)) * 0.4f).coerceIn(0.3f, 0.7f)
        val redComponent = (std.coerceIn(0f, 1f) * 255f).toInt()
        val greenComponent = ((1f - std.coerceIn(0f, 1f)) * 200f).toInt()
        return Color(redComponent, greenComponent, 0).copy(alpha = alpha)
    }
    
    /**
     * 绘制单条路缘线（使用透视投影）
     */
    fun drawRoadEdgeByIndex(index: Int, roadEdge: XYZData, std: Float) {
        if (roadEdge.x.isEmpty() || roadEdge.y.isEmpty()) {
            return
        }
        
        val color = getRoadEdgeColor(std)
        
        // 使用透视投影绘制路缘线
        val path = Path()
        var firstPoint = true
        
        // 遍历路缘线的每个点
        for (i in roadEdge.x.indices) {
            val x = roadEdge.x[i]  // 距离（米）
            val y = roadEdge.y[i]   // 横向偏移（米）
            val z = if (i < roadEdge.z.size) roadEdge.z[i] else 0f  // 高度（米），通常为0
            
            // 使用透视投影转换为屏幕坐标
            val screenPoint = com.example.carrotamap.ui.components.PerspectiveProjection.mapToScreen(
                x = x,
                y = y,
                z = z,
                screenWidth = width,
                screenHeight = size.height,
                centerX = centerX
            )
            
            if (screenPoint != null) {
                if (firstPoint) {
                    path.moveTo(screenPoint.x, screenPoint.y)
                    firstPoint = false
                } else {
                    path.lineTo(screenPoint.x, screenPoint.y)
                }
            }
        }
        
        // 绘制路缘线（比车道线稍粗）
        if (!firstPoint) {
            drawPath(
                path = path,
                color = color,
                style = Stroke(
                    width = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            )
        }
    }
    
    // 绘制2条路缘线
    for (i in 0 until minOf(2, roadEdges.size, roadEdgeStds.size)) {
        val roadEdge = roadEdges[i]
        val std = roadEdgeStds[i]
        drawRoadEdgeByIndex(i, roadEdge, std)
    }
}

/**
 * 🎯 绘制车辆引导路径（使用 position 数据，从车辆车头开始）
 * 在车辆前方，车道中间绘制引导路径
 * - 默认：宽度为车道2/3，浅蓝色
 * - active状态：宽度为车道1/3，浅绿色
 * - 有前车时：从前车后面开始绘制，颜色根据距离变化（近=红橙，远=蓝绿）
 * - 无前车时：从车辆前方开始，延伸到屏幕顶部（无限长）
 */
private fun DrawScope.drawGuidancePath(
    data: XiaogeVehicleData?,
    centerX: Float,
    laneWidth: Float,
    isActive: Boolean,
    width: Float,
    leadDistance: Float?  // 前车距离（米），null表示无前车
) {
    val position = data?.modelV2?.position
    if (position == null || position.x.isEmpty() || position.y.isEmpty()) {
        // 没有路径数据，不绘制
        return
    }
    
    // 根据 active 状态设置路径宽度（米）
    val pathWidthMeters = if (isActive) 0.3f else 0.6f  // active: 0.3m, 默认: 0.6m
    
    // 🎯 如果有前车，找到前车位置对应的索引，从那里开始绘制
    val startIndex = if (leadDistance != null && leadDistance > 0f) {
        // 找到第一个距离大于等于前车距离的索引
        val index = com.example.carrotamap.ui.components.PerspectiveProjection.getPathLengthIndex(position.x, leadDistance)
        // 从前车后面开始（索引+1，或者如果索引已经是最后一个，则从最后一个开始）
        minOf(index + 1, position.x.size - 1)
    } else {
        0  // 从第一个点开始（车辆前方）
    }
    
    if (startIndex >= position.x.size) {
        return  // 没有有效的起始点
    }
    
    // 🎯 根据前车距离计算颜色（距离近=红橙，距离远=蓝绿）
    val pathColor = if (leadDistance != null && leadDistance > 0f) {
        // 有前车：根据距离计算颜色
        when {
            leadDistance < 20f -> Color(0xFFEF4444).copy(alpha = 0.5f)  // 红色
            leadDistance < 40f -> Color(0xFFF59E0B).copy(alpha = 0.5f)  // 橙色
            leadDistance < 60f -> Color(0xFFFBBF24).copy(alpha = 0.5f)  // 黄色
            else -> Color(0xFF10B981).copy(alpha = 0.5f)  // 绿色
        }
    } else {
        // 无前车：根据 active 状态设置颜色
        if (isActive) {
            Color(0xFF6EE7B7).copy(alpha = 0.4f)  // 浅绿色
        } else {
            Color(0xFF93C5FD).copy(alpha = 0.4f)  // 浅蓝色
        }
    }
    
    // 使用透视投影绘制路径
    val path = Path()
    var firstPoint = true
    
    // 遍历路径的每个点（从起始索引开始）
    for (i in startIndex until position.x.size) {
        val x = position.x[i]  // 距离（米）
        val y = position.y[i]  // 横向偏移（米），左侧为正，右侧为负
        val z = if (i < position.z.size) position.z[i] else 0f  // 高度（米），通常为0
        
        // 使用透视投影转换为屏幕坐标（路径中心）
        val centerPoint = PerspectiveProjection.mapToScreen(
            x = x,
            y = y,
            z = z,
            screenWidth = width,
            screenHeight = size.height,
            centerX = centerX
        )
        
        if (centerPoint != null) {
            // 计算路径宽度（根据距离动态调整，远处变窄）
            val normalizedDistance = (x / 100f).coerceIn(0f, 1f)
            val perspectiveScale = 1f - normalizedDistance * 0.4f  // 远处缩小40%
            val currentPathWidthPx = pathWidthMeters * (width / 8f) * perspectiveScale  // 假设8米宽度对应屏幕宽度
            
            // 计算路径左右边界（垂直于路径方向）
            // 简化：假设路径方向与屏幕Y轴平行，左右边界就是横向偏移
            val leftPoint = PerspectiveProjection.mapToScreen(
                x = x,
                y = y + pathWidthMeters / 2f,  // 左侧边界
                z = z,
                screenWidth = width,
                screenHeight = size.height,
                centerX = centerX
            )
            val rightPoint = PerspectiveProjection.mapToScreen(
                x = x,
                y = y - pathWidthMeters / 2f,  // 右侧边界
                z = z,
                screenWidth = width,
                screenHeight = size.height,
                centerX = centerX
            )
            
            // 使用左右边界点绘制路径
            val leftX = leftPoint?.x ?: (centerPoint.x - currentPathWidthPx / 2f)
            val rightX = rightPoint?.x ?: (centerPoint.x + currentPathWidthPx / 2f)
            
            if (firstPoint) {
                path.moveTo(leftX, centerPoint.y)
                firstPoint = false
            } else {
                path.lineTo(leftX, centerPoint.y)
            }
        }
    }
    
    // 绘制路径右边界（从远到近）
    for (i in (position.x.size - 1) downTo startIndex) {
        val x = position.x[i]
        val y = position.y[i]
        val z = if (i < position.z.size) position.z[i] else 0f
        
        val centerPoint = PerspectiveProjection.mapToScreen(
            x = x,
            y = y,
            z = z,
            screenWidth = width,
            screenHeight = size.height,
            centerX = centerX
        )
        
        if (centerPoint != null) {
            val normalizedDistance = (x / 100f).coerceIn(0f, 1f)
            val perspectiveScale = 1f - normalizedDistance * 0.4f
            val currentPathWidthPx = pathWidthMeters * (width / 8f) * perspectiveScale
            
            val rightPoint = PerspectiveProjection.mapToScreen(
                x = x,
                y = y - pathWidthMeters / 2f,
                z = z,
                screenWidth = width,
                screenHeight = size.height,
                centerX = centerX
            )
            
            val rightX = rightPoint?.x ?: (centerPoint.x + currentPathWidthPx / 2f)
            path.lineTo(rightX, centerPoint.y)
        }
    }
    
    // 闭合路径
    path.close()
    
    // 🎯 使用渐变填充，让颜色更平滑（如果有前车）
    if (leadDistance != null && leadDistance > 0f && startIndex < position.x.size) {
        // 有前车：使用从起始位置到结束位置的渐变
        val startPoint = PerspectiveProjection.mapToScreen(
            x = position.x[startIndex],
            y = position.y[startIndex],
            z = if (startIndex < position.z.size) position.z[startIndex] else 0f,
            screenWidth = width,
            screenHeight = size.height,
            centerX = centerX
        )
        val endPoint = if (position.x.isNotEmpty()) {
            val lastIndex = position.x.size - 1
            PerspectiveProjection.mapToScreen(
                x = position.x[lastIndex],
                y = position.y[lastIndex],
                z = if (lastIndex < position.z.size) position.z[lastIndex] else 0f,
                screenWidth = width,
                screenHeight = size.height,
                centerX = centerX
            )
        } else {
            null
        }
        
        val startY = startPoint?.y ?: size.height
        val endY = endPoint?.y ?: 0f
        
        val gradientColors = when {
            leadDistance < 20f -> listOf(
                Color(0xFFEF4444).copy(alpha = 0.6f),  // 起始：红色（更明显）
                Color(0xFFEF4444).copy(alpha = 0.3f)  // 结束：红色（渐淡）
            )
            leadDistance < 40f -> listOf(
                Color(0xFFF59E0B).copy(alpha = 0.6f),  // 起始：橙色
                Color(0xFFF59E0B).copy(alpha = 0.3f)   // 结束：橙色（渐淡）
            )
            leadDistance < 60f -> listOf(
                Color(0xFFFBBF24).copy(alpha = 0.6f),  // 起始：黄色
                Color(0xFFFBBF24).copy(alpha = 0.3f)  // 结束：黄色（渐淡）
            )
            else -> listOf(
                Color(0xFF10B981).copy(alpha = 0.6f),  // 起始：绿色
                Color(0xFF10B981).copy(alpha = 0.3f)  // 结束：绿色（渐淡）
            )
        }
        
        // 使用渐变填充
        drawPath(
            path = path,
            brush = Brush.verticalGradient(
                colors = gradientColors,
                startY = startY,
                endY = endY
            ),
            style = androidx.compose.ui.graphics.drawscope.Fill
        )
    } else {
        // 无前车：使用单色填充
        drawPath(
            path = path,
            color = pathColor,
            style = androidx.compose.ui.graphics.drawscope.Fill
        )
    }
    
    // 绘制路径边框（更明显的视觉效果）
    val borderColor = pathColor.copy(alpha = pathColor.alpha * 1.5f.coerceIn(0f, 1f))
    drawPath(
        path = path,
        color = borderColor,
        style = Stroke(width = 2.dp.toPx())
    )
}

/**
 * 绘制距离标记
 */
private fun DrawScope.drawDistanceMarkers(centerX: Float, laneAreaWidth: Float) {
    val height = size.height
    val distances = listOf(20f, 40f, 60f, 80f)
    val maxDistance = 80f
    
    distances.forEach { distance ->
        val normalizedDistance = distance / maxDistance
        val y = height * (1f - normalizedDistance) * 0.7f
        
        // 绘制标记线
        drawLine(
            color = Color(0xFF64748B).copy(alpha = 0.3f),
            start = Offset(centerX - laneAreaWidth - 20f, y),
            end = Offset(centerX - laneAreaWidth - 5f, y),
            strokeWidth = 1.dp.toPx()
        )
        
        drawLine(
            color = Color(0xFF64748B).copy(alpha = 0.3f),
            start = Offset(centerX + laneAreaWidth + 5f, y),
            end = Offset(centerX + laneAreaWidth + 20f, y),
            strokeWidth = 1.dp.toPx()
        )
    }
}

/**
 * 🆕 绘制弯曲车道线（根据曲率逐点弯曲，参�?openpilot 实现�? * 每个点的偏移量随距离变化，形成真实的曲线效果
 */
private fun DrawScope.drawPerspectiveCurvedLaneLine(
    laneBottomX: Float,
    laneTopX: Float,
    curvatureRate: Float,
    curvatureDirection: Int,
    color: Color
) {
    val height = size.height
    val steps = 80
    val path = Path()
    val maxDistance = 80f  // 最大距离80米    
    for (i in 0..steps) {
        val t = i / steps.toFloat()
        val y = height * (1f - t)
        val xBase = com.example.carrotamap.ui.components.PerspectiveProjection.lerp(laneBottomX, laneTopX, t)
        
        // 🆕 根据距离计算曲率偏移（参考 openpilot 的实现）
        val distance = t * maxDistance
        val curvatureAtDistance = com.example.carrotamap.ui.components.PerspectiveProjection.calculateCurvatureAtDistance(
            curvatureRate,
            curvatureDirection,
            distance,
            size.width
        )
        val x = xBase + curvatureAtDistance
        
        if (i == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = 3.dp.toPx(),
            cap = StrokeCap.Round
        )
    )
}

/**
 * 🆕 计算特定距离处的曲率偏移（参�?openpilot 的曲率计算）
 * 使用二次函数模拟曲线，让车道线根据距离逐渐弯曲
 */
private fun calculateCurvatureAtDistance(
    curvatureRate: Float,
    direction: Int,
    distance: Float,
    width: Float
): Float {
    if (abs(curvatureRate) < 0.01f || distance < 0.1f) return 0f
    
    // 使用二次函数模拟曲线（参考 openpilot 的曲率计算）
    // 曲率随距离的平方增长，模拟真实的道路弯曲
    // 🆕 修复：减小曲率系数，避免曲率过大
    val curvature = curvatureRate * 0.3f  // 从0.5f减小到0.3f
    val normalizedCurvature = (curvature / 0.02f).coerceIn(-1f, 1f)
    val maxOffset = width * 0.12f  // 从0.15f减小到0.12f
    val offset = normalizedCurvature * distance * distance * 0.005f * maxOffset  // 从0.01f减小到0.005f
    
    return if (direction > 0) offset else -offset
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float {
    return (1 - fraction) * start + fraction * stop
}

/**
 * 绘制车道背景（盲区高亮）
 * 🆕 修复：盲区高亮随曲率弯曲，而不是简单的矩形
 */
private fun DrawScope.drawLaneBackgrounds(
    leftBlindspot: Boolean,
    rightBlindspot: Boolean,
    laneWidth: Float,
    centerX: Float,
    width: Float,
    height: Float,
    curvatureRate: Float,
    curvatureDirection: Int
) {
    val maxDistance = 80f
    val steps = 40  // 使用较少的步数以优化性能
    
    if (leftBlindspot) {
        val leftLaneLeftBottom = centerX - laneWidth * 1.5f
        val leftLaneRightBottom = centerX - laneWidth * 0.5f
        val perspectiveScaleTop = 0.6f
        val leftLaneLeftTop = centerX - laneWidth * perspectiveScaleTop * 1.5f
        val leftLaneRightTop = centerX - laneWidth * perspectiveScaleTop * 0.5f
        
        // 绘制左侧盲区（随曲率弯曲，使用渐变填充）
        for (i in 0 until steps) {
            val t1 = i / steps.toFloat()
            val t2 = (i + 1) / steps.toFloat()
            val y1 = height * (1f - t1)
            val y2 = height * (1f - t2)
            val distance1 = t1 * maxDistance
            val distance2 = t2 * maxDistance
            
            val leftX1Base = com.example.carrotamap.ui.components.PerspectiveProjection.lerp(leftLaneLeftBottom, leftLaneLeftTop, t1)
            val leftX2Base = com.example.carrotamap.ui.components.PerspectiveProjection.lerp(leftLaneLeftBottom, leftLaneLeftTop, t2)
            val leftX1 = leftX1Base + com.example.carrotamap.ui.components.PerspectiveProjection.calculateCurvatureAtDistance(curvatureRate, curvatureDirection, distance1, width)
            val leftX2 = leftX2Base + com.example.carrotamap.ui.components.PerspectiveProjection.calculateCurvatureAtDistance(curvatureRate, curvatureDirection, distance2, width)
            
            val rightX1Base = com.example.carrotamap.ui.components.PerspectiveProjection.lerp(leftLaneRightBottom, leftLaneRightTop, t1)
            val rightX2Base = com.example.carrotamap.ui.components.PerspectiveProjection.lerp(leftLaneRightBottom, leftLaneRightTop, t2)
            val rightX1 = rightX1Base + com.example.carrotamap.ui.components.PerspectiveProjection.calculateCurvatureAtDistance(curvatureRate, curvatureDirection, distance1, width)
            val rightX2 = rightX2Base + com.example.carrotamap.ui.components.PerspectiveProjection.calculateCurvatureAtDistance(curvatureRate, curvatureDirection, distance2, width)
            
            val alpha = (0.1f + (1f - t1) * 0.2f).coerceIn(0.1f, 0.3f)
            val path = Path().apply {
                moveTo(leftX1, y1)
                lineTo(leftX2, y2)
                lineTo(rightX2, y2)
                lineTo(rightX1, y1)
                close()
            }
            drawPath(
                path = path,
                color = Color(0xFFEF4444).copy(alpha = alpha),
                style = androidx.compose.ui.graphics.drawscope.Fill
            )
        }
    }
    
    if (rightBlindspot) {
        val rightLaneLeftBottom = centerX + laneWidth * 0.5f
        val rightLaneRightBottom = centerX + laneWidth * 1.5f
        val perspectiveScaleTop = 0.6f
        val rightLaneLeftTop = centerX + laneWidth * perspectiveScaleTop * 0.5f
        val rightLaneRightTop = centerX + laneWidth * perspectiveScaleTop * 1.5f
        
        // 绘制右侧盲区（随曲率弯曲）
        for (i in 0 until steps) {
            val t1 = i / steps.toFloat()
            val t2 = (i + 1) / steps.toFloat()
            val y1 = height * (1f - t1)
            val y2 = height * (1f - t2)
            val distance1 = t1 * maxDistance
            val distance2 = t2 * maxDistance
            
            val leftX1Base = com.example.carrotamap.ui.components.PerspectiveProjection.lerp(rightLaneLeftBottom, rightLaneLeftTop, t1)
            val leftX2Base = com.example.carrotamap.ui.components.PerspectiveProjection.lerp(rightLaneLeftBottom, rightLaneLeftTop, t2)
            val leftX1 = leftX1Base + com.example.carrotamap.ui.components.PerspectiveProjection.calculateCurvatureAtDistance(curvatureRate, curvatureDirection, distance1, width)
            val leftX2 = leftX2Base + com.example.carrotamap.ui.components.PerspectiveProjection.calculateCurvatureAtDistance(curvatureRate, curvatureDirection, distance2, width)
            
            val rightX1Base = com.example.carrotamap.ui.components.PerspectiveProjection.lerp(rightLaneRightBottom, rightLaneRightTop, t1)
            val rightX2Base = com.example.carrotamap.ui.components.PerspectiveProjection.lerp(rightLaneRightBottom, rightLaneRightTop, t2)
            val rightX1 = rightX1Base + com.example.carrotamap.ui.components.PerspectiveProjection.calculateCurvatureAtDistance(curvatureRate, curvatureDirection, distance1, width)
            val rightX2 = rightX2Base + com.example.carrotamap.ui.components.PerspectiveProjection.calculateCurvatureAtDistance(curvatureRate, curvatureDirection, distance2, width)
            
            val alpha = (0.1f + (1f - t1) * 0.2f).coerceIn(0.1f, 0.3f)
            val path = Path().apply {
                moveTo(leftX1, y1)
                lineTo(leftX2, y2)
                lineTo(rightX2, y2)
                lineTo(rightX1, y1)
                close()
            }
            drawPath(
                path = path,
                color = Color(0xFFEF4444).copy(alpha = alpha),
                style = androidx.compose.ui.graphics.drawscope.Fill
            )
        }
    }
}

/**
 * 绘制前车（优化版，使用车辆图片）
 * 🆕 修复：前车使用车辆图片，并随曲率弯曲
 * 🆕 优化：在前车图片下方显示"车速/距离"文本
 */
private fun DrawScope.drawLeadVehicle(
    leadDistance: Float,
    leadSpeedKmh: Float,
    centerX: Float,
    laneWidth: Float,
    curvatureRate: Float,
    curvatureDirection: Int,
    width: Float,
    vRel: Float,
    carBitmap: ImageBitmap?
) {
    val height = size.height
    
    val maxDistance = 80f
    val normalizedDistance = (leadDistance / maxDistance).coerceIn(0f, 1f)
    val logMappedDistance = if (normalizedDistance > 0f) {
        ln(1f + normalizedDistance * 2.718f) / ln(3.718f)
    } else {
        0f
    }
    val leadY = height * (1f - logMappedDistance) * 0.7f
    
    // 🎯 修复：前车位置应该沿着车道中心线，根据前车距离和Y位置计算曲率偏移
    // 前车始终在车道中间，需要同时考虑：
    // 1. 前车的实际距离（leadDistance）用于计算曲率偏移
    // 2. 前车在屏幕上的Y位置（leadY）用于与车道线对齐
    // 使用前车实际距离计算曲率偏移，确保前车跟随车道中心线
    val curvatureAtDistance = com.example.carrotamap.ui.components.PerspectiveProjection.calculateCurvatureAtDistance(
        curvatureRate,
        curvatureDirection,
        leadDistance,
        size.width
    )
    // 前车X位置 = 车辆中心 + 曲率偏移（确保前车在车道中心线上）
    val leadX = centerX + curvatureAtDistance
    
    val vehicleWidth = (laneWidth * 0.6f) * (1f - normalizedDistance * 0.4f)
    val aspectRatio = if (carBitmap != null) carBitmap.height.toFloat() / carBitmap.width.toFloat() else 1.6f
    val vehicleHeight = vehicleWidth * aspectRatio
    
    // 绘制车辆阴影
    drawOval(
        color = Color.Black.copy(alpha = 0.2f * (1f - normalizedDistance * 0.5f)),
        topLeft = Offset(leadX - vehicleWidth / 2f - 2f, leadY + vehicleHeight / 2f + 2f),
        size = Size(vehicleWidth + 4f, 12f * (1f - normalizedDistance * 0.3f))
    )
    
    // 🆕 使用车辆图片绘制前车
    if (carBitmap != null) {
        // 绘制车辆图片（图片本身已包含所有视觉效果，无需额外绘制边框或车窗）
        drawImage(
            image = carBitmap,
            dstSize = androidx.compose.ui.unit.IntSize(
                vehicleWidth.toInt(),
                vehicleHeight.toInt()
            ),
            dstOffset = androidx.compose.ui.unit.IntOffset(
                (leadX - vehicleWidth / 2f).toInt(),
                (leadY - vehicleHeight / 2f).toInt()
            ),
            alpha = 1.0f,
            blendMode = BlendMode.SrcOver,
            filterQuality = FilterQuality.High
        )
    } else {
        // 回退方案：如果没有车辆图片，使用简化的颜色矩形（仅用于开发调试）
        val vehicleColor = when {
            vRel < -5f -> Color(0xFFEF4444) // 接近过快，红色
            vRel < -2f -> Color(0xFFF59E0B) // 接近中等，橙色
            else -> Color(0xFF10B981) // 安全，绿色
        }
        
        // 绘制车辆主体（渐变）
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    vehicleColor.copy(alpha = 0.9f),
                    vehicleColor,
                    vehicleColor.copy(alpha = 0.8f)
                )
            ),
            topLeft = Offset(leadX - vehicleWidth / 2f, leadY - vehicleHeight / 2f),
            size = Size(vehicleWidth, vehicleHeight)
        )
        
        // 绘制车辆轮廓
        drawRect(
            color = vehicleColor.copy(alpha = 0.5f),
            topLeft = Offset(leadX - vehicleWidth / 2f, leadY - vehicleHeight / 2f),
            size = Size(vehicleWidth, vehicleHeight),
            style = Stroke(width = 2.dp.toPx())
        )
    }
    
    // 🆕 在前车图片下方绘制"车速/距离"文本
    val textY = leadY + vehicleHeight / 2f + 8f
    val fontSize = 10.dp.toPx() * (1f - normalizedDistance * 0.2f).coerceIn(0.7f, 1f)  // 根据距离调整字体大小
    val text = if (leadSpeedKmh > 0.1f) {
        "${leadSpeedKmh.toInt()}km/h / ${leadDistance.toInt()}m"
    } else {
        "${leadDistance.toInt()}m"
    }
    
    // 绘制文本背景（半透明圆角矩形）
    val textPadding = 4.dp.toPx()
    val textWidth = text.length * fontSize * 0.6f  // 估算文本宽度
    drawRoundRect(
        color = Color(0xFF1E293B).copy(alpha = 0.85f),
        topLeft = Offset(leadX - textWidth / 2f - textPadding, textY - fontSize / 2f - textPadding),
        size = Size(textWidth + textPadding * 2f, fontSize + textPadding * 2f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
    )
    
    // 绘制文本
    drawContext.canvas.nativeCanvas.apply {
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = fontSize
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        drawText(text, leadX, textY + fontSize / 3f, paint)
    }
}

/**
 * 绘制当前车辆（优化版，3D效果）
 * 🆕 优化：在车辆图片下方显示车速文本或超车提示
 */
private fun DrawScope.drawCurrentVehicle(
    centerX: Float,
    laneWidth: Float,
    carBitmap: ImageBitmap?,
    vEgoKmh: Float,
    data: XiaogeVehicleData?,
    context: Context?
) {
    val height = size.height
    
    val vehicleWidth = laneWidth * 0.9f
    val aspectRatio = if (carBitmap != null) carBitmap.height.toFloat() / carBitmap.width.toFloat() else 1.8f
    val vehicleHeight = vehicleWidth * aspectRatio
    val vehicleY = height - vehicleHeight / 2f - 24f
    
    // 地面阴影（更轻、更小，避免显得一块黑色区域）
    if (carBitmap == null) {
        // 仅在无图片回退时绘制明显阴影
        drawOval(
            color = Color.Black.copy(alpha = 0.22f),
            topLeft = Offset(centerX - vehicleWidth / 2f - 6f, vehicleY + vehicleHeight / 2f + 6f),
            size = Size(vehicleWidth + 12f, 20f)
        )
    } else {
        // 使用更轻的阴影以配合位图自带阴影/高光
        drawOval(
            color = Color.Black.copy(alpha = 0.12f),
            topLeft = Offset(centerX - vehicleWidth / 2f - 4f, vehicleY + vehicleHeight / 2f + 4f),
            size = Size(vehicleWidth + 8f, 16f)
        )
    }
    
    if (carBitmap != null) {
        // 🆕 绘制车辆图片（从后俯视）
        drawImage(
            image = carBitmap,
            dstSize = androidx.compose.ui.unit.IntSize(
                vehicleWidth.toInt(),
                vehicleHeight.toInt()
            ),
            dstOffset = androidx.compose.ui.unit.IntOffset(
                (centerX - vehicleWidth / 2f).toInt(),
                (vehicleY - vehicleHeight / 2f).toInt()
            ),
            alpha = 1.0f,
            blendMode = BlendMode.SrcOver,
            filterQuality = FilterQuality.High
        )
    } else {
        // 资源缺失时的回退：绘制简化的蓝色渐变车身
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF60A5FA),
                    Color(0xFF3B82F6),
                    Color(0xFF2563EB)
                )
            ),
            topLeft = Offset(centerX - vehicleWidth / 2f, vehicleY - vehicleHeight / 2f),
            size = Size(vehicleWidth, vehicleHeight)
        )
        drawRect(
            color = Color(0xFF1E40AF),
            topLeft = Offset(centerX - vehicleWidth / 2f, vehicleY - vehicleHeight / 2f),
            size = Size(vehicleWidth, vehicleHeight),
            style = Stroke(width = 2.5.dp.toPx())
        )
    }
    
    // 🆕 在当前车辆图片下方绘制车速文本或超车提示
    val textY = vehicleY + vehicleHeight / 2f + 10f
    val fontSize = 12.dp.toPx()
    
    // 🎯 根据超车状态和模式决定显示内容
    val overtakeStatus = data?.overtakeStatus
    val displayText: String
    val textColor: Color
    val backgroundColor: Color
    
    if (overtakeStatus != null && context != null) {
        // 获取超车模式
        val prefs = context.getSharedPreferences("CarrotAmap", Context.MODE_PRIVATE)
        val overtakeMode = prefs.getInt("overtake_mode", 0)
        
        // 检查是否正在变道中（通过 laneChangeState 或 statusText）
        val laneChangeState = data.modelV2?.meta?.laneChangeState ?: 0
        val isLaneChanging = laneChangeState != 0
        val statusText = overtakeStatus.statusText
        
        when {
            // 自动变道中（模式2且正在变道或状态为"变道中"）
            overtakeMode == 2 && (isLaneChanging || statusText == "变道中") -> {
                displayText = "自动变道超车请注意安全"
                textColor = Color(0xFF22C55E)  // 绿色
                backgroundColor = Color(0xFF22C55E).copy(alpha = 0.2f)
            }
            // 拨杆模式且可超车（状态为"可超车"或 canOvertake 为 true）
            overtakeMode == 1 && (overtakeStatus.canOvertake || statusText == "可超车") -> {
                displayText = "变道超车 请拨杆确认"
                textColor = Color(0xFF3B82F6)  // 蓝色
                backgroundColor = Color(0xFF3B82F6).copy(alpha = 0.2f)
            }
            // 默认显示车速
            else -> {
                displayText = "${vEgoKmh.toInt()}km/h"
                textColor = Color.White
                backgroundColor = Color(0xFF1E293B).copy(alpha = 0.9f)
            }
        }
    } else {
        // 默认显示车速
        displayText = "${vEgoKmh.toInt()}km/h"
        textColor = Color.White
        backgroundColor = Color(0xFF1E293B).copy(alpha = 0.9f)
    }
    
    // 绘制文本背景（半透明圆角矩形）
    val textPadding = 5.dp.toPx()
    val textWidth = displayText.length * fontSize * 0.6f  // 估算文本宽度
    drawRoundRect(
        color = backgroundColor,
        topLeft = Offset(centerX - textWidth / 2f - textPadding, textY - fontSize / 2f - textPadding),
        size = Size(textWidth + textPadding * 2f, fontSize + textPadding * 2f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())
    )
    
    // 绘制文本
    drawContext.canvas.nativeCanvas.apply {
        val paint = android.graphics.Paint().apply {
            color = textColor.toArgb()
            textSize = fontSize
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        drawText(displayText, centerX, textY + fontSize / 3f, paint)
    }
}

