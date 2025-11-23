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
import com.example.carrotamap.SystemStateData  // 🆕 导入 SystemStateData
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import kotlin.math.abs

private const val CURVATURE_LOG_TAG = "VehicleLaneVis"
private const val CURVATURE_DEBUG_DISTANCE_THRESHOLD = 60f
private const val ENABLE_CURVATURE_LOG = false
private const val DATA_STALE_THRESHOLD_MS = 2000L  // 数据延迟阈值（毫秒）
private const val DATA_DISCONNECTED_THRESHOLD_MS = 4000L  // 🆕 优化：数据断开阈值改为4秒，更快检测断联


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
            // ✅ 根据 openpilot 枚举定义：
            // enum LaneChangeDirection { none @0; left @1; right @2; }
            val direction = when (laneChangeDirection) {
                1 -> "左"   // left @1 = 左变道
                2 -> "右"   // right @2 = 右变道
                0 -> ""     // none @0 = 无变道（理论上不会进入此分支，因为 laneChangeState != 0）
                else -> "未知($laneChangeDirection)"  // 异常值
            }
            OvertakeHintInfo(
                cardColor = UIConstants.COLOR_INFO.copy(alpha = 0.2f),
                icon = "🔄",
                title = if (direction.isNotEmpty()) "变道中($direction)" else "变道中",
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
    dataState: androidx.compose.runtime.State<XiaogeVehicleData?>,  // 🆕 接受 State对象而非值
    userType: Int,
    showDialog: Boolean, // 改为必需参数，由外部控制
    onDismiss: () -> Unit, // 改为必需参数，添加关闭回调
    carrotManFields: CarrotManFields? = null, // 高德地图数据，用于获取道路类型
    deviceIP: String? = null, // 🆕 设备IP地址，用于在UI中显示连接状态
    isTcpConnected: Boolean = false // 🆕 TCP连接状态，用于区分"断开"和"待机"
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
            
            // 🆕 优化：从 State对象读取值，确保自动重组
            // 问题：之前传递 .value 导致只有在父组件重组时才会更新
            // 修复：直接读取 State.value，Compose 会自动订阅变化
            val data by dataState  // 使用 by 委托，自动订阅 State 变化
            
            // 🆕 优化：实时计算数据延迟，确保UI及时更新
            // 问题：之前的 currentTime 只在初始化时计算一次，导致延迟显示不准确
            // 修复：使用 LaunchedEffect 定期更新 currentTime，确保 dataAge 实时计算
            var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
            
            // 定期更新当前时间，用于实时计算数据延迟（每100ms更新一次，平衡性能和实时性）
            LaunchedEffect(Unit) {
                while (true) {
                    delay(100)
                    currentTime = System.currentTimeMillis()
                }
            }
            
            // 🆕 修复：使用局部变量避免 smart cast 问题
            val currentData = data
            val dataAge = when {
                currentData == null -> DATA_DISCONNECTED_THRESHOLD_MS + 1000L  // 数据为null时，使用断开阈值+1秒，避免立即显示"断开"
                currentData.receiveTime <= 0 -> DATA_DISCONNECTED_THRESHOLD_MS + 1000L
                else -> (currentTime - currentData.receiveTime).coerceAtLeast(0L)
            }
            val isDataStale = dataAge > DATA_STALE_THRESHOLD_MS
            
            // 🆕 优化：直接使用 currentData，确保实时性（data 已经通过 by 委托自动订阅）
            val displayData = currentData
            
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
                        // 🆕 优化：使用最新的 currentData 而不是 displayData，确保状态实时更新
                        val systemState = currentData?.systemState
                        
                        TopBar(
                                    dataAge = dataAge,
                                    isDataStale = isDataStale,
                            overtakeMode = overtakeModeForStatus,
                            systemState = systemState,  // 🆕 传递完整的 systemState
                            currentData = currentData,  // 🆕 传递完整数据用于判断 onroad 状态
                            deviceIP = deviceIP,  // 🆕 传递设备IP地址
                            isTcpConnected = isTcpConnected,  // 🆕 传递TCP连接状态
                            onClose = onDismiss
                        )
                        
                        // 超车提示信息卡片（整合了左侧状态信息，支持3行显示）
                        val prefsForHint = prefsForStatus
                        val overtakeModeForHint = prefsForHint.getInt("overtake_mode", 0)
                        // 🆕 优化：使用最新的 currentData 而不是 displayData，确保状态实时更新
                        val hintInfo = getOvertakeHintInfo(
                            overtakeMode = overtakeModeForHint,
                            overtakeStatus = currentData?.overtakeStatus,
                            laneChangeState = currentData?.modelV2?.meta?.laneChangeState ?: 0,
                            laneChangeDirection = currentData?.modelV2?.meta?.laneChangeDirection ?: 0
                        )
                        
                        // 获取额外的信息行（冷却时间、阻止原因）
                        // 🆕 修复：避免重复显示 blockingReason
                        // 如果 hintInfo.detail 已经包含 blockingReason，第三行就不显示
                        val cooldownText = currentData?.overtakeStatus?.cooldownRemaining?.let { cooldown ->
                            if (cooldown > 0) "冷却: ${String.format("%.1f", cooldown / 1000.0)}s" else null
                        }
                        val blockingReason = currentData?.overtakeStatus?.blockingReason
                        // 🆕 修复：检查 hintInfo.detail 是否已经包含 blockingReason，避免重复显示
                        val shouldShowBlockingReason = blockingReason != null && 
                            hintInfo.detail != blockingReason && 
                            !hintInfo.detail.contains(blockingReason)
                        
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
                                    // 🆕 修复：避免重复显示 blockingReason，如果 hintInfo.detail 已包含则不显示
                                    when {
                                        shouldShowBlockingReason -> {
                                            Text(
                                                text = blockingReason!!,
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
                        // 🆕 优化：直接使用 data（已移除延迟），确保表格数据实时更新
                        DataInfoPanel(
                                data = data,
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
    systemState: SystemStateData?,  // 🆕 接受完整的 systemState
    currentData: XiaogeVehicleData?,  // 🆕 接受完整数据用于判断 onroad 状态
    deviceIP: String?,  // 🆕 设备IP地址
    isTcpConnected: Boolean,  // 🆕 TCP连接状态
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
            val systemEnabled = systemState?.enabled == true
            val systemActive = systemState?.active == true
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
        
        // 右侧：设备IP、网络状态和关闭按钮
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 🆕 设备IP显示
            if (deviceIP != null && deviceIP.isNotEmpty()) {
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    color = UIConstants.COLOR_INFO.copy(alpha = 0.2f)
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
                                    color = UIConstants.COLOR_INFO,
                                    shape = androidx.compose.foundation.shape.CircleShape
                                )
                        )
                        Text(
                            text = deviceIP,
                            fontSize = 9.sp,
                            color = UIConstants.COLOR_INFO,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                } else {
                // 未发现设备IP
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    color = UIConstants.COLOR_NEUTRAL.copy(alpha = 0.2f)
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
                                    color = UIConstants.COLOR_NEUTRAL,
                                    shape = androidx.compose.foundation.shape.CircleShape
                                )
                        )
                        Text(
                            text = "未找到设备",
                            fontSize = 9.sp,
                            color = UIConstants.COLOR_NEUTRAL,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            
            // 🆕 优化：更精确的状态判断，区分 TCP断开/无数据/onroad/offroad 和 enabled/active
            val isSystemActive = systemState?.active == true
            val isSystemEnabled = systemState?.enabled == true
            
            // 🆕 判断是否 onroad：有完整数据（carState 和 modelV2 存在）
            val isOnroad = currentData != null && 
                          currentData.carState != null && 
                          currentData.modelV2 != null
            
            // 🆕 优化：区分"TCP连接断开"和"TCP连接正常但无数据"
            val isTcpDisconnected = !isTcpConnected  // TCP连接已断开
            val hasRecentData = dataAge <= DATA_STALE_THRESHOLD_MS  // 数据在2秒内
            
            // 状态判断优先级（重新设计）：
            // 1. TCP断开 → 显示"断开"（红色）
            // 2. TCP连接正常但数据延迟 → 显示"延迟"（橙色）
            // 3. 有数据且 ACC 已启动 → 显示"正常"（绿色）
            // 4. 有数据且车辆 onroad → 显示"准备"（蓝色）
            // 5. TCP连接正常但无数据 → 显示"待机"（灰色）
            val (statusText, statusColor, statusIcon) = when {
                isTcpDisconnected -> Triple("断开", Color(0xFFEF4444), "●")  // 红色：TCP连接断开
                isDataStale && dataAge > 3000 -> Triple("异常", Color(0xFFDC2626), "⚠")  // 深红色：数据严重延迟（3-4秒）
                isDataStale -> Triple("延迟", Color(0xFFF59E0B), "◐")  // 橙色：数据轻微延迟（2-3秒）
                isSystemActive -> Triple("正常", Color(0xFF10B981), "●")  // 绿色：ACC 已启动，openpilot 激活
                isOnroad && isSystemEnabled -> Triple("准备", Color(0xFF3B82F6), "◔")  // 蓝色：车辆 onroad，openpilot 已启用但未激活
                isOnroad -> Triple("准备", Color(0xFF60A5FA), "◑")  // 浅蓝色：车辆 onroad，但 openpilot 未启用
                else -> Triple("待机", Color(0xFF64748B), "○")  // 灰色：TCP连接正常但无数据（offroad 或设备待机）
            }
            
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                color = statusColor.copy(alpha = 0.2f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = statusIcon,
                        fontSize = 8.sp,
                        color = statusColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when {
                            isTcpDisconnected -> statusText  // "断开"
                            isDataStale -> "$statusText ${String.format("%.1f", dataAge / 1000.0)}s"  // "延迟 2.5s"
                            else -> statusText  // "待机"/"准备"/"正常"
                        },
                        fontSize = 9.sp,
                        color = statusColor,
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
    
    // 🆕 优化：按照超车决策的逻辑顺序重新组织检查条件
    // 决策流程：本车状态 → 前车情况 → 道路条件 → 左侧可行性 → 右侧可行性
    val conditions = buildList {
        // ==================== 一、本车基础状态 ====================
        // 1. 本车速度
        val vEgoKmh = (carState?.vEgo ?: 0f) * 3.6f
        add(CheckCondition(
            name = "① 本车速度",
            threshold = "≥ ${minOvertakeSpeedKph.toInt()} km/h",
            actual = "${String.format("%.1f", vEgoKmh)} km/h",
            isMet = vEgoKmh >= minOvertakeSpeedKph
        ))
        
        // 2. 方向盘角度
        val steeringAngle = kotlin.math.abs(carState?.steeringAngleDeg ?: 0f)
        add(CheckCondition(
            name = "② 方向盘角度",
            threshold = "≤ ${MAX_STEERING_ANGLE.toInt()}°",
            actual = "${String.format("%.1f", steeringAngle)}°",
            isMet = steeringAngle <= MAX_STEERING_ANGLE
        ))
        
        // 3. 变道状态
        add(CheckCondition(
            name = "③ 变道状态",
            threshold = "未变道",
            actual = when (laneChangeState) {
                0 -> "未变道"
                1 -> "变道中"
                2 -> "完成"
                3 -> "取消"
                else -> "未知"
            },
            isMet = laneChangeState == 0
        ))
        
        // ==================== 二、前车状态 ====================
        // 4. 前车距离
        val leadDistance = lead0?.x ?: 0f
        val leadProb = lead0?.prob ?: 0f
        val hasValidLead = lead0 != null && leadDistance < MAX_LEAD_DISTANCE && leadProb >= MIN_LEAD_PROB
        add(CheckCondition(
            name = "④ 前车距离",
            threshold = "< ${MAX_LEAD_DISTANCE.toInt()}m",
            actual = if (lead0 != null) "${String.format("%.1f", leadDistance)}m" else "无车",
            isMet = hasValidLead
        ))
        
        // 5. 前车速度
        val leadSpeedKmh = (lead0?.v ?: 0f) * 3.6f
        add(CheckCondition(
            name = "⑤ 前车速度",
            threshold = "≥ ${MIN_LEAD_SPEED_KPH.toInt()} km/h",
            actual = if (lead0 != null) "${String.format("%.1f", leadSpeedKmh)} km/h" else "N/A",
            isMet = leadSpeedKmh >= MIN_LEAD_SPEED_KPH
        ))
        
        // 6. 速度差
        val speedDiff = vEgoKmh - leadSpeedKmh
        add(CheckCondition(
            name = "⑥ 速度差",
            threshold = "≥ ${speedDiffThresholdKph.toInt()} km/h",
            actual = if (lead0 != null) "${String.format("%.1f", speedDiff)} km/h" else "N/A",
            isMet = speedDiff >= speedDiffThresholdKph
        ))
        
        // ==================== 三、道路条件 ====================
        // 7. 道路曲率
        val curvature = kotlin.math.abs(modelV2?.curvature?.maxOrientationRate ?: 0f)
        add(CheckCondition(
            name = "⑦ 道路曲率",
            threshold = "< ${(MAX_CURVATURE * 1000).toInt()} mrad/s",
            actual = "${String.format("%.3f", curvature)} rad/s",
            isMet = curvature < MAX_CURVATURE
        ))
        
        // ==================== 四、左侧超车可行性 ====================
        // 8. 左车道线置信度
        val leftLaneProb = modelV2?.laneLineProbs?.getOrNull(0) ?: 0f
        add(CheckCondition(
            name = "⑧ 左车道线",
            threshold = "≥ ${(MIN_LANE_PROB * 100).toInt()}%",
            actual = "${String.format("%.0f", leftLaneProb * 100)}%",
            isMet = leftLaneProb >= MIN_LANE_PROB
        ))
        
        // 9. 左车道宽度
        val laneWidthLeft = modelV2?.meta?.laneWidthLeft ?: 0f
        add(CheckCondition(
            name = "⑨ 左车道宽",
            threshold = "≥ ${MIN_LANE_WIDTH}m",
            actual = "${String.format("%.2f", laneWidthLeft)}m",
            isMet = laneWidthLeft >= MIN_LANE_WIDTH
        ))
        
        // 10. 左盲区
        val leftBlindspot = carState?.leftBlindspot == true
        add(CheckCondition(
            name = "⑩ 左盲区",
            threshold = "无车",
            actual = if (leftBlindspot) "有车" else "无车",
            isMet = !leftBlindspot
        ))
        
        // ==================== 五、右侧超车可行性 ====================
        // 11. 右车道线置信度
        val rightLaneProb = modelV2?.laneLineProbs?.getOrNull(1) ?: 0f
        add(CheckCondition(
            name = "⑪ 右车道线",
            threshold = "≥ ${(MIN_LANE_PROB * 100).toInt()}%",
            actual = "${String.format("%.0f", rightLaneProb * 100)}%",
            isMet = rightLaneProb >= MIN_LANE_PROB
        ))
        
        // 12. 右车道宽度
        val laneWidthRight = modelV2?.meta?.laneWidthRight ?: 0f
        add(CheckCondition(
            name = "⑫ 右车道宽",
            threshold = "≥ ${MIN_LANE_WIDTH}m",
            actual = "${String.format("%.2f", laneWidthRight)}m",
            isMet = laneWidthRight >= MIN_LANE_WIDTH
        ))
        
        // 13. 右盲区
        val rightBlindspot = carState?.rightBlindspot == true
        add(CheckCondition(
            name = "⑬ 右盲区",
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
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // 表头
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF334155).copy(alpha = 0.3f))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "条件",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF1F5F9),
                        modifier = Modifier.weight(1.8f)
                    )
                    Text(
                        text = "阈值",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF1F5F9),
                        modifier = Modifier.weight(1.6f)
                    )
                    Text(
                        text = "实际",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF1F5F9),
                        modifier = Modifier.weight(1.6f)
                    )
                    Text(
                        text = "✓",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF1F5F9),
                        modifier = Modifier.weight(0.6f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
            }
            
                // 表格内容（按逻辑分组显示）
                conditions.forEachIndexed { index, condition ->
                    // 🆕 添加分组分隔线（每3行或特定位置）
                    if (index == 3 || index == 6 || index == 7 || index == 10) {
                        HorizontalDivider(
                            color = Color(0xFF475569).copy(alpha = 0.4f),
                            thickness = 1.dp,
                            modifier = Modifier.padding(vertical = 2.dp)
            )
        }
        
        Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (index % 2 == 0) Color.Transparent 
                                else Color(0xFF334155).copy(alpha = 0.15f)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = condition.name,
                            fontSize = 9.sp,
                            color = Color(0xFFE2E8F0),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1.8f)
                        )
                        Text(
                            text = condition.threshold,
                            fontSize = 8.5.sp,
                            color = Color(0xFFCBD5E1),
                            modifier = Modifier.weight(1.6f)
                        )
                        Text(
                            text = condition.actual,
                            fontSize = 8.5.sp,
                            color = if (condition.isMet) Color(0xFF94E2D5) else Color(0xFFFCA5A5),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1.6f)
                        )
                        Text(
                            text = if (condition.isMet) "✓" else "✗",
                            fontSize = 12.sp,
                            color = if (condition.isMet) UIConstants.COLOR_SUCCESS else UIConstants.COLOR_DANGER,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(0.6f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
                }
            }
        }
    }
}

