package com.example.carrotamap

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import android.content.pm.PackageManager
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject


// UI组件导入
import com.example.carrotamap.ui.components.*
import com.example.carrotamap.ui.components.CompactStatusCard
import com.example.carrotamap.ui.components.TableHeader
import com.example.carrotamap.ui.components.DataTable

/**
 * 用户数据更新模型（简化版本）
 */
data class UserDataForUpdate(
    val carModel: String,
    val wechatName: String,
    val sponsorAmount: Float,
    val userType: Int
)

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



    
    // 广播接收器管理器
    private lateinit var amapBroadcastManager: AmapBroadcastManager
    // 位置和传感器管理器
    private lateinit var locationSensorManager: LocationSensorManager
    // 权限管理器
    private lateinit var permissionManager: PermissionManager
    // 网络管理器
    private lateinit var networkManager: NetworkManager
    // 数据字段管理器
    private val dataFieldManager = DataFieldManager()
    
    // 控制指令广播接收器
    private val carrotCommandReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.example.cplink.SEND_CARROT_COMMAND" -> {
                    val command = intent.getStringExtra("command") ?: return
                    val arg = intent.getStringExtra("arg") ?: return
                    
                    Log.i(TAG, "📡 收到控制指令广播: carrotCmd=$command, carrotArg=$arg")
                    
                    // 通过NetworkManager发送指令到设备
                    if (::networkManager.isInitialized) {
                        networkManager.sendControlCommand(command, arg)
                    } else {
                        Log.w(TAG, "⚠️ NetworkManager未初始化，无法发送控制指令")
                    }
                }
                "com.example.cplink.CHANGE_SPEED_MODE" -> {
                    val mode = intent.getIntExtra("mode", 0)
                    val modeNames = arrayOf("智能控速", "原车巡航", "弯道减速")
                    
                    Log.i(TAG, "🔄 收到模式切换广播: ${modeNames[mode]} (SpeedFromPCM=$mode)")
                    
                    // 通过NetworkManager发送模式切换到设备
                    if (::networkManager.isInitialized) {
                        CoroutineScope(Dispatchers.Main).launch {
                            try {
                                val result = networkManager.sendModeChangeToComma3(mode)
                                if (result.isSuccess) {
                                    Log.i(TAG, "✅ 模式切换成功: ${modeNames[mode]}")
                                } else {
                                    Log.e(TAG, "❌ 模式切换失败: ${result.exceptionOrNull()?.message}")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ 模式切换异常: ${e.message}", e)
                            }
                        }
                    } else {
                        Log.w(TAG, "⚠️ NetworkManager未初始化，无法切换模式")
                    }
                }
            }
        }
    }
    // 高德地图相关管理器
    private lateinit var amapDestinationManager: AmapDestinationManager
    private lateinit var amapNavigationManager: AmapNavigationManager
    private lateinit var amapDataProcessor: AmapDataProcessor
    // 设备管理器
    private lateinit var deviceManager: DeviceManager
    
    // 内存监控定时器
    private var memoryMonitorTimer: java.util.Timer? = null

    // 设备状态
    private val deviceId = mutableStateOf("")
    private val remainingSeconds = mutableStateOf(0)
    private val userType = mutableStateOf(0) // 用户类型：0=未知，1=新用户，2=支持者，3=赞助者，4=铁粉
    
    // 使用统计状态
    private val usageStats = mutableStateOf(UsageStats(0, 0, 0f))
    
    
    
    // 悬浮窗相关状态
    private val isFloatingWindowEnabled = mutableStateOf(false)
    
    // 页面状态
    private var currentPage by mutableStateOf(0) // 0: 主页, 1: 帮助, 2: 问答, 3: 我的, 4: 实时数据
    
    // 存储启动Intent用于页面导航
    private var pendingNavigationIntent: Intent? = null
    
    // 下载弹窗状态
    private val showDownloadDialog = mutableStateOf(false)
    
    // 自检查状态
    private val selfCheckStatus = mutableStateOf(SelfCheckStatus())
    
    // Activity Result Launcher for overlay permission
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (Settings.canDrawOverlays(this)) {
            Log.i(TAG, "✅ 悬浮窗权限已授予")
            isFloatingWindowEnabled.value = true
        } else {
            Log.w(TAG, "❌ 悬浮窗权限被拒绝")
            isFloatingWindowEnabled.value = false
        }
    }

    // Activity创建时回调 - 完成应用的初始化工作
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 保持屏幕常亮
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Log.i(TAG, "🔆 已设置屏幕常亮")

        // 请求忽略电池优化
        requestIgnoreBatteryOptimizations()
        
        // 请求悬浮窗权限
        requestFloatingWindowPermission()

        Log.i(TAG, "🚀 MainActivity正在启动...")

        // 立即初始化权限管理器，在Activity早期阶段
        initializePermissionManagerEarly()
        
        // 立即设置用户界面，避免白屏
        setupUserInterface()
        
        // 存储Intent用于后续页面导航
        pendingNavigationIntent = intent

        // 开始自检查流程
        startSelfCheckProcess()
        
        // 启动内存监控
        startMemoryMonitoring()
        
        // 注册控制指令广播接收器
        registerCarrotCommandReceiver()

        Log.i(TAG, "✅ MainActivity启动完成")
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.i(TAG, "📱 收到新的Intent，处理页面导航")
        // 处理新的Intent，用于从悬浮窗导航
        pendingNavigationIntent = intent
        handleFloatingWindowNavigation()
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

            // 获取设备ID并更新UI
            val id = deviceManager.getDeviceId()
            deviceId.value = id

            // 记录应用启动（在设备管理器初始化后）
            deviceManager.recordAppStart()

            Log.i(TAG, "✅ 设备管理器初始化成功，设备ID: $id")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 设备管理器初始化失败: ${e.message}", e)
        }
    }

    /**
     * 注册控制指令广播接收器
     */
    private fun registerCarrotCommandReceiver() {
        try {
            val filter = android.content.IntentFilter().apply {
                addAction("com.example.cplink.SEND_CARROT_COMMAND")
                addAction("com.example.cplink.CHANGE_SPEED_MODE")
            }
            registerReceiver(carrotCommandReceiver, filter, RECEIVER_NOT_EXPORTED)
            Log.i(TAG, "✅ 控制指令广播接收器已注册（包含模式切换）")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 注册控制指令广播接收器失败: ${e.message}", e)
        }
    }

    /**
     * 注销控制指令广播接收器
     */
    private fun unregisterCarrotCommandReceiver() {
        try {
            unregisterReceiver(carrotCommandReceiver)
            Log.i(TAG, "✅ 控制指令广播接收器已注销")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 注销控制指令广播接收器失败: ${e.message}", e)
        }
    }

    /**
     * 自动更新使用统计数据到API
     */
    private suspend fun autoUpdateUsageStats(deviceId: String, usageStats: UsageStats) = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "📊 自动更新使用统计数据: 次数=${usageStats.usageCount}, 时长=${usageStats.usageDuration}分钟, 距离=${usageStats.totalDistance}km")
            
            // 检查使用统计数据是否有效
            if (usageStats.usageCount == 0 && usageStats.usageDuration == 0L && usageStats.totalDistance == 0f) {
                Log.w(TAG, "⚠️ 使用统计数据全为0，跳过API更新")
                return@withContext
            }
            
            // 首先获取用户当前数据
            val currentUserData = fetchUserDataForUpdate(deviceId)
            Log.d(TAG, "📋 用户当前数据: 车型=${currentUserData.carModel}, 微信名=${currentUserData.wechatName}, 赞助金额=${currentUserData.sponsorAmount}, 用户类型=${currentUserData.userType}")
            
            val url = URL("https://app.mspa.shop/api/user/update")
            val connection = url.openConnection() as HttpURLConnection
            
            val requestBody = JSONObject().apply {
                put("device_id", deviceId)
                put("car_model", currentUserData.carModel)
                put("wechat_name", currentUserData.wechatName)
                put("sponsor_amount", currentUserData.sponsorAmount)
                put("user_type", currentUserData.userType)
                // 更新使用统计数据
                put("usage_count", usageStats.usageCount)
                put("usage_duration", usageStats.usageDuration / 60.0) // 转换为小时
                put("total_distance", usageStats.totalDistance)
            }.toString()
            
            Log.d(TAG, "📤 发送使用统计更新请求: $requestBody")
            
            connection.apply {
                requestMethod = "POST"
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", "CP搭子/1.0")
                doOutput = true
            }
            
            connection.outputStream.use { outputStream ->
                outputStream.write(requestBody.toByteArray())
            }
            
            val responseCode = connection.responseCode
            Log.d(TAG, "📥 使用统计更新响应码: $responseCode")
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                Log.d(TAG, "📥 使用统计更新响应: $response")
                
                val jsonObject = JSONObject(response)
                if (jsonObject.getBoolean("success")) {
                    val data = jsonObject.optJSONObject("data")
                    if (data != null) {
                        val updatedCount = data.optInt("usage_count", 0)
                        val updatedDuration = data.optDouble("usage_duration", 0.0)
                        val updatedDistance = data.optDouble("total_distance", 0.0)
                        Log.i(TAG, "✅ 使用统计数据更新成功: 次数=$updatedCount, 时长=${updatedDuration}小时, 距离=${updatedDistance}km")
                    } else {
                        Log.i(TAG, "✅ 使用统计数据更新成功")
                    }
                } else {
                    Log.w(TAG, "⚠️ 使用统计更新API返回失败: ${jsonObject.optString("message", "未知错误")}")
                }
            } else {
                val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "无错误信息"
                Log.w(TAG, "⚠️ 使用统计更新失败，响应码: $responseCode, 错误信息: $errorResponse")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 自动更新使用统计失败", e)
            throw e
        }
    }

    /**
     * 获取用户数据用于更新（简化版本，只获取必要字段）
     */
    private suspend fun fetchUserDataForUpdate(deviceId: String): UserDataForUpdate = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://app.mspa.shop/api/user/$deviceId")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", "CP搭子/1.0")
            }
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(response)
                
                if (jsonObject.getBoolean("success")) {
                    val data = jsonObject.getJSONObject("data")
                    UserDataForUpdate(
                        carModel = data.optString("car_model", ""),
                        wechatName = data.optString("wechat_name", ""),
                        sponsorAmount = data.optDouble("sponsor_amount", 0.0).toFloat(),
                        userType = data.optInt("user_type", 0)
                    )
                } else {
                    throw Exception("API返回失败: ${jsonObject.optString("message", "未知错误")}")
                }
            } else if (responseCode == 404) {
                // 用户不存在，返回默认值
                UserDataForUpdate(
                    carModel = "",
                    wechatName = "",
                    sponsorAmount = 0f,
                    userType = 0
                )
            } else {
                throw Exception("HTTP错误: $responseCode")
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取用户数据失败", e)
            // 返回默认值
            UserDataForUpdate(
                carModel = "",
                wechatName = "",
                sponsorAmount = 0f,
                userType = 0
            )
        }
    }

    /**
     * 获取用户类型
     */
    private suspend fun fetchUserType(deviceId: String): Int = withContext(Dispatchers.IO) {
        try {
            // 1. 先检查本地缓存
            val cachedType = getUserTypeFromCache(deviceId)
            if (cachedType != -1) {
                Log.i(TAG, "📱 使用缓存的用户类型: $cachedType")
                return@withContext cachedType
            }
            
            // 2. 缓存未命中，从服务器获取
            Log.i(TAG, "👤 获取用户类型: $deviceId")
            
            val url = URL("https://app.mspa.shop/api/user/$deviceId")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", "CP搭子/1.0")
            }
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(response)
                
                if (jsonObject.getBoolean("success")) {
                    val data = jsonObject.getJSONObject("data")
                    val type = data.optInt("user_type", 0)
                    Log.i(TAG, "✅ 用户类型获取成功: $type")
                    
                    // 3. 保存到缓存
                    saveUserTypeToCache(deviceId, type)
                    
                    type
                } else {
                    Log.w(TAG, "⚠️ API返回失败，使用默认用户类型0")
                    0
                }
            } else if (responseCode == 404) {
                Log.w(TAG, "⚠️ 用户不存在，使用默认用户类型0")
                0
            } else {
                Log.w(TAG, "⚠️ HTTP错误: $responseCode，使用默认用户类型0")
                0
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 获取用户类型失败: ${e.message}，使用默认用户类型0", e)
            0
        }
    }

    /**
     * 从缓存获取用户类型
     */
    private fun getUserTypeFromCache(deviceId: String): Int {
        return try {
            val prefs = getSharedPreferences("user_cache", Context.MODE_PRIVATE)
            val cachedType = prefs.getInt("user_type_$deviceId", -1)
            val cacheTime = prefs.getLong("user_type_time_$deviceId", 0)
            val cacheAge = System.currentTimeMillis() - cacheTime
            
            // 缓存有效期：24小时
            if (cachedType != -1 && cacheAge < 24 * 60 * 60 * 1000) {
                Log.d(TAG, "📱 缓存命中: 类型=$cachedType, 年龄=${cacheAge / 1000}秒")
                cachedType
            } else {
                Log.d(TAG, "📱 缓存过期或不存在: 年龄=${cacheAge / 1000}秒")
                -1
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 读取用户类型缓存失败: ${e.message}", e)
            -1
        }
    }

    /**
     * 保存用户类型到缓存
     */
    private fun saveUserTypeToCache(deviceId: String, userType: Int) {
        try {
            val prefs = getSharedPreferences("user_cache", Context.MODE_PRIVATE)
            prefs.edit()
                .putInt("user_type_$deviceId", userType)
                .putLong("user_type_time_$deviceId", System.currentTimeMillis())
                .apply()
            Log.d(TAG, "💾 用户类型已缓存: $userType")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 保存用户类型缓存失败: ${e.message}", e)
        }
    }

    /**
     * 执行初始位置更新（仅用于距离统计）
     */
    private fun performInitialLocationUpdate() {
        Log.i(TAG, "🚀 执行初始位置更新...")

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

                Log.i(TAG, "📍 更新位置用于距离统计: lat=$latitude, lon=$longitude")

                // 更新位置并计算距离
                if (::deviceManager.isInitialized) {
                    deviceManager.updateLocationAndDistance(latitude, longitude)
                }

                // 启动默认倒计时
                deviceManager.startCountdown(
                    initialSeconds = 850,
                    onUpdate = { seconds -> remainingSeconds.value = seconds },
                    onFinished = { finishAffinity() }
                )

            } catch (e: Exception) {
                Log.e(TAG, "❌ 初始位置更新失败: ${e.message}", e)
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
            CPlinkTheme {
                Scaffold(
                    bottomBar = {
                        BottomNavigationBar(
                            currentPage = currentPage,
                            onPageChange = { page -> currentPage = page }
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
                        when (currentPage) {
                            0 -> HomePage(deviceId.value, remainingSeconds.value, selfCheckStatus.value, userType.value, ::sendCarrotCommand, ::sendCurrentRoadLimitSpeed)
                            1 -> HelpPage()
                            2 -> QAPage()
                            3 -> ProfilePage(usageStats.value, deviceId.value)
                            4 -> DataPage(carrotManFields.value, dataFieldManager, networkManager, amapBroadcastManager)
                        }
                        
                        // 下载弹窗
                        if (showDownloadDialog.value) {
                            CarrotAmapDownloadDialog(
                                onDismiss = { showDownloadDialog.value = false },
                                onDownload = { 
                                    showDownloadDialog.value = false
                                    openGitHubWebsite()
                                }
                            )
                        }
                    }
                    }
                    }
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
     * 请求悬浮窗权限
     */
    private fun requestFloatingWindowPermission() {
        try {
            if (!Settings.canDrawOverlays(this)) {
                Log.i(TAG, "🔳 请求悬浮窗权限")
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                overlayPermissionLauncher.launch(intent)
            } else {
                Log.i(TAG, "🔳 已有悬浮窗权限")
                isFloatingWindowEnabled.value = true
            }
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ 请求悬浮窗权限失败: ${e.message}")
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
        Log.i(TAG, "⏸️ Activity暂停")
        
        // 记录使用时长
        if (::deviceManager.isInitialized) {
            deviceManager.recordAppUsage()
        }
        
        // 用户类型2不支持者不启动悬浮窗
        if (userType.value == 2) {
            Log.i(TAG, "💚 支持者用户，不启动悬浮窗功能")
            return
        }
        
        // 启动悬浮窗服务
        if (isFloatingWindowEnabled.value) {
            val intent = Intent(this, FloatingWindowService::class.java).apply {
                action = FloatingWindowService.ACTION_START_FLOATING
            }
            startService(intent)
        }
    }

    /**
     * Activity恢复时的处理
     */
    override fun onResume() {
        super.onResume()
        Log.i(TAG, "▶️ Activity恢复，隐藏悬浮窗")
        
        // 隐藏悬浮窗
        val intent = Intent(this, FloatingWindowService::class.java).apply {
            action = FloatingWindowService.ACTION_STOP_FLOATING
        }
        startService(intent)
        
        // 重新设置屏幕常亮，确保不会被清除
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // 更新使用统计
        if (::deviceManager.isInitialized) {
            usageStats.value = deviceManager.getUsageStats()
        }
        
        // 处理悬浮窗页面导航
        handleFloatingWindowNavigation()
    }

    // Activity销毁时回调 - 清理所有资源，防止内存泄漏
    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "🔧 MainActivity正在销毁，清理资源...")

        try {
            // 停止内存监控
            stopMemoryMonitoring()
            
            // 记录应用使用时长（在清理前）
            if (::deviceManager.isInitialized) {
                deviceManager.recordAppUsage()
            }
            
            // 注销控制指令广播接收器
            unregisterCarrotCommandReceiver()
            
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
            Log.i(TAG, "✅ 所有监听器已注销并释放资源")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 资源清理失败: ${e.message}", e)
        }
    }
    
    /**
     * 启动内存监控 - 优化版：减少监控频率
     */
    private fun startMemoryMonitoring() {
        memoryMonitorTimer = java.util.Timer("MemoryMonitor", true).apply {
            scheduleAtFixedRate(object : java.util.TimerTask() {
                override fun run() {
                    try {
                        val runtime = Runtime.getRuntime()
                        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
                        val maxMemory = runtime.maxMemory()
                        val usagePercent = (usedMemory.toFloat() / maxMemory * 100).toInt()
                        
                        // 优化：只在内存使用较高时才记录日志
                        if (usagePercent > 60) {
                            Log.d(TAG, "📊 内存使用: ${usedMemory / 1024 / 1024}MB / ${maxMemory / 1024 / 1024}MB ($usagePercent%)")
                        }
                        
                        if (usagePercent > 80) {
                            Log.w(TAG, "⚠️ 内存使用过高 ($usagePercent%)，触发清理")
                            performMemoryCleanup()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ 内存监控失败: ${e.message}", e)
                    }
                }
            }, 60000, 60000) // 优化：改为60秒检查一次，减少系统开销
        }
        Log.i(TAG, "📊 内存监控已启动（优化版：60秒间隔）")
    }
    
    /**
     * 停止内存监控
     */
    private fun stopMemoryMonitoring() {
        memoryMonitorTimer?.cancel()
        memoryMonitorTimer = null
        Log.i(TAG, "📊 内存监控已停止")
    }
    
    /**
     * 执行内存清理
     */
    private fun performMemoryCleanup() {
        try {
            // 清理广播数据列表
            if (::amapBroadcastManager.isInitialized) {
                amapBroadcastManager.clearBroadcastData()
                Log.i(TAG, "🧹 已清理广播数据列表")
            }
            
            // 建议GC
            System.gc()
            Log.i(TAG, "🧹 已建议系统执行GC")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 内存清理失败: ${e.message}", e)
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
     * 处理悬浮窗页面导航
     */
    private fun handleFloatingWindowNavigation() {
        val intent = pendingNavigationIntent
        val openPage = intent?.getIntExtra("OPEN_PAGE", -1)
        if (openPage != null && openPage != -1) {
            Log.i(TAG, "📱 悬浮窗导航到页面: $openPage")
            currentPage = openPage
            // 清除已处理的Intent
            pendingNavigationIntent = null
        }
    }

    /**
     * 开始自检查流程
     */
    private fun startSelfCheckProcess() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // 1. 位置和传感器管理器初始化
                updateSelfCheckStatus("位置传感器管理器", "正在初始化...", false)
                initializeLocationSensorManager()
                updateSelfCheckStatus("位置传感器管理器", "初始化完成", true)
                delay(200) // 减少延迟时间

                // 2. 权限管理器初始化
                updateSelfCheckStatus("权限管理器", "正在初始化...", false)
                initializePermissionManager()
                updateSelfCheckStatus("权限管理器", "初始化完成", true)
                delay(200)

                // 3. 权限管理和位置服务初始化
                updateSelfCheckStatus("权限和位置服务", "正在设置...", false)
                setupPermissionsAndLocation()
                updateSelfCheckStatus("权限和位置服务", "设置完成", true)
                delay(200)

                // 4. 网络管理器初始化（仅初始化，不启动网络服务）
                updateSelfCheckStatus("网络管理器", "正在初始化...", false)
                initializeNetworkManagerOnly()
                updateSelfCheckStatus("网络管理器", "初始化完成", true)
                delay(200)

                // 5-7. 并行初始化高德地图、广播和设备管理器
                updateSelfCheckStatus("系统管理器", "正在并行初始化...", false)
                
                // 并行执行三个管理器的初始化
                val amapJob = CoroutineScope(Dispatchers.IO).launch {
                    initializeAmapManagers()
                }
                val broadcastJob = CoroutineScope(Dispatchers.IO).launch {
                    initializeBroadcastManager()
                }
                val deviceJob = CoroutineScope(Dispatchers.IO).launch {
                    initializeDeviceManager()
                }
                
                // 等待所有并行任务完成
                amapJob.join()
                broadcastJob.join()
                deviceJob.join()
                
                updateSelfCheckStatus("系统管理器", "并行初始化完成", true)
                delay(200)

                // 8. 用户类型获取（快速完成，使用缓存）
                updateSelfCheckStatus("用户类型", "正在获取...", false)
                val fetchedUserType = fetchUserType(deviceId.value)
                userType.value = fetchedUserType
                
                // 保存用户类型到SharedPreferences，供悬浮窗使用
                val sharedPreferences = getSharedPreferences("device_prefs", Context.MODE_PRIVATE)
                sharedPreferences.edit().putInt("user_type", fetchedUserType).apply()
                
                val userTypeText = when (fetchedUserType) {
                    0 -> "未知用户"
                    1 -> "新用户"
                    2 -> "支持者"
                    3 -> "赞助者"
                    4 -> "铁粉"
                    else -> "未知类型($fetchedUserType)"
                }
                updateSelfCheckStatus("用户类型", "获取完成: $userTypeText", true)
                delay(100) // 减少延迟

                // 8.5. 异步更新使用统计（不阻塞启动）
                if (fetchedUserType in 2..4) {
                    updateSelfCheckStatus("使用统计", "后台更新中...", false)
                    // 异步执行使用统计更新，不阻塞启动流程
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            // 获取最新的使用统计数据
                            val latestUsageStats = if (::deviceManager.isInitialized) {
                                deviceManager.getUsageStats()
                            } else {
                                Log.w(TAG, "⚠️ 设备管理器未初始化，使用默认统计数据")
                                UsageStats(0, 0, 0f)
                            }
                            
                            // 更新UI状态
                            withContext(Dispatchers.Main) {
                                usageStats.value = latestUsageStats
                                updateSelfCheckStatus("使用统计", "更新完成", true)
                            }
                            
                            autoUpdateUsageStats(deviceId.value, latestUsageStats)
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ 自动更新使用统计失败: ${e.message}", e)
                            withContext(Dispatchers.Main) {
                                updateSelfCheckStatus("使用统计", "更新失败: ${e.message}", false)
                            }
                        }
                    }
                }
                delay(100) // 减少延迟

                // 9. 执行初始位置更新
                updateSelfCheckStatus("位置更新", "正在执行...", false)
                performInitialLocationUpdate()
                updateSelfCheckStatus("位置更新", "执行完成", true)
                delay(200)

                // 10. 处理静态接收器Intent
                updateSelfCheckStatus("静态接收器", "正在处理...", false)
                handleIntentFromStaticReceiver(intent)
                updateSelfCheckStatus("静态接收器", "处理完成", true)
                delay(200)

                // 11. 启动网络服务（延迟启动）
                updateSelfCheckStatus("网络服务", "正在启动...", false)
                startNetworkService()
                updateSelfCheckStatus("网络服务", "启动完成", true)
                delay(200)

                // 12. 设置UI界面
                updateSelfCheckStatus("用户界面", "正在设置...", false)
                updateSelfCheckStatus("用户界面", "设置完成", true)
                delay(200)

                // 所有检查完成
                updateSelfCheckStatus("系统检查", "所有检查完成", true)
                selfCheckStatus.value = selfCheckStatus.value.copy(isCompleted = true)

                // 根据用户类型进行不同操作
                handleUserTypeAction(fetchedUserType)

            } catch (e: Exception) {
                Log.e(TAG, "❌ 自检查流程失败: ${e.message}", e)
                updateSelfCheckStatus("系统检查", "检查失败: ${e.message}", false)
            }
        }
    }

    /**
     * 更新自检查状态
     */
    private fun updateSelfCheckStatus(component: String, message: String, isCompleted: Boolean) {
        val currentStatus = selfCheckStatus.value
        val newStatus = currentStatus.copy(
            currentComponent = component,
            currentMessage = message,
            isCompleted = isCompleted,
            completedComponents = if (isCompleted) {
                currentStatus.completedComponents + component
            } else {
                currentStatus.completedComponents
            }
        )
        selfCheckStatus.value = newStatus
        Log.i(TAG, "🔍 自检查: $component - $message")
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
     * 早期初始化权限管理器（在Activity早期阶段）
     */
    private fun initializePermissionManagerEarly() {
        Log.i(TAG, "🔐 早期初始化权限管理器...")

        try {
            // 创建一个临时的LocationSensorManager用于权限管理器初始化
            val tempCarrotManFields = mutableStateOf(CarrotManFields())
            val tempLocationSensorManager = LocationSensorManager(this, tempCarrotManFields)
            permissionManager = PermissionManager(this, tempLocationSensorManager)
            // 在Activity早期阶段初始化，此时可以安全注册ActivityResultLauncher
            permissionManager.initialize()
            Log.i(TAG, "✅ 权限管理器早期初始化成功")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 权限管理器早期初始化失败: ${e.message}", e)
        }
    }

    /**
     * 初始化权限管理器（在自检查流程中）
     */
    private fun initializePermissionManager() {
        Log.i(TAG, "🔐 初始化权限管理器...")

        try {
            // 如果权限管理器已经初始化，只需要更新locationSensorManager引用
            if (::permissionManager.isInitialized) {
                // 更新权限管理器中的locationSensorManager引用
                permissionManager.updateLocationSensorManager(locationSensorManager)
                Log.i(TAG, "✅ 权限管理器引用更新成功")
            } else {
                // 如果早期初始化失败，在这里重新初始化
            permissionManager = PermissionManager(this, locationSensorManager)
            permissionManager.initialize()
                Log.i(TAG, "✅ 权限管理器重新初始化成功")
            }
            
            // GPS预热：提前开始位置获取
            startGpsWarmup()
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 权限管理器初始化失败: ${e.message}", e)
        }
    }

    /**
     * GPS预热：提前开始位置获取
     */
    private fun startGpsWarmup() {
        try {
            if (::locationSensorManager.isInitialized) {
                Log.i(TAG, "🌡️ 开始GPS预热...")
                // 启动GPS预热，提前获取位置数据
                locationSensorManager.startGpsWarmup()
                Log.i(TAG, "✅ GPS预热已启动")
            } else {
                Log.w(TAG, "⚠️ 位置传感器管理器未初始化，跳过GPS预热")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ GPS预热失败: ${e.message}", e)
        }
    }

    /**
     * 初始化网络管理器（仅初始化，不启动网络服务）
     */
    private fun initializeNetworkManagerOnly() {
        Log.i(TAG, "🌐 初始化网络管理器（延迟启动网络服务）...")

        try {
            networkManager = NetworkManager(this, carrotManFields)
            // 仅创建NetworkManager实例，不启动网络服务
            Log.i(TAG, "✅ 网络管理器初始化成功（网络服务待启动）")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 网络管理器初始化失败: ${e.message}", e)
        }
    }

    /**
     * 启动网络服务（延迟启动）
     */
    private fun startNetworkService() {
        Log.i(TAG, "🌐 启动网络服务...")

        try {
            if (::networkManager.isInitialized) {
                val success = networkManager.initializeNetworkClient()
                if (success) {
                    Log.i(TAG, "✅ 网络服务启动成功")
                } else {
                    Log.e(TAG, "❌ 网络服务启动失败")
                }
            } else {
                Log.e(TAG, "❌ 网络管理器未初始化，无法启动网络服务")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 网络服务启动失败: ${e.message}", e)
        }
    }

    /**
     * 初始化高德地图管理器
     */
    private fun initializeAmapManagers() {
        Log.i(TAG, "🗺️ 初始化高德地图管理器...")

        try {
            // 初始化数据处理器
            amapDataProcessor = AmapDataProcessor(this, carrotManFields)

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
                putExtra("SOURCE_APP", "CPlink") // 第三方应用名称
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
                putExtra("SOURCE_APP", "CPlink") // 第三方应用名称
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
     * 根据用户类型执行不同操作
     */
    private fun handleUserTypeAction(userType: Int) {
        Log.i(TAG, "🎯 根据用户类型执行操作: $userType")
        
        when (userType) {
            -1 -> {
                // 管理员专用 - 强制退出应用
                Log.i(TAG, "🔧 管理员用户，强制退出应用")
                forceExitApp()
            }
            0 -> {
                // 未知用户 - 跳转到我的界面
                Log.i(TAG, "👤 未知用户，跳转到我的界面")
                currentPage = 3
            }
            1 -> {
                // 新用户 - 跳转到帮助界面
                Log.i(TAG, "🆕 新用户，跳转到帮助界面")
                currentPage = 1
            }
            2 -> {
                // 支持者 - 显示下载弹窗
                Log.i(TAG, "💚 支持者，显示carrotAmap下载弹窗")
                showCarrotAmapDownloadDialog()
            }
            3, 4 -> {
                // 赞助者/铁粉 - 直接打开高德地图
                Log.i(TAG, "💎 赞助者/铁粉，直接打开高德地图")
                launchAmapAuto()
            }
            else -> {
                // 其他情况 - 默认跳转到我的界面
                Log.w(TAG, "⚠️ 未知用户类型: $userType，跳转到我的界面")
                currentPage = 3
            }
        }
    }

    /**
     * 强制退出应用
     */
    private fun forceExitApp() {
        try {
            Log.i(TAG, "🚪 强制退出应用")
            
            // 延迟1秒后强制退出，确保日志记录完成
            CoroutineScope(Dispatchers.Main).launch {
                delay(1000)
                Log.i(TAG, "✅ 应用即将退出")
                finishAffinity()
                System.exit(0)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 强制退出失败: ${e.message}", e)
            // 即使出错也强制退出
            finishAffinity()
            System.exit(0)
        }
    }


    /**
     * 显示carrotAmap下载弹窗
     */
    private fun showCarrotAmapDownloadDialog() {
        try {
            Log.i(TAG, "📱 显示carrotAmap下载弹窗")
            // 设置显示下载弹窗的状态
            showDownloadDialog.value = true
        } catch (e: Exception) {
            Log.e(TAG, "❌ 显示下载弹窗失败: ${e.message}", e)
        }
    }

    /**
     * 打开浏览器访问GitHub网站
     */
    private fun openGitHubWebsite() {
        try {
            Log.i(TAG, "🌐 打开浏览器访问GitHub网站")
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/jixiexiaoge/openpilot/"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            Log.i(TAG, "✅ GitHub网站已打开")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 打开浏览器失败: ${e.message}", e)
        }
    }

    /**
     * 发送Carrot命令到设备
     */
    fun sendCarrotCommand(command: String, arg: String) {
        try {
            Log.i(TAG, "🎮 主页发送Carrot命令: $command $arg")
            
            // 检查NetworkManager是否已初始化
            if (::networkManager.isInitialized) {
                networkManager.sendControlCommand(command, arg)
                Log.i(TAG, "✅ 指令已发送: $command $arg")
            } else {
                Log.w(TAG, "⚠️ NetworkManager未初始化，无法发送指令")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 发送Carrot命令失败: ${e.message}", e)
        }
    }

    /**
     * 发送当前道路限速到comma3设备
     * 从FloatingWindowService移植过来的功能
     */
    fun sendCurrentRoadLimitSpeed() {
        try {
            // 从SharedPreferences获取当前道路限速
            val prefs = getSharedPreferences("CarrotAmap", Context.MODE_PRIVATE)
            val roadLimitSpeed = prefs.getInt("nRoadLimitSpeed", 0)
            
            if (roadLimitSpeed > 0) {
                Log.i(TAG, "🎯 主页发送当前道路限速: ${roadLimitSpeed}km/h")
                
                // 发送速度设置命令
                sendCarrotCommand("SPEED", roadLimitSpeed.toString())
                
                Log.i(TAG, "✅ 道路限速已发送: ${roadLimitSpeed}km/h")
            } else {
                Log.w(TAG, "⚠️ 当前道路限速为0，无法发送")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 发送道路限速失败: ${e.message}", e)
        }
    }





}

// 所有UI组件已移动到独立的ui.components包中
// 主界面UI已简化，功能移至悬浮窗

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
private fun HomePage(deviceId: String, remainingSeconds: Int, selfCheckStatus: SelfCheckStatus, userType: Int, onSendCommand: (String, String) -> Unit, onSendRoadLimitSpeed: () -> Unit) {
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
        // 主内容区域
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
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
            
            // 已完成项目列表
            if (selfCheckStatus.completedComponents.isNotEmpty()) {
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
                                        component
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
            
            // 底部控制按钮区域
            VehicleControlButtons(
                onPageChange = { page -> 
                    // 这里需要访问MainActivity的currentPage状态
                    // 暂时用Log记录，后续可以通过其他方式实现
                    Log.i("MainActivity", "页面切换请求: $page")
                },
                onSendCommand = onSendCommand,
                onSendRoadLimitSpeed = onSendRoadLimitSpeed
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
    onPageChange: (Int) -> Unit
) {
    val pages = listOf(
        BottomNavItem("主页", Icons.Default.Home, 0),
        BottomNavItem("帮助", Icons.Default.Info, 1),
        BottomNavItem("问答", Icons.Default.Info, 2),
        BottomNavItem("我的", Icons.Default.Person, 3),
        BottomNavItem("数据", Icons.Default.Settings, 4)
    )
    
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
 * 底部导航项数据类
 */
private data class BottomNavItem(
    val title: String,
    val icon: ImageVector,
    val index: Int
)

/**
 * 自检查状态数据类
 */
data class SelfCheckStatus(
    val currentComponent: String = "",
    val currentMessage: String = "",
    val isCompleted: Boolean = false,
    val completedComponents: List<String> = emptyList()
)

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
 * 车辆控制按钮组件 - 从悬浮窗迁移过来的5个关键按钮
 */
@Composable
private fun VehicleControlButtons(
    onPageChange: (Int) -> Unit,
    onSendCommand: (String, String) -> Unit,
    onSendRoadLimitSpeed: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
    ) {
        // 控制按钮行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // 加速按钮
            ControlButton(
                icon = "",
                label = "加速",
                color = Color(0xFF22C55E),
                onClick = {
                    Log.i("MainActivity", "🎮 主页：用户点击加速按钮")
                    onSendCommand("SPEED", "UP")
                }
            )
            
            // 减速按钮
            ControlButton(
                icon = "",
                label = "减速",
                color = Color(0xFFEF4444),
                onClick = {
                    Log.i("MainActivity", "🎮 主页：用户点击减速按钮")
                    onSendCommand("SPEED", "DOWN")
                }
            )
            
            // 左变道按钮
            ControlButton(
                icon = "",
                label = "左变道",
                color = Color(0xFF3B82F6),
                onClick = {
                    Log.i("MainActivity", "🎮 主页：用户点击左变道按钮")
                    onSendCommand("LANECHANGE", "LEFT")
                }
            )
            
            // 右变道按钮
            ControlButton(
                icon = "",
                label = "右变道",
                color = Color(0xFF3B82F6),
                onClick = {
                    Log.i("MainActivity", "🎮 主页：用户点击右变道按钮")
                    onSendCommand("LANECHANGE", "RIGHT")
                }
            )
            
            // 设置按钮（原帮助按钮，现在用于设置当前限速）
            ControlButton(
                icon = "",
                label = "设置",
                color = Color(0xFF8B5CF6),
                onClick = {
                    Log.i("MainActivity", "🎯 主页：用户点击设置按钮，发送当前道路限速")
                    onSendRoadLimitSpeed()
                }
            )
        }
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
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
    ) {
        if (icon.isNotEmpty()) {
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
        } else {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}


