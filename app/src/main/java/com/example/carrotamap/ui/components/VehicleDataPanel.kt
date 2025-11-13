package com.example.carrotamap.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.example.carrotamap.XiaogeVehicleData
import com.example.carrotamap.ui.components.getRoadTypeDescription
import kotlin.math.abs
import kotlin.math.ln

/**
 * 车辆数据面板组件
 * 包含顶部状态栏和数据信息面板
 */
@Composable
fun VehicleDataPanel(
    data: XiaogeVehicleData?,
    dataAge: Long,
    isDataStale: Boolean,
    amapRoadType: Int?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TopBar(
            data = data,
            dataAge = dataAge,
            isDataStale = isDataStale,
            onClose = onClose
        )
        
        DataInfoPanel(
            data = data,
            dataAge = dataAge,
            isDataStale = isDataStale,
            amapRoadType = amapRoadType,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 顶部标题栏
 * 显示超车状态指示和数据延迟显示
 */
@Composable
private fun TopBar(
    data: XiaogeVehicleData?,
    dataAge: Long,
    isDataStale: Boolean,
    onClose: () -> Unit
) {
    val configuration = LocalConfiguration.current
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧：超车状态信息和决策原因
        val laneChangeState = data?.modelV2?.meta?.laneChangeState ?: 0
        val overtakeStatus = data?.overtakeStatus
        val statusText = when {
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
        val statusColor = when {
            laneChangeState != 0 -> Color(0xFF3B82F6)  // 变道中：蓝色
            overtakeStatus?.canOvertake == true -> Color(0xFF10B981)  // 可超车：绿色
            overtakeStatus?.cooldownRemaining != null && overtakeStatus.cooldownRemaining > 0 -> Color(0xFFF59E0B)  // 冷却中：橙色
            else -> Color(0xFF94A3B8)  // 监控中：灰色
        }
        
        // 显示超车决策原因（如果有）
        val blockingReason = overtakeStatus?.blockingReason
        
        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            color = statusColor.copy(alpha = 0.18f)
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .widthIn(max = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 260.dp else Dp.Unspecified),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(
                                color = statusColor,
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    )
                    Text(
                        text = statusText,
                        fontSize = 11.sp,
                        color = statusColor,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                // 显示冷却时间（如果有）
                overtakeStatus?.cooldownRemaining?.let { cooldown ->
                    if (cooldown > 0) {
                        Text(
                            text = "冷却: ${(cooldown / 1000.0).toInt()}s",
                            fontSize = 8.sp,
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.Light,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
                // 显示阻止原因（如果有）
                blockingReason?.let { reason ->
                    Text(
                        text = reason,
                        fontSize = 8.5.sp,
                        color = Color(0xFFEF4444),
                        fontWeight = FontWeight.Normal,
                        lineHeight = 11.sp,
                        maxLines = 3,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }
        
        // 右侧：网络状态和关闭按钮
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 网络连接状态（整合数据延迟警告）
            val isDisconnected = dataAge > 5000
            val networkColor = when {
                isDisconnected -> Color(0xFFEF4444)  // 断开：红色
                isDataStale -> Color(0xFFF59E0B)     // 延迟：橙色
                else -> Color(0xFF10B981)            // 正常：绿色
            }
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                color = networkColor.copy(alpha = 0.2f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                color = networkColor,
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = when {
                                isDisconnected -> "断开"
                                isDataStale -> "延迟"
                                else -> "正常"
                            },
                            fontSize = 10.sp,
                            color = networkColor,
                            fontWeight = FontWeight.Medium
                        )
                        // 当数据延迟太大时，在状态文本下方显示延迟毫秒数
                        if (isDataStale && dataAge > 2000) {
                            Text(
                                text = "${dataAge}ms",
                                fontSize = 8.sp,
                                color = Color(0xFF94A3B8),
                                fontWeight = FontWeight.Normal
                            )
                        }
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
 * 数据信息面板
 * 显示车辆状态、前车信息、系统状态等数据
 */
@Composable
private fun DataInfoPanel(
    data: XiaogeVehicleData?,
    dataAge: Long,
    isDataStale: Boolean,
    amapRoadType: Int?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    
    // 获取屏幕高度（dp）
    val screenHeightDp = configuration.screenHeightDp.dp
    // 定义最小高度阈值：当屏幕高度小于此值时，隐藏第二行卡片
    // 考虑顶部状态栏、第一行卡片、变道进度条等，大约需要 400dp
    val minHeightForFullDisplay = 400.dp
    val canShowSecondRow = screenHeightDp >= minHeightForFullDisplay
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 车道变更进度条（当变道中时显示）
        val laneChangeState = data?.modelV2?.meta?.laneChangeState ?: 0
        if (laneChangeState == 1) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF3B82F6).copy(alpha = 0.2f)
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "变道中...",
                        fontSize = 12.sp,
                        color = Color(0xFF3B82F6),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth()
                    )
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        color = Color(0xFF3B82F6),
                        trackColor = Color(0xFF1E293B)
                    )
                }
            }
        }
        
        // 第一行：前车相对速度、前车状态、系统状态
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 前车相对速度
            val lead0 = data?.modelV2?.lead0
            val leadOne = data?.radarState?.leadOne
            val hasLead = (lead0 != null && lead0.prob > 0.5f && lead0.x > 0f) || (leadOne?.status == true)
            
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
                        text = "相对速度",
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Medium
                    )
                    if (hasLead) {
                        val vRel = leadOne?.vRel ?: 0f
                        val vRelKmh = vRel * 3.6f
                        val vRelText = when {
                            vRel > 2f -> "远离"
                            vRel < -2f -> "接近"
                            else -> "保持"
                        }
                        val vRelValue = String.format("%.1f", abs(vRelKmh))
                        val vRelColor = when {
                            vRel < -5f -> Color(0xFFEF4444)
                            vRel < -2f -> Color(0xFFF59E0B)
                            vRel > 5f -> Color(0xFF3B82F6)
                            else -> Color(0xFF10B981)
                        }
                        Text(
                            text = vRelText,
                            fontSize = 12.sp,
                            color = vRelColor,
                            fontWeight = FontWeight.Bold
                        )
                        if (abs(vRelKmh) > 0.5f) {
                            Text(
                                text = "${if (vRel > 0) "+" else "-"}${vRelValue}km/h",
                                fontSize = 9.sp,
                                color = Color(0xFF94A3B8),
                                fontWeight = FontWeight.Medium
                            )
                        } else {
                            Text(
                                text = "0km/h",
                                fontSize = 9.sp,
                                color = Color(0xFF94A3B8),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        Text(
                            text = "无车",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            // 前车状态
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
                        text = "前车状态",
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Medium
                    )
                    if (hasLead) {
                        val lead0Accel = data?.modelV2?.lead0?.a ?: 0f
                        val leadAccelText = when {
                            lead0Accel > 0.5f -> "加速"
                            lead0Accel < -0.5f -> "减速"
                            else -> "匀速"
                        }
                        val leadAccelColor = when {
                            lead0Accel > 0.5f -> Color(0xFF10B981)
                            lead0Accel < -0.5f -> Color(0xFFEF4444)
                            else -> Color(0xFF94A3B8)
                        }
                        Text(
                            text = leadAccelText,
                            fontSize = 12.sp,
                            color = leadAccelColor,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = "无车",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            // 系统状态
            val enabled = data?.systemState?.enabled == true
            val active = data?.systemState?.active == true
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
                        text = "系统状态",
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Medium
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    color = if (enabled && active) Color(0xFF10B981) else Color(0xFF64748B),
                                    shape = androidx.compose.foundation.shape.CircleShape
                                )
                        )
                        Text(
                            text = if (enabled && active) "激活" else "待机",
                            fontSize = 12.sp,
                            color = if (enabled && active) Color(0xFF10B981) else Color(0xFF94A3B8),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        
        // 第二行：曲率信息、超车设置、道路类型（仅在屏幕高度足够时显示）
        if (canShowSecondRow) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
            // 曲率信息
            val curvature = data?.modelV2?.curvature
            val curvatureRate = curvature?.maxOrientationRate ?: 0f
            val curvatureDirection = curvature?.direction ?: 0
            val curvatureText = when {
                abs(curvatureRate) < 0.01f -> "直道"
                curvatureDirection > 0 -> "左弯"
                curvatureDirection < 0 -> "右弯"
                else -> "直道"
            }
            val curvatureValue = if (abs(curvatureRate) > 0.01f) {
                String.format("%.3f", abs(curvatureRate))
            } else {
                "0.000"
            }
            val curvatureColor = when {
                abs(curvatureRate) < 0.01f -> Color(0xFF94A3B8)
                abs(curvatureRate) < 0.02f -> Color(0xFF3B82F6)
                else -> Color(0xFFF59E0B)
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
                        text = "道路曲率",
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = curvatureText,
                        fontSize = 12.sp,
                        color = curvatureColor,
                        fontWeight = FontWeight.Bold
                    )
                    if (abs(curvatureRate) > 0.01f) {
                        Text(
                            text = curvatureValue,
                            fontSize = 9.sp,
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            
            // 超车设置
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
                        text = "超车设置",
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
            
            // 道路类型
            val roadTypeValue = amapRoadType ?: -1
            val roadTypeDescription = if (roadTypeValue >= 0) {
                getRoadTypeDescription(roadTypeValue)
            } else {
                "未知"
            }
            val roadTypeColor = when (roadTypeValue) {
                0 -> Color(0xFF10B981)
                6 -> Color(0xFF3B82F6)
                else -> Color(0xFF94A3B8)
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
                        text = "道路类型",
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${roadTypeDescription}${if (roadTypeValue >= 0) " ($roadTypeValue)" else ""}",
                        fontSize = 12.sp,
                        color = roadTypeColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            }
        }
    }
}

/**
 * 透视投影工具类
 * 将3D世界坐标（XYZ）转换为2D屏幕坐标
 * 参考 openpilot 的 ModelRenderer.mapToScreen() 实现
 * 
 * 使用简化的对数映射算法，基于距离的对数映射实现透视效果
 */
object PerspectiveProjection {
    // 最大绘制距离（米）
    private const val MAX_DISTANCE = 100f
    
    // 屏幕高度使用比例（0.7表示使用屏幕高度的70%）
    private const val HEIGHT_RATIO = 0.7f
    
    /**
     * 将3D坐标转换为2D屏幕坐标
     * 
     * @param x 距离（米），车辆前方为正
     * @param y 横向偏移（米），左侧为正，右侧为负
     * @param z 高度（米），地面为0
     * @param screenWidth 屏幕宽度（像素）
     * @param screenHeight 屏幕高度（像素）
     * @param centerX 车辆中心X坐标（像素），默认为屏幕中心
     * @return 屏幕坐标，如果坐标无效则返回null
     */
    fun mapToScreen(
        x: Float,
        y: Float,
        z: Float,
        screenWidth: Float,
        screenHeight: Float,
        centerX: Float = screenWidth / 2f
    ): Offset? {
        // 过滤无效坐标（x < 0 表示在车辆后方）
        if (x < 0f) return null
        
        // 计算Y坐标（垂直方向）
        // 使用对数映射实现透视效果：距离越远，Y坐标越小（越靠上）
        val normalizedDistance = (x / MAX_DISTANCE).coerceIn(0f, 1f)
        val logMappedDistance = if (normalizedDistance > 0f) {
            ln(1f + normalizedDistance * 2.718f) / ln(3.718f)
        } else {
            0f
        }
        val screenY = screenHeight * (1f - logMappedDistance) * HEIGHT_RATIO
        
        // 计算X坐标（水平方向）
        // 将横向偏移（米）转换为像素
        // 使用透视缩放：距离越远，横向偏移的像素值越小
        val perspectiveScale = 1f - normalizedDistance * 0.4f  // 远处缩小40%
        val pixelsPerMeter = screenWidth / 8f  // 假设8米宽度对应屏幕宽度
        val screenX = centerX + y * pixelsPerMeter * perspectiveScale
        
        // 检查坐标是否在屏幕范围内
        if (screenX < -screenWidth || screenX > screenWidth * 2f ||
            screenY < -screenHeight || screenY > screenHeight * 2f) {
            return null
        }
        
        return Offset(screenX, screenY)
    }
    
    /**
     * 计算路径长度索引
     * 找到距离数组中第一个大于等于目标距离的索引
     * 
     * @param xArray 距离数组（米），支持 FloatArray
     * @param targetDistance 目标距离（米）
     * @return 索引，如果未找到则返回数组长度-1
     */
    fun getPathLengthIndex(xArray: FloatArray, targetDistance: Float): Int {
        if (xArray.isEmpty()) return 0
        
        for (i in xArray.indices) {
            if (xArray[i] >= targetDistance) {
                return i
            }
        }
        
        // 如果所有距离都小于目标距离，返回最后一个索引
        return xArray.size - 1
    }
    
    /**
     * 线性插值函数
     * 
     * @param x 输入值
     * @param xList X值数组（支持 FloatArray）
     * @param yList Y值数组（支持 FloatArray）
     * @param extrapolate 是否允许外推
     * @return 插值结果
     */
    fun interp(
        x: Float,
        xList: FloatArray,
        yList: FloatArray,
        extrapolate: Boolean = false
    ): Float {
        if (xList.isEmpty() || yList.isEmpty() || xList.size != yList.size) {
            return 0f
        }
        
        // 如果x小于最小值，返回第一个y值
        if (x <= xList[0]) {
            return if (extrapolate) yList[0] else yList[0]
        }
        
        // 如果x大于最大值，返回最后一个y值
        if (x >= xList[xList.size - 1]) {
            return if (extrapolate) yList[yList.size - 1] else yList[yList.size - 1]
        }
        
        // 找到x所在的区间
        var i = 0
        while (i < xList.size - 1 && x > xList[i + 1]) {
            i++
        }
        
        // 线性插值
        val xL = xList[i]
        val yL = yList[i]
        val xR = xList[i + 1]
        val yR = yList[i + 1]
        
        if (xR == xL) return yL
        
        val dydx = (yR - yL) / (xR - xL)
        return yL + dydx * (x - xL)
    }
    
    /**
     * 计算特定距离处的曲率偏移（参考 openpilot 的曲率计算）
     * 使用二次函数模拟曲线，让车道线根据距离逐渐弯曲
     */
    fun calculateCurvatureAtDistance(
        curvatureRate: Float,
        direction: Int,
        distance: Float,
        width: Float
    ): Float {
        if (abs(curvatureRate) < 0.01f || distance < 0.1f) return 0f
        
        // 使用二次函数模拟曲线（参考 openpilot 的曲率计算）
        // 曲率随距离的平方增长，模拟真实的道路弯曲
        val curvature = curvatureRate * 0.3f
        val normalizedCurvature = (curvature / 0.02f).coerceIn(-1f, 1f)
        val maxOffset = width * 0.12f
        val offset = normalizedCurvature * distance * distance * 0.005f * maxOffset
        
        return if (direction > 0) offset else -offset
    }
    
    /**
     * 线性插值辅助函数
     */
    fun lerp(start: Float, stop: Float, fraction: Float): Float {
        return (1 - fraction) * start + fraction * stop
    }
}

