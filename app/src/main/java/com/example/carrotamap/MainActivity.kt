package com.example.carrotamap

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carrotamap.ui.theme.CarrotAmapTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.BufferedReader
import java.io.InputStreamReader
import android.content.pm.PackageManager

// 指纹选择相关导入
import com.example.carrotamap.VehicleInfo
import com.example.carrotamap.VehicleInfoManager

// UI组件导入
import com.example.carrotamap.ui.components.*

// 主Activity - 集成所有功能：UI显示、广播处理、CarrotMan映射、网络通信、地图控制
class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = AppConstants.Logging.MAIN_ACTIVITY_TAG
        // 高德地图广播Action常量 - 使用统一的常量管理
        const val ACTION_AMAP_SEND = AppConstants.AmapBroadcast.ACTION_AMAP_SEND
        const val ACTION_AMAP_RECV = AppConstants.AmapBroadcast.ACTION_AMAP_RECV
        const val ACTION_AMAP_LEGACY = AppConstants.AmapBroadcast.ACTION_AMAP_LEGACY
        const val ACTION_AUTONAVI = AppConstants.AmapBroadcast.ACTION_AUTONAVI
        // 核心导航广播类型常量 - 使用统一的常量管理
        const val KEY_TYPE_MAP_STATE = AppConstants.AmapBroadcast.Navigation.MAP_STATE
        const val KEY_TYPE_GUIDE_INFO = AppConstants.AmapBroadcast.Navigation.GUIDE_INFO
        const val KEY_TYPE_LOCATION_INFO = AppConstants.AmapBroadcast.Navigation.LOCATION_INFO
        const val KEY_TYPE_TURN_INFO = AppConstants.AmapBroadcast.Navigation.TURN_INFO
        const val KEY_TYPE_NAVIGATION_STATUS = AppConstants.AmapBroadcast.Navigation.NAVIGATION_STATUS
        const val KEY_TYPE_ROUTE_INFO = AppConstants.AmapBroadcast.Navigation.ROUTE_INFO

        // 限速和摄像头信息
        const val KEY_TYPE_SPEED_LIMIT = AppConstants.AmapBroadcast.SpeedCamera.SPEED_LIMIT
        const val KEY_TYPE_CAMERA_INFO = AppConstants.AmapBroadcast.SpeedCamera.CAMERA_INFO
        const val KEY_TYPE_CAMERA_INFO_V2 = AppConstants.AmapBroadcast.SpeedCamera.CAMERA_INFO_V2
        const val KEY_TYPE_SPEED_LIMIT_NEW = AppConstants.AmapBroadcast.SpeedCamera.SPEED_LIMIT_NEW
        const val KEY_TYPE_SDI_PLUS_INFO = AppConstants.AmapBroadcast.SpeedCamera.SDI_PLUS_INFO

        // 地图和位置信息
        const val KEY_TYPE_FAVORITE_RESULT = AppConstants.AmapBroadcast.MapLocation.FAVORITE_RESULT
        const val KEY_TYPE_ADMIN_AREA = AppConstants.AmapBroadcast.MapLocation.ADMIN_AREA
        const val KEY_TYPE_NAVI_STATUS = AppConstants.AmapBroadcast.MapLocation.NAVI_STATUS
        const val KEY_TYPE_TRAFFIC_INFO = AppConstants.AmapBroadcast.MapLocation.TRAFFIC_INFO
        const val KEY_TYPE_NAVI_SITUATION = AppConstants.AmapBroadcast.MapLocation.NAVI_SITUATION
        const val KEY_TYPE_NEXT_INTERSECTION = AppConstants.AmapBroadcast.MapLocation.NEXT_INTERSECTION
        const val KEY_TYPE_SAPA_INFO = AppConstants.AmapBroadcast.MapLocation.SAPA_INFO
        const val KEY_TYPE_TRAFFIC_LIGHT = AppConstants.AmapBroadcast.MapLocation.TRAFFIC_LIGHT

        // 导航控制相关常量 - 使用统一的常量管理
        const val KEY_TYPE_SIMULATE_NAVIGATION = AppConstants.AmapBroadcast.NavigationControl.SIMULATE_NAVIGATION
        const val KEY_TYPE_ROUTE_PLANNING = AppConstants.AmapBroadcast.NavigationControl.ROUTE_PLANNING
        const val KEY_TYPE_START_NAVIGATION = AppConstants.AmapBroadcast.NavigationControl.START_NAVIGATION
        const val KEY_TYPE_STOP_NAVIGATION = AppConstants.AmapBroadcast.NavigationControl.STOP_NAVIGATION
        const val KEY_TYPE_HOME_COMPANY_NAVIGATION = AppConstants.AmapBroadcast.NavigationControl.HOME_COMPANY_NAVIGATION
    }

    // ===============================
    // 属性声明区域 - Properties Declaration
    // ===============================

    /** Comma3 CarrotMan字段映射数据 */
    private val carrotManFields = mutableStateOf(CarrotManFields())

    // 页面状态枚举
    enum class PageState {
        SPEEDOMETER_PAGE,   // 速度表页面
        DATA_CARDS_PAGE     // 数据卡片页面
    }

    // 页面状态控制 - 默认显示速度表页面
    private val pageState = mutableStateOf(PageState.SPEEDOMETER_PAGE)

    // 卡片显示状态枚举 (保留用于数据卡片页面)
    enum class CardDisplayState {
        ALL_VISIBLE,        // 全部显示
        HIDE_OPENPILOT,     // 隐藏OpenPilot卡片
        HIDE_FIELDS         // 隐藏字段卡片
    }

    // 卡片显示状态控制 - 默认显示所有卡片
    private val cardDisplayState = mutableStateOf(CardDisplayState.ALL_VISIBLE)

    // 说明文字显示控制 - 启动时显示，点击切换后隐藏
    private val showDescription = mutableStateOf(true)
    
    // 广播接收器管理器
    private lateinit var amapBroadcastManager: AmapBroadcastManager
    // 位置和传感器管理器
    private lateinit var locationSensorManager: LocationSensorManager
    // 权限管理器
    private lateinit var permissionManager: PermissionManager
    // 数据字段管理器
    private val dataFieldManager = DataFieldManager()
    // 网络管理器
    private lateinit var networkManager: NetworkManager
    // 高德地图相关管理器
    private lateinit var amapDestinationManager: AmapDestinationManager
    private lateinit var amapNavigationManager: AmapNavigationManager
    private lateinit var amapDataProcessor: AmapDataProcessor
    // 设备管理器
    private lateinit var deviceManager: DeviceManager
    private lateinit var locationReportManager: LocationReportManager

    // 设备状态
    private val deviceId = mutableStateOf("")
    private val remainingSeconds = mutableStateOf(0)
    
    // 车型选择对话框状态
    private val showVehicleSelectionDialog = mutableStateOf(false)
    private lateinit var vehicleInfoManager: VehicleInfoManager
    
    // APK版本检查相关状态
    private val showUpdateDialog = mutableStateOf(false)
    private val updateInfo = mutableStateOf<ApkUpdateInfo?>(null)

    // Activity创建时回调 - 完成应用的初始化工作
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 保持屏幕常亮
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Log.i(TAG, "🔆 已设置屏幕常亮")

        // 请求忽略电池优化
        requestIgnoreBatteryOptimizations()

        Log.i(TAG, "🚀 MainActivity正在启动...")

        initializeLocationSensorManager()   // 1. 位置和传感器管理器初始化
        initializePermissionManager()       // 2. 权限管理器初始化
        setupPermissionsAndLocation()       // 3. 权限管理和位置服务初始化
        initializeNetworkManager()          // 5. 网络管理器初始化
        initializeAmapManagers()            // 6. 高德地图管理器初始化
        initializeBroadcastManager()        // 7. 广播管理器初始化
        initializeDeviceManager()           // 8. 设备管理器初始化
        initializeVehicleInfoManager()      // 9. 车辆信息管理器初始化
        checkAndShowVehicleSelectionDialog() // 10. 检查并显示车型选择对话框
        performInitialLocationReport()      // 11. 执行初始位置上报
        checkForAppUpdate()                 // 12. 检查应用更新
        setupUserInterface()               // 13. UI界面设置
        handleIntentFromStaticReceiver(intent) // 14. 处理来自静态接收器的Intent

        Log.i(TAG, "✅ MainActivity启动完成")
    }

    /**
     * 设置权限和位置服务
     */
    private fun setupPermissionsAndLocation() {
        if (::permissionManager.isInitialized) {
            permissionManager.setupPermissionsAndLocation()
        } else {
            Log.e(TAG, "❌ 权限管理器未初始化，无法设置权限")
        }
    }

    /**
     * 初始化广播管理器
     */
    private fun initializeBroadcastManager() {
        Log.i(TAG, "📡 初始化广播管理器...")

        try {
            amapBroadcastManager = AmapBroadcastManager(this, carrotManFields, networkManager)
            val success = amapBroadcastManager.registerReceiver()

            if (success) {
                Log.i(TAG, "✅ 广播管理器初始化成功")
            } else {
                Log.e(TAG, "❌ 广播管理器初始化失败")
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ 广播管理器初始化异常: ${e.message}", e)
        }
    }

    /**
     * 初始化设备管理器
     */
    private fun initializeDeviceManager() {
        Log.i(TAG, "📱 初始化设备管理器...")

        try {
            deviceManager = DeviceManager(this)
            locationReportManager = LocationReportManager(this, networkManager, deviceManager)

            // 获取设备ID并更新UI
            val id = deviceManager.getDeviceId()
            deviceId.value = id

            Log.i(TAG, "✅ 设备管理器初始化成功，设备ID: $id")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 设备管理器初始化失败: ${e.message}", e)
        }
    }

    /**
     * 初始化车辆信息管理器
     */
    private fun initializeVehicleInfoManager() {
        Log.i(TAG, "🚗 初始化车辆信息管理器...")

        try {
            vehicleInfoManager = VehicleInfoManager(this)
            Log.i(TAG, "✅ 车辆信息管理器初始化成功")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 车辆信息管理器初始化失败: ${e.message}", e)
        }
    }

    /**
     * 检查并显示车型选择对话框
     */
    private fun checkAndShowVehicleSelectionDialog() {
        Log.i(TAG, "🔍 检查车辆信息...")

        try {
            if (::vehicleInfoManager.isInitialized) {
                val vehicleInfo = vehicleInfoManager.getVehicleInfo()
                if (vehicleInfo == null) {
                    Log.i(TAG, "📋 未找到车辆信息，显示车型选择对话框")
                    showVehicleSelectionDialog.value = true
                } else {
                    Log.i(TAG, "✅ 已找到车辆信息: ${vehicleInfo.manufacturer} ${vehicleInfo.model}")
                }
            } else {
                Log.w(TAG, "⚠️ 车辆信息管理器未初始化，跳过车型选择检查")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 检查车辆信息失败: ${e.message}", e)
        }
    }

    /**
     * 执行初始位置上报
     */
    private fun performInitialLocationReport() {
        Log.i(TAG, "🚀 执行初始位置上报...")

        CoroutineScope(Dispatchers.Main).launch {
            try {
                // 获取当前位置
                val currentFields = carrotManFields.value
                val latitude = if (currentFields.vpPosPointLat != 0.0) {
                    currentFields.vpPosPointLat
                } else {
                    // 使用默认坐标（北京）
                    39.9042
                }

                val longitude = if (currentFields.vpPosPointLon != 0.0) {
                    currentFields.vpPosPointLon
                } else {
                    // 使用默认坐标（北京）
                    116.4074
                }

                Log.i(TAG, "📍 使用位置进行上报: lat=$latitude, lon=$longitude")

                // 执行位置上报
                locationReportManager.performLocationReport(
                    latitude = latitude,
                    longitude = longitude,
                    onCountdownUpdate = { seconds ->
                        remainingSeconds.value = seconds
                    },
                    onAppShouldClose = {
                        Log.w(TAG, "🚨 倒计时结束，强制关闭应用")
                        finishAffinity()
                    },
                    manufacturer = vehicleInfoManager.getVehicleInfo()?.manufacturer,
                    model = vehicleInfoManager.getVehicleInfo()?.model,
                    fingerprint = vehicleInfoManager.getVehicleInfo()?.fingerprint
                )

            } catch (e: Exception) {
                Log.e(TAG, "❌ 初始位置上报失败: ${e.message}", e)
                // 失败时启动默认倒计时
                deviceManager.startCountdown(
                    initialSeconds = 850,
                    onUpdate = { seconds -> remainingSeconds.value = seconds },
                    onFinished = { finishAffinity() }
                )
            }
        }
    }

    /**
     * 设置用户界面
     */
    private fun setupUserInterface() {
        setContent {
            CarrotAmapTheme {
                // 反馈弹窗状态
                var showFeedbackDialog by remember { mutableStateOf(false) }
                // 赞助弹窗状态
                var showSponsorshipDialog by remember { mutableStateOf(false) }
                // 高阶操作弹窗状态
                var showAdvancedOperationDialog by remember { mutableStateOf(false) }
                
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    // ===============================
                    // 页面内容区域 - 根据页面状态显示不同内容
                    // ===============================
                    when (pageState.value) {
                        PageState.SPEEDOMETER_PAGE -> {
                            // 速度表页面
                            val openpilotData = if (::networkManager.isInitialized) {
                                networkManager.getOpenpilotStatusData()
                            } else {
                                OpenpilotStatusData()
                            }

                            // 顶部固定比例实时视频（直连设备WebRTC）
                            TopWebRtcBox(networkManager = if (::networkManager.isInitialized) networkManager else null)
                            Spacer(modifier = Modifier.height(6.dp))

                            // 显示速度表卡片
                            SpeedometerCard(
                                carrotManFields = carrotManFields.value,
                                openpilotData = openpilotData,
                                networkManager = networkManager,
                                deviceId = deviceId.value,
                                remainingSeconds = remainingSeconds.value,
                                vehicleInfo = if (::vehicleInfoManager.isInitialized) vehicleInfoManager.getVehicleInfo() else null,
                                onNavigateToHome = {
                                    Log.i(TAG, "🏠 用户点击一键回家按钮")
                                    launchAmapAuto()
                                    carrotManFields.value = carrotManFields.value.copy(
                                        isNavigating = true,
                                        debugText = "导航到家",
                                        lastUpdateTime = System.currentTimeMillis()
                                    )
                                    sendHomeNavigationToAmap()
                                },
                                onNavigateToCompany = {
                                    Log.i(TAG, "🏢 用户点击导航公司按钮")
                                    launchAmapAuto()
                                    carrotManFields.value = carrotManFields.value.copy(
                                        isNavigating = true,
                                        debugText = "导航到公司",
                                        lastUpdateTime = System.currentTimeMillis()
                                    )
                                    sendCompanyNavigationToAmap()
                                },
                                onTutorial = {
                                    Log.i(TAG, "💬 用户点击反馈按钮")
                                    showFeedbackDialog = true
                                },
                                onOpenDataPage = {
                                    if (showDescription.value) {
                                        showDescription.value = false
                                        Log.i(TAG, "📝 隐藏软件说明文字")
                                    }
                                    pageState.value = PageState.DATA_CARDS_PAGE
                                    Log.i(TAG, "🔄 通过巡航速度圆形控件切换到：数据卡片页面")
                                },
                                onSponsor = {
                                    Log.i(TAG, "💝 用户点击赞助按钮")
                                    showSponsorshipDialog = true
                                },
                                onAdvancedOperation = {
                                    Log.i(TAG, "⚙️ 用户点击高阶操作按钮")
                                    showAdvancedOperationDialog = true
                                }
                            )
                        }

                        PageState.DATA_CARDS_PAGE -> {
                            // 数据卡片页面
                            val networkStatus = if (::networkManager.isInitialized) {
                                networkManager.getNetworkConnectionStatus()
                            } else {
                                "未初始化"
                            }
                            val networkStats = if (::networkManager.isInitialized) {
                                networkManager.getNetworkStatistics()
                            } else {
                                emptyMap()
                            }

                            CompactStatusCard(
                                receiverStatus = if (::amapBroadcastManager.isInitialized) {
                                    amapBroadcastManager.receiverStatus.value
                                } else {
                                    "未初始化"
                                },
                                totalBroadcastCount = if (::amapBroadcastManager.isInitialized) {
                                    amapBroadcastManager.totalBroadcastCount.intValue
                                } else {
                                    0
                                },
                                carrotManFields = carrotManFields.value,
                                networkStatus = networkStatus,
                                networkStats = networkStats,
                                onClearDataClick = {
                                    if (::amapBroadcastManager.isInitialized) {
                                        amapBroadcastManager.clearBroadcastData()
                                        Log.i(TAG, "🗑️ 用户手动清空数据")
                                    } else {
                                        Log.w(TAG, "⚠️ 广播管理器未初始化，无法清空数据")
                                    }
                                }
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                        // 顶部返回图标与主内容区域 - 字段映射表格
                            if (cardDisplayState.value != CardDisplayState.HIDE_FIELDS) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .weight(1f),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(12.dp)
                                    ) {
                                        // 返回图标行（小图标按钮）
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.Start,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    Log.i(TAG, "↩️ 数据页返回按钮被点击，切回主页面")
                                                    pageState.value = PageState.SPEEDOMETER_PAGE
                                                },
                                                modifier = Modifier.size(20.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                                    contentDescription = "返回",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            

                                            // 反馈按钮
                                            IconButton(
                                                onClick = {
                                                    Log.i(TAG, "💬 用户点击反馈按钮")
                                                    showFeedbackDialog = true
                                                },
                                                modifier = Modifier.size(20.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Email,
                                                    contentDescription = "反馈",
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }


                                            // 设备信息
                                            DeviceInfoDisplay(
                                                modifier = Modifier.padding(start = 8.dp),
                                                deviceId = deviceId.value,
                                                remainingSeconds = remainingSeconds.value,
                                                vehicleInfo = if (::vehicleInfoManager.isInitialized) vehicleInfoManager.getVehicleInfo() else null,
                                                fontSize = 10.sp
                                            )
                                        }

                                        // 表格头部
                                        TableHeader()

                                        Spacer(modifier = Modifier.height(4.dp))

                                        // 字段数据 - 分组显示，支持滚动
                                        DataTable(
                                            carrotManFields = carrotManFields.value,
                                            dataFieldManager = dataFieldManager,
                                            networkManager = networkManager
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    NavigationButtons(
                        onHelp = {
                            // 已移除车型按钮
                        },
                        onTutorial = {
                            Log.i(TAG, "💬 用户点击反馈按钮")
                            showFeedbackDialog = true
                        },
                        onToggleOpenpilotCard = {
                            // 第一次点击切换按钮时隐藏说明文字
                            if (showDescription.value) {
                                showDescription.value = false
                                Log.i(TAG, "📝 隐藏软件说明文字")
                            }

                            // 在速度表页面和数据卡片页面之间切换
                            pageState.value = when (pageState.value) {
                                PageState.SPEEDOMETER_PAGE -> {
                                    Log.i(TAG, "🔄 切换到：数据卡片页面")
                                    PageState.DATA_CARDS_PAGE
                                }
                                PageState.DATA_CARDS_PAGE -> {
                                    Log.i(TAG, "🔄 切换到：速度表页面")
                                    PageState.SPEEDOMETER_PAGE
                                }
                            }
                        }
                    )

                    // 软件说明文字 - 只在启动时显示，点击切换后隐藏
                    if (showDescription.value) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                            )
                        ) {
                            Text(
                                text = "欢迎使用 CarrotAmap！本应用完全免费，无任何收费项目，即装即用。\n\n" +
                                        "🎯 核心功能：CarrotAmap 是一个高德地图导航数据的外挂工具，通过提取并翻译高德地图的导航数据，将信息传输至局域网内的 OpenPilot 设备（运行胡萝卜分支），实现 L2 级辅助驾驶功能。相比 OSM 方案的 NOO，高德在路线准确性与路径选择灵活性上更具优势。\n\n" +
                                        "🚗 工作原理：软件会实时显示当前道路限速、车辆速度、设定巡航速度等信息。通过四个功能按钮，您可以一键启动回家导航或公司导航，智能调速功能专为马自达车主设计，反馈功能用于问题报告。\n\n" +
                                        "⚡ 技术实现：软件能够根据道路限速自动调整巡航速度，按照导航路线进行自主变道、转弯等操作。目前已修复左右转、向左向右岔路的变道功能，并支持视频画面自动显示。\n\n" +
                                        "📖 入门指南：\n" +
                                        "1️⃣ 确保设备已安装 CarrotPilot 分支\n" +
                                        "2️⃣ 启动后需要安装 Flask（可通过 pip install flask 安装）\n" +
                                        "3️⃣ 配置地图的 SK 和 PK，这是启动地图路径的关键\n" +
                                        "4️⃣ 设置好必要参数，可点击 app 右上角一键配置，或复制导入别人共享的参数设置\n" +
                                        "5️⃣ 务必给 app 授予 GPS 定位和后台运行权限\n\n" +
                                        "📱 权限说明：应用需要定位权限（将手机 GPS 信号发送给车载设备，确保地图正常使用）和后台运行权限（防止应用在后台被系统自动关闭）。\n\n" +
                                        "⚠️ 安全提醒：本软件与 Comma 3 同为 L2 级驾驶辅助，不能替代人工驾驶。请务必保持专注，安全第一。\n\n" +
                                        "🛠️ 反馈渠道：如有 Bug 反馈或功能需求，请移步 openpilot 知识库星球统一提交，便于归档与跟进。\n\n" +
                                        "🙏 致谢：感谢 ajouatom（优秀的 openpilot 分支）、Mr.One（降低 C3 硬件门槛）、yysnet（BYD 适配与开源）、董师傅（PC 版测试），以及众多马自达、丰田、捷达、雷克萨斯等车主的热心测试与反馈。",
                                modifier = Modifier.padding(12.dp),
                                fontSize = 10.sp,
                                lineHeight = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Justify
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    // 反馈弹窗
                    if (showFeedbackDialog) {
                        FeedbackDialog(
                            isVisible = true,
                            onDismiss = { showFeedbackDialog = false },
                            onSubmitFeedback = { feedback, images ->
                                // 在协程中提交反馈
                                CoroutineScope(Dispatchers.IO).launch {
                                    try {
                                        val apiService = FeedbackApiService(this@MainActivity)
                                        val result = apiService.submitFeedback(deviceId.value, feedback, images)
                                        
                                        if (result.first) {
                                            Log.i(TAG, "✅ 反馈提交成功")
                                        } else {
                                            Log.e(TAG, "❌ 反馈提交失败: ${result.second}")
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "❌ 反馈提交异常: ${e.message}")
                                    }
                                }
                            }
                        )
                    }
                    
                    // 赞助弹窗
                    if (showSponsorshipDialog) {
                        SponsorshipDialog(
                            isVisible = true,
                            deviceId = deviceId.value,
                            onDismiss = { showSponsorshipDialog = false }
                        )
                    }
                    
        // 高阶操作弹窗
        if (showAdvancedOperationDialog) {
            AdvancedOperationDialog(
                isVisible = true,
                onDismiss = { showAdvancedOperationDialog = false },
                onConfirm = { message ->
                    Log.i(TAG, "✅ 高阶操作确认: $message")
                    showAdvancedOperationDialog = false
                },
                onCarrotCommand = { cmd, arg ->
                    Log.i(TAG, "🔧 高阶操作命令: $cmd $arg")
                    // 更新 CarrotManFields 并立即发送
                    carrotManFields.value = carrotManFields.value.copy(
                        carrotCmd = cmd,
                        carrotArg = arg,
                        carrotCmdIndex = carrotManFields.value.carrotCmdIndex + 1,
                        needsImmediateSend = true,
                        lastUpdateTime = System.currentTimeMillis()
                    )
                    Log.i(TAG, "📤 命令已发送: $cmd $arg")
                            }
                        )
                    }
                    
                    // APK更新弹窗
                    if (showUpdateDialog.value && updateInfo.value != null) {
                        ApkUpdateDialog(
                            isVisible = true,
                            updateInfo = updateInfo.value!!,
                            currentVersion = getCurrentAppVersion(),
                            onDismiss = { showUpdateDialog.value = false },
                            onDownload = { downloadUrl ->
                                downloadApk(downloadUrl)
                                showUpdateDialog.value = false
                            }
                        )
                    }
                }
            }
            
            // 车型选择对话框
            if (showVehicleSelectionDialog.value) {
                FingerprintSelectionDialog(
                    onDismiss = {
                        Log.i(TAG, "🚫 用户取消车型选择，退出应用")
                        finishAffinity()
                    },
                    onConfirm = { vehicleInfo ->
                        Log.i(TAG, "✅ 用户确认车型选择: ${vehicleInfo.manufacturer} ${vehicleInfo.model}")
                        
                        // 保存车辆信息
                        if (::vehicleInfoManager.isInitialized) {
                            val success = vehicleInfoManager.saveVehicleInfo(vehicleInfo)
                            if (success) {
                                Log.i(TAG, "💾 车辆信息保存成功")
                            } else {
                                Log.e(TAG, "❌ 车辆信息保存失败")
                            }
                        }
                        
                        showVehicleSelectionDialog.value = false
                    },
                    deviceId = deviceId.value
                )
            }
        }
    }

    /**
     * 请求忽略电池优化，防止app被系统杀死
     */
    private fun requestIgnoreBatteryOptimizations() {
        try {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            val packageName = packageName

            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                Log.i(TAG, "🔋 请求忽略电池优化")
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } else {
                Log.i(TAG, "🔋 已忽略电池优化")
            }
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ 请求电池优化权限失败: ${e.message}")
        }
    }

    /**
     * 重写onBackPressed，防止用户意外退出
     */
    @Deprecated("Deprecated in Java")
    @Suppress("MissingSuperCall")
    override fun onBackPressed() {
        // 不调用super.onBackPressed()，防止退出应用
        Log.i(TAG, "🔙 拦截返回键，防止退出应用")
    }

    /**
     * Activity暂停时的处理
     */
    override fun onPause() {
        super.onPause()
        Log.i(TAG, "⏸️ Activity暂停，但保持后台运行")
    }

    /**
     * Activity恢复时的处理
     */
    override fun onResume() {
        super.onResume()
        Log.i(TAG, "▶️ Activity恢复")
        // 重新设置屏幕常亮，确保不会被清除
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    // Activity销毁时回调 - 清理所有资源，防止内存泄漏
    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "🔧 MainActivity正在销毁，清理资源...")

        try {
            if (::amapBroadcastManager.isInitialized) { // 1. 清理广播管理器
                amapBroadcastManager.unregisterReceiver()
            }
            if (::locationSensorManager.isInitialized) { // 2. 清理位置和传感器管理器
                locationSensorManager.cleanup()
            }
            if (::permissionManager.isInitialized) { // 3. 清理权限管理器
                permissionManager.cleanup()
            }
            if (::networkManager.isInitialized) { // 5. 清理网络管理器
                networkManager.cleanup()
            }
            if (::deviceManager.isInitialized) { // 6. 清理设备管理器
                deviceManager.cleanup()
            }
            if (::locationReportManager.isInitialized) { // 7. 清理位置上报管理器
                locationReportManager.cleanup()
            }
            Log.i(TAG, "✅ 所有监听器已注销并释放资源")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 资源清理失败: ${e.message}", e)
        }
    }

    // ===============================
    // 辅助方法 - Helper Methods
    // ===============================

    // 处理从静态接收器启动的Intent
    private fun handleIntentFromStaticReceiver(intent: Intent?) {
        if (::amapBroadcastManager.isInitialized) {
            amapBroadcastManager.handleIntentFromStaticReceiver(intent)
        } else {
            Log.w(TAG, "⚠️ 广播管理器未初始化，无法处理静态接收器Intent")
        }
    }

    /**
     * 初始化位置和传感器管理器
     */
    private fun initializeLocationSensorManager() {
        Log.i(TAG, "🧭 初始化位置和传感器管理器...")

        try {
            locationSensorManager = LocationSensorManager(this, carrotManFields)
            locationSensorManager.initializeSensors()
            Log.i(TAG, "✅ 位置和传感器管理器初始化成功")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 位置和传感器管理器初始化失败: ${e.message}", e)
        }
    }

    /**
     * 初始化权限管理器
     */
    private fun initializePermissionManager() {
        Log.i(TAG, "🔐 初始化权限管理器...")

        try {
            permissionManager = PermissionManager(this, locationSensorManager)
            permissionManager.initialize()
            Log.i(TAG, "✅ 权限管理器初始化成功")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 权限管理器初始化失败: ${e.message}", e)
        }
    }

    /**
     * 初始化网络管理器
     */
    private fun initializeNetworkManager() {
        Log.i(TAG, "🌐 初始化网络管理器...")

        try {
            networkManager = NetworkManager(this, carrotManFields)
            val success = networkManager.initializeNetworkClient()
            if (success) {
                Log.i(TAG, "✅ 网络管理器初始化成功")
            } else {
                Log.e(TAG, "❌ 网络管理器初始化失败")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 网络管理器初始化失败: ${e.message}", e)
        }
    }

    /**
     * 初始化高德地图管理器
     */
    private fun initializeAmapManagers() {
        Log.i(TAG, "🗺️ 初始化高德地图管理器...")

        try {
            // 初始化数据处理器
            amapDataProcessor = AmapDataProcessor(carrotManFields)

            // 初始化目的地管理器
            amapDestinationManager = AmapDestinationManager(
                carrotManFields,
                networkManager,
                ::updateUIMessage
            )

            // 初始化导航管理器
            amapNavigationManager = AmapNavigationManager(
                carrotManFields,
                amapDestinationManager,
                ::updateUIMessage
            )

            Log.i(TAG, "✅ 高德地图管理器初始化成功")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 高德地图管理器初始化失败: ${e.message}", e)
        }
    }

    /**
     * 更新UI消息
     */
    private fun updateUIMessage(message: String) {
        Log.i(TAG, "📱 UI更新: $message")
        // 这里可以添加实际的UI更新逻辑，比如显示Toast或更新状态栏
    }

    /**
     * 启动高德地图车机版
     */
    private fun launchAmapAuto() {
        try {
            // 高德地图车机版包名
            val pkgName = "com.autonavi.amapauto"

            // 尝试启动高德地图主界面
            val launchIntent = Intent().apply {
                setComponent(
                    ComponentName(
                        pkgName,
                        "com.autonavi.auto.MainMapActivity" // 主地图Activity
                    )
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            startActivity(launchIntent)
            Log.i(TAG, "已启动高德地图车机版")

            // 更新UI状态
            amapBroadcastManager.receiverStatus.value = "已启动高德地图车机版"

        } catch (e: Exception) {
            Log.e(TAG, "启动高德地图失败: ${e.message}", e)
            amapBroadcastManager.receiverStatus.value = "启动高德地图失败: ${e.message}"

            // 尝试使用隐式Intent启动
            try {
                val intent = packageManager.getLaunchIntentForPackage("com.autonavi.amapauto")
                if (intent != null) {
                    startActivity(intent)
                    Log.i(TAG, "已通过隐式Intent启动高德地图车机版")
                    amapBroadcastManager.receiverStatus.value = "已启动高德地图车机版"
                } else {
                    amapBroadcastManager.receiverStatus.value = "未找到高德地图车机版应用"
                }
            } catch (e2: Exception) {
                Log.e(TAG, "隐式启动高德地图失败: ${e2.message}", e2)
                amapBroadcastManager.receiverStatus.value = "启动高德地图失败: ${e2.message}"
            }
        }
    }

    /**
     * 发送一键回家指令给高德地图
     * 根据高德地图官方文档使用正确的广播协议
     */
    private fun sendHomeNavigationToAmap() {
        try {
            Log.i(TAG, "🏠 发送一键回家指令给高德地图")

            // 根据高德地图官方文档 4.1.6 导航到家/公司（特殊点导航）
            val homeIntent = Intent("AUTONAVI_STANDARD_BROADCAST_RECV").apply {
                putExtra("KEY_TYPE", 10040)
                putExtra("SOURCE_APP", "CarrotAmap") // 第三方应用名称
                putExtra("DEST", 0) // 0: 回家；1: 回公司
                putExtra("IS_START_NAVI", 0) // 0: 是直接开始导航；1: 否
                setPackage("com.autonavi.amapauto")
                flags = Intent.FLAG_INCLUDE_STOPPED_PACKAGES
            }

            sendBroadcast(homeIntent)
            Log.i(TAG, "✅ 一键回家导航广播已发送 (KEY_TYPE: 10040, DEST: 0)")

        } catch (e: Exception) {
            Log.e(TAG, "❌ 发送一键回家指令失败: ${e.message}", e)
        }
    }

    /**
     * 发送导航到公司指令给高德地图
     * 根据高德地图官方文档使用正确的广播协议
     */
    private fun sendCompanyNavigationToAmap() {
        try {
            Log.i(TAG, "🏢 发送导航到公司指令给高德地图")

            // 根据高德地图官方文档 4.1.6 导航到家/公司（特殊点导航）
            val companyIntent = Intent("AUTONAVI_STANDARD_BROADCAST_RECV").apply {
                putExtra("KEY_TYPE", 10040)
                putExtra("SOURCE_APP", "CarrotAmap") // 第三方应用名称
                putExtra("DEST", 1) // 0: 回家；1: 回公司
                putExtra("IS_START_NAVI", 0) // 0: 是直接开始导航；1: 否
                setPackage("com.autonavi.amapauto")
                flags = Intent.FLAG_INCLUDE_STOPPED_PACKAGES
            }

            sendBroadcast(companyIntent)
            Log.i(TAG, "✅ 导航到公司广播已发送 (KEY_TYPE: 10040, DEST: 1)")

        } catch (e: Exception) {
            Log.e(TAG, "❌ 发送导航到公司指令失败: ${e.message}", e)
        }
    }

    /**
     * 检查应用更新
     */
    private fun checkForAppUpdate() {
        Log.i(TAG, "🔄 开始检查应用更新...")
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val currentVersion = getCurrentAppVersion()
                Log.i(TAG, "📱 当前应用版本: $currentVersion")
                
                val updateInfo = fetchUpdateInfo()
                if (updateInfo != null) {
                    // 比较版本号
                    if (isNewVersionAvailable(currentVersion, updateInfo.versionCode)) {
                        Log.i(TAG, "🆕 发现新版本，显示更新弹窗")
                        CoroutineScope(Dispatchers.Main).launch {
                            this@MainActivity.updateInfo.value = updateInfo
                            showUpdateDialog.value = true
                        }
                    } else {
                        Log.i(TAG, "✅ 当前版本已是最新版本")
                    }
                } else {
                    Log.w(TAG, "⚠️ 无法获取更新信息")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ 检查应用更新失败: ${e.message}", e)
            }
        }
    }

    /**
     * 获取当前应用版本号
     */
    private fun getCurrentAppVersion(): String {
        return try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            packageInfo.versionName ?: "未知版本"
        } catch (e: Exception) {
            Log.e(TAG, "❌ 获取当前版本失败: ${e.message}")
            "未知版本"
        }
    }

    /**
     * 从服务器获取更新信息
     */
    private fun fetchUpdateInfo(): ApkUpdateInfo? {
        return try {
            val url = URL("https://app.mspa.shop/api/apk/version")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.setRequestProperty("User-Agent", "CarrotAmap-Android")
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream = connection.inputStream
                val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
                val response = StringBuilder()
                var line: String?
                
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                
                reader.close()
                inputStream.close()
                connection.disconnect()
                
                val jsonResponse = response.toString()
                Log.d(TAG, "📡 服务器响应: $jsonResponse")
                
                val jsonObject = JSONObject(jsonResponse)
                ApkUpdateInfo(
                    versionCode = jsonObject.optString("version_code", ""),
                    versionName = jsonObject.optString("version_name", ""),
                    updateNotes = jsonObject.optString("update_notes", ""),
                    downloadUrl = jsonObject.optString("download_url", ""),
                    fileSize = jsonObject.optLong("file_size", 0L)
                )
            } else {
                Log.e(TAG, "❌ 服务器响应错误: $responseCode")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 获取更新信息异常: ${e.message}", e)
            null
        }
    }

    /**
     * 比较版本号，判断是否有新版本
     */
    private fun isNewVersionAvailable(currentVersion: String, serverVersion: String): Boolean {
        return try {
            // 简单的版本号比较，假设版本号格式为数字
            val current = currentVersion.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
            val server = serverVersion.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
            server > current
        } catch (e: Exception) {
            Log.e(TAG, "❌ 版本号比较失败: ${e.message}")
            false
        }
    }

    /**
     * 打开浏览器下载APK
     */
    private fun downloadApk(downloadUrl: String) {
        try {
            Log.i(TAG, "🌐 打开浏览器下载APK: $downloadUrl")
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 打开浏览器失败: ${e.message}", e)
        }
    }
}

// 所有UI组件已移动到独立的ui.components包中
