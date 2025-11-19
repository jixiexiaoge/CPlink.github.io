package com.example.carrotamap

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.carrotamap.ui.theme.CPlinkTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import android.content.pm.PackageManager
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject


/**
 * 自检查状态数据类
 */
data class SelfCheckStatus(
    val currentComponent: String = "",
    val currentMessage: String = "",
    val isCompleted: Boolean = false,
    val completedComponents: List<String> = emptyList(),
    val completedMessages: Map<String, String> = emptyMap() // 存储组件名称和对应的消息内容
)

/**
 * 用户数据更新模型（简化版本）
 */
data class UserDataForUpdate(
    val carModel: String,
    val wechatName: String,
    val sponsorAmount: Float,
    val userType: Int
)

/**
 * MainActivity核心逻辑类
 * 负责核心业务逻辑、状态管理、权限处理等
 */
class MainActivityCore(
    private val activity: ComponentActivity,
    private val context: Context
) {
    companion object {
        private const val TAG = AppConstants.Logging.MAIN_ACTIVITY_TAG
    }

    // ===============================
    // 核心状态管理
    // ===============================
    
    /** Comma3 CarrotMan字段映射数据 */
    val carrotManFields = mutableStateOf(CarrotManFields())
    
    // 设备状态
    val deviceId = mutableStateOf("")
    val userType = mutableStateOf(0) // 用户类型：0=未知，1=新用户，2=支持者，3=赞助者，4=铁粉
    
    // 使用统计状态
    val usageStats = mutableStateOf(UsageStats(0, 0, 0f))
    
    // 页面状态
    var currentPage by mutableStateOf(0) // 0: 主页, 1: 帮助, 2: 问答, 3: 我的, 4: 实时数据
    
    // 存储启动Intent用于页面导航
    var pendingNavigationIntent: Intent? = null
    
    // 自检查状态
    val selfCheckStatus = mutableStateOf(SelfCheckStatus())
    
    // 网络连接状态
    val networkStatus = mutableStateOf("🔍 正在连接...")
    val deviceInfo = mutableStateOf("")

    // 实时网络流程事件（用于在主页顶部显示发现->连接链路）
    val pipelineEvents = mutableStateListOf<String>()

    fun addPipelineEvent(message: String) {
        // 带时间戳入队，最多保留20条
        val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        pipelineEvents.add("[$ts] $message")
        if (pipelineEvents.size > 40) {
            pipelineEvents.removeFirst()
        }
    }

    // ===============================
    // 管理器实例
    // ===============================
    
    // 广播接收器管理器
    lateinit var amapBroadcastManager: AmapBroadcastManager
    // 位置和传感器管理器
    lateinit var locationSensorManager: LocationSensorManager
    // 权限管理器
    lateinit var permissionManager: PermissionManager
    // 网络管理器
    lateinit var networkManager: NetworkManager
    // ZMQ客户端
    val zmqClient = ZmqClient()
    // 数据字段管理器
    val dataFieldManager = DataFieldManager()
    
    // 高德地图相关管理器（已整合到AmapBroadcastHandlers中）
    // 设备管理器
    lateinit var deviceManager: DeviceManager
    
    // 小鸽数据接收器
    lateinit var xiaogeDataReceiver: XiaogeDataReceiver
    val xiaogeData = mutableStateOf<XiaogeVehicleData?>(null)
    
    // 自动超车管理器
    lateinit var autoOvertakeManager: AutoOvertakeManager
    
    // 内存监控定时器
    var memoryMonitorTimer: java.util.Timer? = null
    
    // 协程作用域管理
    private val coreScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // ===============================
    // 权限处理
    // ===============================
    
    // Android 13+ 通知权限请求
    val notificationPermissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Log.i(TAG, "🔔 通知权限已授予")
        } else {
            Log.w(TAG, "🔔 通知权限被拒绝")
        }
    }

    // ===============================
    // 控制指令广播接收器
    // ===============================
    
    val carrotCommandReceiver = object : android.content.BroadcastReceiver() {
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
                "com.example.cplink.CHANGE_AUTO_TURN_CONTROL" -> {
                    val mode = intent.getIntExtra("mode", 2)
                    val modeNames = arrayOf("禁用控制", "自动变道", "控速变道", "导航限速")
                    
                    Log.i(TAG, "🔄 收到自动转向控制模式切换广播: ${modeNames[mode]} (AutoTurnControl=$mode)")
                    
                    // 通过NetworkManager发送自动转向控制模式切换到设备
                    if (::networkManager.isInitialized) {
                        CoroutineScope(Dispatchers.Main).launch {
                            try {
                                val result = networkManager.sendAutoTurnControlChangeToComma3(mode)
                                if (result.isSuccess) {
                                    Log.i(TAG, "✅ 自动转向控制模式切换成功: ${modeNames[mode]}")
                                } else {
                                    Log.e(TAG, "❌ 自动转向控制模式切换失败: ${result.exceptionOrNull()?.message}")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ 自动转向控制模式切换异常: ${e.message}", e)
                            }
                        }
                    } else {
                        Log.w(TAG, "⚠️ NetworkManager未初始化，无法切换自动转向控制模式")
                    }
                }
            }
        }
    }

    // ===============================
    // 权限管理方法
    // ===============================
    
    /**
     * 请求忽略电池优化，防止app被系统杀死
     */
    fun requestIgnoreBatteryOptimizations() {
        try {
            val powerManager = activity.getSystemService(PowerManager::class.java)
            val packageName = activity.packageName

            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                Log.i(TAG, "🔋 请求忽略电池优化")
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                activity.startActivity(intent)
            } else {
                Log.i(TAG, "🔋 已忽略电池优化")
            }
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ 请求电池优化权限失败: ${e.message}")
        }
    }

    /**
     * Android 13+ 请求通知权限，确保前台服务通知正常显示
     */
    fun requestNotificationPermissionIfNeeded() {
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                val granted = activity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    Log.i(TAG, "🔔 请求通知权限 (Android 13+)")
                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    Log.i(TAG, "🔔 已有通知权限")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ 请求通知权限失败: ${e.message}")
        }
    }

    // ===============================
    // 服务管理方法
    // ===============================
    
    /**
     * 启动前台服务
     */
    fun startForegroundService() {
        try {
            Log.i(TAG, "🔔 启动前台服务...")
            
            val serviceIntent = Intent(activity, CarrotAmapForegroundService::class.java).apply {
                action = CarrotAmapForegroundService.ACTION_START_SERVICE
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activity.startForegroundService(serviceIntent)
            } else {
                activity.startService(serviceIntent)
            }
            
            Log.i(TAG, "✅ 前台服务启动成功")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 启动前台服务失败: ${e.message}", e)
        }
    }
    
    /**
     * 停止前台服务
     */
    fun stopForegroundService() {
        try {
            Log.i(TAG, "🛑 停止前台服务...")
            
            val serviceIntent = Intent(activity, CarrotAmapForegroundService::class.java).apply {
                action = CarrotAmapForegroundService.ACTION_STOP_SERVICE
            }
            
            activity.stopService(serviceIntent)
            
            Log.i(TAG, "✅ 前台服务停止成功")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 停止前台服务失败: ${e.message}", e)
        }
    }

    // ===============================
    // 广播接收器管理
    // ===============================
    
    /**
     * 注册控制指令广播接收器
     */
    fun registerCarrotCommandReceiver() {
        try {
            val filter = android.content.IntentFilter().apply {
                addAction("com.example.cplink.SEND_CARROT_COMMAND")
                addAction("com.example.cplink.CHANGE_SPEED_MODE")
                addAction("com.example.cplink.CHANGE_AUTO_TURN_CONTROL")
            }
            activity.registerReceiver(carrotCommandReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
            Log.i(TAG, "✅ 控制指令广播接收器已注册（包含模式切换）")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 注册控制指令广播接收器失败: ${e.message}", e)
        }
    }

    /**
     * 注销控制指令广播接收器
     */
    fun unregisterCarrotCommandReceiver() {
        try {
            activity.unregisterReceiver(carrotCommandReceiver)
            Log.i(TAG, "✅ 控制指令广播接收器已注销")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 注销控制指令广播接收器失败: ${e.message}", e)
        }
    }

    // ===============================
    // 用户类型和API管理
    // ===============================
    
    /**
     * 自动更新使用统计数据到API
     */
    suspend fun autoUpdateUsageStats(deviceId: String, usageStats: UsageStats) = withContext(Dispatchers.IO) {
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
            
            // 使用 HTTPS 协议访问域名
            val url = URL("https://app.mspa.shop/api/user/update")
            val connection = url.openConnection() as HttpURLConnection
            
            val requestBody = JSONObject().apply {
                put("device_id", deviceId)
                put("car_model", currentUserData.carModel)
                put("wechat_name", currentUserData.wechatName)
                put("sponsor_amount", currentUserData.sponsorAmount)
                put("user_type", currentUserData.userType)
                // 更新使用统计数据（转换为整数）
                put("usage_count", usageStats.usageCount)
                put("usage_duration", (usageStats.usageDuration / 60.0).toInt()) // 转换为小时（整数）
                put("total_distance", usageStats.totalDistance.toInt()) // 转换为整数公里
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
                        val updatedDuration = data.optInt("usage_duration", 0) // 改为整数
                        val updatedDistance = data.optInt("total_distance", 0) // 改为整数
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
            // 使用 HTTPS 协议访问域名
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
     * 获取用户类型 - 直接调用API，不使用缓存
     */
    suspend fun fetchUserType(deviceId: String): Int = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "👤 直接获取用户类型: $deviceId")
            
            // 使用 HTTPS 协议访问域名
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


    // ===============================
    // 命令发送方法
    // ===============================
    
    /**
     * 发送Carrot命令到设备
     */
    fun sendCarrotCommand(command: String, arg: String) {
        try {
            Log.i(TAG, "🎮 主页发送Carrot命令: $command $arg")
            
            // 检查NetworkManager是否已初始化
            if (::networkManager.isInitialized) {
                Log.d(TAG, "✅ NetworkManager已初始化，准备发送控制指令")
                networkManager.sendControlCommand(command, arg)
                Log.i(TAG, "✅ 指令已发送: $command $arg")
            } else {
                Log.w(TAG, "⚠️ NetworkManager未初始化，无法发送指令")
                Log.w(TAG, "⚠️ 请等待网络服务启动完成后再试")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 发送Carrot命令失败: ${e.message}", e)
        }
    }

    /**
     * 发送当前道路限速到comma3设备
     */
    fun sendCurrentRoadLimitSpeed() {
        try {
            // 🆕 从carrotManFields获取当前道路限速（与UI保持一致）
            val roadLimitSpeed = carrotManFields.value.nRoadLimitSpeed
            
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

    /**
     * 手动发送导航确认到comma3设备（"开地图"按钮功能）
     * 前提条件：active 为 true（OpenpPilot已激活）
     */
    fun sendNavigationConfirmationManually() {
        try {
            Log.i(TAG, "🗺️ 用户点击'开地图'按钮")
            
            // 检查NetworkManager是否已初始化
            if (!::networkManager.isInitialized) {
                Log.w(TAG, "⚠️ NetworkManager未初始化，无法发送导航确认")
                return
            }
            
            // 检查 active 状态
            val isActive = carrotManFields.value.active
            if (!isActive) {
                Log.w(TAG, "⚠️ OpenpPilot未激活（active=false），无法发送导航确认")
                return
            }
            
            // 获取目的地信息
            val goalName = carrotManFields.value.szGoalName.ifEmpty { "目的地" }
            val goalLat = carrotManFields.value.goalPosY
            val goalLon = carrotManFields.value.goalPosX
            
            // 检查坐标有效性
            if (goalLat == 0.0 || goalLon == 0.0) {
                Log.w(TAG, "⚠️ 无有效坐标信息: lat=$goalLat, lon=$goalLon")
                return
            }
            
            Log.i(TAG, "📍 准备发送导航确认: name=$goalName, lat=$goalLat, lon=$goalLon")
            
            // 在后台协程中发送
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    val result = networkManager.sendNavigationConfirmationToComma3(goalName, goalLat, goalLon)
                    if (result.isSuccess) {
                        Log.i(TAG, "✅ 导航确认发送成功")
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            android.widget.Toast.makeText(
                                activity,
                                "✅ 导航确认已发送",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        Log.e(TAG, "❌ 导航确认发送失败: ${result.exceptionOrNull()?.message}")
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            android.widget.Toast.makeText(
                                activity,
                                "❌ 导航确认发送失败",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 导航确认发送异常: ${e.message}", e)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            activity,
                            "❌ 发送失败: ${e.message}",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 发送导航确认失败: ${e.message}", e)
        }
    }

    // ===============================
    // 高德地图相关方法
    // ===============================
    
    /**
     * 启动高德地图车机版
     */
    fun launchAmapAuto() {
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

            activity.startActivity(launchIntent)
            Log.i(TAG, "已启动高德地图车机版")

            // 更新UI状态
            amapBroadcastManager.receiverStatus.value = "已启动高德地图车机版"

        } catch (e: Exception) {
            Log.e(TAG, "启动高德地图失败: ${e.message}", e)
            amapBroadcastManager.receiverStatus.value = "启动高德地图失败: ${e.message}"

            // 尝试使用隐式Intent启动
            try {
                val intent = activity.packageManager.getLaunchIntentForPackage("com.autonavi.amapauto")
                if (intent != null) {
                    activity.startActivity(intent)
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
     */
    fun sendHomeNavigationToAmap() {
        try {
            Log.i(TAG, "🏠 发送一键回家指令给高德地图")

            val homeIntent = Intent("AUTONAVI_STANDARD_BROADCAST_RECV").apply {
                putExtra("KEY_TYPE", 10040)
                putExtra("SOURCE_APP", "CPlink")
                putExtra("DEST", 0) // 0: 回家；1: 回公司
                putExtra("IS_START_NAVI", 0) // 0: 是直接开始导航；1: 否
                setPackage("com.autonavi.amapauto")
                flags = Intent.FLAG_INCLUDE_STOPPED_PACKAGES
            }

            activity.sendBroadcast(homeIntent)
            Log.i(TAG, "✅ 一键回家导航广播已发送 (KEY_TYPE: 10040, DEST: 0)")

        } catch (e: Exception) {
            Log.e(TAG, "❌ 发送一键回家指令失败: ${e.message}", e)
        }
    }

    /**
     * 发送导航到公司指令给高德地图
     */
    fun sendCompanyNavigationToAmap() {
        try {
            Log.i(TAG, "🏢 发送导航到公司指令给高德地图")

            val companyIntent = Intent("AUTONAVI_STANDARD_BROADCAST_RECV").apply {
                putExtra("KEY_TYPE", 10040)
                putExtra("SOURCE_APP", "CPlink")
                putExtra("DEST", 1) // 0: 回家；1: 回公司
                putExtra("IS_START_NAVI", 0) // 0: 是直接开始导航；1: 否
                setPackage("com.autonavi.amapauto")
                flags = Intent.FLAG_INCLUDE_STOPPED_PACKAGES
            }

            activity.sendBroadcast(companyIntent)
            Log.i(TAG, "✅ 导航到公司广播已发送 (KEY_TYPE: 10040, DEST: 1)")

        } catch (e: Exception) {
            Log.e(TAG, "❌ 发送导航到公司指令失败: ${e.message}", e)
        }
    }

    // ===============================
    // 用户类型处理
    // ===============================
    
    /**
     * 根据用户类型执行不同操作
     */
    fun handleUserTypeAction(userType: Int) {
        Log.i(TAG, "🎯 根据用户类型执行操作: $userType")
        
        when (userType) {
            -1 -> {
                // 管理员专用 - 强制退出应用
                Log.i(TAG, "🔧 管理员用户，强制退出应用")
                forceExitApp()
            }
            0 -> {
                // 未知用户 - 跳转到我的界面，50秒后强制退出
                Log.i(TAG, "👤 未知用户，跳转到我的界面，50秒后强制退出")
                currentPage = 2
                // 启动50秒倒计时，然后强制退出
                startUserType0Countdown()
            }
            1 -> {
                // 新用户 - 跳转到帮助界面
                Log.i(TAG, "🆕 新用户，跳转到帮助界面")
                currentPage = 2
            }
            2 -> {
                // 支持者 - 显示功能说明弹窗（由UI层控制显示）
                Log.i(TAG, "💚 支持者，初始化完成")
            }
            3, 4 -> {
                // 赞助者/铁粉 - 不再自动启动高德地图，改为手动启动（九宫格9号按钮）
                Log.i(TAG, "💎 赞助者/铁粉，初始化完成（不自动启动高德地图）")
                // launchAmapAuto() // 已注释：改为手动启动（九宫格9号按钮）
            }
            else -> {
                // 其他情况 - 默认跳转到我的界面
                Log.w(TAG, "⚠️ 未知用户类型: $userType，跳转到我的界面")
                currentPage =2
            }
        }
    }

    /**
     * 启动用户类型0的倒计时（50秒后强制退出）
     */
    private fun startUserType0Countdown() {
        try {
            Log.i(TAG, "⏱️ 启动用户类型0倒计时：50秒后强制退出")
            
            // 在协程作用域中启动倒计时
            coreScope.launch {
                // 等待50秒
                delay(50000)
                
                // 显示提示信息
                Log.i(TAG, "⏰ 倒计时结束，显示提示并强制退出")
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        activity,
                        "Apps 需要重新启动来更新用户身份",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    
                    // 延迟2秒后强制退出，确保用户能看到提示
                    delay(2000)
                    
                    Log.i(TAG, "✅ 应用即将退出（用户类型0）")
                    activity.finishAffinity()
                    System.exit(0)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 启动倒计时失败: ${e.message}", e)
            // 如果倒计时启动失败，直接强制退出
            forceExitApp()
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
                activity.finishAffinity()
                System.exit(0)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 强制退出失败: ${e.message}", e)
            // 即使出错也强制退出
            activity.finishAffinity()
            System.exit(0)
        }
    }


    // ===============================
    // 内存管理
    // ===============================
    
    /**
     * 启动内存监控 - 优化版：减少监控频率
     */
    fun startMemoryMonitoring() {
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
    fun stopMemoryMonitoring() {
        memoryMonitorTimer?.cancel()
        memoryMonitorTimer = null
        Log.i(TAG, "📊 内存监控已停止")
    }
    
    /**
     * 清理协程作用域
     */
    fun cleanupCoroutineScope() {
        try {
            coreScope.cancel()
            Log.i(TAG, "🧹 协程作用域已清理")
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ 清理协程作用域失败: ${e.message}")
        }
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
    // 辅助方法
    // ===============================
    
    /**
     * 更新UI消息
     */
    fun updateUIMessage(message: String) {
        Log.i(TAG, "📱 UI更新: $message")
        // 这里可以添加实际的UI更新逻辑，比如显示Toast或更新状态栏
    }

    /**
     * 处理从静态接收器启动的Intent
     */
    fun handleIntentFromStaticReceiver(intent: Intent?) {
        if (::amapBroadcastManager.isInitialized) {
            amapBroadcastManager.handleIntentFromStaticReceiver(intent)
        } else {
            Log.w(TAG, "⚠️ 广播管理器未初始化，无法处理静态接收器Intent")
        }
    }
}
