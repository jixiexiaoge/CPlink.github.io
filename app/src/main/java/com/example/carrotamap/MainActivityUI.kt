package com.example.carrotamap

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carrotamap.ui.theme.CPlinkTheme
import kotlinx.coroutines.launch

// UI组件导入
import com.example.carrotamap.ui.components.*
import com.example.carrotamap.ui.components.CompactStatusCard
import com.example.carrotamap.ui.components.TableHeader
import com.example.carrotamap.ui.components.DataTable

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
                                onLaunchAmap = { core.launchAmapAuto() }
                            )
                            1 -> HelpPage()
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
                            Text(
                                text = "实时数据信息",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B),
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            
                            // 表格头部
                            TableHeader()
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
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
        onLaunchAmap: () -> Unit
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
                
                // 顶部控制按钮区域
                VehicleControlButtons(
                    onPageChange = { page -> 
                        // 这里需要访问MainActivity的currentPage状态
                        // 暂时用Log记录，后续可以通过其他方式实现
                        android.util.Log.i("MainActivity", "页面切换请求: $page")
                    },
                    onSendCommand = onSendCommand,
                    onSendRoadLimitSpeed = onSendRoadLimitSpeed,
                    onLaunchAmap = onLaunchAmap,
                    userType = userType,
                    carrotManFields = carrotManFields
                )
                
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
                
                // Comma3数据表格（可折叠）
                Spacer(modifier = Modifier.height(16.dp))
                Comma3DataTable(
                    carrotManFields = carrotManFields,
                    dataFieldManager = dataFieldManager
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
        // 根据用户类型决定是否显示数据页面
        val basePages = listOf(
            BottomNavItem("主页", Icons.Default.Home, 0),
            BottomNavItem("帮助", Icons.Default.Info, 1),
            BottomNavItem("我的", Icons.Default.Person, 2)
        )
        
        val pages = if (userType == 4) {
            // 铁粉用户可以看到数据页面
            basePages + BottomNavItem("数据", Icons.Default.Settings, 3)
        } else {
            // 其他用户类型不显示数据页面
            basePages
        }
        
        NavigationBar(
            containerColor = Color.White,
            contentColor = Color(0xFF2196F3)
        ) {
            pages.forEach { page ->
                NavigationBarItem(
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
     * 车辆控制按钮组件 - 带速度圆环显示
     */
    @Composable
    private fun VehicleControlButtons(
        onPageChange: (Int) -> Unit,
        onSendCommand: (String, String) -> Unit,
        onSendRoadLimitSpeed: () -> Unit,
        onLaunchAmap: () -> Unit,
        userType: Int,
        carrotManFields: CarrotManFields
    ) {
        var showAdvancedDialog by remember { mutableStateOf(false) }
        val context = androidx.compose.ui.platform.LocalContext.current
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
        ) {
            // 控制按钮行 - 2个速度圆环 + 3个按钮（优化布局对齐）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧速度圆环 - 巡航设定速度（蓝色）
                SpeedIndicatorCompose(
                    value = try { carrotManFields.vCruiseKph?.toInt() ?: 0 } catch (e: Exception) { 0 },
                    color = Color(0xFF2196F3),
                    label = ""
                )
                
                // 回家按钮（只显示图标，不显示文字）
                ControlButton(
                    icon = "🏠",
                    label = "",
                    color = Color(0xFFFFD700),
                    onClick = {
                        android.util.Log.i("MainActivity", "🏠 主页：用户点击回家按钮")
                        sendHomeNavigationToAmap(context)
                    }
                )
                
                // 高阶按钮（打开高阶功能弹窗 - 需要用户类型3或4）
                ControlButton(
                    icon = "",
                    label = "高阶",
                    color = Color(0xFFF59E0B),
                    onClick = {
                        android.util.Log.i("MainActivity", "🚀 主页：用户点击高阶按钮，用户类型: $userType")
                        
                        // 检查用户类型：只有赞助者(3)或铁粉(4)才能使用高阶功能
                        if (userType == 3 || userType == 4) {
                            android.util.Log.i("MainActivity", "✅ 用户类型验证通过，打开高阶功能弹窗")
                            showAdvancedDialog = true
                        } else {
                            android.util.Log.w("MainActivity", "⚠️ 用户类型不足，无法使用高阶功能")
                            // 显示Toast提示用户
                            android.widget.Toast.makeText(
                                context,
                                "⭐ 高阶功能需要赞助者权限\n请前往「我的」页面\n检查信息并更新用户类型",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )
                
                // 公司按钮（只显示图标，不显示文字）
                ControlButton(
                    icon = "🏢",
                    label = "",
                    color = Color(0xFFFF8C00),
                    onClick = {
                        android.util.Log.i("MainActivity", "🏢 主页：用户点击公司按钮")
                        sendCompanyNavigationToAmap(context)
                    }
                )
                
                // 右侧速度圆环 - 车辆巡航速度（绿色）
                SpeedIndicatorCompose(
                    value = try { carrotManFields.carcruiseSpeed?.toInt() ?: 0 } catch (e: Exception) { 0 },
                    color = Color(0xFF22C55E),
                    label = ""
                )
            }
        }
        
        // 高阶功能弹窗
        if (showAdvancedDialog) {
            AdvancedFunctionsDialog(
                onDismiss = { showAdvancedDialog = false },
                onSendCommand = onSendCommand,
                onSendRoadLimitSpeed = onSendRoadLimitSpeed,
                onLaunchAmap = onLaunchAmap,
                context = context
            )
        }
    }

    /**
     * 控制按钮组件
     */
    @Composable
    private fun ControlButton(
        icon: String,
        label: String,
        color: Color,
        onClick: () -> Unit
    ) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = color,
                contentColor = Color.White
            ),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            modifier = Modifier
                .width(56.dp)
                .height(48.dp),
            contentPadding = PaddingValues(0.dp) // 移除内边距以便图标完美居中
        ) {
            // 使用Box来实现完美居中
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    // 情况1: 只有图标，没有文字（图标居中显示）
                    icon.isNotEmpty() && label.isEmpty() -> {
                        Text(
                            text = icon,
                            fontSize = 24.sp, // 加大图标尺寸，更醒目
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                    // 情况2: 既有图标又有文字（垂直排列）
                    icon.isNotEmpty() && label.isNotEmpty() -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = icon,
                        fontSize = 16.sp
                    )
                    Text(
                        text = label,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                    }
                    // 情况3: 只有文字，没有图标（文字居中）
                    else -> {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
        }
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
                            text = "📥",
                            fontSize = 20.sp
                        )
                        Text(
                            text = "Comma3实时数据",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1D4ED8)
                        )
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
    
    /**
     * 高阶功能弹窗 - 3x3九宫格按钮（集成加速/减速/变道/调试/控速/设置功能）
     * 按钮布局：
     * 1(调试)  2(加速)  3(关闭)
     * 4(左变道) 5(智能控速)  6(右变道)
     * 7(设置)  8(减速)  9(启动地图)
     * 
     * 注：回家和公司按钮已移动到主页面控制按钮行
     */
    @Composable
    private fun AdvancedFunctionsDialog(
        onDismiss: () -> Unit,
        onSendCommand: (String, String) -> Unit,
        onSendRoadLimitSpeed: () -> Unit,
        onLaunchAmap: () -> Unit,
        context: android.content.Context
    ) {
        // 智能控速模式状态：0=智能控速, 1=原车巡航, 2=弯道减速
        var speedControlMode by remember { 
            mutableStateOf(
                context.getSharedPreferences("CarrotAmap", android.content.Context.MODE_PRIVATE)
                    .getInt("speed_from_pcm_mode", 0)
            ) 
        }
        var isSpeedModeLoading by remember { mutableStateOf(false) }
        val coroutineScope = rememberCoroutineScope()
        androidx.compose.ui.window.Dialog(
            onDismissRequest = onDismiss
        ) {
            Card(
                modifier = Modifier
                    .wrapContentSize() // 自适应内容大小
                    .padding(0.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(8.dp), // 最小外边距
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 3x3 九宫格按钮
                    for (row in 0..2) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            for (col in 0..2) {
                                val buttonNumber = row * 3 + col + 1
                                
                                when (buttonNumber) {
                                    // 2号按钮 - 加速（绿色）
                                    2 -> {
                                        Button(
                                            onClick = {
                                                android.util.Log.i("MainActivity", "🎮 高阶弹窗：用户点击加速按钮")
                                                onSendCommand("SPEED", "UP")
                                                onDismiss()
                                            },
                                            modifier = Modifier.size(56.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF22C55E) // 绿色
                                            ),
                                            contentPadding = PaddingValues(0.dp),
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = "加速",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                    // 3号按钮 - 关闭（红色）
                                    3 -> {
                                        Button(
                                            onClick = onDismiss,
                                            modifier = Modifier.size(56.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFFEF4444) // 红色
                                            ),
                                            contentPadding = PaddingValues(0.dp),
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = "×",
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                    // 4号按钮 - 左变道（蓝色）
                                    4 -> {
                                        Button(
                                            onClick = {
                                                android.util.Log.i("MainActivity", "🎮 高阶弹窗：用户点击左变道按钮")
                                                onSendCommand("LANECHANGE", "LEFT")
                                                onDismiss()
                                            },
                                            modifier = Modifier.size(56.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF3B82F6) // 蓝色
                                            ),
                                            contentPadding = PaddingValues(0.dp),
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = "左变道",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                        }
                                    }
                                    // 6号按钮 - 右变道（蓝色）
                                    6 -> {
                                        Button(
                                            onClick = {
                                                android.util.Log.i("MainActivity", "🎮 高阶弹窗：用户点击右变道按钮")
                                                onSendCommand("LANECHANGE", "RIGHT")
                                                onDismiss()
                                            },
                                            modifier = Modifier.size(56.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF3B82F6) // 蓝色
                                            ),
                                            contentPadding = PaddingValues(0.dp),
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = "右变道",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                        }
                                    }
                                    // 8号按钮 - 减速（红色）
                                    8 -> {
                                        Button(
                                            onClick = {
                                                android.util.Log.i("MainActivity", "🎮 高阶弹窗：用户点击减速按钮")
                                                onSendCommand("SPEED", "DOWN")
                                                onDismiss()
                                            },
                                            modifier = Modifier.size(56.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFFEF4444) // 红色
                                            ),
                                            contentPadding = PaddingValues(0.dp),
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = "减速",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                    // 7号按钮 - 设置（紫色）
                                    7 -> {
                                        Button(
                                            onClick = {
                                                android.util.Log.i("MainActivity", "🎯 高阶弹窗：用户点击设置按钮，发送当前道路限速")
                                                onSendRoadLimitSpeed()
                                                //onDismiss()
                                            },
                                            modifier = Modifier.size(56.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF8B5CF6) // 紫色
                                            ),
                                            contentPadding = PaddingValues(0.dp),
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = "设置",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                        }
                                    }
                                    // 9号按钮 - 启动高德地图（蓝色）
                                    9 -> {
                                        Button(
                                            onClick = {
                                                android.util.Log.i("MainActivity", "🗺️ 高阶功能：用户点击启动高德地图按钮")
                                                onLaunchAmap()
                                                onDismiss()
                                            },
                                            modifier = Modifier.size(56.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF3B82F6) // 蓝色表示启动功能
                                            ),
                                            contentPadding = PaddingValues(0.dp),
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                        ) {
                                            Column(
                                                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                                                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                                            ) {
                                                Text(
                                                    text = "🗺️",
                                                    fontSize = 20.sp
                                                )
                                                Text(
                                                    text = "地图",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                    // 1号按钮 - 调试（紫色）
                                    1 -> {
                                        Button(
                                            onClick = {
                                                android.util.Log.i("MainActivity", "🔧 高阶弹窗：用户点击调试按钮，启动模拟导航")
                                                startSimulatedNavigation(context)
                                                onDismiss()
                                            },
                                            modifier = Modifier.size(56.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF8B5CF6) // 紫色
                                            ),
                                            contentPadding = PaddingValues(0.dp),
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = "调试",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                        }
                                    }
                                    // 5号按钮 - 智能控速（动态颜色）
                                    5 -> {
                                        val modeNames = arrayOf("智能\n控速", "原车\n巡航", "弯道\n减速")
                                        val modeColors = arrayOf(
                                            Color(0xFF22C55E), // 绿色 - 智能控速
                                            Color(0xFF3B82F6), // 蓝色 - 原车巡航
                                            Color(0xFFF59E0B)  // 橙色 - 弯道减速
                                        )
                                        
                                        Button(
                                            onClick = {
                                                if (!isSpeedModeLoading) {
                                                    android.util.Log.i("MainActivity", "🎮 高阶弹窗：用户点击智能控速按钮")
                                                    isSpeedModeLoading = true
                                                    
                                                    coroutineScope.launch {
                                                        // 切换模式
                                                        val currentMode = speedControlMode
                                                        val nextMode = (currentMode + 1) % 3
                                                        
                                                        android.util.Log.i("MainActivity", "🔄 切换速度控制模式: ${modeNames[currentMode].replace("\n", "")} → ${modeNames[nextMode].replace("\n", "")}")
                                                        
                                                        // 发送模式切换广播给MainActivity
                                                        val intent = android.content.Intent("com.example.cplink.CHANGE_SPEED_MODE").apply {
                                                            putExtra("mode", nextMode)
                                                            setPackage(context.packageName)
                                                        }
                                                        context.sendBroadcast(intent)
                                                        
                                                        // 保存新模式到SharedPreferences
                                                        context.getSharedPreferences("CarrotAmap", android.content.Context.MODE_PRIVATE)
                                                            .edit()
                                                            .putInt("speed_from_pcm_mode", nextMode)
                                                            .apply()
                                                        
                                                        // 模拟网络延迟
                                                        kotlinx.coroutines.delay(500)
                                                        
                                                        // 更新UI状态
                                                        speedControlMode = nextMode
                                                        isSpeedModeLoading = false
                                                        
                                                        android.util.Log.i("MainActivity", "✅ 模式切换完成: ${modeNames[nextMode].replace("\n", "")} (SpeedFromPCM=$nextMode)")
                                                    }
                                                }
                                            },
                                            modifier = Modifier.size(56.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isSpeedModeLoading) {
                                                    Color(0xFF6B7280) // 加载中灰色
                                                } else {
                                                    modeColors[speedControlMode]
                                                }
                                            ),
                                            contentPadding = PaddingValues(0.dp),
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                            enabled = !isSpeedModeLoading
                                        ) {
                                            Text(
                                                text = if (isSpeedModeLoading) {
                                                    "切换\n中..."
                                                } else {
                                                    modeNames[speedControlMode]
                                                },
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                lineHeight = 12.sp
                                            )
                                        }
                                    }
                                    // 其他按钮 - 默认灰蓝色，待分配功能
                                    else -> {
                                        Button(
                                            onClick = {
                                                android.util.Log.i("MainActivity", "🔧 高阶功能：点击按钮 #$buttonNumber")
                                                onDismiss()
                                            },
                                            modifier = Modifier.size(56.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF94A3B8) // 灰蓝色表示未分配
                                            ),
                                            contentPadding = PaddingValues(0.dp),
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = "$buttonNumber",
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 发送回家导航指令给高德地图
     */
    private fun sendHomeNavigationToAmap(context: android.content.Context) {
        try {
            android.util.Log.i("MainActivity", "🏠 发送一键回家指令给高德地图")
            val homeIntent = android.content.Intent("AUTONAVI_STANDARD_BROADCAST_RECV").apply {
                putExtra("KEY_TYPE", 10040)
                putExtra("SOURCE_APP", "CPlink")
                putExtra("DEST", 0) // 0: 回家；1: 回公司
                putExtra("IS_START_NAVI", 0) // 0: 是直接开始导航；1: 否
                setPackage("com.autonavi.amapauto")
                flags = android.content.Intent.FLAG_INCLUDE_STOPPED_PACKAGES
            }
            context.sendBroadcast(homeIntent)
            android.util.Log.i("MainActivity", "✅ 一键回家导航广播已发送")
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "❌ 发送一键回家指令失败: ${e.message}", e)
        }
    }
    
    /**
     * 发送导航到公司指令给高德地图
     */
    private fun sendCompanyNavigationToAmap(context: android.content.Context) {
        try {
            android.util.Log.i("MainActivity", "🏢 发送导航到公司指令给高德地图")
            val companyIntent = android.content.Intent("AUTONAVI_STANDARD_BROADCAST_RECV").apply {
                putExtra("KEY_TYPE", 10040)
                putExtra("SOURCE_APP", "CPlink")
                putExtra("DEST", 1) // 0: 回家；1: 回公司
                putExtra("IS_START_NAVI", 0) // 0: 是直接开始导航；1: 否
                setPackage("com.autonavi.amapauto")
                flags = android.content.Intent.FLAG_INCLUDE_STOPPED_PACKAGES
            }
            context.sendBroadcast(companyIntent)
            android.util.Log.i("MainActivity", "✅ 导航到公司广播已发送")
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "❌ 发送导航到公司指令失败: ${e.message}", e)
        }
    }
    
    /**
     * 启动模拟导航功能
     * 使用当前位置作为起点，上海东方明珠作为目的地
     */
    private fun startSimulatedNavigation(context: android.content.Context) {
        try {
            android.util.Log.i("MainActivity", "🔧 启动模拟导航功能")
            
            // 获取当前位置信息
            val currentLat = getCurrentLocationLatitude(context)
            val currentLon = getCurrentLocationLongitude(context)
            
            // 设置目的地为上海东方明珠
            val destLat = 31.2397  // 上海东方明珠纬度
            val destLon = 121.4998  // 上海东方明珠经度
            
            android.util.Log.i("MainActivity", "📍 起点坐标: lat=$currentLat, lon=$currentLon")
            android.util.Log.i("MainActivity", "🏗️ 目的地坐标（上海东方明珠）: lat=$destLat, lon=$destLon")
            
            // 检查起点和终点是否相同
            if (currentLat == destLat && currentLon == destLon) {
                android.util.Log.w("MainActivity", "⚠️ 起点和终点坐标相同，调整目的地位置")
                // 如果坐标相同，使用不同的目的地位置（深圳）
                val adjustedDestLat = 22.5431
                val adjustedDestLon = 114.0579
                android.util.Log.i("MainActivity", "🏢 调整后目的地坐标: lat=$adjustedDestLat, lon=$adjustedDestLon")
                
                // 发送模拟导航广播给高德地图车机版
                sendSimulatedNavigationIntent(context, currentLat, currentLon, adjustedDestLat, adjustedDestLon)
            } else {
                // 发送模拟导航广播给高德地图车机版
                sendSimulatedNavigationIntent(context, currentLat, currentLon, destLat, destLon)
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "❌ 启动模拟导航失败: ${e.message}", e)
        }
    }
    
    /**
     * 发送模拟导航Intent
     */
    private fun sendSimulatedNavigationIntent(
        context: android.content.Context,
        startLat: Double, 
        startLon: Double, 
        destLat: Double, 
        destLon: Double
    ) {
        try {
            val intent = android.content.Intent("AUTONAVI_STANDARD_BROADCAST_RECV").apply {
                putExtra("KEY_TYPE", 10076) // 模拟导航类型
                putExtra("SOURCE_APP", "CPlink")
                
                // 起点信息
                putExtra("EXTRA_SLAT", startLat)
                putExtra("EXTRA_SLON", startLon)
                putExtra("EXTRA_SNAME", "当前位置")
                
                // 目的地信息
                putExtra("EXTRA_DLAT", destLat)
                putExtra("EXTRA_DLON", destLon)
                putExtra("EXTRA_DNAME", "上海东方明珠")
                
                // 其他必要参数
                putExtra("EXTRA_DEV", 0) // 0: 加密，不需要偏移
                putExtra("EXTRA_M", 0)  // 0: 默认驾驶模式
                putExtra("KEY_RECYLE_SIMUNAVI", true) // 关键：启动模拟导航
                
                setPackage("com.autonavi.amapauto")
                flags = android.content.Intent.FLAG_INCLUDE_STOPPED_PACKAGES
            }
            
            context.sendBroadcast(intent)
            android.util.Log.i("MainActivity", "✅ 模拟导航广播已发送给高德地图车机版")
            
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "❌ 发送模拟导航广播失败: ${e.message}", e)
        }
    }
    
    /**
     * 获取当前位置纬度
     */
    private fun getCurrentLocationLatitude(context: android.content.Context): Double {
        return try {
            // 尝试从多个SharedPreferences获取当前位置
            val carrotPrefs = context.getSharedPreferences("CarrotAmap", android.content.Context.MODE_PRIVATE)
            val devicePrefs = context.getSharedPreferences("device_prefs", android.content.Context.MODE_PRIVATE)
            
            // 优先从CarrotAmap获取
            var lat = carrotPrefs.getFloat("vpPosPointLat", 0.0f).toDouble()
            if (lat == 0.0) {
                // 尝试从device_prefs获取
                lat = devicePrefs.getFloat("vpPosPointLat", 0.0f).toDouble()
            }
            
            if (lat != 0.0) {
                android.util.Log.i("MainActivity", "✅ 获取到当前位置纬度: $lat")
                lat
            } else {
                // 如果无法获取位置，使用北京作为默认起点
                android.util.Log.w("MainActivity", "⚠️ 未找到当前位置，使用默认起点坐标（北京）")
                39.9042
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "❌ 获取当前位置纬度失败: ${e.message}", e)
            39.9042 // 默认坐标（北京）
        }
    }
    
    /**
     * 获取当前位置经度
     */
    private fun getCurrentLocationLongitude(context: android.content.Context): Double {
        return try {
            // 尝试从多个SharedPreferences获取当前位置
            val carrotPrefs = context.getSharedPreferences("CarrotAmap", android.content.Context.MODE_PRIVATE)
            val devicePrefs = context.getSharedPreferences("device_prefs", android.content.Context.MODE_PRIVATE)
            
            // 优先从CarrotAmap获取
            var lon = carrotPrefs.getFloat("vpPosPointLon", 0.0f).toDouble()
            if (lon == 0.0) {
                // 尝试从device_prefs获取
                lon = devicePrefs.getFloat("vpPosPointLon", 0.0f).toDouble()
            }
            
            if (lon != 0.0) {
                android.util.Log.i("MainActivity", "✅ 获取到当前位置经度: $lon")
                lon
            } else {
                // 如果无法获取位置，使用北京作为默认起点
                android.util.Log.w("MainActivity", "⚠️ 未找到当前位置，使用默认起点坐标（北京）")
                116.4074
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "❌ 获取当前位置经度失败: ${e.message}", e)
            116.4074 // 默认坐标（北京）
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
 * 速度圆环Compose组件
 * 参考FloatingWindowService的SpeedIndicatorView设计
 * 优化：调整尺寸与按钮对齐
 */
@Composable
private fun SpeedIndicatorCompose(
    value: Int,
    color: Color,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(56.dp) // 与按钮宽度一致
    ) {
        // 圆环部分
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(48.dp) // 与按钮高度一致
        ) {
            androidx.compose.foundation.Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                val radius = size.minDimension / 2f - 6.dp.toPx()
                
                // 绘制白色背景圆
                drawCircle(
                    color = Color.White,
                    radius = radius,
                    center = center
                )
                
                // 绘制彩色圆环
                drawCircle(
                    color = color,
                    radius = radius,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6.dp.toPx())
                )
            }
            
            // 数值文本
            Text(
                text = value.toString(),
                fontSize = 18.sp, // 增大字体
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        
        // 标签文本（如果需要）
        if (label.isNotEmpty()) {
            Text(
                text = label,
                fontSize = 8.sp,
                color = Color(0xFF64748B),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 10.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
