package com.example.carrotamap
import android.content.Context
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * 网络管理器
 * 负责处理所有网络相关的功能，包括CarrotMan网络客户端管理、设备发现、数据发送等
 */
class NetworkManager(
    private val context: Context,
    private val carrotManFields: MutableState<CarrotManFields>
) {
    companion object {
        private const val TAG = "NetworkManager"
    }

    // 网络客户端
    private lateinit var carrotNetworkClient: CarrotManNetworkClient
    
    // 网络状态
    private val networkConnectionStatus = mutableStateOf("未连接")
    private val discoveredDevicesList = mutableStateListOf<CarrotManNetworkClient.DeviceInfo>()
    private val networkStatistics = mutableStateOf(mapOf<String, Any>())
    private val autoSendEnabled = mutableStateOf(true)
    private var lastDataSendTime = 0L
    private val dataSendInterval = 200L

    // OpenpPilot状态数据
    private val openpilotStatusData = mutableStateOf(OpenpilotStatusData())
    
    // 自动发送状态跟踪 - 避免重复发送
    private var lastAutoSendState = false

    // 导航确认服务已移除

    /**
     * 初始化网络客户端
     */
    fun initializeNetworkClient(): Boolean {
        Log.i(TAG, "🌐 初始化CarrotMan网络客户端...")
        
        return try {
            carrotNetworkClient = CarrotManNetworkClient(context)

            carrotNetworkClient.setOnDeviceDiscovered { device ->
                CoroutineScope(Dispatchers.Main).launch {
                    discoveredDevicesList.add(device)
                    Log.i(TAG, "🎯 发现Comma3设备: $device")
                }
            }
            
            carrotNetworkClient.setOnConnectionStatusChanged { connected, message ->
                CoroutineScope(Dispatchers.Main).launch {
                    networkConnectionStatus.value = if (connected) "✅ $message" else "❌ $message"
                    //Log.i(TAG, "🌐 网络状态变化: $message") //手动注释
                }
            }
            
            carrotNetworkClient.setOnDataSent { packetCount ->
                CoroutineScope(Dispatchers.Main).launch {
                    networkStatistics.value = carrotNetworkClient.getConnectionStatus()
                }
            }

            carrotNetworkClient.setOnOpenpilotStatusReceived { jsonData ->
                CoroutineScope(Dispatchers.Main).launch {
                    parseOpenpilotStatusData(jsonData)
                }
            }


            
            // 启动网络服务和自动数据发送
            carrotNetworkClient.start()
            carrotNetworkClient.startAutoDataSending(autoSendEnabled, carrotManFields)

            // 导航确认服务已移除

            Log.i(TAG, "✅ CarrotMan网络客户端初始化成功")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 网络客户端初始化失败: ${e.message}", e)
            networkConnectionStatus.value = "❌ 初始化失败: ${e.message}"
            false
        }
    }

    /**
     * 解析OpenpPilot状态JSON数据
     */
    private fun parseOpenpilotStatusData(jsonData: String) {
        try {
            Log.d(TAG, "🔍 开始解析OpenpPilot JSON数据: ${jsonData.take(200)}...")

            val jsonObject = JSONObject(jsonData)

            // 记录接收到的关键字段
            val vEgo = jsonObject.optInt("v_ego_kph", 0)
            val vCruise = jsonObject.optDouble("v_cruise_kph", 0.0).toFloat()
            val isActive = jsonObject.optBoolean("active", false)
            val isOnroad = jsonObject.optBoolean("IsOnroad", false)

            Log.d(TAG, "🚗 解析关键数据: 车速=${vEgo}km/h, 巡航=${vCruise}km/h, 激活=${isActive}, 在路上=${isOnroad}")

            // 详细记录巡航速度相关字段
            if (jsonObject.has("v_cruise_kph")) {
                Log.i(TAG, "✅ 发现v_cruise_kph字段: ${jsonObject.optDouble("v_cruise_kph", 0.0)}")
            } else {
                Log.w(TAG, "⚠️ 未发现v_cruise_kph字段，检查可能的替代字段...")
                // 检查可能的其他字段名
                val possibleFields = listOf("vCruiseKph", "cruise_speed", "v_cruise", "cruiseSpeed")
                possibleFields.forEach { field ->
                    if (jsonObject.has(field)) {
                        Log.i(TAG, "🔍 发现替代字段 $field: ${jsonObject.opt(field)}")
                    }
                }
            }

            // 解析新的carcruiseSpeed字段（兼容旧版本）
            val carcruiseSpeed = jsonObject.optDouble("carcruiseSpeed", 0.0).toFloat()
            if (jsonObject.has("carcruiseSpeed")) {
                Log.i(TAG, "✅ 发现carcruiseSpeed字段: ${carcruiseSpeed}km/h")
            } else {
                Log.d(TAG, "ℹ️ 未发现carcruiseSpeed字段，使用默认值0.0（兼容旧版本）")
            }

            val statusData = OpenpilotStatusData(
                carrot2 = jsonObject.optString("Carrot2", ""),
                isOnroad = isOnroad,
                carrotRouteActive = jsonObject.optBoolean("CarrotRouteActive", false),
                ip = jsonObject.optString("ip", ""),
                port = jsonObject.optInt("port", 0),
                logCarrot = jsonObject.optString("log_carrot", ""),
                vCruiseKph = jsonObject.optDouble("v_cruise_kph", 0.0).toFloat(),
                vEgoKph = vEgo,
                tbtDist = jsonObject.optInt("tbt_dist", 0),
                sdiDist = jsonObject.optInt("sdi_dist", 0),
                active = isActive,
                xState = jsonObject.optInt("xState", 0),
                trafficState = jsonObject.optInt("trafficState", 0),
                carcruiseSpeed = carcruiseSpeed, // 新增字段
                lastUpdateTime = System.currentTimeMillis()
            )

            val oldData = openpilotStatusData.value
            openpilotStatusData.value = statusData

            Log.i(TAG, "✅ OpenpPilot状态已更新: 车速=${statusData.vEgoKph}km/h, 激活=${statusData.active}, 在路上=${statusData.isOnroad}")

            // 如果是重要状态变化，记录详细日志
            if (oldData.vEgoKph != statusData.vEgoKph || oldData.active != statusData.active) {
                Log.i(TAG, "🔄 状态变化: 车速 ${oldData.vEgoKph} -> ${statusData.vEgoKph}, 激活 ${oldData.active} -> ${statusData.active}")
            }
            
            // 自动发送逻辑：当CarrotRouteActive为False且active为true时自动发送
            checkAndAutoSendNavigationConfirmation(statusData)

        } catch (e: JSONException) {
            Log.e(TAG, "JSON解析失败: ${e.message}, 原始数据: $jsonData", e)
        } catch (e: Exception) {
            Log.e(TAG, "解析OpenpPilot状态数据失败: ${e.message}, 原始数据: $jsonData", e)
        }
    }



    /**
     * 检查并自动发送导航确认
     * 当CarrotRouteActive为False且active为true时自动发送
     */
    private fun checkAndAutoSendNavigationConfirmation(statusData: OpenpilotStatusData) {
        // 检查发送条件：CarrotRouteActive为False且active为true
        val shouldAutoSend = !statusData.carrotRouteActive && statusData.active
        
        // 如果状态发生变化且满足发送条件，则自动发送
        if (shouldAutoSend && !lastAutoSendState) {
            Log.i(TAG, "🚀 触发自动发送条件: CarrotRouteActive=${statusData.carrotRouteActive}, active=${statusData.active}")
            
            // 获取目的地信息
            val goalName = carrotManFields.value.szGoalName.ifEmpty { "目的地" }
            val goalLat = carrotManFields.value.goalPosY
            val goalLon = carrotManFields.value.goalPosX
            
            if (goalLat != 0.0 && goalLon != 0.0) {
                Log.i(TAG, "📍 自动发送导航确认: name=$goalName, lat=$goalLat, lon=$goalLon")
                
                // 在后台协程中发送
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val result = sendNavigationConfirmationToComma3(goalName, goalLat, goalLon)
                        if (result.isSuccess) {
                            Log.i(TAG, "✅ 自动发送导航确认成功")
                        } else {
                            Log.e(TAG, "❌ 自动发送导航确认失败: ${result.exceptionOrNull()?.message}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ 自动发送导航确认异常: ${e.message}", e)
                    }
                }
            } else {
                Log.w(TAG, "⚠️ 无有效坐标信息，跳过自动发送: lat=$goalLat, lon=$goalLon")
            }
        }
        
        // 更新上次发送状态
        lastAutoSendState = shouldAutoSend
    }

    /**
     * 映射xState枚举值到中文描述
     */
    fun mapXStateToDescription(xState: Int): String {
        return when (xState) {
            0 -> "跟车模式"      // lead
            1 -> "巡航模式"      // cruise
            2 -> "端到端巡航"    // e2eCruise
            3 -> "端到端停车"    // e2eStop
            4 -> "端到端准备"    // e2ePrepare
            5 -> "端到端已停"    // e2eStopped
            else -> "未知状态($xState)"
        }
    }

    /**
     * 获取交通状态描述
     */
    fun getTrafficStateDescription(trafficState: Int): String {
        return when (trafficState) {
            0 -> "无信号"
            1 -> "红灯"
            2 -> "绿灯"
            3 -> "左转"
            else -> "未知($trafficState)"
        }
    }

    /**
     * 格式化时间戳
     */
    fun formatTimestamp(timestamp: Long): String {
        if (timestamp == 0L) return "未设置"
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    /**
     * 获取OpenpPilot状态字段数据
     */
    fun getOpenpilotStatusFields(statusData: OpenpilotStatusData): List<Triple<String, String, String>> {
        return listOf(
            // 基础信息
            Triple("Carrot2", "版本信息", statusData.carrot2.ifEmpty { "未知" }),
            Triple("ip", "设备IP", statusData.ip.ifEmpty { "未连接" }),
            Triple("port", "通信端口", statusData.port.toString()),

            // 系统状态
            Triple("IsOnroad", "道路状态", if (statusData.isOnroad) "在路上" else "未上路"),
            Triple("active", "自动驾驶", if (statusData.active) "激活" else "未激活"),
            Triple("CarrotRouteActive", "导航状态", if (statusData.carrotRouteActive) "导航中" else "未导航"),
            Triple("log_carrot", "系统日志", statusData.logCarrot.ifEmpty { "无日志" }),

            // 速度信息
            Triple("v_ego_kph", "当前车速", "${statusData.vEgoKph} km/h"),
            Triple("v_cruise_kph", "巡航速度", "${statusData.vCruiseKph} km/h"),

            // 导航距离
            Triple("tbt_dist", "转弯距离", "${statusData.tbtDist} m"),
            Triple("sdi_dist", "限速距离", "${statusData.sdiDist} m"),

            // 控制状态
            Triple("xState", "纵向状态", mapXStateToDescription(statusData.xState)),
            Triple("trafficState", "交通状态", getTrafficStateDescription(statusData.trafficState)),

            // 时间信息
            Triple("lastUpdateTime", "更新时间", formatTimestamp(statusData.lastUpdateTime))
        )
    }

    /**
     * 获取网络连接状态
     */
    fun getNetworkConnectionStatus(): String = networkConnectionStatus.value

    /**
     * 获取发现的设备列表
     */
    fun getDiscoveredDevices(): List<CarrotManNetworkClient.DeviceInfo> = discoveredDevicesList.toList()

    /**
     * 获取网络统计信息
     */
    fun getNetworkStatistics(): Map<String, Any> = networkStatistics.value

    /**
     * 获取OpenpPilot状态数据
     */
    fun getOpenpilotStatusData(): OpenpilotStatusData = openpilotStatusData.value

    /**
     * 获取网络客户端实例
     */
    fun getNetworkClient(): CarrotManNetworkClient? {
        return if (::carrotNetworkClient.isInitialized) carrotNetworkClient else null
    }

    /**
     * 获取当前连接设备的IP地址
     */
    fun getCurrentDeviceIP(): String? {
        return if (::carrotNetworkClient.isInitialized) {
            carrotNetworkClient.getCurrentDevice()?.ip
        } else {
            null
        }
    }

    /**
     * 发送目的地信息到comma3设备
     */
    fun sendDestinationToComma3(longitude: Double, latitude: Double, name: String, address: String = "") {
        if (::carrotNetworkClient.isInitialized) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    carrotNetworkClient.sendDestinationUpdate(
                        goalPosX = longitude,   // 经度
                        goalPosY = latitude,    // 纬度
                        szGoalName = name,
                        goalAddress = address,
                        priority = "high"
                    )
                    // 注意：不再额外发送CarrotManData，避免重复发送和绕过许可证检查
                    Log.i(TAG, "🎯 目的地信息已发送到comma3: $name ($latitude, $longitude)")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 发送目的地信息到comma3失败: ${e.message}", e)
                }
            }
        } else {
            Log.w(TAG, "⚠️ 网络客户端未初始化，无法发送目的地信息")
        }
    }

    /**
     * 发送交通灯状态更新到comma3设备
     * 基于逆向工程文档的协议规范实现
     */
    fun sendTrafficLightUpdate(trafficState: Int, leftSec: Int) {
        if (::carrotNetworkClient.isInitialized) {
            try {
                carrotNetworkClient.sendTrafficLightUpdate(trafficState, leftSec)
                Log.i(TAG, "🚦 交通灯状态更新已发送: 状态=$trafficState, 倒计时=${leftSec}s")
            } catch (e: Exception) {
                Log.e(TAG, "❌ 发送交通灯状态更新失败: ${e.message}", e)
            }
        } else {
            Log.w(TAG, "⚠️ 网络客户端未初始化，无法发送交通灯状态")
        }
    }

    /**
     * 发送DETECT命令到comma3设备（只在前方120m内有红灯时发送）
     * 基于优化后的检测逻辑实现，使用真实GPS坐标
     */
    fun sendDetectCommand(trafficState: Int, leftSec: Int, distance: Int, gpsLat: Double = 0.0, gpsLon: Double = 0.0) {
        if (::carrotNetworkClient.isInitialized) {
            try {
                carrotNetworkClient.sendDetectCommand(trafficState, leftSec, distance, gpsLat, gpsLon)
                Log.i(TAG, "🔍 DETECT命令已发送: 状态=$trafficState, 倒计时=${leftSec}s, 距离=${distance}m, GPS=($gpsLat,$gpsLon)")
            } catch (e: Exception) {
                Log.e(TAG, "❌ 发送DETECT命令失败: ${e.message}", e)
            }
        } else {
            Log.w(TAG, "⚠️ 网络客户端未初始化，无法发送DETECT命令")
        }
    }

    /**
     * 发送设置配置到comma3设备
     * 通过HTTP POST请求发送到 http://设备IP:8082/store_toggle_values
     */
    suspend fun sendSettingsToComma3(): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val deviceIP = getCurrentDeviceIP()
                if (deviceIP == null) {
                    Log.w(TAG, "⚠️ 无法获取设备IP地址，无法发送设置")
                    return@withContext Result.failure(Exception("设备未连接"))
                }

                val url = "http://$deviceIP:8082/store_toggle_values"
                val settingsData = mapOf(
                    "AutoTurnControl" to "2",
                    "AutoTurnControlSpeedTurn" to "20",
                    "IsMetric" to "1",
                    "LanguageSetting" to "main_zh-CHS",
                    "SpeedFromPCM" to "0",
                    "ShowDebugUI" to "1"  // 齿轮图标点击时添加此字段
                )

                Log.i(TAG, "🔧 发送设置配置到comma3设备: $url")
                Log.d(TAG, "📋 设置数据: $settingsData")

                val result = sendHttpPostRequest(url, settingsData)
                Log.i(TAG, "✅ 设置配置发送成功")
                Result.success("设置配置发送成功")

            } catch (e: Exception) {
                Log.e(TAG, "❌ 发送设置配置失败: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    /**
     * 发送模式切换到comma3设备
     * 通过HTTP POST请求发送SpeedFromPCM参数
     */
    suspend fun sendModeChangeToComma3(speedFromPCM: Int): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val deviceIP = getCurrentDeviceIP()
                if (deviceIP == null) {
                    Log.w(TAG, "⚠️ 无法获取设备IP地址，无法发送模式切换")
                    return@withContext Result.failure(Exception("设备未连接"))
                }

                val url = "http://$deviceIP:8082/store_toggle_values"
                val modeData = mapOf(
                    "SpeedFromPCM" to speedFromPCM.toString(),
                    "ShowDateTime" to speedFromPCM.toString()  // ShowDateTime值跟随SpeedFromPCM变化
                )

                Log.i(TAG, "🔄 发送模式切换到comma3设备: $url")
                Log.d(TAG, "📋 模式数据: SpeedFromPCM=$speedFromPCM, ShowDateTime=$speedFromPCM")

                val result = sendHttpPostRequest(url, modeData)
                Log.i(TAG, "✅ 模式切换发送成功: SpeedFromPCM=$speedFromPCM")
                Result.success("模式切换成功")

            } catch (e: Exception) {
                Log.e(TAG, "❌ 发送模式切换失败: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    /**
     * 发送导航确认到comma3设备
     * 通过HTTP POST请求发送导航确认数据到 /nav_confirmation
     * 根据抓包分析，需要同时在URL和Body中发送参数
     */
    suspend fun sendNavigationConfirmationToComma3(
        goalName: String,
        goalLat: Double,
        goalLon: Double
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val deviceIP = getCurrentDeviceIP()
                if (deviceIP == null) {
                    Log.w(TAG, "⚠️ 无法获取设备IP地址，无法发送导航确认")
                    return@withContext Result.failure(Exception("设备未连接"))
                }

                // URL编码目的地名称
                val encodedName = java.net.URLEncoder.encode(goalName, "UTF-8")

                // 构建带参数的URL（根据抓包信息）
                val url = "http://$deviceIP:8082/nav_confirmation?addr=$encodedName&lon=$goalLon&lat=$goalLat"

                // Body参数（根据抓包信息）- name参数也需要URL编码
                val navData = mapOf(
                    "name" to encodedName,  // 使用URL编码后的名称
                    "lat" to goalLat.toString(),
                    "lon" to goalLon.toString(),
                    "save_type" to "recent"
                )

                Log.i(TAG, "🧭 发送导航确认到comma3设备: $url")
                Log.d(TAG, "📍 导航数据: name=$goalName, lat=$goalLat, lon=$goalLon, save_type=recent")

                val result = sendHttpPostFormRequest(url, navData)
                Log.i(TAG, "✅ 导航确认发送成功: $goalName")
                Result.success("导航确认成功")

            } catch (e: Exception) {
                Log.e(TAG, "❌ 发送导航确认失败: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    /**
     * 发送设备位置上报到Azure Logic App
     * 用于设备标识和位置追踪，包含车辆信息
     */
    suspend fun sendDeviceLocationReport(
        deviceId: String,
        latitude: Double,
        longitude: Double,
        manufacturer: String? = null,
        model: String? = null,
        fingerprint: String? = null
    ): Result<Int> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://defaulte3f0b629b0b043238be4e8c5116552.ba.environment.api.powerplatform.com:443/powerautomate/automations/direct/workflows/880b98dfa98148779fbc858897b417e6/triggers/manual/paths/invoke?api-version=1&sp=%2Ftriggers%2Fmanual%2Frun&sv=1.0&sig=XR057b-J1RRarSyF6_fwBVS_SZcXx6neMHZJk2R_OdQ"

                val reportData = mapOf(
                    "id" to deviceId,
                    "lat" to latitude.toString(),
                    "lon" to longitude.toString(),
                    "manufacturer" to (manufacturer ?: "null"),
                    "model" to (model ?: "null"),
                    "fingerprint" to (fingerprint ?: "null")
                )

                Log.i(TAG, "📡 发送设备位置上报到Azure: $url")
                Log.d(TAG, "📍 上报数据: id=$deviceId, lat=$latitude, lon=$longitude, manufacturer=$manufacturer, model=$model, fingerprint=$fingerprint")

                val result = sendHttpPostRequestJson(url, reportData)

                // 尝试解析返回的倒计时数值
                val countdownSeconds = try {
                    result.toIntOrNull() ?: 850 // 默认850秒
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ 解析倒计时数值失败，使用默认值: ${e.message}")
                    850
                }

                Log.i(TAG, "✅ 设备位置上报成功，倒计时: ${countdownSeconds}秒")
                Result.success(countdownSeconds)

            } catch (e: Exception) {
                Log.e(TAG, "❌ 发送设备位置上报失败: ${e.message}", e)
                // 网络失败时返回默认倒计时
                Result.success(850)
            }
        }
    }

    /**
     * 发送form-urlencoded格式的HTTP POST请求
     * 专门用于导航确认功能
     */
    private suspend fun sendHttpPostFormRequest(url: String, data: Map<String, String>): String {
        return withContext(Dispatchers.IO) {
            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.5 Mobile/15E148 Safari/604.1")
                connection.doOutput = true
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                // 构建form-urlencoded数据
                val formData = data.entries.joinToString("&") { (key, value) ->
                    "$key=$value"  // value已经是URL编码的
                }

                Log.d(TAG, "📤 发送Form数据: $formData")

                // 发送数据
                connection.outputStream.use { outputStream ->
                    outputStream.write(formData.toByteArray())
                    outputStream.flush()
                }

                // 读取响应
                val responseCode = connection.responseCode
                Log.d(TAG, "📥 HTTP响应码: $responseCode")

                if (responseCode == java.net.HttpURLConnection.HTTP_OK || responseCode == 302) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    Log.d(TAG, "📥 HTTP响应内容: $response")
                    response
                } else {
                    val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "未知错误"
                    Log.w(TAG, "⚠️ HTTP请求失败: $responseCode - $errorResponse")
                    throw Exception("HTTP请求失败: $responseCode")
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    /**
     * 发送JSON格式的HTTP POST请求
     */
    private suspend fun sendHttpPostRequestJson(url: String, data: Map<String, String>): String {
        return withContext(Dispatchers.IO) {
            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Accept", "application/json")
                connection.doOutput = true
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                // 构建JSON数据
                val jsonData = buildString {
                    append("{")
                    data.entries.forEachIndexed { index, entry ->
                        append("\"${entry.key}\":\"${entry.value}\"")
                        if (index < data.size - 1) append(",")
                    }
                    append("}")
                }

                Log.d(TAG, "📤 发送JSON数据: $jsonData")

                // 发送数据
                connection.outputStream.use { outputStream ->
                    outputStream.write(jsonData.toByteArray())
                    outputStream.flush()
                }

                // 读取响应
                val responseCode = connection.responseCode
                Log.d(TAG, "📥 HTTP响应码: $responseCode")

                if (responseCode == java.net.HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    Log.d(TAG, "📥 HTTP响应内容: $response")
                    response
                } else {
                    val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "未知错误"
                    Log.w(TAG, "⚠️ HTTP请求失败: $responseCode - $errorResponse")
                    throw Exception("HTTP请求失败: $responseCode")
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    /**
     * 发送HTTP POST请求的通用方法
     */
    private suspend fun sendHttpPostRequest(url: String, data: Map<String, String>): String {
        return withContext(Dispatchers.IO) {
            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.connectTimeout = 5000 // 5秒连接超时
                connection.readTimeout = 10000 // 10秒读取超时

                // 构建JSON数据
                val jsonData = org.json.JSONObject(data).toString()

                // 发送数据
                connection.outputStream.use { outputStream ->
                    outputStream.write(jsonData.toByteArray())
                    outputStream.flush()
                }

                // 读取响应
                val responseCode = connection.responseCode
                if (responseCode == java.net.HttpURLConnection.HTTP_OK) {
                    connection.inputStream.use { inputStream ->
                        inputStream.bufferedReader().readText()
                    }
                } else {
                    throw Exception("HTTP错误: $responseCode")
                }
            } finally {
                connection.disconnect()
            }
        }
    }



    /**
     * 🔍 获取连接状态详情
     */
    fun getConnectionStatus(): Map<String, Any> {
        return if (::carrotNetworkClient.isInitialized) {
            carrotNetworkClient.getConnectionStatus()
        } else {
            mapOf("error" to "网络客户端未初始化")
        }
    }



    /**
     * 获取导航确认服务状态 (已移除)
     */
    fun getNavigationConfirmationStatus(): Map<String, Any> {
        return mapOf("error" to "导航确认服务已移除")
    }



    /**
     * 清理资源
     */
    fun cleanup() {
        try {
            if (::carrotNetworkClient.isInitialized) {
                carrotNetworkClient.cleanup()
            }
            discoveredDevicesList.clear()
            Log.i(TAG, "🧹 网络管理器资源已清理")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 网络管理器清理失败: ${e.message}", e)
        }
    }
}
