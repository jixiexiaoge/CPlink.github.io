package com.example.carrotamap

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carrotamap.ui.theme.CPlinkTheme
import kotlinx.coroutines.launch

// UI组件导入
import com.example.carrotamap.ui.components.*
import com.example.carrotamap.ui.components.CompactStatusCard
import com.example.carrotamap.ui.components.DataTable
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * MainActivity UI组件管理类
 * 负责所有UI组件的定义和界面逻辑
 */
class MainActivityUI(
    private val core: MainActivityCore
) {

    /**
     * 设置用户界面
     */
    @Composable
    fun SetupUserInterface() {
        CPlinkTheme {
            Scaffold(
                bottomBar = {
                    BottomNavigationBar(
                        currentPage = core.currentPage,
                        onPageChange = { page -> core.currentPage = page },
                        userType = core.userType.value
                    )
                }
            ) { paddingValues ->
                // 使用可滚动布局支持横屏和不同屏幕高度
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    // 主内容区域 - 可滚动
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        // 根据当前页面显示不同内容
                        when (core.currentPage) {
                            0 -> HomePage(
                                deviceId = core.deviceId.value,
                                selfCheckStatus = core.selfCheckStatus.value,
                                userType = core.userType.value,
                                carrotManFields = core.carrotManFields.value,
                                dataFieldManager = core.dataFieldManager,
                                onSendCommand = { command, arg -> core.sendCarrotCommand(command, arg) },
                                onSendRoadLimitSpeed = { core.sendCurrentRoadLimitSpeed() },
                                onLaunchAmap = { core.launchAmapAuto() },
                                onSendNavConfirmation = { core.sendNavigationConfirmationManually() } // 🆕 发送导航确认
                            )
                            1 -> HelpPage(
                                deviceIP = core.networkManager.getCurrentDeviceIP()
                            )
                            2 -> ProfilePage(
                                usageStats = core.usageStats.value,
                                deviceId = core.deviceId.value
                            )
                            3 -> DataPage(
                                        carrotManFields = core.carrotManFields.value,
                                        dataFieldManager = core.dataFieldManager,
                                        networkManager = core.networkManager,
                                        amapBroadcastManager = core.amapBroadcastManager
                                    )
                            4 -> CommandPage(
                                networkManager = core.networkManager,
                                zmqClient = core.zmqClient
                            )
                        }
                        
                        // 功能说明弹窗（仅用户类型为2时显示）
                        var showFeatureDialog by remember { mutableStateOf(false) }
                        var hasShownDialog by remember { mutableStateOf(false) }
                        
                        // 当用户类型为2时，自动显示功能说明弹窗（仅显示一次）
                        LaunchedEffect(core.userType.value) {
                            if (core.userType.value == 2 && !hasShownDialog) {
                                showFeatureDialog = true
                                hasShownDialog = true
                            }
                        }
                        
                        if (showFeatureDialog && core.userType.value == 2) {
                            AppFeatureDialog(
                                onDismiss = { showFeatureDialog = false }
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * 实时数据页面组件
     */
    @Composable
    private fun DataPage(
        carrotManFields: CarrotManFields,
        dataFieldManager: DataFieldManager,
        networkManager: NetworkManager,
        amapBroadcastManager: AmapBroadcastManager
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFF8FAFC),
                            Color(0xFFE2E8F0)
                        )
                    )
                )
        ) {
            // 使用Column布局，让数据表格可以独立滚动
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 状态卡片（固定高度，不滚动）
                CompactStatusCard(
                    receiverStatus = amapBroadcastManager.receiverStatus.value,
                    totalBroadcastCount = amapBroadcastManager.totalBroadcastCount.intValue,
                    carrotManFields = carrotManFields,
                    networkStatus = networkManager.getNetworkConnectionStatus(),
                    networkStats = networkManager.getNetworkStatistics(),
                    onClearDataClick = {
                        amapBroadcastManager.clearBroadcastData()
                    }
                )
                
                // 数据表格（可滚动）
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f), // 占据剩余空间
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    // 使用可滚动的Column，让用户可以滑动查看所有数据
                    // Box确保滚动容器有明确的高度约束
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val scrollState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .fillMaxSize() // 填充整个Box，确保有明确的高度
                                .padding(16.dp)
                                .verticalScroll(scrollState) // 添加垂直滚动功能
                        ) {
                            // 数据表格
                            DataTable(
                                carrotManFields = carrotManFields,
                                dataFieldManager = dataFieldManager,
                                networkManager = networkManager
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * 主页组件
     */
    @Composable
    private fun HomePage(
        deviceId: String,
        selfCheckStatus: SelfCheckStatus,
        userType: Int,
        carrotManFields: CarrotManFields,
        dataFieldManager: DataFieldManager,
        onSendCommand: (String, String) -> Unit,
        onSendRoadLimitSpeed: () -> Unit,
        onLaunchAmap: () -> Unit,
        onSendNavConfirmation: () -> Unit // 🆕 发送导航确认
    ) {
        val scrollState = rememberScrollState()
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFF8FAFC),
                            Color(0xFFE2E8F0)
                        )
                    )
                )
        ) {
            // 主内容区域（支持滚动，底部留出按钮空间）
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(bottom = 80.dp) // 为底部固定按钮留出空间
            ) {
                // 🔄 调整布局：实时数据组件移到顶部
                // Comma3数据表格（可折叠）
                Comma3DataTable(
                    carrotManFields = carrotManFields,
                    dataFieldManager = dataFieldManager,
                    userType = userType
                )
                
                // 🆕 详细信息显示区域（只有用户类型3或4才显示）
                if (userType == 3 || userType == 4) {
                    Spacer(modifier = Modifier.height(8.dp))
                    VehicleLaneDetailsSection(
                        core = core,
                        carrotManFields = carrotManFields
                    )
                }
                
                // 添加底部安全间距
                Spacer(modifier = Modifier.height(6.dp))
            }
            
            // 底部固定控制按钮区域（不受滚动影响）
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                MainActivityUIComponents.VehicleControlButtons(
                    core = core,
                    onPageChange = { page -> 
                        // 这里需要访问MainActivity的currentPage状态
                        // 暂时用Log记录，后续可以通过其他方式实现
                        android.util.Log.i("MainActivity", "页面切换请求: $page")
                    },
                    onSendCommand = onSendCommand,
                    onSendRoadLimitSpeed = onSendRoadLimitSpeed,
                    onLaunchAmap = onLaunchAmap,
                    onSendNavConfirmation = onSendNavConfirmation, // 🆕 传递发送导航确认回调
                    userType = userType,
                    carrotManFields = carrotManFields
                )
            }
        }
    }

    /**
     * 底部导航栏组件
     */
    @Composable
    private fun BottomNavigationBar(
        currentPage: Int,
        onPageChange: (Int) -> Unit,
        userType: Int = 0
    ) {
        // 根据用户类型决定是否显示数据页面和命令页面
        val basePages = listOf(
            BottomNavItem("主页", Icons.Default.Home, 0),
            BottomNavItem("帮助", Icons.Default.Info, 1),
            BottomNavItem("我的", Icons.Default.Person, 2)
        )
        
        val pages = if (userType == 4) {
            // 铁粉用户可以看到数据页面和命令页面
            basePages + BottomNavItem("数据", Icons.Default.Settings, 3) + 
                       BottomNavItem("命令", Icons.Default.Build, 4)
        } else {
            // 其他用户类型不显示数据页面，但可以显示命令页面
            basePages + BottomNavItem("命令", Icons.Default.Build, 4)
        }
        
        NavigationBar(
            modifier = Modifier.fillMaxWidth(),
            containerColor = Color.White,
            contentColor = Color(0xFF2196F3),
            tonalElevation = 0.dp
        ) {
            pages.forEach { page ->
                NavigationBarItem(
                    modifier = Modifier.weight(1f),
                    icon = {
                        Icon(
                            imageVector = page.icon,
                            contentDescription = page.title
                        )
                    },
                    label = {
                        Text(
                            text = page.title,
                            fontSize = 12.sp
                        )
                    },
                    selected = currentPage == page.index,
                    onClick = { onPageChange(page.index) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF2196F3),
                        selectedTextColor = Color(0xFF2196F3),
                        unselectedIconColor = Color(0xFF999999),
                        unselectedTextColor = Color(0xFF999999)
                    )
                )
            }
        }
    }

    /**
     * 应用功能说明弹窗组件（仅用户类型为2时显示）
     */
    @Composable
    private fun AppFeatureDialog(
        onDismiss: () -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = "🚗 CP搭子功能说明",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B)
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "感谢您的支持！CP搭子是一个智能驾驶助手应用，为您提供以下功能：",
                        fontSize = 14.sp,
                        color = Color(0xFF64748B),
                        lineHeight = 20.sp
                    )
                    
                    Text(
                        text = "核心功能：",
                        fontSize = 14.sp,
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.Medium
                    )
                    
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "🗺️ 高德地图导航集成 - 与高德地图车机版无缝对接",
                            "🚗 智能驾驶辅助 - 自动按导航变道和转弯", 
                            "📊 限速自动调整 - 根据道路限速自动调整车速",
                            "🚦 交通灯识别 - 红灯自动减速停车",
                            "🛣️ 弯道智能减速 - 根据弯道曲率自动调整速度",
                            "📡 实时数据监控 - 查看车辆和导航实时数据",
                            "🎮 手动控制命令 - 支持手动发送控制指令"
                        ).forEach { feature ->
                            Text(
                                text = feature,
                                fontSize = 13.sp,
                                color = Color(0xFF475569),
                                modifier = Modifier.padding(start = 8.dp),
                                lineHeight = 18.sp
                            )
                        }
                    }
                    
                    Text(
                        text = "使用提示：在主页可以查看实时数据，使用控制按钮发送指令，在「我的」页面查看使用统计。",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF3B82F6)
                    )
                ) {
                    Text(
                        text = "我知道了",
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            containerColor = Color.White,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
        )
    }


    /**
     * 获取用户类型显示文本
     */
    private fun getUserTypeText(userType: Int): String {
        return when (userType) {
            2 -> "支持者"
            3 -> "赞助者"
            4 -> "铁粉"
            else -> "普通用户"
        }
    }

    /**
     * Comma3数据表格组件（可折叠）
     */
    @Composable
    private fun Comma3DataTable(
        carrotManFields: CarrotManFields,
        dataFieldManager: DataFieldManager,
        userType: Int
    ) {
        var isExpanded by remember { mutableStateOf(false) }
        val userTypeText = getUserTypeText(userType)
        
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // 标题行（可点击）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isExpanded = !isExpanded },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = userTypeText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1D4ED8)
                        )
                        // 红绿灯状态指示器
                        TrafficLightIndicator(
                            trafficState = carrotManFields.traffic_state,
                            leftSec = carrotManFields.left_sec,
                            direction = carrotManFields.traffic_light_direction
                        )
                        // szTBTMainText 文本信息（如果有）
                        if (carrotManFields.szTBTMainText.isNotEmpty()) {
                            Text(
                                text = carrotManFields.szTBTMainText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF374151),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                    
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "折叠" else "展开",
                        tint = Color(0xFF64748B)
                    )
                }
                
                // 数据表格（可折叠）
                if (isExpanded) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 表格头部
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF8FAFC))
                            .padding(vertical = 5.dp, horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "字段",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "描述",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "值",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    // 数据行
                    dataFieldManager.getOpenpilotReceiveFields(carrotManFields).forEach { fieldData ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp, horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = fieldData.first,
                                fontSize = 11.sp,
                                color = Color(0xFF374151),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = fieldData.second,
                                fontSize = 11.sp,
                                color = Color(0xFF6B7280),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = fieldData.third,
                                fontSize = 11.sp,
                                color = Color(0xFF059669),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 交通灯状态指示器
 */
@Composable
private fun TrafficLightIndicator(
    trafficState: Int,
    leftSec: Int,
    direction: Int
) {
    val color = when (trafficState) {
        0 -> Color.Gray
        1 -> Color.Red
        2 -> Color.Green
        3 -> Color.Yellow
        else -> Color.Gray
    }
    
    // 方向图标（使用 Material Icons 中可用的图标或文本符号）
    val directionIcon: ImageVector? = when (direction) {
        1 -> Icons.AutoMirrored.Filled.ArrowBack  // 左转
        2 -> Icons.AutoMirrored.Filled.ArrowForward  // 右转
        3 -> Icons.AutoMirrored.Filled.ArrowBack  // 左转掉头（使用左箭头）
        4 -> null  // 直行（使用文本符号 ↑）
        5 -> Icons.AutoMirrored.Filled.ArrowForward  // 右转掉头（使用右箭头）
        else -> null  // 0或其他：无方向图标
    }
    
    // 直行方向文本符号
    val directionText: String? = when (direction) {
        4 -> "↑"  // 直行
        else -> null
    }
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 红绿灯状态指示器（带内部图标）
        Box(
            modifier = Modifier
                .size(20.dp),
            contentAlignment = Alignment.Center
        ) {
            // 背景圆形
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color, androidx.compose.foundation.shape.CircleShape)
            )
            
            // 方向图标（白色，居中显示）
            directionIcon?.let { icon ->
                Icon(
                    imageVector = icon,
                    contentDescription = when (direction) {
                        1 -> "左转"
                        2 -> "右转"
                        3 -> "左转掉头"
                        4 -> "直行"
                        5 -> "右转掉头"
                        else -> "方向"
                    },
                    modifier = Modifier.size(12.dp),
                    tint = Color.White
                )
            }
            
            // 直行方向文本符号（白色，居中显示）
            directionText?.let { text ->
                Text(
                    text = text,
                    fontSize = 12.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        // 倒计时秒数（如果有）
        if (leftSec > 0) {
            Text(
                text = "$leftSec",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 12.sp,
                color = color,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * 底部导航项数据类
 */
private data class BottomNavItem(
    val title: String,
    val icon: ImageVector,
    val index: Int
)

/**
 * 🆕 UI 常量配置（从 VehicleLaneVisualization.kt 移植）
 */
private object VehicleLaneUIConstants {
    val COLOR_SUCCESS = Color(0xFF10B981)
    val COLOR_WARNING = Color(0xFFF59E0B)
    val COLOR_DANGER = Color(0xFFEF4444)
    val COLOR_INFO = Color(0xFF3B82F6)
    val COLOR_NEUTRAL = Color(0xFF94A3B8)
    val CARD_BACKGROUND = Color(0xFF1E293B).copy(alpha = 0.8f)
    val CARD_SHAPE = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
}

/**
 * 🆕 超车提示信息数据类
 */
private data class OvertakeHintInfo(
    val cardColor: Color,
    val icon: String,
    val title: String,
    val detail: String,
    val titleColor: Color
)

/**
 * 🆕 检查条件数据类
 */
private data class CheckCondition(
    val name: String,
    val threshold: String,
    val actual: String,
    val isMet: Boolean,
    val hasData: Boolean = true // 是否有数据
)

/**
 * 🆕 车辆和车道详细信息显示区域（从弹窗移植到主页面）
 * 仅用户类型 3 或 4 显示
 */
@Composable
private fun VehicleLaneDetailsSection(
    core: MainActivityCore,
    carrotManFields: CarrotManFields
) {
    // 🆕 优化：从 State对象读取值，确保自动重组
    val data by core.xiaogeData  // 使用 by 委托，自动订阅 State 变化
    
    // 🆕 优化：实时计算数据延迟，确保UI及时更新
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
    
    // 定期更新当前时间，用于实时计算数据延迟（每100ms更新一次，平衡性能和实时性）
    LaunchedEffect(Unit) {
        while (true) {
            delay(100)
            currentTime = System.currentTimeMillis()
        }
    }
    
    // 数据延迟计算
    val DATA_STALE_THRESHOLD_MS = 2000L
    val DATA_DISCONNECTED_THRESHOLD_MS = 4000L
    val currentData = data
    val dataAge = when {
        currentData == null -> DATA_DISCONNECTED_THRESHOLD_MS + 1000L
        currentData.receiveTime <= 0 -> DATA_DISCONNECTED_THRESHOLD_MS + 1000L
        else -> (currentTime - currentData.receiveTime).coerceAtLeast(0L)
    }
    val isDataStale = dataAge > DATA_STALE_THRESHOLD_MS
    
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 超车提示信息卡片
        val prefsForHint = context.getSharedPreferences("CarrotAmap", android.content.Context.MODE_PRIVATE)
        val overtakeModeForHint = prefsForHint.getInt("overtake_mode", 0)
        val hintInfo = getOvertakeHintInfo(
            overtakeMode = overtakeModeForHint,
            overtakeStatus = currentData?.overtakeStatus,
            laneChangeState = currentData?.modelV2?.meta?.laneChangeState ?: 0,
            laneChangeDirection = currentData?.modelV2?.meta?.laneChangeDirection ?: 0
        )
        
        // 获取额外的信息行（冷却时间、阻止原因）
        val cooldownText = currentData?.overtakeStatus?.cooldownRemaining?.let { cooldown ->
            if (cooldown > 0) "冷却: ${String.format("%.1f", cooldown / 1000.0)}s" else null
        }
        val blockingReason = currentData?.overtakeStatus?.blockingReason
        val shouldShowBlockingReason = blockingReason != null && 
            hintInfo.detail != blockingReason && 
            !hintInfo.detail.contains(blockingReason)
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = hintInfo.cardColor
            ),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = hintInfo.icon,
                    fontSize = 14.sp
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    // 第一行：标题（状态文本）
                    Text(
                        text = hintInfo.title,
                        fontSize = 11.sp,
                        color = hintInfo.titleColor,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    // 第二行：详情描述
                    Text(
                        text = hintInfo.detail,
                        fontSize = 9.sp,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    // 第三行：冷却时间或阻止原因（优先显示阻止原因）
                    when {
                        shouldShowBlockingReason -> {
                            Text(
                                text = blockingReason!!,
                                fontSize = 8.sp,
                                color = Color(0xFFEF4444),
                                fontWeight = FontWeight.Light,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                        cooldownText != null -> {
                            Text(
                                text = cooldownText,
                                fontSize = 8.sp,
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
        
        // 数据信息面板（13个检查条件的表格）
        VehicleLaneDataInfoPanel(
            data = currentData,
            dataAge = dataAge,
            isDataStale = isDataStale,
            carrotManFields = carrotManFields,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 🆕 获取超车提示信息（从 VehicleLaneVisualization.kt 移植）
 */
private fun getOvertakeHintInfo(
    overtakeMode: Int,
    overtakeStatus: OvertakeStatusData?,
    laneChangeState: Int,
    laneChangeDirection: Int
): OvertakeHintInfo {
    return when {
        // 自动超车模式（模式2）且满足超车条件
        overtakeMode == 2 && overtakeStatus?.canOvertake == true -> OvertakeHintInfo(
            cardColor = VehicleLaneUIConstants.COLOR_SUCCESS.copy(alpha = 0.2f),
            icon = "⚠️",
            title = "自动超车请注意安全",
            detail = "系统将自动执行超车操作，请保持注意力集中",
            titleColor = VehicleLaneUIConstants.COLOR_SUCCESS
        )
        // 拨杆超车模式（模式1）且满足超车条件
        overtakeMode == 1 && overtakeStatus?.canOvertake == true -> OvertakeHintInfo(
            cardColor = VehicleLaneUIConstants.COLOR_INFO.copy(alpha = 0.2f),
            icon = "🔔",
            title = "变道超车请拨杆确认",
            detail = "系统已检测到超车条件，请拨动转向杆确认",
            titleColor = VehicleLaneUIConstants.COLOR_INFO
        )
        // 禁止超车模式（模式0）
        overtakeMode == 0 -> OvertakeHintInfo(
            cardColor = VehicleLaneUIConstants.COLOR_NEUTRAL.copy(alpha = 0.2f),
            icon = "🚫",
            title = "超车功能已禁用",
            detail = "请在设置中启用超车功能",
            titleColor = VehicleLaneUIConstants.COLOR_NEUTRAL
        )
        // 不能超车且有阻止原因
        overtakeStatus != null && !overtakeStatus.canOvertake && overtakeStatus.blockingReason != null -> OvertakeHintInfo(
            cardColor = VehicleLaneUIConstants.COLOR_WARNING.copy(alpha = 0.2f),
            icon = "ℹ️",
            title = "超车条件不满足",
            detail = overtakeStatus.blockingReason,
            titleColor = VehicleLaneUIConstants.COLOR_WARNING
        )
        // 冷却中
        overtakeStatus?.cooldownRemaining != null && overtakeStatus.cooldownRemaining > 0 -> OvertakeHintInfo(
            cardColor = VehicleLaneUIConstants.COLOR_WARNING.copy(alpha = 0.2f),
            icon = "⏱️",
            title = "超车冷却中",
            detail = "剩余 ${String.format("%.1f", overtakeStatus.cooldownRemaining / 1000.0)} 秒",
            titleColor = VehicleLaneUIConstants.COLOR_WARNING
        )
        // 变道中
        laneChangeState != 0 -> {
            val direction = when (laneChangeDirection) {
                1 -> "左"
                2 -> "右"
                0 -> ""
                else -> "未知($laneChangeDirection)"
            }
            OvertakeHintInfo(
                cardColor = VehicleLaneUIConstants.COLOR_INFO.copy(alpha = 0.2f),
                icon = "🔄",
                title = if (direction.isNotEmpty()) "变道中($direction)" else "变道中",
                detail = "正在执行变道操作，请保持稳定",
                titleColor = VehicleLaneUIConstants.COLOR_INFO
            )
        }
        // 默认监控状态
        else -> OvertakeHintInfo(
            cardColor = VehicleLaneUIConstants.COLOR_NEUTRAL.copy(alpha = 0.2f),
            icon = "👁️",
            title = "监控中",
            detail = "系统正在监控超车条件",
            titleColor = VehicleLaneUIConstants.COLOR_NEUTRAL
        )
    }
}

/**
 * 🆕 顶部状态栏（从 VehicleLaneVisualization.kt 移植，移除关闭按钮）
 */
@Composable
private fun VehicleLaneTopBar(
    dataAge: Long,
    isDataStale: Boolean,
    overtakeMode: Int,
    systemState: SystemStateData?,
    currentData: XiaogeVehicleData?,
    deviceIP: String?,
    isTcpConnected: Boolean
) {
    val DATA_STALE_THRESHOLD_MS = 2000L
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧：超车设置和系统状态
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 超车设置状态
            val overtakeModeNames = arrayOf("禁止超车", "拨杆超车", "自动超车")
            val overtakeModeColors = arrayOf(
                VehicleLaneUIConstants.COLOR_NEUTRAL,
                VehicleLaneUIConstants.COLOR_INFO,
                VehicleLaneUIConstants.COLOR_SUCCESS
            )
            val overtakeModeColor = overtakeModeColors[overtakeMode]
            
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                color = overtakeModeColor.copy(alpha = 0.2f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .background(
                                color = overtakeModeColor,
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    )
                    Text(
                        text = overtakeModeNames[overtakeMode],
                        fontSize = 8.sp,
                        color = overtakeModeColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            // 系统状态
            val systemEnabled = systemState?.enabled == true
            val systemActive = systemState?.active == true
            val systemColor = if (systemEnabled && systemActive) VehicleLaneUIConstants.COLOR_SUCCESS else VehicleLaneUIConstants.COLOR_NEUTRAL
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                color = systemColor.copy(alpha = 0.2f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .background(
                                color = systemColor,
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    )
                    Text(
                        text = if (systemEnabled && systemActive) "激活" else "待机",
                        fontSize = 8.sp,
                        color = systemColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        
        // 右侧：设备IP和网络状态（无关闭按钮）
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 设备IP显示
            if (deviceIP != null && deviceIP.isNotEmpty()) {
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                    color = VehicleLaneUIConstants.COLOR_INFO.copy(alpha = 0.2f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .background(
                                    color = VehicleLaneUIConstants.COLOR_INFO,
                                    shape = androidx.compose.foundation.shape.CircleShape
                                )
                        )
                        Text(
                            text = deviceIP,
                            fontSize = 8.sp,
                            color = VehicleLaneUIConstants.COLOR_INFO,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                    color = VehicleLaneUIConstants.COLOR_NEUTRAL.copy(alpha = 0.2f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .background(
                                    color = VehicleLaneUIConstants.COLOR_NEUTRAL,
                                    shape = androidx.compose.foundation.shape.CircleShape
                                )
                        )
                        Text(
                            text = "未找到设备",
                            fontSize = 8.sp,
                            color = VehicleLaneUIConstants.COLOR_NEUTRAL,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            
            // 网络状态
            val isSystemActive = systemState?.active == true
            val isSystemEnabled = systemState?.enabled == true
            val isOnroad = currentData != null && 
                          currentData.carState != null && 
                          currentData.modelV2 != null
            val isTcpDisconnected = !isTcpConnected
            
            val (statusText, statusColor, statusIcon) = when {
                isTcpDisconnected -> Triple("断开", Color(0xFFEF4444), "●")
                isDataStale && dataAge > 3000 -> Triple("异常", Color(0xFFDC2626), "⚠")
                isDataStale -> Triple("延迟", Color(0xFFF59E0B), "◐")
                isSystemActive -> Triple("正常", Color(0xFF10B981), "●")
                isOnroad && isSystemEnabled -> Triple("准备", Color(0xFF3B82F6), "◔")
                isOnroad -> Triple("准备", Color(0xFF60A5FA), "◑")
                else -> Triple("待机", Color(0xFF64748B), "○")
            }
            
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                color = statusColor.copy(alpha = 0.2f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = statusIcon,
                        fontSize = 7.sp,
                        color = statusColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when {
                            isTcpDisconnected -> statusText
                            isDataStale -> "$statusText ${String.format("%.1f", dataAge / 1000.0)}s"
                            else -> statusText
                        },
                        fontSize = 8.sp,
                        color = statusColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * 🆕 数据信息面板（从 VehicleLaneVisualization.kt 移植）
 */
@Composable
private fun VehicleLaneDataInfoPanel(
    data: XiaogeVehicleData?,
    dataAge: Long,
    isDataStale: Boolean,
    carrotManFields: CarrotManFields?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("CarrotAmap", android.content.Context.MODE_PRIVATE) }
    
    var minOvertakeSpeedKph by remember { mutableStateOf(prefs.getFloat("overtake_param_min_speed_kph", 60f).coerceIn(40f, 100f)) }
    var speedDiffThresholdKph by remember { mutableStateOf(prefs.getFloat("overtake_param_speed_diff_kph", 10f).coerceIn(5f, 30f)) }
    
    LaunchedEffect(Unit) {
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
    
    val carState = data?.carState
    val modelV2 = data?.modelV2
    val lead0 = modelV2?.lead0
    val laneChangeState = data?.modelV2?.meta?.laneChangeState ?: 0
    
    val MAX_LEAD_DISTANCE = 80.0f
    val MIN_LEAD_PROB = 0.5f
    val MIN_LEAD_SPEED_KPH = 50.0f
    val MAX_CURVATURE = 0.02f
    val MAX_STEERING_ANGLE = 15.0f
    val MIN_LANE_PROB = 0.7f
    val MIN_LANE_WIDTH = 3.0f
    
    val conditions = buildList {
        // 一、本车基础状态
        val vEgoKmh = (carState?.vEgo ?: 0f) * 3.6f
        val hasVEgoData = carState?.vEgo != null
        add(CheckCondition(
            name = "① 本车速度",
            threshold = "≥ ${minOvertakeSpeedKph.toInt()} km/h",
            actual = if (hasVEgoData) "${String.format("%.1f", vEgoKmh)} km/h" else "N/A",
            isMet = hasVEgoData && vEgoKmh >= minOvertakeSpeedKph,
            hasData = hasVEgoData
        ))
        
        val steeringAngle = kotlin.math.abs(carState?.steeringAngleDeg ?: 0f)
        val hasSteeringData = carState?.steeringAngleDeg != null
        add(CheckCondition(
            name = "② 方向盘角度",
            threshold = "≤ ${MAX_STEERING_ANGLE.toInt()}°",
            actual = if (hasSteeringData) "${String.format("%.1f", steeringAngle)}°" else "N/A",
            isMet = hasSteeringData && steeringAngle <= MAX_STEERING_ANGLE,
            hasData = hasSteeringData
        ))
        
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
            isMet = laneChangeState == 0,
            hasData = true // 变道状态总是有数据
        ))
        
        // 二、前车状态
        val leadDistance = lead0?.x ?: 0f
        val leadProb = lead0?.prob ?: 0f
        val hasValidLead = lead0 != null && leadDistance < MAX_LEAD_DISTANCE && leadProb >= MIN_LEAD_PROB
        val hasLeadData = lead0 != null
        add(CheckCondition(
            name = "④ 前车距离",
            threshold = "< ${MAX_LEAD_DISTANCE.toInt()}m",
            actual = if (hasLeadData) "${String.format("%.1f", leadDistance)}m" else "无车",
            isMet = hasValidLead,
            hasData = hasLeadData
        ))
        
        val leadSpeedKmh = (lead0?.v ?: 0f) * 3.6f
        add(CheckCondition(
            name = "⑤ 前车速度",
            threshold = "≥ ${MIN_LEAD_SPEED_KPH.toInt()} km/h",
            actual = if (hasLeadData) "${String.format("%.1f", leadSpeedKmh)} km/h" else "N/A",
            isMet = hasLeadData && leadSpeedKmh >= MIN_LEAD_SPEED_KPH,
            hasData = hasLeadData
        ))
        
        val speedDiff = vEgoKmh - leadSpeedKmh
        add(CheckCondition(
            name = "⑥ 速度差",
            threshold = "≥ ${speedDiffThresholdKph.toInt()} km/h",
            actual = if (hasLeadData) "${String.format("%.1f", speedDiff)} km/h" else "N/A",
            isMet = hasLeadData && speedDiff >= speedDiffThresholdKph,
            hasData = hasLeadData
        ))
        
        // 三、道路条件
        val curvature = kotlin.math.abs(modelV2?.curvature?.maxOrientationRate ?: 0f)
        val hasCurvatureData = modelV2?.curvature?.maxOrientationRate != null
        add(CheckCondition(
            name = "⑦ 道路曲率",
            threshold = "< ${(MAX_CURVATURE * 1000).toInt()} mrad/s",
            actual = if (hasCurvatureData) "${String.format("%.1f", curvature * 1000)} mrad/s" else "N/A",
            isMet = hasCurvatureData && curvature < MAX_CURVATURE,
            hasData = hasCurvatureData
        ))
        
        // 四、左右超车可行性（合并显示）
        val leftLaneProb = modelV2?.laneLineProbs?.getOrNull(0) ?: 0f
        val rightLaneProb = modelV2?.laneLineProbs?.getOrNull(1) ?: 0f
        val hasLaneProbData = modelV2?.laneLineProbs != null && modelV2.laneLineProbs.size >= 2
        add(CheckCondition(
            name = "⑧ 车道线",
            threshold = "≥ ${(MIN_LANE_PROB * 100).toInt()}%",
            actual = if (hasLaneProbData) "左:${String.format("%.0f", leftLaneProb * 100)}% / 右:${String.format("%.0f", rightLaneProb * 100)}%" else "N/A",
            isMet = hasLaneProbData && leftLaneProb >= MIN_LANE_PROB && rightLaneProb >= MIN_LANE_PROB,
            hasData = hasLaneProbData
        ))
        
        val laneWidthLeft = modelV2?.meta?.laneWidthLeft ?: 0f
        val laneWidthRight = modelV2?.meta?.laneWidthRight ?: 0f
        val hasLaneWidthData = modelV2?.meta != null
        add(CheckCondition(
            name = "⑨ 车道宽",
            threshold = "≥ ${MIN_LANE_WIDTH}m",
            actual = if (hasLaneWidthData) "左:${String.format("%.2f", laneWidthLeft)}m / 右:${String.format("%.2f", laneWidthRight)}m" else "N/A",
            isMet = hasLaneWidthData && laneWidthLeft >= MIN_LANE_WIDTH && laneWidthRight >= MIN_LANE_WIDTH,
            hasData = hasLaneWidthData
        ))
        
        val leftBlindspot = carState?.leftBlindspot == true
        val rightBlindspot = carState?.rightBlindspot == true
        val hasBlindspotData = carState != null
        add(CheckCondition(
            name = "⑩ 盲区",
            threshold = "无车",
            actual = if (hasBlindspotData) "左:${if (leftBlindspot) "有车" else "无车"} / 右:${if (rightBlindspot) "有车" else "无车"}" else "N/A",
            isMet = hasBlindspotData && !leftBlindspot && !rightBlindspot,
            hasData = hasBlindspotData
        ))
    }
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 变道中时显示进度条
        if (laneChangeState == 1) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF3B82F6).copy(alpha = 0.2f)
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "变道中...",
                        fontSize = 10.sp,
                        color = Color(0xFF3B82F6),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth()
                    )
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
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
                containerColor = VehicleLaneUIConstants.CARD_BACKGROUND
            ),
            shape = VehicleLaneUIConstants.CARD_SHAPE
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // 表头（3列：条件、阈值、实际值+状态）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF334155).copy(alpha = 0.3f))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "条件",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF1F5F9),
                        modifier = Modifier.weight(2f)
                    )
                    Text(
                        text = "阈值",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF1F5F9),
                        modifier = Modifier.weight(1.8f)
                    )
                    Text(
                        text = "实际值",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF1F5F9),
                        modifier = Modifier.weight(2.2f)
                    )
                }
                
                // 表格内容
                conditions.forEachIndexed { index, condition ->
                    // 分隔线位置：前车状态(3)、道路条件(6)、左右超车可行性(7)
                    if (index == 3 || index == 6 || index == 7) {
                        HorizontalDivider(
                            color = Color(0xFF475569).copy(alpha = 0.4f),
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (index % 2 == 0) Color.Transparent 
                                else Color(0xFF334155).copy(alpha = 0.15f)
                            )
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = condition.name,
                            fontSize = 8.sp,
                            color = Color(0xFFE2E8F0),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(2f)
                        )
                        Text(
                            text = condition.threshold,
                            fontSize = 7.5.sp,
                            color = Color(0xFFCBD5E1),
                            modifier = Modifier.weight(1.8f)
                        )
                        // 合并实际值和状态列（紧凑布局）
                        Row(
                            modifier = Modifier.weight(2.2f),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = condition.actual,
                                fontSize = 7.5.sp,
                                color = when {
                                    !condition.hasData -> Color(0xFF94A3B8) // 灰色：没有数据
                                    condition.isMet -> Color(0xFF94E2D5) // 绿色：符合阈值
                                    else -> Color(0xFFFCA5A5) // 红色：不符合阈值
                                },
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Text(
                                text = when {
                                    !condition.hasData -> "—"
                                    condition.isMet -> "✓"
                                    else -> "✗"
                                },
                                fontSize = 10.sp,
                                color = when {
                                    !condition.hasData -> Color(0xFF94A3B8) // 灰色：没有数据
                                    condition.isMet -> VehicleLaneUIConstants.COLOR_SUCCESS // 绿色：符合阈值
                                    else -> VehicleLaneUIConstants.COLOR_DANGER // 红色：不符合阈值
                                },
                                fontWeight = FontWeight.Bold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

