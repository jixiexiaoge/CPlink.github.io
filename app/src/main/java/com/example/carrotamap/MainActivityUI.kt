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
import com.example.carrotamap.ui.components.VehicleLaneVisualization

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
                        
                        // 下载弹窗
                        if (core.showDownloadDialog.value) {
                            CarrotAmapDownloadDialog(
                                onDismiss = { core.showDownloadDialog.value = false },
                                onDownload = { 
                                    core.showDownloadDialog.value = false
                                    core.openGitHubWebsite()
                                }
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
            // 使用LazyColumn替代Column + verticalScroll
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 状态卡片
                item {
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
                }
                
                // 数据表格
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
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
        
        // 自动隐藏已完成项目卡片的状态
        var showCompletedCard by remember { mutableStateOf(true) }
        
        // 当初始化完成后，延迟3秒自动隐藏已完成项目卡片
        LaunchedEffect(selfCheckStatus.isCompleted) {
            if (selfCheckStatus.isCompleted && showCompletedCard) {
                kotlinx.coroutines.delay(3000) // 延迟3秒
                showCompletedCard = false
            }
        }
        
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
            // 主内容区域
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 🔄 调整布局：实时数据组件移到顶部
                // Comma3数据表格（可折叠）
                Comma3DataTable(
                    carrotManFields = carrotManFields,
                    dataFieldManager = dataFieldManager
                )
                
                // 车辆和车道可视化弹窗状态（通过长按高阶按钮显示）
                var showVehicleLaneDialog by remember { mutableStateOf(false) }
                
                // 车辆和车道可视化弹窗（只有用户类型3或4才显示）
                VehicleLaneVisualization(
                    data = core.xiaogeData.value,
                    userType = userType,
                    showDialog = showVehicleLaneDialog,
                    onDismiss = { 
                        android.util.Log.i("MainActivity", "🔍 关闭车道可视化弹窗")
                        showVehicleLaneDialog = false 
                    },
                    carrotManFields = carrotManFields  // 传递高德地图数据，用于显示道路类型
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 可滚动的内容区域
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // 当前检查项卡片（只在未完成时显示）
                    if (selfCheckStatus.currentComponent.isNotEmpty() && !selfCheckStatus.isCompleted) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F9FF)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 3.dp,
                                        color = Color(0xFF3B82F6)
                                    )
                                    
                                    Text(
                                        text = selfCheckStatus.currentComponent,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1D4ED8)
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                Text(
                                    text = selfCheckStatus.currentMessage,
                                    fontSize = 14.sp,
                                    color = Color(0xFF64748B),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // 已完成项目列表（初始化完成后3秒自动隐藏）
                    if (selfCheckStatus.completedComponents.isNotEmpty() && showCompletedCard) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp)
                            ) {
                                Text(
                                    text = "已完成项目",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B)
                                )
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                selfCheckStatus.completedComponents.forEachIndexed { index, component ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .background(
                                                    Color(0xFF22C55E),
                                                    androidx.compose.foundation.shape.CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "✓",
                                                fontSize = 12.sp,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        
                                        Text(
                                            text = if (index == 3) {
                                                // 第4行（索引3）整合系统信息
                                                val systemInfo = buildString {
                                                    append(component)
                                                    if (deviceId.isNotEmpty()) {
                                                        append(" (ID: $deviceId)")
                                                    }
                                                    val userTypeText = when (userType) {
                                                        0 -> "未知用户"
                                                        1 -> "新用户"
                                                        2 -> "支持者"
                                                        3 -> "赞助者"
                                                        4 -> "铁粉"
                                                        else -> "未知类型($userType)"
                                                    }
                                                    append(" - $userTypeText")
                                                    append(" - 智能驾驶助手")
                                                }
                                                systemInfo
                                            } else {
                                                // 显示组件名称和消息内容
                                                val message = selfCheckStatus.completedMessages[component] ?: ""
                                                if (message.isNotEmpty()) {
                                                    "$component: $message"
                                                } else {
                                                    component
                                                }
                                            },
                                            fontSize = 14.sp,
                                            color = Color(0xFF16A34A),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                // 🔄 调整布局：控制按钮卡片移到底部
                // 底部控制按钮区域（包含速度圆环和3个按钮）
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
                    onShowVehicleLaneDialog = { showVehicleLaneDialog = true }, // 🆕 显示车道可视化弹窗
                    userType = userType,
                    carrotManFields = carrotManFields
                )
                
                // 添加底部安全间距
                Spacer(modifier = Modifier.height(6.dp))
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
     * CarrotAmap下载弹窗组件
     */
    @Composable
    private fun CarrotAmapDownloadDialog(
        onDismiss: () -> Unit,
        onDownload: () -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = "🚗 请使用 CarrotAmap",
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
                        text = "感谢您的支持！作为支持者，您需要使用 CarrotAmap 应用来获得完整的导航功能。",
                        fontSize = 14.sp,
                        color = Color(0xFF64748B),
                        lineHeight = 20.sp
                    )
                    
                    Text(
                        text = "CarrotAmap 是基于高德地图的增强导航应用，提供：",
                        fontSize = 14.sp,
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.Medium
                    )
                    
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            "🚗 自动按导航变道和转弯",
                            "🗺️ 自动沿导航路线行驶", 
                            "📊 根据限速自动调整车速",
                            "🚦 红灯自动减速停车",
                            "🛣️ 弯道自动减速"
                        ).forEach { feature ->
                            Text(
                                text = feature,
                                fontSize = 13.sp,
                                color = Color(0xFF475569),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = onDownload,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF3B82F6)
                    )
                ) {
                    Text(
                        text = "立即下载",
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismiss
                ) {
                    Text(
                        text = "稍后再说",
                        color = Color(0xFF64748B)
                    )
                }
            },
            containerColor = Color.White,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
        )
    }


    /**
     * Comma3数据表格组件（可折叠）
     */
    @Composable
    private fun Comma3DataTable(
        carrotManFields: CarrotManFields,
        dataFieldManager: DataFieldManager
    ) {
        var isExpanded by remember { mutableStateOf(false) }
        
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
                            text = "CP数据",
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
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // 表格头部
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF8FAFC))
                            .padding(vertical = 8.dp, horizontal = 12.dp),
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
                                .padding(vertical = 6.dp, horizontal = 12.dp),
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

