package com.example.carrotamap.ui.components

import android.util.Log
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
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
import com.example.carrotamap.CarrotManFields
import com.example.carrotamap.LeadData
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import kotlin.math.abs

private const val CURVATURE_LOG_TAG = "VehicleLaneVis"
private const val CURVATURE_DEBUG_DISTANCE_THRESHOLD = 60f
private const val ENABLE_CURVATURE_LOG = false
private const val DATA_STALE_THRESHOLD_MS = 2000L  // 数据延迟阈值（毫秒）
private const val DATA_DISCONNECTED_THRESHOLD_MS = 15000L  // 数据断开阈值（毫秒），与XiaogeDataReceiver的DATA_TIMEOUT_MS保持一致


/**
 * UI 常量配置
 */
private object UIConstants {
    // 颜色定义
    val COLOR_SUCCESS = Color(0xFF10B981)  // 绿色：成功/正常
    val COLOR_WARNING = Color(0xFFF59E0B)   // 橙色：警告
    val COLOR_DANGER = Color(0xFFEF4444)    // 红色：危险
    val COLOR_INFO = Color(0xFF3B82F6)     // 蓝色：信息
    val COLOR_NEUTRAL = Color(0xFF94A3B8)   // 灰色：中性
    
    // 前车距离阈值
    const val LEAD_DISTANCE_DANGER = 30f    // 危险距离（米）
    const val LEAD_DISTANCE_WARNING = 50f   // 警告距离（米）
    
    // 车道宽度阈值
    const val LANE_WIDTH_WIDE = 3.5f        // 宽车道（米）
    const val LANE_WIDTH_NORMAL = 3.0f     // 标准车道（米）
    
    // 车道线置信度阈值
    const val LANE_PROB_HIGH = 0.8f        // 高置信度
    const val LANE_PROB_MEDIUM = 0.5f       // 中置信度
    
    // 卡片样式
    val CARD_BACKGROUND = Color(0xFF1E293B).copy(alpha = 0.8f)
    val CARD_SHAPE = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
}

/**
 * 超车提示信息数据类
 */
private data class OvertakeHintInfo(
    val cardColor: Color,
    val icon: String,
    val title: String,
    val detail: String,
    val titleColor: Color
)

/**
 * 颜色映射器 - 统一管理颜色映射逻辑
 */
private object ColorMapper {
    /**
     * 根据车道宽度返回颜色
     */
    fun forLaneWidth(width: Float): Color = when {
        width >= UIConstants.LANE_WIDTH_WIDE -> UIConstants.COLOR_SUCCESS
        width >= UIConstants.LANE_WIDTH_NORMAL -> UIConstants.COLOR_INFO
        else -> UIConstants.COLOR_WARNING
    }
    
    /**
     * 根据车道线置信度返回颜色
     */
    fun forLaneProb(prob: Float): Color = when {
        prob >= UIConstants.LANE_PROB_HIGH -> UIConstants.COLOR_SUCCESS
        prob >= UIConstants.LANE_PROB_MEDIUM -> UIConstants.COLOR_INFO
        else -> UIConstants.COLOR_WARNING
    }
    
    /**
     * 根据前车距离返回颜色
     */
    fun forLeadDistance(distance: Float): Color = when {
        distance < UIConstants.LEAD_DISTANCE_DANGER -> UIConstants.COLOR_DANGER
        distance < UIConstants.LEAD_DISTANCE_WARNING -> UIConstants.COLOR_WARNING
        else -> UIConstants.COLOR_NEUTRAL
    }
    
    /**
     * 根据相对速度返回颜色
     */
    fun forRelativeSpeed(vRel: Float): Color = when {
        vRel < -5f -> UIConstants.COLOR_DANGER  // 接近过快：红色
        vRel < -2f -> UIConstants.COLOR_WARNING  // 接近：橙色
        vRel > 5f -> UIConstants.COLOR_INFO   // 远离过快：蓝色
        else -> UIConstants.COLOR_SUCCESS        // 保持：绿色
    }
    
    /**
     * 根据前车加速度返回颜色
     */
    fun forLeadAcceleration(accel: Float): Color = when {
        accel > 0.5f -> UIConstants.COLOR_SUCCESS
        accel < -0.5f -> UIConstants.COLOR_DANGER
        else -> UIConstants.COLOR_NEUTRAL
    }
    
    /**
     * 根据道路类型返回颜色
     */
    fun forRoadType(roadType: Int): Color = when (roadType) {
        0, 6 -> UIConstants.COLOR_SUCCESS  // 高速公路/快速道：绿色
        -1 -> UIConstants.COLOR_NEUTRAL  // 未知：灰色
        else -> UIConstants.COLOR_WARNING  // 其他：橙色
    }
    
    /**
     * 根据曲率返回颜色
     */
    fun forCurvature(curvatureRate: Float): Color = when {
        abs(curvatureRate) < 0.01f -> UIConstants.COLOR_NEUTRAL
        abs(curvatureRate) < 0.02f -> UIConstants.COLOR_INFO
        else -> UIConstants.COLOR_WARNING
    }
}

/**
 * 获取超车提示信息
 */
private fun getOvertakeHintInfo(
    overtakeMode: Int,
    overtakeStatus: com.example.carrotamap.OvertakeStatusData?,
    laneChangeState: Int,
    laneChangeDirection: Int
): OvertakeHintInfo {
    return when {
        // 自动超车模式（模式2）且满足超车条件
        overtakeMode == 2 && overtakeStatus?.canOvertake == true -> OvertakeHintInfo(
            cardColor = UIConstants.COLOR_SUCCESS.copy(alpha = 0.2f),
            icon = "⚠️",
            title = "自动超车请注意安全",
            detail = "系统将自动执行超车操作，请保持注意力集中",
            titleColor = UIConstants.COLOR_SUCCESS
        )
        // 拨杆超车模式（模式1）且满足超车条件
        overtakeMode == 1 && overtakeStatus?.canOvertake == true -> OvertakeHintInfo(
            cardColor = UIConstants.COLOR_INFO.copy(alpha = 0.2f),
            icon = "🔔",
            title = "变道超车请拨杆确认",
            detail = "系统已检测到超车条件，请拨动转向杆确认",
            titleColor = UIConstants.COLOR_INFO
        )
        // 禁止超车模式（模式0）
        overtakeMode == 0 -> OvertakeHintInfo(
            cardColor = UIConstants.COLOR_NEUTRAL.copy(alpha = 0.2f),
            icon = "🚫",
            title = "超车功能已禁用",
            detail = "请在设置中启用超车功能",
            titleColor = UIConstants.COLOR_NEUTRAL
        )
        // 不能超车且有阻止原因
        overtakeStatus != null && !overtakeStatus.canOvertake && overtakeStatus.blockingReason != null -> OvertakeHintInfo(
            cardColor = UIConstants.COLOR_WARNING.copy(alpha = 0.2f),
            icon = "ℹ️",
            title = "超车条件不满足",
            detail = overtakeStatus.blockingReason,
            titleColor = UIConstants.COLOR_WARNING
        )
        // 冷却中
        overtakeStatus?.cooldownRemaining != null && overtakeStatus.cooldownRemaining > 0 -> OvertakeHintInfo(
            cardColor = UIConstants.COLOR_WARNING.copy(alpha = 0.2f),
            icon = "⏱️",
            title = "超车冷却中",
            detail = "剩余 ${String.format("%.1f", overtakeStatus.cooldownRemaining / 1000.0)} 秒",
            titleColor = UIConstants.COLOR_WARNING
        )
        // 变道中
        laneChangeState != 0 -> {
            val direction = when (laneChangeDirection) {
                -1 -> "左"
                1 -> "右"
                else -> ""
            }
            OvertakeHintInfo(
                cardColor = UIConstants.COLOR_INFO.copy(alpha = 0.2f),
                icon = "🔄",
                title = "变道中($direction)",
                detail = "正在执行变道操作，请保持稳定",
                titleColor = UIConstants.COLOR_INFO
            )
        }
        // 默认监控状态
        else -> OvertakeHintInfo(
            cardColor = UIConstants.COLOR_NEUTRAL.copy(alpha = 0.2f),
            icon = "👁️",
            title = "监控中",
            detail = "系统正在监控超车条件",
            titleColor = UIConstants.COLOR_NEUTRAL
        )
    }
}

/**
 * 获取盲区状态信息
 */
private fun getBlindspotInfo(leftBlindspot: Boolean, rightBlindspot: Boolean): Pair<String, Color> {
    val text = when {
        leftBlindspot && rightBlindspot -> "左右有车"
        leftBlindspot -> "左侧有车"
        rightBlindspot -> "右侧有车"
        else -> "无车"
    }
    val color = if (leftBlindspot || rightBlindspot) {
        UIConstants.COLOR_DANGER
    } else {
        UIConstants.COLOR_SUCCESS
    }
    return text to color
}

/**
 * 车辆和车道可视化弹窗组件，绘制车道、车辆及核心状态。
 * 仅用户类型 3 或 4 显示。
 */
@Composable
fun VehicleLaneVisualization(
    data: XiaogeVehicleData?,
    userType: Int,
    showDialog: Boolean, // 改为必需参数，由外部控制
    onDismiss: () -> Unit, // 改为必需参数，添加关闭回调
    carrotManFields: CarrotManFields? = null // 高德地图数据，用于获取道路类型
) {
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
            
            // 限制界面刷新频率在 10Hz
            var displayData by remember { mutableStateOf(data) }
            LaunchedEffect(data) {
                delay(100) // 限制为10Hz
                displayData = data
            }
            
            // 计算数据延迟，使用Android端接收时间而不是Python端时间戳
            // 这样可以准确反映数据的新鲜度，即使Python端时间不同步也能正确显示
            // ✅ 优化：使用 when 表达式，更清晰地处理边界情况
            val currentTime = System.currentTimeMillis()
            val currentDisplayData = displayData  // 使用局部变量避免智能转换问题
            val dataAge = when {
                currentDisplayData == null -> Long.MAX_VALUE
                currentDisplayData.receiveTime <= 0 -> Long.MAX_VALUE
                else -> (currentTime - currentDisplayData.receiveTime).coerceAtLeast(0L)
            }
            val isDataStale = dataAge > DATA_STALE_THRESHOLD_MS
            
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
                        .padding(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 顶部标题栏（超车设置、系统状态、网络状态和关闭按钮）
                        val contextForPrefs = LocalContext.current
                        val prefsForStatus = contextForPrefs.getSharedPreferences("CarrotAmap", android.content.Context.MODE_PRIVATE)
                        val overtakeModeForStatus = prefsForStatus.getInt("overtake_mode", 0)
                        val systemState = displayData?.systemState
                        
                        TopBar(
                            dataAge = dataAge,
                            isDataStale = isDataStale,
                            overtakeMode = overtakeModeForStatus,
                            systemEnabled = systemState?.enabled == true,
                            systemActive = systemState?.active == true,
                            onClose = onDismiss
                        )
                        
                        // 超车提示信息卡片（整合了左侧状态信息，支持3行显示）
                        val prefsForHint = prefsForStatus
                        val overtakeModeForHint = prefsForHint.getInt("overtake_mode", 0)
                        val hintInfo = getOvertakeHintInfo(
                            overtakeMode = overtakeModeForHint,
                            overtakeStatus = displayData?.overtakeStatus,
                            laneChangeState = displayData?.modelV2?.meta?.laneChangeState ?: 0,
                            laneChangeDirection = displayData?.modelV2?.meta?.laneChangeDirection ?: 0
                        )
                        
                        // 获取额外的信息行（冷却时间、阻止原因）
                        val cooldownText = displayData?.overtakeStatus?.cooldownRemaining?.let { cooldown ->
                            if (cooldown > 0) "冷却: ${String.format("%.1f", cooldown / 1000.0)}s" else null
                        }
                        val blockingReason = displayData?.overtakeStatus?.blockingReason
                        
    Card(
                            modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
                                containerColor = hintInfo.cardColor
        ),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
    ) {
                            Row(
            modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = hintInfo.icon,
                                    fontSize = 16.sp
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    // 第一行：标题（状态文本）
                                    Text(
                                        text = hintInfo.title,
                                        fontSize = 12.sp,
                                        color = hintInfo.titleColor,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    // 第二行：详情描述
                                    Text(
                                        text = hintInfo.detail,
                                        fontSize = 10.sp,
                                        color = UIConstants.COLOR_NEUTRAL,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    // 第三行：冷却时间或阻止原因（优先显示阻止原因）
                                    when {
                                        blockingReason != null -> {
                                            Text(
                                                text = blockingReason,
                                                fontSize = 9.sp,
                                                color = Color(0xFFEF4444),
                                                fontWeight = FontWeight.Light,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                        }
                                        cooldownText != null -> {
                                            Text(
                                                text = cooldownText,
                                                fontSize = 9.sp,
                                                color = Color(0xFF94A3B8),
                                                fontWeight = FontWeight.Light,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        
                        // 数据信息面板（底部显示）
                        DataInfoPanel(
                                data = displayData,
                                dataAge = dataAge,
                                isDataStale = isDataStale,
                            carrotManFields = carrotManFields,
                                modifier = Modifier
                                    .fillMaxWidth()
                            )
}
                }
            }
        }
    }
}

/** 顶部标题栏，展示超车设置、系统状态、网络状态和关闭按钮。 */
@Composable
private fun TopBar(
    dataAge: Long,
    isDataStale: Boolean,
    overtakeMode: Int,
    systemEnabled: Boolean,
    systemActive: Boolean,
    onClose: () -> Unit
) {
    Row(
            modifier = Modifier
            .fillMaxWidth()
            .height(40.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧：超车设置和系统状态
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 超车设置状态
            val overtakeModeNames = arrayOf("禁止超车", "拨杆超车", "自动超车")
            val overtakeModeColors = arrayOf(
                UIConstants.COLOR_NEUTRAL,
                UIConstants.COLOR_INFO,
                UIConstants.COLOR_SUCCESS
            )
            val overtakeModeColor = overtakeModeColors[overtakeMode]
            
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                color = overtakeModeColor.copy(alpha = 0.2f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(
                                color = overtakeModeColor,
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    )
                    Text(
                        text = overtakeModeNames[overtakeMode],
                        fontSize = 9.sp,
                        color = overtakeModeColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            // 系统状态
            val systemColor = if (systemEnabled && systemActive) UIConstants.COLOR_SUCCESS else UIConstants.COLOR_NEUTRAL
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                color = systemColor.copy(alpha = 0.2f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(
                                color = systemColor,
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    )
                    Text(
                        text = if (systemEnabled && systemActive) "激活" else "待机",
                        fontSize = 9.sp,
                        color = systemColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        
        // 右侧：网络状态和关闭按钮
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 根据延迟推断网络状态
            val isDisconnected = dataAge > DATA_DISCONNECTED_THRESHOLD_MS
            val networkColor = when {
                isDisconnected -> Color(0xFFEF4444)  // 断开：红色
                isDataStale -> Color(0xFFF59E0B)     // 延迟：橙色
                else -> Color(0xFF10B981)            // 正常：绿色
        }
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                color = networkColor.copy(alpha = 0.2f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(
                                color = networkColor,
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    )
                    Text(
                        text = when {
                            isDisconnected -> "断开"
                            isDataStale -> if (dataAge > 1000) {
                                "${String.format("%.1f", dataAge / 1000.0)}s"
                } else {
                                "${String.format("%.1f", dataAge / 1.0)}ms"
                            }
                            else -> "正常"
                        },
                        fontSize = 9.sp,
                        color = networkColor,
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
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

    
    /**
 * 可复用的信息卡片组件
 */
@Composable
private fun RowScope.InfoCard(
    title: String,
    value: String,
    valueColor: Color = UIConstants.COLOR_NEUTRAL,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.weight(1f),
        colors = CardDefaults.cardColors(
            containerColor = UIConstants.CARD_BACKGROUND
        ),
        shape = UIConstants.CARD_SHAPE
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                fontSize = 10.sp,
                color = UIConstants.COLOR_NEUTRAL,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                fontSize = 12.sp,
                color = valueColor,
                fontWeight = FontWeight.Bold
            )
            subtitle?.let {
                Text(
                    text = it,
                    fontSize = 9.sp,
                    color = UIConstants.COLOR_NEUTRAL,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * 前车相对速度卡片组件（纯视觉方案）
 */
@Composable
private fun RowScope.LeadVehicleSpeedCard(
    lead0: LeadData?,
    hasLead: Boolean,
    modifier: Modifier = Modifier
) {
    
    if (hasLead && lead0 != null) {
        val distance = lead0.x
        val distanceText = String.format("%.1fm", distance)
        val distanceColor = ColorMapper.forLeadDistance(distance)
        
        InfoCard(
            title = "前车距离",
            value = distanceText,
            valueColor = distanceColor,
            modifier = modifier
        )
            } else {
        InfoCard(
            title = "前车距离",
            value = "无车",
            valueColor = UIConstants.COLOR_NEUTRAL,
            modifier = modifier
        )
    }
}

/**
 * 前车状态卡片组件
 */
@Composable
private fun RowScope.LeadVehicleStatusCard(
    lead0: LeadData?,
    hasLead: Boolean,
    modifier: Modifier = Modifier
) {
    
    if (hasLead && lead0 != null) {
        // 加速度字段已删除 - 简化版不再显示
        val leadAccelText = "N/A"
        
        InfoCard(
            title = "前车状态",
            value = leadAccelText,
            valueColor = UIConstants.COLOR_NEUTRAL,
            modifier = modifier
        )
    } else {
        InfoCard(
            title = "前车状态",
            value = "无车",
            valueColor = UIConstants.COLOR_NEUTRAL,
            modifier = modifier
        )
    }
}

/**
 * 系统状态卡片组件
 */
@Composable
private fun SystemStatusCard(
    enabled: Boolean,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = UIConstants.CARD_BACKGROUND
        ),
        shape = UIConstants.CARD_SHAPE
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
                color = UIConstants.COLOR_NEUTRAL,
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
                            color = if (enabled && active) UIConstants.COLOR_SUCCESS else Color(0xFF64748B),
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                )
                Text(
                    text = if (enabled && active) "激活" else "待机",
                    fontSize = 12.sp,
                    color = if (enabled && active) UIConstants.COLOR_SUCCESS else UIConstants.COLOR_NEUTRAL,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * 侧方车辆信息卡片组件（可复用）
 * ✅ 优化：提取重复代码，减少代码冗余
 * @param title 卡片标题（如"左侧车辆"或"右侧车辆"）
 * @param leadData 侧方车辆数据（SideLeadDataExtended）
 * @param modifier 修饰符
 */
@Composable
private fun RowScope.SideVehicleCard(
    title: String,
    leadData: com.example.carrotamap.SideLeadDataExtended?,
    modifier: Modifier = Modifier
) {
    val vehicleText = if (leadData?.status == true) {
        "${String.format("%.1f", leadData.dRel)}m"
    } else {
        "无车"
    }
    val vehicleColor = if (leadData?.status == true) {
        ColorMapper.forLeadDistance(leadData.dRel)
    } else {
        UIConstants.COLOR_NEUTRAL
    }
    val vehicleSubtitle = if (leadData?.status == true) {
        // 显示相对速度（车道内概率已删除）
        val vRelText = if (abs(leadData.vRel) > 0.1f) {
            val vRelKmh = leadData.vRel * 3.6f
            "${if (vRelKmh > 0) "+" else ""}${String.format("%.1f", abs(vRelKmh))}km/h"
        } else {
            null
        }
        vRelText
    } else null
    
    InfoCard(
        title = title,
        value = vehicleText,
        valueColor = vehicleColor,
        subtitle = vehicleSubtitle,
        modifier = modifier
    )
}

/**
 * 检查条件数据类
 */
private data class CheckCondition(
    val name: String,              // 检查条件名称
    val threshold: String,         // 条件满足值（阈值）
    val actual: String,            // 实际值
    val isMet: Boolean             // 是否满足条件
)

/** 数据信息面板，展示关键决策信息（简化版：表格形式）。 */
@Composable
private fun DataInfoPanel(
    data: XiaogeVehicleData?,
    dataAge: Long,
    isDataStale: Boolean,
    carrotManFields: CarrotManFields? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // ✅ 优化：使用 remember 和状态监听，确保参数改变后表格实时更新
    val prefs = remember { context.getSharedPreferences("CarrotAmap", android.content.Context.MODE_PRIVATE) }
    
    // 使用状态变量监听参数变化
    var minOvertakeSpeedKph by remember { mutableStateOf(prefs.getFloat("overtake_param_min_speed_kph", 60f).coerceIn(40f, 100f)) }
    var speedDiffThresholdKph by remember { mutableStateOf(prefs.getFloat("overtake_param_speed_diff_kph", 10f).coerceIn(5f, 30f)) }
    
    // ✅ 添加参数变化监听，当参数改变时实时更新表格
    LaunchedEffect(Unit) {
        // 定期检查参数变化（每500ms检查一次，平衡性能和实时性）
        while (true) {
            delay(500)
            val newMinSpeed = prefs.getFloat("overtake_param_min_speed_kph", 60f).coerceIn(40f, 100f)
            val newSpeedDiff = prefs.getFloat("overtake_param_speed_diff_kph", 10f).coerceIn(5f, 30f)
            
            if (newMinSpeed != minOvertakeSpeedKph) {
                minOvertakeSpeedKph = newMinSpeed
            }
            if (newSpeedDiff != speedDiffThresholdKph) {
                speedDiffThresholdKph = newSpeedDiff
            }
        }
    }
    
    // 性能优化：提取 carState 和 modelV2，减少重复访问（在顶层作用域）
    val carState = data?.carState
    val modelV2 = data?.modelV2
    val lead0 = modelV2?.lead0
    val laneChangeState = data?.modelV2?.meta?.laneChangeState ?: 0
    
    // 常量定义（与 AutoOvertakeManager.kt 保持一致）
    val MAX_LEAD_DISTANCE = 80.0f
    val MIN_LEAD_PROB = 0.5f
    val MIN_LEAD_SPEED_KPH = 50.0f
    val MAX_CURVATURE = 0.02f
    val MAX_STEERING_ANGLE = 15.0f
    val MIN_LANE_PROB = 0.7f
    val MIN_LANE_WIDTH = 3.0f
    
    // 计算所有检查条件
    val conditions = buildList {
        // 一、前置条件（checkPrerequisites）
        // 1. 本车速度
        val vEgoKmh = (carState?.vEgo ?: 0f) * 3.6f
        add(CheckCondition(
            name = "本车速度",
            threshold = "≥ ${minOvertakeSpeedKph.toInt()} km/h",
            actual = "${String.format("%.1f", vEgoKmh)} km/h",
            isMet = vEgoKmh >= minOvertakeSpeedKph
        ))
        
        // 2. 前车存在且距离较近
        val leadDistance = lead0?.x ?: 0f
        val leadProb = lead0?.prob ?: 0f
        val hasValidLead = lead0 != null && leadDistance < MAX_LEAD_DISTANCE && leadProb >= MIN_LEAD_PROB
        add(CheckCondition(
            name = "前车距离",
            threshold = "< ${MAX_LEAD_DISTANCE.toInt()}m 且置信度 ≥ ${(MIN_LEAD_PROB * 100).toInt()}%",
            actual = if (lead0 != null) "${String.format("%.1f", leadDistance)}m (${String.format("%.0f", leadProb * 100)}%)" else "无前车",
            isMet = hasValidLead
        ))
        
        // 3. 前车速度
        val leadSpeedKmh = (lead0?.v ?: 0f) * 3.6f
        add(CheckCondition(
            name = "前车速度",
            threshold = "≥ ${MIN_LEAD_SPEED_KPH.toInt()} km/h",
            actual = if (lead0 != null) "${String.format("%.1f", leadSpeedKmh)} km/h" else "N/A",
            isMet = leadSpeedKmh >= MIN_LEAD_SPEED_KPH
        ))
        
        // 4. 曲率检查
        val curvature = kotlin.math.abs(modelV2?.curvature?.maxOrientationRate ?: 0f)
        add(CheckCondition(
            name = "道路曲率",
            threshold = "< ${(MAX_CURVATURE * 1000).toInt()} mrad/s",
            actual = "${String.format("%.3f", curvature)} rad/s",
            isMet = curvature < MAX_CURVATURE
        ))
        
        // 5. 变道状态
        add(CheckCondition(
            name = "变道状态",
            threshold = "= 0 (未变道)",
            actual = when (laneChangeState) {
                0 -> "未变道"
                1 -> "变道中"
                2 -> "变道完成"
                3 -> "变道取消"
                else -> "未知($laneChangeState)"
            },
            isMet = laneChangeState == 0
        ))
        
        // 6. 方向盘角度
        val steeringAngle = kotlin.math.abs(carState?.steeringAngleDeg ?: 0f)
        add(CheckCondition(
            name = "方向盘角度",
            threshold = "≤ ${MAX_STEERING_ANGLE.toInt()}°",
            actual = "${String.format("%.1f", steeringAngle)}°",
            isMet = steeringAngle <= MAX_STEERING_ANGLE
        ))
        
        // 二、超车判断（shouldOvertake）
        // 7. 速度差
        val speedDiff = vEgoKmh - leadSpeedKmh
        add(CheckCondition(
            name = "速度差",
            threshold = "≥ ${speedDiffThresholdKph.toInt()} km/h",
            actual = if (lead0 != null) "${String.format("%.1f", speedDiff)} km/h" else "N/A",
            isMet = speedDiff >= speedDiffThresholdKph
        ))
        
        // 三、左超车可行性（checkLeftOvertakeFeasibility）
        // 8. 左车道线置信度
        val leftLaneProb = modelV2?.laneLineProbs?.getOrNull(0) ?: 0f
        add(CheckCondition(
            name = "左车道线置信度",
            threshold = "≥ ${(MIN_LANE_PROB * 100).toInt()}%",
            actual = "${String.format("%.0f", leftLaneProb * 100)}%",
            isMet = leftLaneProb >= MIN_LANE_PROB
        ))
        
        // 9. 左车道宽度
        val laneWidthLeft = modelV2?.meta?.laneWidthLeft ?: 0f
        add(CheckCondition(
            name = "左车道宽度",
            threshold = "≥ ${MIN_LANE_WIDTH.toInt()}m",
            actual = "${String.format("%.2f", laneWidthLeft)}m",
            isMet = laneWidthLeft >= MIN_LANE_WIDTH
        ))
        
        // 10. 左盲区
        val leftBlindspot = carState?.leftBlindspot == true
        add(CheckCondition(
            name = "左盲区",
            threshold = "无车",
            actual = if (leftBlindspot) "有车" else "无车",
            isMet = !leftBlindspot
        ))
        
        // 四、右超车可行性（checkRightOvertakeFeasibility）
        // 11. 右车道线置信度
        val rightLaneProb = modelV2?.laneLineProbs?.getOrNull(1) ?: 0f
        add(CheckCondition(
            name = "右车道线置信度",
            threshold = "≥ ${(MIN_LANE_PROB * 100).toInt()}%",
            actual = "${String.format("%.0f", rightLaneProb * 100)}%",
            isMet = rightLaneProb >= MIN_LANE_PROB
        ))
        
        // 12. 右车道宽度
        val laneWidthRight = modelV2?.meta?.laneWidthRight ?: 0f
        add(CheckCondition(
            name = "右车道宽度",
            threshold = "≥ ${MIN_LANE_WIDTH.toInt()}m",
            actual = "${String.format("%.2f", laneWidthRight)}m",
            isMet = laneWidthRight >= MIN_LANE_WIDTH
        ))
        
        // 13. 右盲区
        val rightBlindspot = carState?.rightBlindspot == true
        add(CheckCondition(
            name = "右盲区",
            threshold = "无车",
            actual = if (rightBlindspot) "有车" else "无车",
            isMet = !rightBlindspot
        ))
    }
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 变道中时显示进度条
        if (laneChangeState == 1) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF3B82F6).copy(alpha = 0.2f)
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "变道中...",
                        fontSize = 11.sp,
                        color = Color(0xFF3B82F6),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth()
                    )
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = Color(0xFF3B82F6),
                        trackColor = Color(0xFF1E293B)
                    )
                }
            }
        }
        
        // 检查条件表格
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = UIConstants.CARD_BACKGROUND
            ),
            shape = UIConstants.CARD_SHAPE
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                // 表头
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "检查条件",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = UIConstants.COLOR_NEUTRAL,
                        modifier = Modifier.weight(2f)
                    )
                    Text(
                        text = "条件满足值",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = UIConstants.COLOR_NEUTRAL,
                        modifier = Modifier.weight(1.5f)
                    )
                    Text(
                        text = "实际值",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = UIConstants.COLOR_NEUTRAL,
                        modifier = Modifier.weight(1.5f)
                    )
                    Text(
                        text = "状态",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = UIConstants.COLOR_NEUTRAL,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
                
                // 分隔线
                HorizontalDivider(
                    color = UIConstants.COLOR_NEUTRAL.copy(alpha = 0.3f),
                    thickness = 0.5.dp
                )
                
                // 表格内容（紧凑显示）
                conditions.forEach { condition ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 0.5.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = condition.name,
                            fontSize = 9.sp,
                            color = UIConstants.COLOR_NEUTRAL,
                            modifier = Modifier.weight(2f)
                        )
                        Text(
                            text = condition.threshold,
                            fontSize = 8.sp,
                            color = UIConstants.COLOR_NEUTRAL.copy(alpha = 0.7f),
                            modifier = Modifier.weight(1.5f)
                        )
                        Text(
                            text = condition.actual,
                            fontSize = 8.sp,
                            color = UIConstants.COLOR_NEUTRAL,
                            modifier = Modifier.weight(1.5f)
                        )
                        Text(
                            text = if (condition.isMet) "✓" else "✗",
                            fontSize = 10.sp,
                            color = if (condition.isMet) UIConstants.COLOR_SUCCESS else UIConstants.COLOR_DANGER,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

