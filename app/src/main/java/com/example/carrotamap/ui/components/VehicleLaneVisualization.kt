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
import kotlin.math.abs
import kotlin.math.ln
import android.content.SharedPreferences
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.text.TextStyle
import kotlin.math.min

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
                            data = data,
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
                            Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                            ) {
                                drawLaneVisualization(data)
                            }
                        }
                        
                        // 数据信息面板（底部显示）
                        DataInfoPanel(
                            data = data,
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
 */
@Composable
private fun TopBar(
    data: XiaogeVehicleData?,
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
 */
private fun DrawScope.drawLaneVisualization(data: XiaogeVehicleData?) {
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
    
    // 车道线X位置
    val lane1X = centerX - laneWidth * 1.5f
    val lane2X = centerX - laneWidth * 0.5f
    val lane3X = centerX + laneWidth * 0.5f
    val lane4X = centerX + laneWidth * 1.5f
    
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
    
    // 计算曲率偏移
    val curvatureOffset = calculateCurvatureOffset(curvatureRate, curvatureDirection, width, vEgo)
    
    // 绘制距离标记
    drawDistanceMarkers(centerX, laneWidth * 1.5f)
    
    // 绘制车道线（虚线样式）
    val leftLaneProb = data?.modelV2?.laneLineProbs?.getOrNull(0) ?: 0f
    val rightLaneProb = data?.modelV2?.laneLineProbs?.getOrNull(1) ?: 0f
    
    drawDashedLaneLine(lane1X, curvatureOffset, Color(0xFF64748B).copy(alpha = 0.4f))
    drawDashedLaneLine(lane2X, curvatureOffset, Color(0xFFFBBF24).copy(alpha = leftLaneProb.coerceIn(0.3f, 1f)))
    drawDashedLaneLine(lane3X, curvatureOffset, Color(0xFFFBBF24).copy(alpha = rightLaneProb.coerceIn(0.3f, 1f)))
    drawDashedLaneLine(lane4X, curvatureOffset, Color(0xFF64748B).copy(alpha = 0.4f))
    
    // 绘制前车
    data?.modelV2?.lead0?.let { lead0 ->
        if (lead0.prob > 0.5f && lead0.x > 0f) {
            drawLeadVehicle(
                leadDistance = lead0.x,
                centerX = centerX,
                laneWidth = laneWidth,
                curvatureOffset = curvatureOffset,
                vRel = data.radarState?.leadOne?.vRel ?: 0f
            )
        }
    }
    
    // 绘制当前车辆
    drawCurrentVehicle(centerX, laneWidth)
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
 * 绘制虚线车道线
 */
private fun DrawScope.drawDashedLaneLine(
    laneX: Float,
    curvatureOffset: Float,
    color: Color
) {
    val height = size.height
    val dashLength = 20f
    val gapLength = 15f
    val totalLength = dashLength + gapLength
    val segments = (height / totalLength).toInt()
    
    for (i in 0..segments) {
        val startY = height - (i * totalLength)
        val endY = (startY - dashLength).coerceAtLeast(0f)
        
        if (startY >= 0) {
            val progress = 1f - (startY / height)
            val currentOffset = curvatureOffset * progress
            
            val path = Path().apply {
                moveTo(laneX + currentOffset * (1f - progress), startY)
                lineTo(laneX + currentOffset, endY)
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
    }
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
 */
private fun DrawScope.drawLeadVehicle(
    leadDistance: Float,
    centerX: Float,
    laneWidth: Float,
    curvatureOffset: Float,
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
    val leadX = centerX + curvatureOffset * normalizedDistance
    
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
    laneWidth: Float
) {
    val height = size.height
    
    val vehicleWidth = laneWidth * 0.65f
    val vehicleHeight = vehicleWidth * 1.8f
    val vehicleY = height - vehicleHeight / 2f - 30f
    
    // 绘制车辆阴影（地面投影）
    drawOval(
        color = Color.Black.copy(alpha = 0.4f),
        topLeft = Offset(centerX - vehicleWidth / 2f - 5f, vehicleY + vehicleHeight / 2f + 5f),
        size = Size(vehicleWidth + 10f, 20f)
    )
    
    // 绘制车辆主体（蓝色渐变）
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
    
    // 绘制车辆高光
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color.Transparent,
                Color.White.copy(alpha = 0.2f),
                Color.Transparent
            )
        ),
        topLeft = Offset(centerX - vehicleWidth / 2f, vehicleY - vehicleHeight / 2f),
        size = Size(vehicleWidth, vehicleHeight * 0.6f)
    )
    
    // 绘制车辆轮廓
    drawRect(
        color = Color(0xFF1E40AF),
        topLeft = Offset(centerX - vehicleWidth / 2f, vehicleY - vehicleHeight / 2f),
        size = Size(vehicleWidth, vehicleHeight),
        style = Stroke(width = 2.5.dp.toPx())
    )
    
    // 绘制前挡风玻璃
    val windshieldWidth = vehicleWidth * 0.7f
    val windshieldHeight = vehicleHeight * 0.2f
    drawRect(
        color = Color(0xFF1E293B).copy(alpha = 0.6f),
        topLeft = Offset(centerX - windshieldWidth / 2f, vehicleY - vehicleHeight / 2f + 10f),
        size = Size(windshieldWidth, windshieldHeight)
    )
    
    // 绘制车灯
    val lightRadius = 4f
    drawCircle(
        color = Color(0xFFFEF08A),
        radius = lightRadius,
        center = Offset(centerX - vehicleWidth / 3f, vehicleY - vehicleHeight / 2f + 5f)
    )
    drawCircle(
        color = Color(0xFFFEF08A),
        radius = lightRadius,
        center = Offset(centerX + vehicleWidth / 3f, vehicleY - vehicleHeight / 2f + 5f)
    )
    
    // 绘制车灯光晕
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFFEF08A).copy(alpha = 0.3f),
                Color.Transparent
            )
        ),
        radius = lightRadius * 2.5f,
        center = Offset(centerX - vehicleWidth / 3f, vehicleY - vehicleHeight / 2f + 5f)
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFFEF08A).copy(alpha = 0.3f),
                Color.Transparent
            )
        ),
        radius = lightRadius * 2.5f,
        center = Offset(centerX + vehicleWidth / 3f, vehicleY - vehicleHeight / 2f + 5f)
    )
}

/**
 * 数据信息面板（优化版）
 */
@Composable
private fun DataInfoPanel(
    data: XiaogeVehicleData?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 主要数据卡片（速度和前车信息）
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
        
        // 第二行：速度差、前车加速度、第二前车（压缩布局）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 速度差卡片 (vRel)
            val vRelKmh = (data?.radarState?.leadOne?.vRel ?: 0f) * 3.6f
            MetricCard(
                label = "速度差",
                value = "${vRelKmh.toInt()}",
                unit = "km/h",
                color = if (vRelKmh < -5f) Color(0xFFEF4444) else Color(0xFF059669),
                modifier = Modifier.weight(1f)
            )
            
            // 前车加速度卡片 (lead0.a)
            val lead0Accel = data?.modelV2?.lead0?.a ?: 0f
            val accelText = if (abs(lead0Accel) > 0.01f) {
                String.format("%.2f", lead0Accel)
            } else {
                "0.00"
            }
            val accelColor = when {
                lead0Accel < -0.5f -> Color(0xFFEF4444) // 明显减速
                lead0Accel < -0.1f -> Color(0xFFF59E0B) // 轻微减速
                lead0Accel > 0.5f -> Color(0xFF22C55E)  // 明显加速
                else -> Color(0xFF94A3B8)               // 平稳
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
                        text = "前车加速度",
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "$accelText m/s²",
                        fontSize = 11.sp,
                        color = accelColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // 第二前车卡片 (lead1)
            val lead1 = data?.modelV2?.lead1
            val lead1Distance = lead1?.x ?: 0f
            val lead1Prob = lead1?.prob ?: 0f
            val lead1Text = if (lead1Prob > 0.5f && lead1Distance > 0.1f) {
                "${lead1Distance.toInt()}m"
            } else {
                "--"
            }
            val lead1Color = when {
                lead1Distance > 150f -> Color(0xFF10B981) // 空间充足
                lead1Distance > 100f -> Color(0xFFF59E0B) // 空间一般
                lead1Distance > 0.1f -> Color(0xFFEF4444) // 空间不足
                else -> Color(0xFF64748B)                // 无检测
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
                        text = "第二前车",
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = lead1Text,
                        fontSize = 11.sp,
                        color = lead1Color,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        
        // 详细信息卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E293B).copy(alpha = 0.6f)
            ),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp) // 减小行间距，压缩布局
            ) {
                // 第一行：车道线置信度、车道宽度、路边距离（压缩布局）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val leftLaneProb = data?.modelV2?.laneLineProbs?.getOrNull(0) ?: 0f
                    val rightLaneProb = data?.modelV2?.laneLineProbs?.getOrNull(1) ?: 0f
                    
                    InfoItem(
                        label = "车道线",
                        value = "L:${(leftLaneProb * 100).toInt()}% R:${(rightLaneProb * 100).toInt()}%",
                        valueColor = if (leftLaneProb > 0.7f && rightLaneProb > 0.7f) Color(0xFF059669) else Color(0xFFF59E0B),
                        modifier = Modifier.weight(1f)
                    )
                    
                    val laneWidthLeft = data?.modelV2?.meta?.laneWidthLeft ?: 0f
                    val laneWidthRight = data?.modelV2?.meta?.laneWidthRight ?: 0f
                    InfoItem(
                        label = "车道宽度",
                        value = "L:${String.format("%.1f", laneWidthLeft)}m R:${String.format("%.1f", laneWidthRight)}m",
                        valueColor = Color(0xFF94A3B8),
                        modifier = Modifier.weight(1f)
                    )
                    
                    // 路边距离
                    val distToEdgeLeft = data?.modelV2?.meta?.distanceToRoadEdgeLeft ?: 0f
                    val distToEdgeRight = data?.modelV2?.meta?.distanceToRoadEdgeRight ?: 0f
                    InfoItem(
                        label = "路边距离",
                        value = "L:${String.format("%.1f", distToEdgeLeft)}m R:${String.format("%.1f", distToEdgeRight)}m",
                        valueColor = Color(0xFF94A3B8),
                        modifier = Modifier.weight(1f)
                    )
                }
                
                Divider(color = Color(0xFF475569).copy(alpha = 0.3f), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 2.dp))
                
                // 第二行：限速信息、道路类型、侧方车辆（压缩布局）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val roadLimitSpeed = data?.carrotMan?.nRoadLimitSpeed ?: 0
                    InfoItem(
                        label = "限速",
                        value = if (roadLimitSpeed > 0) "$roadLimitSpeed km/h" else "未知",
                        valueColor = if (roadLimitSpeed > 0) Color(0xFF8B5CF6) else Color(0xFF64748B),
                        modifier = Modifier.weight(1f)
                    )
                    
                    // 道路类型
                    val roadcate = data?.carrotMan?.roadcate ?: 0
                    val roadTypeText = when(roadcate) {
                        1 -> "高速"
                        2 -> "快速路"
                        else -> "普通道路"
                    }
                    InfoItem(
                        label = "道路类型",
                        value = roadTypeText,
                        valueColor = if (roadcate in 1..2) Color(0xFF059669) else Color(0xFF94A3B8),
                        modifier = Modifier.weight(1f)
                    )
                    
                    // 侧方车辆状态
                    val leadLeft = data?.radarState?.leadLeft?.status == true
                    val leadRight = data?.radarState?.leadRight?.status == true
                    val sideVehicleText = buildString {
                        append("左")
                        append(if (leadLeft) "有车" else "无")
                        append(" 右")
                        append(if (leadRight) "有车" else "无")
                    }
                    InfoItem(
                        label = "侧方车辆",
                        value = sideVehicleText,
                        valueColor = if (leadLeft || leadRight) Color(0xFFF59E0B) else Color(0xFF059669),
                        modifier = Modifier.weight(1f)
                    )
                }
                
                Divider(color = Color(0xFF475569).copy(alpha = 0.3f), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 2.dp))
                
                // 第三行：安全状态（盲区、车道线类型、变道状态）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val leftBlindspot = data?.carState?.leftBlindspot == true
                    val rightBlindspot = data?.carState?.rightBlindspot == true
                    val blindspotText = buildString {
                        append("左")
                        append(if (leftBlindspot) "✗" else "✓")
                        append(" 右")
                        append(if (rightBlindspot) "✗" else "✓")
                    }
                    InfoItem(
                        label = "盲区",
                        value = blindspotText,
                        valueColor = if (leftBlindspot || rightBlindspot) Color(0xFFEF4444) else Color(0xFF059669),
                        modifier = Modifier.weight(1f)
                    )
                    
                    val leftLaneLine = data?.carState?.leftLaneLine ?: 0
                    val rightLaneLine = data?.carState?.rightLaneLine ?: 0
                    val laneLineType = buildString {
                        append(if (leftLaneLine == 0) "虚线" else "实线")
                        append("/")
                        append(if (rightLaneLine == 0) "虚线" else "实线")
                    }
                    InfoItem(
                        label = "车道线类型",
                        value = laneLineType,
                        valueColor = if (leftLaneLine == 0 && rightLaneLine == 0) Color(0xFF059669) else Color(0xFFF59E0B),
                        modifier = Modifier.weight(1f)
                    )
                    
                    // 变道状态
                    val laneChangeState = data?.modelV2?.meta?.laneChangeState ?: 0
                    val laneChangeDirection = data?.modelV2?.meta?.laneChangeDirection ?: 0
                    val laneChangeText = when(laneChangeState) {
                        0 -> "未变道"
                        1 -> if (laneChangeDirection > 0) "左变道中" else if (laneChangeDirection < 0) "右变道中" else "变道中"
                        else -> "状态:$laneChangeState"
                    }
                    InfoItem(
                        label = "变道状态",
                        value = laneChangeText,
                        valueColor = if (laneChangeState == 0) Color(0xFF94A3B8) else Color(0xFF3B82F6),
                        modifier = Modifier.weight(1f)
                    )
                }
                
                Divider(color = Color(0xFF475569).copy(alpha = 0.3f), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 2.dp))
                
                // 第四行：曲率信息和系统状态
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val curvatureRate = data?.modelV2?.curvature?.maxOrientationRate ?: 0f
                    val curvatureDirection = data?.modelV2?.curvature?.direction ?: 0
                    val curvatureText = if (abs(curvatureRate) < 0.02f) {
                        "直道"
                    } else {
                        val direction = if (curvatureDirection > 0) "左转" else "右转"
                        "$direction ${String.format("%.3f", abs(curvatureRate))}"
                    }
                    InfoItem(
                        label = "弯道",
                        value = curvatureText,
                        valueColor = if (abs(curvatureRate) < 0.02f) Color(0xFF059669) else Color(0xFFF59E0B),
                        modifier = Modifier.weight(1f)
                    )
                    
                    val enabled = data?.systemState?.enabled == true
                    val active = data?.systemState?.active == true
                    val systemText = if (enabled && active) "激活" else "待机"
                    InfoItem(
                        label = "系统",
                        value = systemText,
                        valueColor = if (enabled && active) Color(0xFF059669) else Color(0xFF94A3B8),
                        modifier = Modifier.weight(1f)
                    )
                    
                    val hasLead = data?.longitudinalPlan?.hasLead == true || 
                                  (data?.radarState?.leadOne?.status == true)
                    InfoItem(
                        label = "前车",
                        value = if (hasLead) "检测到" else "无",
                        valueColor = if (hasLead) Color(0xFFF59E0B) else Color(0xFF94A3B8),
                        modifier = Modifier.weight(1f)
                    )
                }
                
                Divider(color = Color(0xFF475569).copy(alpha = 0.3f), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 2.dp))
                
                // 第五行：超车模式
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val prefs = context.getSharedPreferences("CarrotAmap", android.content.Context.MODE_PRIVATE)
                    val overtakeMode = prefs.getInt("overtake_mode", 0)
                    val overtakeModeNames = arrayOf("禁止超车", "拨杆超车", "自动超车")
                    val overtakeModeColors = arrayOf(
                        Color(0xFF94A3B8),
                        Color(0xFF3B82F6),
                        Color(0xFF22C55E)
                    )
                    InfoItem(
                        label = "超车模式",
                        value = overtakeModeNames[overtakeMode],
                        valueColor = overtakeModeColors[overtakeMode],
                        modifier = Modifier.weight(1f)
                    )
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
