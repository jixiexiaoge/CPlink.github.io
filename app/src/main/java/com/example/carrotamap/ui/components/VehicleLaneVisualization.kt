package com.example.carrotamap.ui.components

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.example.carrotamap.XiaogeVehicleData
import com.example.carrotamap.R
import kotlin.math.abs
import kotlin.math.ln
import android.content.SharedPreferences
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.text.TextStyle
import kotlin.math.min
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import kotlinx.coroutines.delay

/**
 * 车辆和车道可视化弹窗组件 - 优化版
 * 绘制4条车道线（3个车道），当前车辆，前车，曲率弯曲，盲区高亮
 * 并显示核心数据信息
 * 只有用户类型3（赞助者）或4（铁粉）才自动显示
 */
@Composable
fun VehicleLaneVisualization(
    data: XiaogeVehicleData?,
    userType: Int,
    showDialog: Boolean, // 改为必需参数，由外部控制
    onDismiss: () -> Unit, // 改为必需参数，添加关闭回调
    modifier: Modifier = Modifier
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
            val screenWidth = context.resources.displayMetrics.widthPixels
            val dialogWidth = with(density) { (screenWidth * 0.9f).toDp() }  // 宽度为屏幕的90%
            
            // 🆕 数据更新频率控制：限制为10Hz（每100ms更新一次）
            var displayData by remember { mutableStateOf(data) }
            LaunchedEffect(data) {
                delay(100) // 限制为10Hz
                displayData = data
            }
            
            // 🆕 数据一致性检查：计算数据年龄和延迟
            val currentTime = System.currentTimeMillis()
            val dataTimestamp = (displayData?.timestamp ?: 0.0) * 1000.0 // 转换为毫秒
            val dataAge = currentTime - dataTimestamp.toLong()
            val isDataStale = dataAge > 500 // 超过500ms认为数据延迟
            
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
                        // 顶部标题栏
                        TopBar(
                            data = displayData,
                            dataAge = dataAge,
                            isDataStale = isDataStale,
                            onClose = onDismiss
                        )
                        
                        // 车道可视化画布（占据较小区域）
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp), // 使用固定高度，减少占用空间
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF1E293B).copy(alpha = 0.6f)
                            ),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                        ) {
                            // 🆕 优化车辆图片资源处理：支持多种格式和分辨率
                            val carBitmap: ImageBitmap? = remember(context) {
                                runCatching {
                                    // 优先尝试加载 drawable 资源
                                    var resId = context.resources.getIdentifier("car", "drawable", context.packageName)
                                    if (resId == 0) {
                                        // 如果 drawable 不存在，尝试 mipmap
                                        resId = context.resources.getIdentifier("car", "mipmap", context.packageName)
                                    }
                                    if (resId != 0) {
                                        ImageBitmap.imageResource(context.resources, resId)
                                    } else {
                                        null
                                    }
                                }.getOrNull()
                            }
                            
                            // 🆕 性能优化：缓存曲率偏移计算（只在曲率变化时重新计算）
                            val curvature = displayData?.modelV2?.curvature
                            val curvatureRate = curvature?.maxOrientationRate ?: 0f
                            val curvatureDirection = curvature?.direction ?: 0
                            val vEgo = displayData?.carState?.vEgo ?: 20f
                            
                            // 🆕 性能优化：使用 remember 缓存曲率偏移（在 Composable 层）
                            val cachedCurvatureOffset = remember(curvatureRate, curvatureDirection, vEgo) {
                                // 使用固定宽度作为参考（实际绘制时会使用实际宽度）
                                // 这里只是预计算，实际绘制时会根据实际 size.width 调整
                                calculateCurvatureOffset(
                                    curvatureRate,
                                    curvatureDirection,
                                    400f, // 使用固定参考宽度
                                    vEgo
                                )
                            }
                            
                            Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                            ) {
                                // 根据实际宽度调整曲率偏移
                                val actualWidth = size.width
                                val curvatureOffset = if (actualWidth > 0f) {
                                    cachedCurvatureOffset * (actualWidth / 400f)
                                } else {
                                    cachedCurvatureOffset
                                }
                                drawLaneVisualization(displayData, carBitmap, curvatureOffset)
                            }
                        }
                        
                        // 数据信息面板（底部显示）
                        DataInfoPanel(
                            data = displayData,
                            dataAge = dataAge,
                            isDataStale = isDataStale,
                            modifier = Modifier
                                .fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

/**
 * 顶部标题栏
 * 🆕 优化：添加超车状态指示和数据延迟显示
 */
@Composable
private fun TopBar(
    data: XiaogeVehicleData?,
    dataAge: Long,
    isDataStale: Boolean,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧标题
        Column {
            Text(
                text = "智能驾驶视图",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Bird's Eye View",
                fontSize = 11.sp,
                color = Color(0xFF94A3B8),
                fontWeight = FontWeight.Light
            )
        }
        
        // 右侧系统状态和关闭按钮
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 🆕 超车状态指示器
            val laneChangeState = data?.modelV2?.meta?.laneChangeState ?: 0
            val overtakeStatus = data?.overtakeStatus
            val overtakeStatusText = when {
                laneChangeState != 0 -> {
                    val direction = when (data?.modelV2?.meta?.laneChangeDirection) {
                        -1 -> "左"
                        1 -> "右"
                        else -> ""
                    }
                    "变道中($direction)"
                }
                overtakeStatus != null -> overtakeStatus.statusText
                else -> "监控中"
            }
            val overtakeStatusColor = when {
                laneChangeState != 0 -> Color(0xFF3B82F6)  // 变道中：蓝色
                overtakeStatus?.canOvertake == true -> Color(0xFF10B981)  // 可超车：绿色
                overtakeStatus?.cooldownRemaining != null && overtakeStatus.cooldownRemaining > 0 -> Color(0xFFF59E0B)  // 冷却中：橙色
                else -> Color(0xFF94A3B8)  // 监控中：灰色
            }
            
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                color = overtakeStatusColor.copy(alpha = 0.2f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = overtakeStatusColor,
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    )
                    Column {
                        Text(
                            text = overtakeStatusText,
                            fontSize = 11.sp,
                            color = overtakeStatusColor,
                            fontWeight = FontWeight.Medium
                        )
                        // 显示冷却时间（如果有）
                        overtakeStatus?.cooldownRemaining?.let { cooldown ->
                            if (cooldown > 0) {
                                Text(
                                    text = "冷却: ${(cooldown / 1000.0).toInt()}s",
                                    fontSize = 9.sp,
                                    color = Color(0xFF94A3B8),
                                    fontWeight = FontWeight.Light
                                )
                            }
                        }
                    }
                }
            }
            
            // 系统状态指示器
            val enabled = data?.systemState?.enabled == true
            val active = data?.systemState?.active == true
            
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                color = if (enabled && active) 
                    Color(0xFF10B981).copy(alpha = 0.2f) 
                else 
                    Color(0xFF64748B).copy(alpha = 0.2f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = if (enabled && active) Color(0xFF10B981) else Color(0xFF64748B),
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    )
                    Text(
                        text = if (enabled && active) "激活" else "待机",
                        fontSize = 12.sp,
                        color = if (enabled && active) Color(0xFF10B981) else Color(0xFF94A3B8),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            // 🆕 数据延迟指示器
            if (isDataStale) {
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    color = Color(0xFFEF4444).copy(alpha = 0.2f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = Color(0xFFEF4444),
                                    shape = androidx.compose.foundation.shape.CircleShape
                                )
                        )
                        Text(
                            text = "延迟: ${dataAge}ms",
                            fontSize = 10.sp,
                            color = Color(0xFFEF4444),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            
            // 关闭按钮
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = Color(0xFF334155)
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * 绘制车道可视化（优化版）
 * 🆕 性能优化：使用缓存的曲率偏移
 */
private fun DrawScope.drawLaneVisualization(
    data: XiaogeVehicleData?, 
    carBitmap: ImageBitmap?,
    cachedCurvatureOffset: Float
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
    
    // 车道线底部和顶部的X位置（加入透视收敛效果）
    val perspectiveScaleTop = 0.6f
    val laneWidthTop = laneWidth * perspectiveScaleTop
    // 底部（靠近用户）更宽，顶部更窄，营造后俯视透视
    val lane1BottomX = centerX - laneWidth * 1.5f
    val lane2BottomX = centerX - laneWidth * 0.5f
    val lane3BottomX = centerX + laneWidth * 0.5f
    val lane4BottomX = centerX + laneWidth * 1.5f
    val lane1TopX = centerX - laneWidthTop * 1.5f
    val lane2TopX = centerX - laneWidthTop * 0.5f
    val lane3TopX = centerX + laneWidthTop * 0.5f
    val lane4TopX = centerX + laneWidthTop * 1.5f
    
    // 获取数据
    val curvature = data?.modelV2?.curvature
    val curvatureRate = curvature?.maxOrientationRate ?: 0f
    val curvatureDirection = curvature?.direction ?: 0
    val vEgo = data?.carState?.vEgo ?: 20f
    
    // 绘制盲区高亮
    drawLaneBackgrounds(
        leftBlindspot = data?.carState?.leftBlindspot == true,
        rightBlindspot = data?.carState?.rightBlindspot == true,
        laneWidth = laneWidth,
        centerX = centerX,
        width = width,
        height = height
    )
    
    // 🆕 性能优化：使用缓存的曲率偏移（已在外部计算）
    val curvatureOffset = cachedCurvatureOffset
    
    // 绘制距离标记
    drawDistanceMarkers(centerX, laneWidth * 1.5f)
    
    // 🆕 绘制弯曲车道线（根据曲率逐点弯曲）
    val leftLaneProb = data?.modelV2?.laneLineProbs?.getOrNull(0) ?: 0f
    val rightLaneProb = data?.modelV2?.laneLineProbs?.getOrNull(1) ?: 0f
    
    drawPerspectiveCurvedLaneLine(lane1BottomX, lane1TopX, curvatureRate, curvatureDirection, Color(0xFF64748B).copy(alpha = 0.5f))
    drawPerspectiveCurvedLaneLine(lane2BottomX, lane2TopX, curvatureRate, curvatureDirection, Color(0xFFFBBF24).copy(alpha = leftLaneProb.coerceIn(0.5f, 1f)))
    drawPerspectiveCurvedLaneLine(lane3BottomX, lane3TopX, curvatureRate, curvatureDirection, Color(0xFFFBBF24).copy(alpha = rightLaneProb.coerceIn(0.5f, 1f)))
    drawPerspectiveCurvedLaneLine(lane4BottomX, lane4TopX, curvatureRate, curvatureDirection, Color(0xFF64748B).copy(alpha = 0.5f))
    
    // 绘制前车
    data?.modelV2?.lead0?.let { lead0 ->
        if (lead0.prob > 0.5f && lead0.x > 0f) {
            drawLeadVehicle(
                leadDistance = lead0.x,
                centerX = centerX,
                laneWidth = laneWidth,
                curvatureRate = curvatureRate,
                curvatureDirection = curvatureDirection,
                width = width,
                vRel = data.radarState?.leadOne?.vRel ?: 0f
            )
        }
    }
    
    // 绘制当前车辆
    drawCurrentVehicle(centerX, laneWidth, carBitmap)
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
 * 计算曲率偏移量
 */
private fun calculateCurvatureOffset(
    curvatureRate: Float,
    direction: Int,
    width: Float,
    vEgo: Float = 20f
): Float {
    if (abs(curvatureRate) < 0.01f || vEgo < 0.1f) return 0f
    
    val lateralAccel = abs(curvatureRate) * vEgo
    val curvature = lateralAccel / (vEgo * vEgo)
    
    val maxOffset = width * 0.15f
    val normalizedCurvature = (curvature / 0.02f).coerceIn(0f, 1f)
    val offset = normalizedCurvature * maxOffset
    
    return if (direction > 0) offset else -offset
}

/**
 * 🆕 绘制弯曲车道线（根据曲率逐点弯曲，参考 openpilot 实现）
 * 每个点的偏移量随距离变化，形成真实的曲线效果
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
        val xBase = lerp(laneBottomX, laneTopX, t)
        
        // 🆕 根据距离计算曲率偏移（参考 openpilot 的实现）
        val distance = t * maxDistance
        val curvatureAtDistance = calculateCurvatureAtDistance(
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
 * 🆕 计算特定距离处的曲率偏移（参考 openpilot 的曲率计算）
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
    val curvature = curvatureRate * 0.5f
    val normalizedCurvature = (curvature / 0.02f).coerceIn(-1f, 1f)
    val maxOffset = width * 0.15f
    val offset = normalizedCurvature * distance * distance * 0.01f * maxOffset
    
    return if (direction > 0) offset else -offset
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float {
    return (1 - fraction) * start + fraction * stop
}

/**
 * 绘制车道背景（盲区高亮）
 */
private fun DrawScope.drawLaneBackgrounds(
    leftBlindspot: Boolean,
    rightBlindspot: Boolean,
    laneWidth: Float,
    centerX: Float,
    width: Float,
    height: Float
) {
    val leftLaneLeft = centerX - laneWidth * 1.5f
    val leftLaneRight = centerX - laneWidth * 0.5f
    val rightLaneLeft = centerX + laneWidth * 0.5f
    val rightLaneRight = centerX + laneWidth * 1.5f
    
    if (leftBlindspot) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFEF4444).copy(alpha = 0.1f),
                    Color(0xFFEF4444).copy(alpha = 0.3f),
                    Color(0xFFEF4444).copy(alpha = 0.1f)
                )
            ),
            topLeft = Offset(leftLaneLeft, 0f),
            size = Size(leftLaneRight - leftLaneLeft, height)
        )
    }
    
    if (rightBlindspot) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFEF4444).copy(alpha = 0.1f),
                    Color(0xFFEF4444).copy(alpha = 0.3f),
                    Color(0xFFEF4444).copy(alpha = 0.1f)
                )
            ),
            topLeft = Offset(rightLaneLeft, 0f),
            size = Size(rightLaneRight - rightLaneLeft, height)
        )
    }
}

/**
 * 绘制前车（优化版，带阴影和渐变）
 * 🆕 优化：前车位置也随曲率弯曲
 */
private fun DrawScope.drawLeadVehicle(
    leadDistance: Float,
    centerX: Float,
    laneWidth: Float,
    curvatureRate: Float,
    curvatureDirection: Int,
    width: Float,
    vRel: Float
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
    // 🆕 使用弯曲车道线的曲率计算方式，让前车位置也随距离弯曲
    val curvatureAtDistance = calculateCurvatureAtDistance(
        curvatureRate,
        curvatureDirection,
        leadDistance,
        size.width
    )
    val leadX = centerX + curvatureAtDistance
    
    val vehicleWidth = (laneWidth * 0.6f) * (1f - normalizedDistance * 0.4f)
    val vehicleHeight = vehicleWidth * 1.6f
    
    // 绘制车辆阴影
    drawRect(
        color = Color.Black.copy(alpha = 0.3f * (1f - normalizedDistance * 0.5f)),
        topLeft = Offset(leadX - vehicleWidth / 2f + 4f, leadY - vehicleHeight / 2f + 4f),
        size = Size(vehicleWidth, vehicleHeight)
    )
    
    // 根据相对速度选择颜色
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
    
    // 绘制车窗
    val windowWidth = vehicleWidth * 0.6f
    val windowHeight = vehicleHeight * 0.25f
    drawRect(
        color = Color(0xFF1E293B).copy(alpha = 0.7f),
        topLeft = Offset(leadX - windowWidth / 2f, leadY - windowHeight / 2f),
        size = Size(windowWidth, windowHeight)
    )
    
    // 绘制距离文本背景
    val distanceText = "${leadDistance.toInt()}m"
    drawCircle(
        color = Color(0xFF1E293B).copy(alpha = 0.8f),
        radius = 18f * (1f - normalizedDistance * 0.3f),
        center = Offset(leadX, leadY - vehicleHeight / 2f - 25f)
    )
}

/**
 * 绘制当前车辆（优化版，3D效果）
 */
private fun DrawScope.drawCurrentVehicle(
    centerX: Float,
    laneWidth: Float,
    carBitmap: ImageBitmap?
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
        // 绘制车辆图片（从后俯视）
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
}

/**
 * 数据信息面板（优化版）
 * 🆕 优化：添加数据延迟显示
 */
@Composable
private fun DataInfoPanel(
    data: XiaogeVehicleData?,
    dataAge: Long,
    isDataStale: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 🆕 简化数据信息显示：只保留核心决策数据
        
        // 第一行：速度信息（车速、前车距离、前车速度）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 当前速度卡片 (vEgo)
            val vEgoKmh = (data?.carState?.vEgo ?: 0f) * 3.6f
            MetricCard(
                label = "车速",
                value = "${vEgoKmh.toInt()}",
                unit = "km/h",
                color = Color(0xFF3B82F6),
                modifier = Modifier.weight(1f)
            )
            
            // 前车距离卡片 (dRel)
            val dRel = data?.radarState?.leadOne?.dRel ?: 0f
            if (dRel > 0.1f) {
                MetricCard(
                    label = "前车距离",
                    value = String.format("%.1f", dRel),
                    unit = "m",
                    color = when {
                        dRel < 20f -> Color(0xFFEF4444)
                        dRel < 40f -> Color(0xFFF59E0B)
                        else -> Color(0xFF10B981)
                    },
                    modifier = Modifier.weight(1f)
                )
            } else {
                MetricCard(
                    label = "前车距离",
                    value = "--",
                    unit = "",
                    color = Color(0xFF64748B),
                    modifier = Modifier.weight(1f)
                )
            }
            
            // 前车速度卡片 (vLead)
            val vLeadKmh = (data?.radarState?.leadOne?.vLead ?: 0f) * 3.6f
            if (vLeadKmh > 0.1f) {
                MetricCard(
                    label = "前车",
                    value = "${vLeadKmh.toInt()}",
                    unit = "km/h",
                    color = Color(0xFFFF6B35),
                    modifier = Modifier.weight(1f)
                )
            } else {
                MetricCard(
                    label = "前车",
                    value = "--",
                    unit = "",
                    color = Color(0xFF64748B),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        // 第二行：安全状态（盲区、变道状态、超车模式）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 盲区状态
            val leftBlindspot = data?.carState?.leftBlindspot == true
            val rightBlindspot = data?.carState?.rightBlindspot == true
            val blindspotText = buildString {
                append("左")
                append(if (leftBlindspot) "✗" else "✓")
                append(" 右")
                append(if (rightBlindspot) "✗" else "✓")
            }
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E293B).copy(alpha = 0.8f)
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "盲区",
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = blindspotText,
                        fontSize = 12.sp,
                        color = if (leftBlindspot || rightBlindspot) Color(0xFFEF4444) else Color(0xFF059669),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // 变道状态
            val laneChangeState = data?.modelV2?.meta?.laneChangeState ?: 0
            val laneChangeDirection = data?.modelV2?.meta?.laneChangeDirection ?: 0
            val laneChangeText = when(laneChangeState) {
                0 -> "未变道"
                1 -> if (laneChangeDirection > 0) "左变道中" else if (laneChangeDirection < 0) "右变道中" else "变道中"
                else -> "状态:$laneChangeState"
            }
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E293B).copy(alpha = 0.8f)
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "变道状态",
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = laneChangeText,
                        fontSize = 12.sp,
                        color = if (laneChangeState == 0) Color(0xFF94A3B8) else Color(0xFF3B82F6),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // 超车模式
            val prefs = context.getSharedPreferences("CarrotAmap", android.content.Context.MODE_PRIVATE)
            val overtakeMode = prefs.getInt("overtake_mode", 0)
            val overtakeModeNames = arrayOf("禁止超车", "拨杆超车", "自动超车")
            val overtakeModeColors = arrayOf(
                Color(0xFF94A3B8),
                Color(0xFF3B82F6),
                Color(0xFF22C55E)
            )
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E293B).copy(alpha = 0.8f)
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "超车模式",
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = overtakeModeNames[overtakeMode],
                        fontSize = 12.sp,
                        color = overtakeModeColors[overtakeMode],
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        
        // 🆕 可选第三行：数据延迟警告（仅在异常时显示）
        if (isDataStale) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFEF4444).copy(alpha = 0.2f)
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⚠️",
                        fontSize = 16.sp
                    )
                    Column {
                        Text(
                            text = "数据延迟警告",
                            fontSize = 12.sp,
                            color = Color(0xFFEF4444),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "数据延迟: ${dataAge}ms (超过500ms)",
                            fontSize = 10.sp,
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * 指标卡片组件
 */
@Composable
private fun MetricCard(
    label: String,
    value: String,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E293B).copy(alpha = 0.8f)
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                fontSize = 9.sp,
                color = Color(0xFF94A3B8),
                fontWeight = FontWeight.Medium
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = value,
                    fontSize = 20.sp,
                    color = color,
                    fontWeight = FontWeight.Bold
                )
                if (unit.isNotEmpty()) {
                    Text(
                        text = unit,
                        fontSize = 10.sp,
                        color = color.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 1.dp)
                    )
                }
            }
        }
    }
}

/**
 * 信息项组件
 */
@Composable
private fun InfoItem(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(
            text = label,
            fontSize = 8.sp,
            color = Color(0xFF94A3B8),
            fontWeight = FontWeight.Normal,
            lineHeight = 10.sp
        )
        Text(
            text = value,
            fontSize = 11.sp,
            color = valueColor,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 13.sp
        )
    }
}
