package com.example.carrotamap

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject

/**
 * 高德地图广播管理器
 * 负责处理高德地图广播接收、数据解析和CarrotMan字段映射
 */
class AmapBroadcastManager(
    private val context: Context,
    private val carrotManFields: MutableState<CarrotManFields>,
    private val networkManager: NetworkManager? = null
) {
    companion object {
        private const val TAG = "AmapBroadcastManager"
    }

    // 广播数据存储
    val broadcastDataList = mutableStateListOf<BroadcastData>()
    val receiverStatus = mutableStateOf("等待广播数据...")
    val totalBroadcastCount = mutableIntStateOf(0)
    val lastUpdateTime = mutableLongStateOf(0L)

    // 协程作用域
    private val receiverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 广播处理器 (传入Context用于地图切换)
    private val amapDataProcessor = AmapDataProcessor(carrotManFields)
    private val broadcastHandlers = AmapBroadcastHandlers(carrotManFields, networkManager, context, amapDataProcessor)

    // 智能数据变化检测
    private var lastSpeedLimit: Int? = null
    private var lastRoadName: String? = null
    private var lastSpeedLimitSendTime: Long = 0L
    private val speedLimitSendInterval = 2000L

    // 限速信息数据类
    private data class SpeedLimitInfo(
        val speedLimit: Int,
        val roadName: String,
        val sendTime: Long
    )

    // 增强版高德地图广播接收器
    private val enhancedAmapReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (context == null || intent == null) return
            
            try {
                val action = intent.action
                //Log.i(TAG, "📡 收到广播: $action")

                // 记录广播的基本信息（对简要类型抑制详细行）
                val keyType = intent.getIntExtra("KEY_TYPE", -1)
                val extraState = intent.getIntExtra("EXTRA_STATE", -1)
                val isBriefType = when (keyType) {
                    AppConstants.AmapBroadcast.Navigation.GUIDE_INFO,           // 10001
                    AppConstants.AmapBroadcast.MapLocation.UNKNOWN_INFO_13011,  // 13011
                    AppConstants.AmapBroadcast.MapLocation.GEOLOCATION_INFO,    // 12205
                    AppConstants.AmapBroadcast.Navigation.TURN_INFO,            // 10016
                    AppConstants.AmapBroadcast.Navigation.MAP_STATE,            // 10019
                    AppConstants.AmapBroadcast.MapLocation.TRAFFIC_LIGHT        // 60073
                    -> true
                    else -> false
                }
                if (!isBriefType) {
                    Log.d(TAG, "📦 广播详情: action=$action, KEY_TYPE=$keyType, EXTRA_STATE=$extraState")
                }

                when (action) {
                    AppConstants.AmapBroadcast.ACTION_AMAP_SEND,
                    AppConstants.AmapBroadcast.ACTION_AMAP_LEGACY,
                    AppConstants.AmapBroadcast.ACTION_AUTONAVI -> {
                        //Log.i(TAG, "🎯 处理高德地图标准广播") //手动注释
                        handleAmapSendBroadcast(intent)
                    }
                    AppConstants.AmapBroadcast.ACTION_AMAP_RECV -> {
                        Log.v(TAG, "收到发送给高德的广播数据")
                        logAllExtras(intent)
                    }
                    "AMAP_NAVI_ACTION_UPDATE", "AMAP_NAVI_ACTION_TURN",
                    "AMAP_NAVI_ACTION_ROUTE", "AMAP_NAVI_ACTION_LOCATION" -> {
                        Log.i(TAG, "🎯 处理高德地图导航广播")
                        handleAlternativeAmapBroadcast(intent)
                    }
                    else -> {
                        Log.w(TAG, "❓ 未知广播action: $action")
                        logAllExtras(intent)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "处理广播数据失败: ${e.message}", e)
            }
        }
    }

    /**
     * 创建Intent过滤器
     */
    private fun createIntentFilter(): IntentFilter {
        return IntentFilter().apply {
            // 高德地图标准广播
            addAction(AppConstants.AmapBroadcast.ACTION_AMAP_SEND)
            addAction(AppConstants.AmapBroadcast.ACTION_AMAP_RECV)
            addAction(AppConstants.AmapBroadcast.ACTION_AMAP_LEGACY)
            addAction(AppConstants.AmapBroadcast.ACTION_AUTONAVI)
            
            // 高德地图导航广播
            addAction("AMAP_NAVI_ACTION_UPDATE")
            addAction("AMAP_NAVI_ACTION_TURN")
            addAction("AMAP_NAVI_ACTION_ROUTE")
            addAction("AMAP_NAVI_ACTION_LOCATION")
            
            // 其他可能的广播
            addAction("com.autonavi.minimap.broadcast")
            addAction("com.autonavi.minimap.navigation.broadcast")
        }
    }

    /**
     * 注册广播接收器
     */
    fun registerReceiver(): Boolean {
        val intentFilter = createIntentFilter()
        return try {
            ContextCompat.registerReceiver(
                context,
                enhancedAmapReceiver,
                intentFilter,
                ContextCompat.RECEIVER_EXPORTED
            )
            Log.i(TAG, "✅ 增强版广播接收器注册成功")
            Log.d(TAG, "📡 注册的广播Action列表:")
            intentFilter.actionsIterator().forEach { action ->
                Log.d(TAG, "  - $action")
            }
            receiverStatus.value = "增强版接收器已启动，等待广播数据..."
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ 广播接收器注册失败: ${e.message}", e)
            receiverStatus.value = "接收器注册失败: ${e.message}"
            false
        }
    }

    /**
     * 取消注册广播接收器
     */
    fun unregisterReceiver() {
        try {
            context.unregisterReceiver(enhancedAmapReceiver)
            receiverScope.cancel()
            Log.i(TAG, "✅ 广播接收器已注销")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 广播接收器注销失败: ${e.message}", e)
        }
    }

    /**
     * 清空广播数据
     */
    fun clearBroadcastData() {
        broadcastDataList.clear()
        totalBroadcastCount.intValue = 0
        receiverStatus.value = "数据已清空，等待新的广播..."
        Log.i(TAG, "🗑️ 广播数据已清空")
    }


    /**
     * 处理来自静态接收器的Intent
     */
    fun handleIntentFromStaticReceiver(intent: Intent?) {
        intent?.let {
            if (it.action == AppConstants.AmapBroadcast.ACTION_AMAP_SEND) {
                Log.i(TAG, "📨 从静态接收器启动，处理Intent数据")
                handleAmapSendBroadcast(it)
            }
        }
    }

    /**
     * 🎯 处理高德地图发送的广播数据 - 核心方法
     */
    private fun handleAmapSendBroadcast(intent: Intent) {
        val keyType = intent.getIntExtra("KEY_TYPE", -1)
        
        // 🎯 根据KEY_TYPE决定日志输出级别
        val isBriefLog = when (keyType) {
            AppConstants.AmapBroadcast.Navigation.GUIDE_INFO, // 10001
            AppConstants.AmapBroadcast.MapLocation.UNKNOWN_INFO_13011, // 13011
            AppConstants.AmapBroadcast.MapLocation.GEOLOCATION_INFO, // 12205
            AppConstants.AmapBroadcast.Navigation.MAP_STATE -> true // 10019
            else -> (keyType == 10016 || keyType == 10019)
        }
        if (isBriefLog) {
            //Log.d(TAG, "📝 处理广播 (简要) KEY_TYPE=$keyType") //零时注释
        } else {
            // 其他KEY_TYPE - 输出详细广播数据
            Log.d(TAG, "🔍 开始处理高德地图广播数据 (KEY_TYPE: $keyType):")
            logAllExtras(intent)
        }

        try {
            // 🔧 解析基础广播数据
            val broadcastData = parseBroadcastData(intent)

            // 🚀 异步处理数据更新，避免阻塞UI
            receiverScope.launch {
                // 通知UI更新
                updateBroadcastData(broadcastData)

                // 根据具体类型处理数据
                when (keyType) {
                    AppConstants.AmapBroadcast.Navigation.MAP_STATE -> handleMapState(intent)
                    AppConstants.AmapBroadcast.Navigation.GUIDE_INFO -> handleGuideInfo(intent)
                    AppConstants.AmapBroadcast.Navigation.LOCATION_INFO -> handleLocationInfo(intent)
                    AppConstants.AmapBroadcast.Navigation.TURN_INFO -> handleTurnInfo(intent)
                    AppConstants.AmapBroadcast.Navigation.NAVIGATION_STATUS -> handleNavigationStatus(intent)
                    AppConstants.AmapBroadcast.Navigation.ROUTE_INFO -> handleRouteInfo(intent)
                    // 🎯 临时注释：只使用引导信息广播(KEY_TYPE: 10001)的限速数据
                    // AppConstants.AmapBroadcast.SpeedCamera.SPEED_LIMIT -> handleSpeedLimit(intent)
                    // 新增：区间测速(12110) 专用处理
                    AppConstants.AmapBroadcast.SpeedCamera.SPEED_LIMIT -> handleSpeedLimitInterval(intent)
                    // 13005 与 10007 解析与映射已移除：仅跳过
                    AppConstants.AmapBroadcast.SpeedCamera.CAMERA_INFO -> {
                        Log.d(TAG, "🧹 忽略电子眼(13005)映射：已按要求移除")
                    }
                    AppConstants.AmapBroadcast.SpeedCamera.SDI_PLUS_INFO -> {
                        Log.d(TAG, "🧹 忽略SDI Plus(10007)映射：已按要求移除")
                    }
                    AppConstants.AmapBroadcast.MapLocation.TRAFFIC_INFO -> handleTrafficInfo(intent)
                    AppConstants.AmapBroadcast.MapLocation.NAVI_SITUATION -> handleNaviSituation(intent)
                    AppConstants.AmapBroadcast.MapLocation.TRAFFIC_LIGHT -> handleTrafficLightInfo(intent)
                    AppConstants.AmapBroadcast.MapLocation.GEOLOCATION_INFO -> handleGeolocationInfo(intent)
                    AppConstants.AmapBroadcast.LaneInfo.DRIVE_WAY_INFO -> handleDriveWayInfo(intent)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "处理KEY_TYPE $keyType 失败: ${e.message}", e)
        }
    }

    /**
     * 🔧 记录所有Intent额外数据（调试用）
     */
    private fun logAllExtras(intent: Intent) {
        val extras = intent.extras
        if (extras != null) {
            Log.d(TAG, "📋 Intent包含的所有数据:")
            for (key in extras.keySet()) {
                val value: String = try {
                    // 使用更安全的方式获取值，避免类型转换错误
                    @Suppress("DEPRECATION")
                    val obj = extras.get(key)
                    when (obj) {
                        is String -> obj
                        is Int -> obj.toString()
                        is Long -> obj.toString()
                        is Double -> obj.toString()
                        is Float -> obj.toString()
                        is Boolean -> obj.toString()
                        is Byte -> obj.toString()
                        is Short -> obj.toString()
                        is Char -> obj.toString()
                        null -> "null"
                        else -> "未知类型: ${obj.javaClass.simpleName} = $obj"
                    }
                } catch (e: Exception) {
                    "获取失败: ${e.message}"
                }
                Log.d(TAG, "   📌 $key = $value")
            }
        } else {
            Log.d(TAG, "📋 Intent中没有额外数据")
        }
    }

    // 处理其他格式的高德地图广播
    private fun handleAlternativeAmapBroadcast(intent: Intent) {
        Log.i(TAG, "🔄 处理其他格式高德广播: ${intent.action}")
        logAllExtras(intent)
        extractBasicNavigationInfo(intent)
    }

    // 从未识别的广播中提取基础导航信息
    private fun extractBasicNavigationInfo(intent: Intent) {
        Log.d(TAG, "🔍 尝试从未识别广播中提取基础导航信息...")
        // 提取常见的导航相关字段
        intent.extras?.let { bundle ->
            var hasUpdate = false

            // 提取位置信息
            val lat = bundle.getDouble("latitude", 0.0).takeIf { it != 0.0 }
                ?: bundle.getDouble("lat", 0.0)
            val lon = bundle.getDouble("longitude", 0.0).takeIf { it != 0.0 }
                ?: bundle.getDouble("lon", 0.0)

            if (lat != 0.0 && lon != 0.0) {
                carrotManFields.value = carrotManFields.value.copy(
                    vpPosPointLat = lat,
                    vpPosPointLon = lon,
                    // 协议标准位置字段同步
                    xPosLat = lat,
                    xPosLon = lon,
                    lastUpdateTime = System.currentTimeMillis()
                )
                hasUpdate = true
                Log.i(TAG, "✅ 提取到位置信息: lat=$lat, lon=$lon")
            }

            // 提取速度信息
            val speed = bundle.getDouble("speed", 0.0).takeIf { it > 0.0 }
                ?: bundle.getFloat("speed", 0.0f).toDouble().takeIf { it > 0.0 }

            if (speed != null && speed > 0.0) {
                carrotManFields.value = carrotManFields.value.copy(
                    nPosSpeed = speed,
                    lastUpdateTime = System.currentTimeMillis()
                )
                hasUpdate = true
                Log.i(TAG, "✅ 提取到速度信息: speed=${speed}km/h")
            }

            if (hasUpdate) {
                Log.i(TAG, "🔄 从未识别广播中成功提取并更新了导航信息")
            } else {
                Log.d(TAG, "ℹ️ 未从广播中找到可用的导航信息")
            }
        }
    }

    // 解析广播数据的基础方法
    private fun parseBroadcastData(intent: Intent): BroadcastData {
        val keyType = intent.getIntExtra("KEY_TYPE", -1)
        val timestamp = System.currentTimeMillis()

        // 提取所有额外数据
        val rawExtras = mutableMapOf<String, String>()
        intent.extras?.let { bundle ->
            for (key in bundle.keySet()) {
                val value = try {
                    // 使用最安全的方法：直接获取原始值并判断类型
                    @Suppress("DEPRECATION")
                    val rawValue = bundle.get(key)
                    when (rawValue) {
                        is String -> rawValue
                        is Int -> rawValue.toString()
                        is Long -> rawValue.toString()
                        is Double -> rawValue.toString()
                        is Float -> rawValue.toString()
                        is Boolean -> rawValue.toString()
                        is Short -> rawValue.toString()
                        is Byte -> rawValue.toString()
                        is Char -> rawValue.toString()
                        is ByteArray -> "ByteArray[${rawValue.size}]"
                        is IntArray -> "IntArray[${rawValue.size}]"
                        is LongArray -> "LongArray[${rawValue.size}]"
                        is DoubleArray -> "DoubleArray[${rawValue.size}]"
                        is FloatArray -> "FloatArray[${rawValue.size}]"
                        is BooleanArray -> "BooleanArray[${rawValue.size}]"
                        is Array<*> -> "Array[${rawValue.size}]"
                        null -> "null"
                        else -> rawValue.toString()
                    }
                } catch (e: Exception) {
                    "获取失败: ${e.message}"
                }
                rawExtras[key] = value
            }
        }

        return BroadcastData(
            keyType = keyType,
            dataType = getDataTypeDescription(keyType),
            timestamp = timestamp,
            rawExtras = rawExtras,
            parsedContent = "解析中..."
        )
    }

    // 更新广播数据到UI
    fun updateBroadcastData(broadcastData: BroadcastData) {
        try {
            broadcastDataList.add(0, broadcastData) // 添加到列表顶部
            totalBroadcastCount.intValue++
            lastUpdateTime.longValue = broadcastData.timestamp

            // 限制列表大小，避免内存溢出
            if (broadcastDataList.size > 100) {
                // 安全地移除多余的元素，保留前50个
                val currentSize = broadcastDataList.size
                val removeCount = currentSize - 50
                if (removeCount > 0 && removeCount <= currentSize) {
                    // 从末尾开始移除，避免索引问题
                    repeat(removeCount) {
                        if (broadcastDataList.size > 50) {
                            broadcastDataList.removeAt(broadcastDataList.size - 1)
                        }
                    }
                }
                Log.d(TAG, "📊 列表大小控制: $currentSize -> ${broadcastDataList.size}")
            }

            receiverStatus.value = "已接收 ${totalBroadcastCount.intValue} 条广播数据"

        } catch (e: Exception) {
            Log.e(TAG, "更新广播数据失败: ${e.message}", e)
            // 发生异常时，尝试清理列表
            if (broadcastDataList.size > 200) {
                broadcastDataList.clear()
                Log.w(TAG, "列表异常，已清空重置")
            }
        }
    }

    // 获取数据类型描述
    private fun getDataTypeDescription(keyType: Int): String {
        return when (keyType) {
            AppConstants.AmapBroadcast.Navigation.MAP_STATE -> "地图状态"
            AppConstants.AmapBroadcast.Navigation.GUIDE_INFO -> "引导信息"
            AppConstants.AmapBroadcast.Navigation.LOCATION_INFO -> "定位信息"
            AppConstants.AmapBroadcast.Navigation.TURN_INFO -> "转向信息"
            AppConstants.AmapBroadcast.Navigation.NAVIGATION_STATUS -> "导航状态"
            AppConstants.AmapBroadcast.Navigation.ROUTE_INFO -> "路线信息"
            AppConstants.AmapBroadcast.SpeedCamera.SPEED_LIMIT -> "限速信息"
            AppConstants.AmapBroadcast.SpeedCamera.CAMERA_INFO -> "电子眼信息"
            AppConstants.AmapBroadcast.SpeedCamera.CAMERA_INFO_V2 -> "电子眼信息V2"
            AppConstants.AmapBroadcast.MapLocation.FAVORITE_RESULT -> "收藏点结果"
            AppConstants.AmapBroadcast.NavigationControl.HOME_COMPANY_NAVIGATION -> "家/公司导航"
            AppConstants.AmapBroadcast.MapLocation.ADMIN_AREA -> "行政区域"
            AppConstants.AmapBroadcast.MapLocation.NAVI_STATUS -> "导航状态变化"
            AppConstants.AmapBroadcast.MapLocation.TRAFFIC_INFO -> "路况信息"
            AppConstants.AmapBroadcast.MapLocation.NAVI_SITUATION -> "导航态势"
            AppConstants.AmapBroadcast.MapLocation.NEXT_INTERSECTION -> "下一路口"
            AppConstants.AmapBroadcast.SpeedCamera.SPEED_LIMIT_NEW -> "新版限速"
            AppConstants.AmapBroadcast.MapLocation.SAPA_INFO -> "服务区信息"
            AppConstants.AmapBroadcast.MapLocation.TRAFFIC_LIGHT -> "红绿灯信息"
            AppConstants.AmapBroadcast.SpeedCamera.SDI_PLUS_INFO -> "SDI Plus信息"
            AppConstants.AmapBroadcast.MapLocation.ROUTE_INFO_QUERY -> "路线信息查询"
            AppConstants.AmapBroadcast.LaneInfo.DRIVE_WAY_INFO -> "车道线信息"
            AppConstants.AmapBroadcast.NavigationControl.ROUTE_PLANNING -> "路线规划"
            AppConstants.AmapBroadcast.NavigationControl.START_NAVIGATION -> "开始导航"
            AppConstants.AmapBroadcast.NavigationControl.STOP_NAVIGATION -> "停止导航"
            else -> "未知类型($keyType)"
        }
    }

    // ===============================
    // 广播处理方法委托
    // ===============================
    private fun handleMapState(intent: Intent) = broadcastHandlers.handleMapState(intent)
    private fun handleGuideInfo(intent: Intent) = broadcastHandlers.handleGuideInfo(intent)
    private fun handleLocationInfo(intent: Intent) = broadcastHandlers.handleLocationInfo(intent)
    private fun handleTurnInfo(intent: Intent) = broadcastHandlers.handleTurnInfo(intent)
    private fun handleNavigationStatus(intent: Intent) = broadcastHandlers.handleNavigationStatus(intent)
    private fun handleRouteInfo(intent: Intent) = broadcastHandlers.handleRouteInfo(intent)
    // 🎯 临时注释：只使用引导信息广播(KEY_TYPE: 10001)的限速数据
    // private fun handleSpeedLimit(intent: Intent) = broadcastHandlers.handleSpeedLimit(intent)
    private fun handleCameraInfo(intent: Intent) = broadcastHandlers.handleCameraInfo(intent)
    private fun handleSdiPlusInfo(intent: Intent) = broadcastHandlers.handleSdiPlusInfo(intent)
    private fun handleSpeedLimitInterval(intent: Intent) = broadcastHandlers.handleSpeedLimitInterval(intent)
    private fun handleTrafficInfo(intent: Intent) = broadcastHandlers.handleTrafficInfo(intent)
    private fun handleNaviSituation(intent: Intent) = broadcastHandlers.handleNaviSituation(intent)
    private fun handleTrafficLightInfo(intent: Intent) = broadcastHandlers.handleTrafficLightInfo(intent)
    private fun handleGeolocationInfo(intent: Intent) = broadcastHandlers.handleGeolocationInfo(intent)
    private fun handleDriveWayInfo(intent: Intent) = broadcastHandlers.handleDriveWayInfo(intent)
}
