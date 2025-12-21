package com.example.carrotamap

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.alpha // 🆕 导入 alpha

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
                                xiaogeTcpConnected = core.xiaogeTcpConnected.value,
                                xiaogeDataTimeout = core.xiaogeDataTimeout.value,
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
                            
                            // 🆕 添加超车条件表格（移动到此处）
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "超车条件监控",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            // 从 core 获取实时数据
                            val xiaogeData by core.xiaogeData
                            VehicleConditionsTable(data = xiaogeData)
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
        xiaogeTcpConnected: Boolean,
        xiaogeDataTimeout: Boolean,
        onSendCommand: (String, String) -> Unit,
        onSendRoadLimitSpeed: () -> Unit,
        onLaunchAmap: () -> Unit,
        onSendNavConfirmation: () -> Unit // 🆕 发送导航确认
    ) {
        val scrollState = rememberScrollState()
        // 🆕 获取实时数据，用于显示序号、时间以及车道位置信息
        val data by core.xiaogeData
        
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
            // 主布局容器
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 🆕 车道信息显示（常驻顶部）
                LaneInfoDisplay(
                    laneInfoList = carrotManFields.laneInfoList,
                    naviIcon = carrotManFields.amapIcon,
                    nextRoadNOAOrNot = carrotManFields.nextRoadNOAOrNot,
                    trafficLightCount = carrotManFields.traffic_light_count,
                    routeRemainTrafficLightNum = carrotManFields.routeRemainTrafficLightNum,
                    xiaogeData = data
                )

                // 主内容区域（支持滚动，底部留出按钮空间）
                Column(
                    modifier = Modifier
                        .weight(1f) // 占据剩余空间
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(bottom = 80.dp) // 为底部固定按钮留出空间
                ) {
                    // 🔄 调整布局：实时数据组件移到顶部
                    // Comma3数据表格（可折叠）
                    Comma3DataTable(
                        carrotManFields = carrotManFields,
                        dataFieldManager = dataFieldManager,
                        userType = userType,
                        xiaogeTcpConnected = xiaogeTcpConnected,
                        xiaogeDataTimeout = xiaogeDataTimeout,
                        xiaogeData = data  // 🆕 传递数据，用于显示序号和时间
                    )
                    
                    // 🆕 详细信息显示区域（用户类型 3, 4 或 0先锋用户 显示）
                    if (userType == 3 || userType == 4 || userType == 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        VehicleLaneDetailsSection(
                            core = core,
                            carrotManFields = carrotManFields
                        )
                    }
                    
                    // 添加底部安全间距
                    Spacer(modifier = Modifier.height(6.dp))
                }
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
        
        val pages = if (userType == 4 || userType == 0) {
            // 铁粉用户和先锋用户可以看到数据页面和命令页面
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
            0 -> "先锋"
            1 -> "新用户"
            2 -> "支持者"
            3 -> "赞助者"
            4 -> "铁粉"
            else -> "未知类型($userType)"
        }
    }

    /**
     * 根据TCP连接状态返回颜色
     * @param isConnected TCP是否已连接
     * @param isDataTimeout 数据是否超时（连接但无数据）
     * @return 颜色：灰色=无连接，绿色=正常，黄色=异常
     */
    private fun getTcpConnectionStatusColor(
        isConnected: Boolean,
        isDataTimeout: Boolean
    ): Color {
        return when {
            !isConnected -> Color(0xFF9CA3AF) // 灰色：无连接
            isDataTimeout -> Color(0xFFF59E0B) // 黄色：异常（连接但数据超时）
            else -> Color(0xFF10B981) // 绿色：正常（连接且有数据）
        }
    }

    /**
     * Comma3数据表格组件（可折叠）
     */
    @Composable
    private fun Comma3DataTable(
        carrotManFields: CarrotManFields,
        dataFieldManager: DataFieldManager,
        userType: Int,
        xiaogeTcpConnected: Boolean,
        xiaogeDataTimeout: Boolean,
        xiaogeData: XiaogeVehicleData? = null  // 🆕 添加数据参数，用于显示序号和时间
    ) {
        var isExpanded by remember { mutableStateOf(false) }
        val userTypeText = getUserTypeText(userType)
        val connectionStatusColor = getTcpConnectionStatusColor(xiaogeTcpConnected, xiaogeDataTimeout)
        
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
                            color = connectionStatusColor
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
                    
                    // 🆕 在表格底部显示数据包序号和时间信息（用于调试和判断断联时间）
                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFFE5E7EB),
                        thickness = 1.dp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp, horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (xiaogeData != null) {
                            // 数据序号
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "序号:",
                                    fontSize = 10.sp,
                                    color = Color(0xFF64748B),
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${xiaogeData.sequence}",
                                    fontSize = 10.sp,
                                    color = Color(0xFF059669),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            // 接收时间
                            val receiveTimeText = if (xiaogeData.receiveTime > 0) {
                                val now = System.currentTimeMillis()
                                val age = now - xiaogeData.receiveTime
                                "${age}ms前"
                            } else {
                                "未知"
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "接收:",
                                    fontSize = 10.sp,
                                    color = Color(0xFF64748B),
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = receiveTimeText,
                                    fontSize = 10.sp,
                                    color = if (xiaogeData.receiveTime > 0) {
                                        Color(0xFF059669)
                                    } else {
                                        Color(0xFF9CA3AF)
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            // 无数据时显示提示
                            Text(
                                text = "等待数据...",
                                fontSize = 10.sp,
                                color = Color(0xFF9CA3AF),
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
 * 🆕 车辆条件检查表格（从 VehicleLaneDataInfoPanel 分离）
 */
@Composable
private fun VehicleConditionsTable(
    data: XiaogeVehicleData?,
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
        // 一、本车状态（合并：速度、方向盘、变道）
        val vEgoKmh = (carState?.vEgo ?: 0f) * 3.6f
        val hasVEgoData = carState?.vEgo != null
        val vEgoOk = hasVEgoData && vEgoKmh >= minOvertakeSpeedKph
        
        val steeringAngle = kotlin.math.abs(carState?.steeringAngleDeg ?: 0f)
        val hasSteeringData = carState?.steeringAngleDeg != null
        val steeringOk = hasSteeringData && steeringAngle <= MAX_STEERING_ANGLE
        
        val laneChangeOk = laneChangeState == 0
        val laneChangeText = when (laneChangeState) {
            0 -> "未变道"
            1 -> "变道中"
            2 -> "完成"
            3 -> "取消"
            else -> "未知"
        }
        
        val carStateOk = vEgoOk && steeringOk && laneChangeOk
        val carStateData = hasVEgoData || hasSteeringData
        add(CheckCondition(
            name = "① 本车状态",
            threshold = "速度≥${minOvertakeSpeedKph.toInt()}/转向≤${MAX_STEERING_ANGLE.toInt()}°/未变道",
            actual = if (carStateData) "速度:${String.format("%.0f", vEgoKmh)} / 转向:${String.format("%.0f", steeringAngle)}° / $laneChangeText" else "N/A",
            isMet = carStateOk,
            hasData = carStateData || true // 变道状态总是有数据
        ))
        
        // 二、前车状态（合并：距离、速度、速度差）
        val leadDistance = lead0?.x ?: 0f
        val leadProb = lead0?.prob ?: 0f
        val hasValidLead = lead0 != null && leadDistance < MAX_LEAD_DISTANCE && leadProb >= MIN_LEAD_PROB
        val hasLeadData = lead0 != null
        
        val leadSpeedKmh = (lead0?.v ?: 0f) * 3.6f
        val leadSpeedOk = hasLeadData && leadSpeedKmh >= MIN_LEAD_SPEED_KPH
        
        val speedDiff = vEgoKmh - leadSpeedKmh
        val speedDiffOk = hasLeadData && speedDiff >= speedDiffThresholdKph
        
        val leadStateOk = hasValidLead && leadSpeedOk && speedDiffOk
        add(CheckCondition(
            name = "② 前车状态",
            threshold = "距离<${MAX_LEAD_DISTANCE.toInt()}m/速度≥${MIN_LEAD_SPEED_KPH.toInt()}/差≥${speedDiffThresholdKph.toInt()}",
            actual = if (hasLeadData) "${String.format("%.0f", leadDistance)}m / ${String.format("%.0f", leadSpeedKmh)} / ${String.format("%.0f", speedDiff)}" else "无车",
            isMet = leadStateOk,
            hasData = hasLeadData
        ))
        
        // 三、道路车道（合并：曲率、车道线、车道宽）
        val curvature = kotlin.math.abs(modelV2?.curvature?.maxOrientationRate ?: 0f)
        val hasCurvatureData = modelV2?.curvature?.maxOrientationRate != null
        val curvatureOk = hasCurvatureData && curvature < MAX_CURVATURE
        
        val leftLaneProb = modelV2?.laneLineProbs?.getOrNull(0) ?: 0f
        val rightLaneProb = modelV2?.laneLineProbs?.getOrNull(1) ?: 0f
        val hasLaneProbData = modelV2?.laneLineProbs != null && modelV2.laneLineProbs.size >= 2
        val laneProbOk = hasLaneProbData && leftLaneProb >= MIN_LANE_PROB && rightLaneProb >= MIN_LANE_PROB
        
        val laneWidthLeft = modelV2?.meta?.laneWidthLeft ?: 0f
        val laneWidthRight = modelV2?.meta?.laneWidthRight ?: 0f
        val hasLaneWidthData = modelV2?.meta != null
        val laneWidthOk = hasLaneWidthData && laneWidthLeft >= MIN_LANE_WIDTH && laneWidthRight >= MIN_LANE_WIDTH
        
        val roadStateOk = curvatureOk && laneProbOk && laneWidthOk
        val roadStateData = hasCurvatureData || hasLaneProbData || hasLaneWidthData
        add(CheckCondition(
            name = "③ 道路车道",
            threshold = "曲率<${(MAX_CURVATURE * 1000).toInt()}/线≥${(MIN_LANE_PROB * 100).toInt()}%/宽≥${MIN_LANE_WIDTH.toInt()}m",
            actual = if (roadStateData) {
                val curvText = if (hasCurvatureData) "${String.format("%.0f", curvature * 1000)}" else "N/A"
                val probText = if (hasLaneProbData) "${String.format("%.0f", leftLaneProb * 100)}/${String.format("%.0f", rightLaneProb * 100)}" else "N/A"
                val widthText = if (hasLaneWidthData) "${String.format("%.1f", laneWidthLeft)}/${String.format("%.1f", laneWidthRight)}" else "N/A"
                "$curvText / $probText% / ${widthText}m"
            } else "N/A",
            isMet = roadStateOk,
            hasData = roadStateData
        ))
        
        // 四、盲区检测
        val leftBlindspot = carState?.leftBlindspot == true
        val rightBlindspot = carState?.rightBlindspot == true
        val hasBlindspotData = carState != null
        add(CheckCondition(
            name = "④ 盲区检测",
            threshold = "无车",
            actual = if (hasBlindspotData) "左:${if (leftBlindspot) "有车" else "无车"} / 右:${if (rightBlindspot) "有车" else "无车"}" else "N/A",
            isMet = hasBlindspotData && !leftBlindspot && !rightBlindspot,
            hasData = hasBlindspotData
        ))
    }

    // 检查条件表格
    Card(
        modifier = modifier.fillMaxWidth(),
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
                // 分隔线位置：每行之间添加分隔线
                if (index > 0) {
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
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 超车提示信息卡片（移动到数据面板下方）
        val overtakeModeForHint = prefs.getInt("overtake_mode", 0)
        val hintInfo = getOvertakeHintInfo(
            overtakeMode = overtakeModeForHint,
            overtakeStatus = data?.overtakeStatus,
            laneChangeState = data?.modelV2?.meta?.laneChangeState ?: 0,
            laneChangeDirection = data?.modelV2?.meta?.laneChangeDirection ?: 0
        )
        
        // 获取额外的信息行（冷却时间、阻止原因）
        val cooldownText = data?.overtakeStatus?.cooldownRemaining?.let { cooldown ->
            if (cooldown > 0) "冷却: ${String.format("%.1f", cooldown / 1000.0)}s" else null
        }
        val blockingReason = data?.overtakeStatus?.blockingReason
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

        // 🆕 NOA 战术引导卡片 - 增强版
        if (carrotManFields != null && (
            carrotManFields.exitNameInfo.isNotEmpty() || 
            carrotManFields.sapaName.isNotEmpty() || 
            carrotManFields.roundAboutNum > 0 ||
            carrotManFields.viaPOIdistance > 0 ||
            carrotManFields.segAssistantAction > 0
        )) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.8f)),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 标题栏
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🎯 NOA 战术引导",
                            fontSize = 10.sp,
                            color = Color(0xFF3B82F6),
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // NOA 状态
                            if (carrotManFields.nextRoadNOAOrNot) {
                                Text(
                                    text = "NOA可用",
                                    fontSize = 8.sp,
                                    color = Color(0xFF10B981),
                                    modifier = Modifier
                                        .background(Color(0xFF10B981).copy(alpha = 0.1f), RoundedCornerShape(2.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                            // 定位信息（调试用）
                            if (carrotManFields.curSegNum > 0 || carrotManFields.curPointNum > 0) {
                                Text(
                                    text = "段${carrotManFields.curSegNum}·点${carrotManFields.curPointNum}",
                                    fontSize = 7.sp,
                                    color = Color(0xFF64748B),
                                    modifier = Modifier
                                        .background(Color(0xFF64748B).copy(alpha = 0.1f), RoundedCornerShape(2.dp))
                                        .padding(horizontal = 3.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    // 途径点信息（第一优先级）
                    if (carrotManFields.viaPOIdistance > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF6366F1).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("📍 途径点", fontSize = 8.sp, color = Color(0xFF818CF8))
                                Text(
                                    text = "${carrotManFields.viaPOIdistance}m",
                                    fontSize = 12.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (carrotManFields.viaPOItime > 0) {
                                Text(
                                    text = "约 ${carrotManFields.viaPOItime / 60} 分钟",
                                    fontSize = 9.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }
                    }

                    // 主要战术信息行
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // 出口信息
                        if (carrotManFields.exitNameInfo.isNotEmpty()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("🚏 出口", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                Text(
                                    text = carrotManFields.exitNameInfo,
                                    fontSize = 10.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                if (carrotManFields.exitDirectionInfo.isNotEmpty()) {
                                    Text(
                                        text = carrotManFields.exitDirectionInfo,
                                        fontSize = 7.sp,
                                        color = Color(0xFFFBBF24),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        // 环岛信息
                        if (carrotManFields.roundAboutNum > 0) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("🔄 环岛", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                Text(
                                    text = "第 ${carrotManFields.roundAboutNum} 出口",
                                    fontSize = 10.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                                if (carrotManFields.roundAllNum > 0) {
                                    Text(
                                        text = "共 ${carrotManFields.roundAllNum} 个",
                                        fontSize = 7.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                            }
                        }

                        // 服务区信息
                        if (carrotManFields.sapaName.isNotEmpty()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("🏪 设施", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                Text(
                                    text = carrotManFields.sapaName,
                                    fontSize = 10.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                if (carrotManFields.sapaDist > 0) {
                                    Text(
                                        text = "${carrotManFields.sapaDist}m",
                                        fontSize = 7.sp,
                                        color = Color(0xFF10B981)
                                    )
                                }
                            }
                        }
                    }
                    
                    // 辅助动作与后续指引
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 辅助动作（岔路、分流等复杂路况）
                        if (carrotManFields.segAssistantAction > 0) {
                            val actionText = when (carrotManFields.segAssistantAction) {
                                1 -> "⚠️ 注意分流"
                                2 -> "⚠️ 注意岔路"
                                3 -> "⚠️ 保持车道"
                                else -> "辅助动作:${carrotManFields.segAssistantAction}"
                            }
                            Text(
                                text = actionText,
                                fontSize = 8.sp,
                                color = Color(0xFFFBBF24),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .background(Color(0xFFFBBF24).copy(alpha = 0.1f), RoundedCornerShape(3.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                        
                        // 下下个动作预览
                        if (carrotManFields.nextNextAddIcon.isNotEmpty()) {
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "后续: ${carrotManFields.nextNextAddIcon}",
                                fontSize = 8.sp,
                                color = Color(0xFF6366F1),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 🆕 车道图标映射工具
 */
object LaneIconHelper {
    // Lane Actions (aligned with Amap LaneAction)
    private const val ACTION_AHEAD = 0
    private const val ACTION_LEFT = 1
    private const val ACTION_RIGHT = 3
    private const val ACTION_LU_TURN = 5
    private const val ACTION_RU_TURN = 8

    // Lane Types (IDs)
    private const val LANE_TYPE_AHEAD_LEFT = 2
    private const val LANE_TYPE_AHEAD_RIGHT = 4
    private const val LANE_TYPE_LEFT_RIGHT = 6
    private const val LANE_TYPE_AHEAD_LEFT_RIGHT = 7
    private const val LANE_TYPE_AHEAD_LU_TURN = 9
    private const val LANE_TYPE_AHEAD_RU_TURN = 10
    private const val LANE_TYPE_LEFT_LU_TURN = 11
    private const val LANE_TYPE_RIGHT_RU_TURN = 12
    private const val LANE_TYPE_AHEAD_RIGHT_RU_TURN = 13
    private const val LANE_TYPE_LEFT_IN_LEFT_LU_TURN = 14
    private const val LANE_TYPE_AHEAD_LEFT_LU_TURN = 15
    private const val LANE_TYPE_LEFT_RU_TURN = 19
    private const val LANE_TYPE_BUS = 20
    private const val LANE_TYPE_VARIABLE = 21
    private const val LANE_TYPE_RIGHT_ONLY = 18
    private const val LANE_TYPE_AHEAD_ONLY_SPECIAL = 15 // User correction
    private const val LANE_TYPE_AHEAD_RIGHT_SPECIAL = 32 // User correction

    /**
     * Map Amap Navigation Icon (TBT) to Lane Action
     */
    private fun mapNaviIconToAction(naviIcon: Int): Int {
        return when (naviIcon) {
            2, 4 -> ACTION_LEFT
            3, 5 -> ACTION_RIGHT
            9 -> ACTION_AHEAD
            6 -> ACTION_LU_TURN
            7 -> ACTION_RU_TURN
            else -> ACTION_AHEAD
        }
    }

    /**
     * 根据高德图标 ID 和推荐状态获取资源 ID
     */
    fun getLaneIconResId(context: android.content.Context, iconId: String, isRecommended: Boolean, naviIcon: Int = -1): Int? {
        val res = context.resources
        val packageName = context.packageName
        
        fun isValidResId(id: Int): Boolean {
            return id != 0 && (id ushr 24) != 0
        }

        fun getValidIdentifier(name: String): Int {
            // 1. Try with global_image_ prefix (standard for this project)
            var id = res.getIdentifier("global_image_$name", "drawable", packageName)
            if (isValidResId(id)) return id
            
            // 2. Try raw name
            id = res.getIdentifier(name, "drawable", packageName)
            if (isValidResId(id)) return id
            
            return 0
        }

        // Convert iconId to Int for logic
        val laneType = iconId.toIntOrNull() ?: 0
        // Hex string for fallback (e.g. 15 -> "f")
        val hexId = Integer.toHexString(laneType)

        // 0. Special Case: User corrections for specific IDs
        when (laneType) {
            LANE_TYPE_RIGHT_ONLY -> { // ID 18
                val resId = getValidIdentifier("auto_landback_3")
                if (resId != 0) return resId
            }
            LANE_TYPE_AHEAD_ONLY_SPECIAL -> { // ID 15 is Ahead
                val resId = getValidIdentifier("auto_landback_0")
                if (resId != 0) return resId
            }
            LANE_TYPE_AHEAD_RIGHT_SPECIAL -> { // ID 32
                val resId = getValidIdentifier("landfront_40")
                if (resId != 0) return resId
            }
            30 -> { // ID 30
                val resId = getValidIdentifier("landfront_20")
                if (resId != 0) return resId
            }
            3 -> { // ID 3
                val resId = getValidIdentifier("landback_3")
                if (resId != 0) return resId
            }
            16 -> { // ID 16
                val resId = getValidIdentifier("auto_landback_1")
                if (resId != 0) return resId
            }
            1 -> { // ID 1
                val resId = getValidIdentifier("landback_1")
                if (resId != 0) return resId
            }
            0 -> { // ID 0
                val resId = getValidIdentifier("landback_0")
                if (resId != 0) return resId
            }
            4 -> { // ID 4
                val resId = getValidIdentifier("landback_4")
                if (resId != 0) return resId
            }
            54 -> { // ID 54
                val resId = getValidIdentifier("landfront_15")
                if (resId != 0) return resId
            }
            5 -> { // ID 5
                val resId = getValidIdentifier("landback_5")
                if (resId != 0) return resId
            }
            else -> {
                if (iconId == "3") { // Special check for raw string "3"
                    val resId = getValidIdentifier("landback_3") // Updated to match ID 3 correction
                    if (resId != 0) return resId
                }
            }
        }

        // 1. Try Complex Lane Logic (DriveWayLinear logic)
        if (isRecommended) {
            val action = if (naviIcon != -1) mapNaviIconToAction(naviIcon) else ACTION_AHEAD
            val complexResName = getComplexLaneIcon(laneType, action)
            if (complexResName != null) {
                // Try auto_landfront first if it exists (none found yet, but for future-proofing)
                var resId = getValidIdentifier("auto_${complexResName.replace("landfront", "landback")}")
                if (resId != 0) return resId

                resId = getValidIdentifier(complexResName)
                if (resId != 0) return resId
            }
        }

        // 2. Try Auto Series (Highly recommended by user)
        // Try offset mapping for IDs >= 15 (e.g. 15 -> auto_landback_0)
        if (laneType >= 15) {
            val offsetId = laneType - 15
            val offsetHex = Integer.toHexString(offsetId)
            
            var resIdOffset = getValidIdentifier("auto_landback_$offsetId")
            if (resIdOffset != 0) return resIdOffset
            
            if (offsetHex != offsetId.toString()) {
                resIdOffset = getValidIdentifier("auto_landback_$offsetHex")
                if (resIdOffset != 0) return resIdOffset
            }
        }

        // Try auto_landback_{id}
        var resId = getValidIdentifier("auto_landback_$iconId")
        if (resId != 0) return resId

        // Try auto_landback_{hex}
        if (hexId != iconId) {
            resId = getValidIdentifier("auto_landback_$hexId")
            if (resId != 0) return resId
        }

        // 3. Fallback: Dynamic Lookup (landfront for recommended)
        if (isRecommended) {
            // Try standard landfront_{id}
            resId = getValidIdentifier("landfront_$iconId")
            if (resId != 0) return resId
            
            // Try hex version
            if (hexId != iconId) {
                resId = getValidIdentifier("landfront_$hexId")
                if (resId != 0) return resId
            }
        }

        // 4. Background (Not recommended or fallback)
        // Try landback_{id}
        resId = getValidIdentifier("landback_$iconId")
        if (resId != 0) return resId

        // Try landback_{hex}
        if (hexId != iconId) {
            resId = getValidIdentifier("landback_$hexId")
            if (resId != 0) return resId
        }

        // 4. Last resort: Try getting identifier directly
        resId = getValidIdentifier(iconId)
        if (resId != 0) return resId

        return null
    }

    /**
     * Logic from DriveWayLinear.java complexGuide
     */
    private fun getComplexLaneIcon(laneType: Int, action: Int): String? {
        return when (laneType) {
            LANE_TYPE_AHEAD_RU_TURN -> when (action) { // 10 (a)
                ACTION_AHEAD -> "landfront_a0"
                ACTION_RU_TURN -> "landfront_a8"
                else -> null
            }
            LANE_TYPE_AHEAD_LU_TURN -> when (action) { // 9
                ACTION_AHEAD -> "landfront_90"
                ACTION_LU_TURN -> "landfront_95"
                else -> null
            }
            LANE_TYPE_AHEAD_LEFT -> when (action) { // 2
                ACTION_AHEAD -> "landfront_20"
                ACTION_LEFT -> "landfront_21"
                else -> null
            }
            LANE_TYPE_AHEAD_RIGHT -> when (action) { // 4
                ACTION_AHEAD -> "landfront_40"
                ACTION_RIGHT -> "landfront_43"
                else -> null
            }
            LANE_TYPE_LEFT_RIGHT -> when (action) { // 6
                ACTION_LEFT -> "landfront_61"
                ACTION_RIGHT -> "landfront_63"
                else -> null
            }
            LANE_TYPE_AHEAD_LEFT_RIGHT -> when (action) { // 7
                ACTION_AHEAD -> "landfront_70"
                ACTION_LEFT -> "landfront_71"
                ACTION_RIGHT -> "landfront_73"
                else -> null
            }
            LANE_TYPE_LEFT_LU_TURN -> when (action) { // 11 (b)
                ACTION_LU_TURN -> "landfront_b5"
                ACTION_LEFT -> "landfront_b1"
                else -> null
            }
            LANE_TYPE_RIGHT_RU_TURN -> when (action) { // 12 (c)
                ACTION_RU_TURN -> "landfront_c8"
                ACTION_RIGHT -> "landfront_c3"
                else -> null
            }
            LANE_TYPE_LEFT_IN_LEFT_LU_TURN -> when (action) { // 14 (e)
                ACTION_LEFT -> "landfront_e1"
                ACTION_LU_TURN -> "landfront_e5"
                else -> null
            }
            LANE_TYPE_AHEAD_LEFT_LU_TURN -> when (action) { // 15 (f)
                ACTION_AHEAD -> "landfront_f0"
                ACTION_LEFT -> "landfront_f1"
                ACTION_LU_TURN -> "landfront_f5"
                else -> null
            }
            LANE_TYPE_LEFT_RU_TURN -> when (action) { // 19 (j)
                ACTION_LEFT -> "landfront_j1"
                ACTION_LU_TURN, ACTION_RU_TURN -> "landfront_j8"
                else -> null
            }
            LANE_TYPE_AHEAD_RIGHT_RU_TURN -> when (action) { // 13 (d)
                ACTION_AHEAD -> "landfront_70"
                ACTION_RIGHT -> "landfront_73"
                ACTION_RU_TURN -> "landfront_c8" // Corrected: Use RU_Turn icon from ID 12
                else -> null
            }
            LANE_TYPE_BUS -> "landfront_kk" // 20
            LANE_TYPE_VARIABLE -> "landback_l" // 21
            else -> null
        }
    }
}

/**
 * 🆕 车道信息显示组件
 */
@Composable
fun LaneInfoDisplay(
    laneInfoList: List<LaneInfo>,
    naviIcon: Int = -1,
    nextRoadNOAOrNot: Boolean = false,
    trafficLightCount: Int = 0,
    routeRemainTrafficLightNum: Int = 0,
    xiaogeData: XiaogeVehicleData? = null,
    modifier: Modifier = Modifier
) {
    // 即使没有车道信息，也显示顶部栏（高德风格）
    val context = LocalContext.current

    // 动态计算宽度：如果车道很多，缩小单个车道宽度
    val itemWidth = if (laneInfoList.size > 6) 32.dp else 40.dp
    val itemHeight = 40.dp

    // 使用 Surface 作为底层容器，确保背景色在最底层，且可以设置阴影/提升感
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFF0091FF), // 高德地图蓝色背景
        tonalElevation = 1.dp      // 增加微小提升感，确保层级正确
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 8.dp)
                .heightIn(min = 52.dp), // 略微增加最小高度，确保图标不被压缩
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：NOA状态
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                Surface(
                    color = Color.White.copy(alpha = 0.2f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "NOA",
                        color = if (nextRoadNOAOrNot) Color.Green else Color.LightGray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }

            // 中间：车道信息
            Row(
                modifier = Modifier.weight(3f),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (laneInfoList.isEmpty()) {
                    // 无车道信息时，显示视觉车道位置信息
                    val meta = xiaogeData?.modelV2?.meta
                    val displayText = if (meta != null) {
                        val leftWidth = meta.laneWidthLeft
                        val rightWidth = meta.laneWidthRight
                        val threshold = 3.2f
                        
                        when {
                            leftWidth > threshold && rightWidth > threshold -> "在中间车道行驶"
                            leftWidth <= threshold && rightWidth > threshold -> "在最左侧车道行驶"
                            leftWidth > threshold && rightWidth <= threshold -> "在最右侧车道行驶"
                            else -> "车道行驶中"
                        }
                    } else {
                        "无视觉车道信息数据"
                    }
                    
                    Text(
                        text = displayText,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    laneInfoList.forEach { lane ->
                        val resId = remember(lane.id, lane.isRecommended, naviIcon) {
                            LaneIconHelper.getLaneIconResId(context, lane.id, lane.isRecommended, naviIcon)
                        }
                        
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = itemWidth, height = itemHeight)
                                    .then(
                                        if (lane.isRecommended) {
                                            Modifier
                                                .background(
                                                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                                                    shape = RoundedCornerShape(4.dp)
                                                )
                                                .border(
                                                    width = 2.5.dp,
                                                    color = Color(0xFF10B981),
                                                    shape = RoundedCornerShape(4.dp)
                                                )
                                                .padding(2.dp)
                                        } else {
                                            Modifier
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (resId != null && resId != 0) {
                                    // 显示图标
                                    androidx.compose.foundation.Image(
                                        painter = androidx.compose.ui.res.painterResource(id = resId),
                                        contentDescription = "Lane ${lane.id}",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .alpha(if (lane.isRecommended) 1.0f else 0.6f), // 推荐车道不透明，其他稍淡
                                         contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                    )
                                } else {
                                    // 找不到图片，显示ID作为占位符
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = lane.id,
                                            color = if (lane.isRecommended) Color(0xFF10B981) else Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        // 调试信息：显示 hex
                                        val hex = lane.id.toIntOrNull()?.let { Integer.toHexString(it) } ?: ""
                                        if (hex.isNotEmpty() && hex != lane.id) {
                                            Text(
                                                text = "($hex)",
                                                color = Color.Gray,
                                                fontSize = 8.sp
                                            )
                                        }
                                    }
                                }
                            }
                            
                            /*
                            // Debug Info (Simplified to one line)
                            Text(
                                text = "${lane.driveWayNumber}|${lane.driveWayLaneExtended}|${lane.trafficLaneExtendedNew}|${lane.trafficLaneType}",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Normal
                            )
                            */
                        }
                    }
                }
            }

            // 右侧：红绿灯信息
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 使用红绿灯文字或图标
                    Text(
                        text = "🚦",
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "${if (trafficLightCount >= 0) trafficLightCount else 0} / $routeRemainTrafficLightNum",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
