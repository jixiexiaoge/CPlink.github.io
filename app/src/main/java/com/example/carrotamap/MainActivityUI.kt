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
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.SignalCellularAlt
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
import com.example.carrotamap.ui.components.LaneInfoDisplay
import com.example.carrotamap.ui.components.LaneIconHelper
import kotlinx.coroutines.delay
import kotlin.math.abs
import androidx.compose.ui.draw.alpha

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
                    roadcate = carrotManFields.roadcate,
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
                    // 🆕 详细信息显示区域（用户类型 3, 4 或 0先锋用户 显示）
                    if (userType == 3 || userType == 4 || userType == 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        VehicleLaneDetailsSection(
                            core = core,
                            carrotManFields = carrotManFields
                        )
                    }

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
                    
                    // 🆕 蓝牙控制卡片
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val bluetoothHelper = core.getBluetoothHelperOrNull()
                    if (bluetoothHelper != null) {
                        BluetoothControlCard(bluetoothHelper)
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
    
    /**
     * 🆕 增强型蓝牙控制卡片
     */
    @Composable
    private fun BluetoothControlCard(bluetoothHelper: BluetoothHelper) {
        val connectionState by bluetoothHelper.connectionState.collectAsState()
        val connectedDeviceName by bluetoothHelper.connectedDeviceName.collectAsState()
        val scannedDevices by bluetoothHelper.scannedDevices.collectAsState()
        val isScanning by bluetoothHelper.isScanning.collectAsState()
        
        var showDeviceListDialog by remember { mutableStateOf(false) }
        val context = LocalContext.current
        
        // 权限请求启动器
        val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            if (allGranted) {
                bluetoothHelper.startScan()
                showDeviceListDialog = true
            } else {
                android.widget.Toast.makeText(context, "需要蓝牙和定位权限才能扫描设备", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        
        // 自动连接逻辑
        LaunchedEffect(Unit) {
            if (connectionState == BluetoothState.DISCONNECTED) {
                bluetoothHelper.tryAutoConnect()
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when (connectionState) {
                                    BluetoothState.CONNECTED -> Icons.Default.BluetoothConnected
                                    BluetoothState.CONNECTING, BluetoothState.AUTO_CONNECTING -> Icons.AutoMirrored.Filled.BluetoothSearching
                                    else -> Icons.Default.Bluetooth
                                },
                                contentDescription = "蓝牙",
                                tint = when (connectionState) {
                                    BluetoothState.CONNECTED -> Color(0xFF3B82F6)
                                    BluetoothState.CONNECTING, BluetoothState.AUTO_CONNECTING -> Color(0xFFF59E0B)
                                    else -> Color.Gray
                                },
                                modifier = Modifier.size(24.dp)
                            )
                            if (connectionState == BluetoothState.CONNECTING || connectionState == BluetoothState.AUTO_CONNECTING) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    color = Color(0xFFF59E0B),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "蓝牙控制器",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B)
                            )
                            Text(
                                text = when (connectionState) {
                                    BluetoothState.CONNECTED -> "已连接: ${connectedDeviceName ?: "未知"}"
                                    BluetoothState.CONNECTING -> "正在连接..."
                                    BluetoothState.AUTO_CONNECTING -> "自动连接中..."
                                    BluetoothState.DISCONNECTED -> "未连接"
                                },
                                fontSize = 12.sp,
                                color = when (connectionState) {
                                    BluetoothState.CONNECTED -> Color(0xFF10B981)
                                    BluetoothState.CONNECTING, BluetoothState.AUTO_CONNECTING -> Color(0xFFF59E0B)
                                    else -> Color(0xFF94A3B8)
                                }
                            )
                        }
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (connectionState == BluetoothState.DISCONNECTED) {
                            IconButton(
                                onClick = {
                                    if (bluetoothHelper.hasPermissions()) {
                                        bluetoothHelper.startScan()
                                        showDeviceListDialog = true
                                    } else {
                                        permissionLauncher.launch(AppConstants.Permissions.BLUETOOTH_PERMISSIONS)
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "刷新", tint = Color(0xFF3B82F6))
                            }
                        }
                        
                        Switch(
                            checked = connectionState != BluetoothState.DISCONNECTED,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    if (bluetoothHelper.hasPermissions()) {
                                        bluetoothHelper.startScan()
                                        showDeviceListDialog = true
                                    } else {
                                        permissionLauncher.launch(AppConstants.Permissions.BLUETOOTH_PERMISSIONS)
                                    }
                                } else {
                                    bluetoothHelper.disconnect()
                                    showDeviceListDialog = false
                                }
                            }
                        )
                    }
                }
            }
        }
        
        // 设备选择对话框
        if (showDeviceListDialog && connectionState == BluetoothState.DISCONNECTED) {
            AlertDialog(
                onDismissRequest = { 
                    bluetoothHelper.stopScan()
                    showDeviceListDialog = false 
                },
                title = { 
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("可用设备")
                        if (isScanning) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = { bluetoothHelper.startScan() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "刷新")
                            }
                        }
                    }
                },
                text = {
                    Box(modifier = Modifier.heightIn(max = 400.dp)) {
                        if (scannedDevices.isEmpty() && !isScanning) {
                            Text("未发现可用设备", modifier = Modifier.padding(16.dp))
                        } else {
                            LazyColumn {
                                items(scannedDevices.size) { index ->
                                    val item = scannedDevices[index]
                                    val device = item.device
                                    val deviceName = bluetoothHelper.getDeviceName(device)
                                    
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                bluetoothHelper.connect(device) { success ->
                                                    if (success) showDeviceListDialog = false
                                                }
                                            }
                                            .padding(vertical = 12.dp, horizontal = 8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = deviceName,
                                                        fontSize = 16.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = Color(0xFF1E293B)
                                                    )
                                                    if (item.isPaired) {
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Surface(
                                                            color = Color(0xFFE2E8F0),
                                                            shape = RoundedCornerShape(4.dp)
                                                        ) {
                                                            Text(
                                                                text = "已配对",
                                                                fontSize = 10.sp,
                                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                                                color = Color(0xFF64748B)
                                                            )
                                                        }
                                                    }
                                                }
                                                Text(
                                                    text = device.address,
                                                    fontSize = 12.sp,
                                                    color = Color(0xFF94A3B8)
                                                )
                                            }
                                            
                                            // 信号强度指示
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.SignalCellularAlt,
                                                    contentDescription = "信号",
                                                    modifier = Modifier.size(16.dp),
                                                    tint = when {
                                                        item.rssi > -60 -> Color(0xFF10B981)
                                                        item.rssi > -80 -> Color(0xFFF59E0B)
                                                        else -> Color(0xFFEF4444)
                                                    }
                                                )
                                                Text(
                                                    text = "${item.rssi} dBm",
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF64748B),
                                                    modifier = Modifier.padding(start = 4.dp)
                                                )
                                            }
                                        }
                                        HorizontalDivider(
                                            modifier = Modifier.padding(top = 8.dp),
                                            thickness = 0.5.dp,
                                            color = Color(0xFFF1F5F9)
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { 
                        bluetoothHelper.stopScan()
                        showDeviceListDialog = false 
                    }) {
                        Text("关闭")
                    }
                }
            )
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
    val CARD_BACKGROUND = Color(0xFF1E293B).copy(alpha = 0.85f)
    val CARD_SHAPE = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
    val PANEL_SPACING = 4.dp
    val TEXT_SIZE_TITLE = 11.sp
    val TEXT_SIZE_BODY = 9.sp
    val TEXT_SIZE_SMALL = 7.5.sp
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
    overtakeStatus: OvertakeStatusData?
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
        // 变道中 (由 AutoOvertakeManager 通过 statusText 传递)
        overtakeStatus?.statusText == "变道中" -> {
            val direction = when (overtakeStatus.lastDirection) {
                "LEFT" -> "左"
                "RIGHT" -> "右"
                else -> ""
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
            title = overtakeStatus?.statusText ?: "监控中",
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
    
    val MAX_LEAD_DISTANCE = 80.0f
    val MIN_LEAD_PROB = 0.5f
    val MIN_LEAD_SPEED_KPH = 50.0f
    val MAX_CURVATURE = 0.02f
    val MAX_STEERING_ANGLE = 15.0f
    val MIN_LANE_PROB = 0.7f
    
    val conditions = buildList {
        // 一、本车状态（合并：速度、方向盘）
        val vEgoKmh = (carState?.vEgo ?: 0f) * 3.6f
        val hasVEgoData = carState?.vEgo != null
        val vEgoOk = hasVEgoData && vEgoKmh >= minOvertakeSpeedKph
        
        val steeringAngle = kotlin.math.abs(carState?.steeringAngleDeg ?: 0f)
        val hasSteeringData = carState?.steeringAngleDeg != null
        val steeringOk = hasSteeringData && steeringAngle <= MAX_STEERING_ANGLE
        
        val carStateOk = vEgoOk && steeringOk
        val carStateData = hasVEgoData || hasSteeringData
        add(CheckCondition(
            name = "① 本车状态",
            threshold = "速度≥${minOvertakeSpeedKph.toInt()}/转向≤${MAX_STEERING_ANGLE.toInt()}°",
            actual = if (carStateData) "速度:${String.format("%.0f", vEgoKmh)} / 转向:${String.format("%.0f", steeringAngle)}°" else "N/A",
            isMet = carStateOk,
            hasData = carStateData
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
        
        // 三、道路车道（合并：曲率、车道线、路边缘）
        val curvature = kotlin.math.abs(modelV2?.curvature?.maxOrientationRate ?: 0f)
        val hasCurvatureData = modelV2?.curvature?.maxOrientationRate != null
        val curvatureOk = hasCurvatureData && curvature < MAX_CURVATURE
        
        val leftLaneProb = modelV2?.laneLineProbs?.getOrNull(0) ?: 0f
        val rightLaneProb = modelV2?.laneLineProbs?.getOrNull(1) ?: 0f
        val hasLaneProbData = modelV2?.laneLineProbs != null && modelV2.laneLineProbs.size >= 2
        val laneProbOk = hasLaneProbData && leftLaneProb >= MIN_LANE_PROB && rightLaneProb >= MIN_LANE_PROB
        
        val roadEdgeLeft = modelV2?.meta?.distanceToRoadEdgeLeft ?: 0f
        val roadEdgeRight = modelV2?.meta?.distanceToRoadEdgeRight ?: 0f
        val hasRoadEdgeData = modelV2?.meta != null
        val roadEdgeOk = hasRoadEdgeData && roadEdgeLeft > 0.5f && roadEdgeRight > 0.5f
        
        val roadStateOk = curvatureOk && laneProbOk && roadEdgeOk
        val roadStateData = hasCurvatureData || hasLaneProbData || hasRoadEdgeData
        add(CheckCondition(
            name = "③ 道路车道",
            threshold = "曲率<${(MAX_CURVATURE * 1000).toInt()}/线≥${(MIN_LANE_PROB * 100).toInt()}%/路缘>0.5m",
            actual = if (roadStateData) {
                val curvText = if (hasCurvatureData) "${String.format("%.0f", curvature * 1000)}" else "N/A"
                val probText = if (hasLaneProbData) "${String.format("%.0f", leftLaneProb * 100)}/${String.format("%.0f", rightLaneProb * 100)}" else "N/A"
                val edgeText = if (hasRoadEdgeData) "${String.format("%.1f", roadEdgeLeft)}/${String.format("%.1f", roadEdgeRight)}" else "N/A"
                "$curvText / $probText% / ${edgeText}m"
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
        // 超车提示信息计算
        val overtakeModeForHint = prefs.getInt("overtake_mode", 0)
        val hintInfo = getOvertakeHintInfo(
            overtakeMode = overtakeModeForHint,
            overtakeStatus = data?.overtakeStatus
        )
        
        // 获取额外的信息行（冷却时间、阻止原因）
        val cooldownText = data?.overtakeStatus?.cooldownRemaining?.let { cooldown ->
            if (cooldown > 0) "冷却: ${String.format("%.1f", cooldown / 1000.0)}s" else null
        }
        val blockingReason = data?.overtakeStatus?.blockingReason
        val shouldShowBlockingReason = blockingReason != null && 
            hintInfo.detail != blockingReason && 
            !hintInfo.detail.contains(blockingReason)
        
        // 🆕 NOA 战术引导卡片 - 增强版 (集成超车提示)
        if (carrotManFields != null && (
            carrotManFields.exitNameInfo.isNotEmpty() || 
            carrotManFields.sapaName.isNotEmpty() || 
            carrotManFields.roundAboutNum > 0 ||
            carrotManFields.viaPOIdistance > 0 ||
            carrotManFields.segAssistantAction > 0 ||
            carrotManFields.nSdiBlockType == 2 ||
            hintInfo.title != "监控中" || 
            blockingReason != null
        )) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = VehicleLaneUIConstants.CARD_BACKGROUND),
                shape = VehicleLaneUIConstants.CARD_SHAPE
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 标题栏
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🎯 NOA 战术引导",
                            fontSize = VehicleLaneUIConstants.TEXT_SIZE_TITLE,
                            color = VehicleLaneUIConstants.COLOR_INFO,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 🆕 实时车道显示
                            if (data?.overtakeStatus != null && data.overtakeStatus.totalLanes > 0) {
                                val laneStatus = data.overtakeStatus
                                Text(
                                    text = "🛣️ 第 ${laneStatus.currentLane} / ${laneStatus.totalLanes} 车道",
                                    fontSize = 8.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            // 🆕 超车状态提示 (集成到标题栏)
                            if (hintInfo.title != "监控中" || blockingReason != null) {
                                Text(
                                    text = "${hintInfo.icon} ${hintInfo.title}",
                                    fontSize = 8.sp,
                                    color = hintInfo.titleColor,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .background(hintInfo.cardColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                        .border(0.5.dp, hintInfo.cardColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            } else {
                                Text(
                                    text = "👁️ 监控中",
                                    fontSize = 8.sp,
                                    color = Color(0xFF94A3B8),
                                    modifier = Modifier
                                        .background(Color(0xFF94A3B8).copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            // NOA 状态
                            if (carrotManFields.nextRoadNOAOrNot) {
                                Text(
                                    text = "NOA",
                                    fontSize = 8.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .background(VehicleLaneUIConstants.COLOR_SUCCESS, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // 🆕 路线总剩余时间与进度 (合并到一行)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (carrotManFields.routeRemainTimeAuto.isNotEmpty()) {
                                Text(
                                    text = "🕒 ${carrotManFields.routeRemainTimeAuto}",
                                    fontSize = 9.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))
                            }
                            if (carrotManFields.routeRemainDisAuto.isNotEmpty()) {
                                Text(
                                    text = "🏁 ${carrotManFields.routeRemainDisAuto}",
                                    fontSize = 9.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                            
                            // 🆕 路缘距离信息整合
                            val meta = data?.modelV2?.meta
                            val roadEdgeLeft = meta?.distanceToRoadEdgeLeft ?: 0f
                            val roadEdgeRight = meta?.distanceToRoadEdgeRight ?: 0f
                            
                            if (roadEdgeLeft > 0 || roadEdgeRight > 0) {
                                androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(12.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (roadEdgeLeft > 0) {
                                        Text(
                                            text = "L: ${String.format("%.1f", roadEdgeLeft)}m",
                                            fontSize = 8.sp,
                                            color = Color(0xFF94A3B8),
                                            modifier = Modifier
                                                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(2.dp))
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                    if (roadEdgeRight > 0) {
                                        Text(
                                            text = "R: ${String.format("%.1f", roadEdgeRight)}m",
                                            fontSize = 8.sp,
                                            color = Color(0xFF94A3B8),
                                            modifier = Modifier
                                                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(2.dp))
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (carrotManFields.nextRoadProgressPercent >= 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .width(40.dp)
                                        .height(4.dp)
                                        .background(Color(0xFF334155), RoundedCornerShape(2.dp))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(carrotManFields.nextRoadProgressPercent / 100f)
                                            .fillMaxHeight()
                                            .background(VehicleLaneUIConstants.COLOR_INFO, RoundedCornerShape(2.dp))
                                    )
                                }
                                Text(
                                    text = " ${carrotManFields.nextRoadProgressPercent}%",
                                    fontSize = 8.sp,
                                    color = VehicleLaneUIConstants.COLOR_INFO,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // 🆕 超车详情、阻止原因与车道提醒 (更明显的提示)
                    val laneReminder = data?.overtakeStatus?.laneReminder
                    if (hintInfo.title != "监控中" || blockingReason != null || cooldownText != null || laneReminder != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (laneReminder != null) VehicleLaneUIConstants.COLOR_WARNING.copy(alpha = 0.15f)
                                    else hintInfo.cardColor.copy(alpha = 0.15f), 
                                    RoundedCornerShape(6.dp)
                                )
                                .border(
                                    0.5.dp, 
                                    if (laneReminder != null) VehicleLaneUIConstants.COLOR_WARNING.copy(alpha = 0.3f)
                                    else hintInfo.cardColor.copy(alpha = 0.3f), 
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when {
                                    laneReminder != null -> "📢 $laneReminder"
                                    blockingReason != null -> "🚫 $blockingReason"
                                    cooldownText != null -> "⏱️ $cooldownText"
                                    else -> "ℹ️ ${hintInfo.detail}"
                                },
                                fontSize = 9.sp,
                                color = if (laneReminder != null) Color(0xFFFBBF24) else if (blockingReason != null) Color(0xFFFCA5A5) else Color.White,
                                fontWeight = if (laneReminder != null || blockingReason != null) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }

                    // 🆕 区间测速信息
                    if (carrotManFields.nSdiBlockType == 2) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(VehicleLaneUIConstants.COLOR_DANGER.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                .border(0.5.dp, VehicleLaneUIConstants.COLOR_DANGER.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("📏 区间测速", fontSize = 8.sp, color = VehicleLaneUIConstants.COLOR_DANGER)
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        text = "${carrotManFields.nSdiDist}m",
                                        fontSize = 11.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "/ ${carrotManFields.nSdiBlockDist}m",
                                        fontSize = 8.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("限速 ", fontSize = 7.sp, color = Color(0xFF94A3B8))
                                    Text(
                                        text = "${carrotManFields.nSdiBlockSpeed}",
                                        fontSize = 9.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (carrotManFields.nSdiAverageSpeed > 0) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("平均 ", fontSize = 7.sp, color = Color(0xFF94A3B8))
                                        Text(
                                            text = "${carrotManFields.nSdiAverageSpeed}",
                                            fontSize = 9.sp,
                                            color = if (carrotManFields.nSdiAverageSpeed > carrotManFields.nSdiBlockSpeed) VehicleLaneUIConstants.COLOR_DANGER else VehicleLaneUIConstants.COLOR_SUCCESS,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 途径点信息
                    if (carrotManFields.viaPOIdistance > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF6366F1).copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                .border(0.5.dp, Color(0xFF6366F1).copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("📍 途径点", fontSize = 8.sp, color = Color(0xFF818CF8))
                                Text(
                                    text = "${carrotManFields.viaPOIdistance}m",
                                    fontSize = 11.sp,
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

                    // 主要战术信息行 (出口、环岛、服务区)
                    if (carrotManFields.exitNameInfo.isNotEmpty() || carrotManFields.roundAboutNum > 0 || carrotManFields.sapaName.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // 出口信息
                            if (carrotManFields.exitNameInfo.isNotEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                                        .padding(8.dp)
                                ) {
                                    Text("🚏 出口", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                    Text(
                                        text = carrotManFields.exitNameInfo,
                                        fontSize = 10.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
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
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                                        .padding(8.dp)
                                ) {
                                    Text("🔄 环岛", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                    Text(
                                        text = "第 ${carrotManFields.roundAboutNum} 出口",
                                        fontSize = 10.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
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
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                                        .padding(8.dp)
                                ) {
                                    Text("🏪 设施", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                    Text(
                                        text = carrotManFields.sapaName,
                                        fontSize = 10.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    if (carrotManFields.sapaDist > 0) {
                                        Text(
                                            text = if (carrotManFields.sapaDistAuto.isNotEmpty()) carrotManFields.sapaDistAuto else "${carrotManFields.sapaDist}m",
                                            fontSize = 7.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    // 🆕 电子眼增强信息
                    if (carrotManFields.nSdiType != -1 && (carrotManFields.cameraPenalty || carrotManFields.newCamera)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFEF4444).copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📸 电子眼", fontSize = 8.sp, color = Color(0xFFF87171))
                            if (carrotManFields.cameraPenalty) {
                                Text(
                                    text = "⚠️ 抓拍违章",
                                    fontSize = 8.sp,
                                    color = Color(0xFFFBBF24),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (carrotManFields.newCamera) {
                                Text(
                                    text = "🆕 新增",
                                    fontSize = 8.sp,
                                    color = Color(0xFF34D399),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (carrotManFields.cameraID != -1L) {
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = "ID: ${carrotManFields.cameraID}",
                                    fontSize = 6.sp,
                                    color = Color(0xFF64748B)
                                )
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
                                5 -> "🛣️ 沿主路行驶"
                                25 -> "📸 压线拍照"
                                34 -> "🛣️ 汇入主路"
                                117 -> "🎯 到达目的地"
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


