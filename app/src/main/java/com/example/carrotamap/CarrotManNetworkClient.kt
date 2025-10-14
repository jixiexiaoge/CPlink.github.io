package com.example.carrotamap

// Android 系统相关导入
import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log

// 协程相关导入
import kotlinx.coroutines.*

// JSON数据处理导入
import org.json.JSONObject

// Java 网络和IO相关导入
import java.io.IOException
import java.net.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashMap

// Compose相关导入
import androidx.compose.runtime.MutableState

// CarrotMan 网络客户端类 - 负责与 Comma3 OpenPilot 设备进行 UDP 网络通信
class CarrotManNetworkClient(
    private val context: Context
) {
    
    companion object {
        private const val TAG = AppConstants.Logging.NETWORK_CLIENT_TAG
        
        // 网络通信端口配置 - 使用统一的常量管理
        private const val BROADCAST_PORT = AppConstants.Network.BROADCAST_PORT
        private const val MAIN_DATA_PORT = AppConstants.Network.MAIN_DATA_PORT
        private const val COMMAND_PORT = AppConstants.Network.COMMAND_PORT
        
        // 通信时间参数配置 - 使用统一的常量管理
        private const val DISCOVER_CHECK_INTERVAL = AppConstants.Network.DISCOVER_CHECK_INTERVAL
        private const val DATA_SEND_INTERVAL = AppConstants.Network.DATA_SEND_INTERVAL
        private const val SOCKET_TIMEOUT = AppConstants.Network.SOCKET_TIMEOUT
        private const val DEVICE_TIMEOUT = AppConstants.Network.DEVICE_TIMEOUT
        
        // 网络数据配置 - 使用统一的常量管理
        private const val MAX_PACKET_SIZE = AppConstants.Network.MAX_PACKET_SIZE
    }
    
    // 网络状态管理
    private var isRunning = false
    private var discoveredDevices = mutableMapOf<String, DeviceInfo>()
    private var currentTargetDevice: DeviceInfo? = null
    
    // Socket连接管理
    private var listenSocket: DatagramSocket? = null
    private var dataSocket: DatagramSocket? = null
    
    // 协程任务管理
    private val networkScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var listenJob: Job? = null
    private var dataSendJob: Job? = null
    private var autoSendJob: Job? = null
    private var deviceCheckJob: Job? = null
    
    // 数据统计管理
    private var carrotIndex = 0L
    private var totalPacketsSent = 0
    private var lastSendTime = 0L
    private var lastDataReceived = 0L
    private var lastNoConnectionLogTime = 0L // 添加无连接日志时间控制

    // ATC状态跟踪（用于日志记录）
    private var lastAtcPausedState: Boolean? = null
    
    // 事件回调接口
    private var onDeviceDiscovered: ((DeviceInfo) -> Unit)? = null
    private var onConnectionStatusChanged: ((Boolean, String) -> Unit)? = null
    private var onDataSent: ((Int) -> Unit)? = null
    private var onOpenpilotStatusReceived: ((String) -> Unit)? = null
    
    // Comma3设备信息数据类
    data class DeviceInfo(
        val ip: String,          // 设备IP地址
        val port: Int,           // 通信端口号
        val version: String,     // 设备版本信息
        val lastSeen: Long = System.currentTimeMillis()  // 最后发现时间
    ) {
        override fun toString(): String = "$ip:$port (v$version)"
        
        fun isActive(): Boolean {
            return System.currentTimeMillis() - lastSeen < DEVICE_TIMEOUT
        }
    }
    
    // 启动 CarrotMan 网络服务
    fun start() {
        if (isRunning) {
            Log.w(TAG, "网络服务已在运行中，忽略重复启动请求")
            return
        }
        
        Log.i(TAG, "启动 CarrotMan 网络客户端服务")
        isRunning = true
        
        try {
            initializeSockets()
            startDeviceListener()
            startDeviceHealthCheck()
            onConnectionStatusChanged?.invoke(false, "")
            Log.i(TAG, "CarrotMan 网络服务启动成功")
        } catch (e: Exception) {
            Log.e(TAG, "启动网络服务失败: ${e.message}", e)
            onConnectionStatusChanged?.invoke(false, "")
            stop()
        }
    }
    
    // 停止 CarrotMan 网络服务
    fun stop() {
        Log.i(TAG, "停止 CarrotMan 网络客户端服务")
        isRunning = false
        
        listenJob?.cancel()
        dataSendJob?.cancel()
        autoSendJob?.cancel()
        deviceCheckJob?.cancel()
        
        listenSocket?.close()
        dataSocket?.close()
        
        listenSocket = null
        dataSocket = null
        currentTargetDevice = null
        
        onConnectionStatusChanged?.invoke(false, "")
        Log.i(TAG, "CarrotMan 网络服务已完全停止")
    }
    
    // 初始化UDP Socket连接
    private fun initializeSockets() {
        try {
            Log.d(TAG, "开始初始化UDP Socket连接...")

            listenSocket = DatagramSocket(BROADCAST_PORT).apply {
                soTimeout = 1000 // 1秒超时，更频繁地检查isRunning状态
                reuseAddress = true
                broadcast = true // 启用广播接收
                Log.d(TAG, "监听Socket已创建，端口: $BROADCAST_PORT，超时: 1000ms")
            }

            dataSocket = DatagramSocket().apply {
                soTimeout = SOCKET_TIMEOUT
                Log.d(TAG, "数据发送Socket已创建，端口: ${localPort}")
            }

            Log.i(TAG, "Socket初始化成功 - 监听端口: $BROADCAST_PORT (广播模式)")

        } catch (e: Exception) {
            Log.e(TAG, "Socket初始化失败: ${e.message}", e)
            listenSocket?.close()
            dataSocket?.close()
            listenSocket = null
            dataSocket = null
            throw e
        }
    }
    
    // 启动设备广播监听服务
    private fun startDeviceListener() {
        listenJob = networkScope.launch {
            ErrorHandler.logSuccess(TAG, "启动设备广播监听服务", "端口: $BROADCAST_PORT")

            while (isRunning) {
                try {
                    // 持续监听设备广播
                    listenForDeviceBroadcasts()
                } catch (e: Exception) {
                    if (isRunning) {
                        val errorResult = ErrorHandler.analyzeException(e)
                        ErrorHandler.logError(TAG, "设备广播监听", e, errorResult)

                        // 短暂延迟后重试，避免快速失败循环
                        delay(if (errorResult.retryDelayMs > 0) errorResult.retryDelayMs else 1000)
                    }
                }

                if (isRunning) {
                    delay(100) // 短暂延迟，避免CPU占用过高
                }
            }
            ErrorHandler.logDebug(TAG, "设备广播监听服务已停止")
        }
    }
    
    // 持续监听设备广播消息
    private suspend fun listenForDeviceBroadcasts() = withContext(Dispatchers.IO) {
        val buffer = ByteArray(MAX_PACKET_SIZE)
        val packet = DatagramPacket(buffer, buffer.size)

        ErrorHandler.logDebug(TAG, "开始监听UDP广播数据，端口: $BROADCAST_PORT")

        try {
            // 单次接收广播数据
            listenSocket?.receive(packet)
            val receivedData = String(packet.data, 0, packet.length)
            val deviceIP = packet.address.hostAddress ?: "unknown"

            //Log.i(TAG, "📡 收到设备广播: [$receivedData] from $deviceIP")
            Log.d(TAG, "📊 当前状态: 已发现设备=${discoveredDevices.size}, 当前连接=${currentTargetDevice?.ip ?: "无"}")

            lastDataReceived = System.currentTimeMillis()
            parseDeviceBroadcast(receivedData, deviceIP)

        } catch (e: SocketTimeoutException) {
            // 超时是正常的，不需要特殊处理
            Log.v(TAG, "广播监听超时，继续等待...")
        } catch (e: Exception) {
            if (isRunning) {
                Log.w(TAG, "接收广播数据异常: ${e.message}")
                throw e // 重新抛出异常，由上层处理
            }
        }
    }
    
    // 解析收到的设备广播数据
    private fun parseDeviceBroadcast(broadcastData: String, deviceIP: String) {
        try {
            //Log.i(TAG, "🔍 解析设备广播数据: $broadcastData from $deviceIP")
            Log.d(TAG, "📊 解析前状态: 已发现设备=${discoveredDevices.size}, 当前连接=${currentTargetDevice?.ip ?: "无"}")

            if (broadcastData.trim().startsWith("{")) {
                val jsonBroadcast = JSONObject(broadcastData)

                // 检查是否为OpenpPilot状态数据
                if (isOpenpilotStatusData(jsonBroadcast)) {
                    Log.d(TAG, "📡 检测到OpenpPilot状态数据 from $deviceIP")
                    onOpenpilotStatusReceived?.invoke(broadcastData)

                    // OpenpPilot状态数据也表示设备存在，需要添加到设备列表
                    val ip = jsonBroadcast.optString("ip", deviceIP)
                    val port = jsonBroadcast.optInt("port", MAIN_DATA_PORT)
                    val version = "openpilot"
                    val device = DeviceInfo(ip, port, version)
                    addDiscoveredDevice(device)
                    Log.d(TAG, "从OpenpPilot状态数据中发现设备: $device")
                    return
                }

                // 处理设备发现数据
                val ip = jsonBroadcast.optString("ip", deviceIP)
                val port = jsonBroadcast.optInt("port", MAIN_DATA_PORT)
                val version = jsonBroadcast.optString("version", "unknown")

                val device = DeviceInfo(ip, port, version)
                addDiscoveredDevice(device)
                Log.d(TAG, "JSON格式设备信息解析成功: $device")

            } else {
                Log.d(TAG, "收到简单格式广播，使用默认配置: $deviceIP")
                val device = DeviceInfo(deviceIP, MAIN_DATA_PORT, "detected")
                addDiscoveredDevice(device)
            }

        } catch (e: Exception) {
            Log.w(TAG, "广播解析失败，回退到默认模式: $broadcastData - ${e.message}")
            val device = DeviceInfo(deviceIP, MAIN_DATA_PORT, "fallback")
            addDiscoveredDevice(device)
        }
    }

    // 检查JSON数据是否为OpenpPilot状态数据
    private fun isOpenpilotStatusData(jsonObject: JSONObject): Boolean {
        // OpenpPilot状态数据的特征字段
        return jsonObject.has("Carrot2") ||
               jsonObject.has("IsOnroad") ||
               jsonObject.has("v_ego_kph") ||
               jsonObject.has("active") ||
               jsonObject.has("xState")
    }
    
    // 添加新发现的设备到设备列表
    private fun addDiscoveredDevice(device: DeviceInfo) {
        val deviceKey = "${device.ip}:${device.port}"

        Log.d(TAG, "🔍 尝试添加设备: $device, 设备键: $deviceKey")
        Log.d(TAG, "📊 当前设备列表: ${discoveredDevices.keys}")

        if (!discoveredDevices.containsKey(deviceKey)) {
            discoveredDevices[deviceKey] = device
            //Log.i(TAG, "🎯 发现新的Comma3设备: $device")
            onDeviceDiscovered?.invoke(device)

            // 更新状态为发现设备
            if (currentTargetDevice == null) {
                Log.i(TAG, "🔄 更新状态: 发现设备 ${device.ip}，正在连接...")
                onConnectionStatusChanged?.invoke(false, "发现设备 ${device.ip}，正在连接...")
                //Log.i(TAG, "🚀 自动连接到第一个发现的设备")
                connectToDevice(device)
            } else {
                Log.d(TAG, "⚠️ 已有连接设备 ${currentTargetDevice?.ip}，不自动连接新设备")
            }
        } else {
            discoveredDevices[deviceKey] = device.copy(lastSeen = System.currentTimeMillis())
            Log.v(TAG, "🔄 更新设备活跃时间: $deviceKey")
        }

        Log.d(TAG, "📊 添加后状态: 已发现设备=${discoveredDevices.size}, 当前连接=${currentTargetDevice?.ip ?: "无"}")
    }
    
    // 连接到指定的Comma3设备
    fun connectToDevice(device: DeviceInfo) {
        //Log.i(TAG, "🔗 开始连接到Comma3设备: $device")

        currentTargetDevice = device
        dataSendJob?.cancel()
        startDataTransmission()

        //Log.i(TAG, "✅ 更新连接状态: 已连接到设备 ${device.ip}")
        onConnectionStatusChanged?.invoke(true, "")
        Log.i(TAG, "🎉 设备连接建立成功: ${device.ip}")
    }
    
    // 启动数据传输任务
    private fun startDataTransmission() {
        dataSendJob = networkScope.launch {
            ErrorHandler.logSuccess(TAG, "启动数据传输任务", "设备: ${currentTargetDevice?.ip}")
            
            while (isRunning && currentTargetDevice != null) {
                // 使用改进的异常处理机制发送心跳
                ErrorHandler.executeWithRetry(
                    operation = "发送心跳包",
                    tag = TAG,
                    maxRetries = 3
                ) {
                    sendHeartbeat()
                }
                
                delay(DATA_SEND_INTERVAL)
            }
            ErrorHandler.logDebug(TAG, "数据传输任务已停止")
        }
    }
    
    // 启动设备健康检查服务
    private fun startDeviceHealthCheck() {
        deviceCheckJob = networkScope.launch {
            Log.i(TAG, "启动设备健康检查服务，检查间隔: ${DISCOVER_CHECK_INTERVAL}ms")
            
            while (isRunning) {
                try {
                    val currentTime = System.currentTimeMillis()
                    val initialDeviceCount = discoveredDevices.size
                    
                    val removedDevices = discoveredDevices.values.filter { device ->
                        currentTime - device.lastSeen > DEVICE_TIMEOUT
                    }
                    
                    removedDevices.forEach { device ->
                        val deviceKey = "${device.ip}:${device.port}"
                        discoveredDevices.remove(deviceKey)
                        Log.i(TAG, "移除离线设备: $device")
                    }
                    
                    currentTargetDevice?.let { device ->
                        val deviceKey = "${device.ip}:${device.port}"
                        
                        if (!discoveredDevices.containsKey(deviceKey)) {
                            Log.w(TAG, "当前连接设备已离线: $device")
                            
                            currentTargetDevice = null
                            dataSendJob?.cancel()
                            
                            discoveredDevices.values.firstOrNull()?.let { newDevice ->
                                Log.i(TAG, "自动切换到备用设备: $newDevice")
                                connectToDevice(newDevice)
                            } ?: run {
                                Log.w(TAG, "没有可用的备用设备")
                                onConnectionStatusChanged?.invoke(false, "")
                            }
                        }
                    }
                    
                    if (removedDevices.isNotEmpty()) {
                        Log.d(TAG, "健康检查完成 - 设备数量: $initialDeviceCount -> ${discoveredDevices.size}")
                    }

                    // 检查是否需要更新连接状态
                    if (currentTargetDevice == null && discoveredDevices.isEmpty()) {
                        onConnectionStatusChanged?.invoke(false, "")
                    } else if (currentTargetDevice == null && discoveredDevices.isNotEmpty()) {
                        onConnectionStatusChanged?.invoke(false, "")
                    }

                    delay(DISCOVER_CHECK_INTERVAL)
                    
                } catch (e: Exception) {
                    val errorResult = ErrorHandler.analyzeException(e)
                    ErrorHandler.logError(TAG, "设备健康检查", e, errorResult)
                    delay(if (errorResult.retryDelayMs > 0) errorResult.retryDelayMs else 5000)
                }
            }
            Log.d(TAG, "设备健康检查服务已停止")
        }
    }
    
    // 发送心跳包维持连接
    private suspend fun sendHeartbeat() = withContext(Dispatchers.IO) {
        val heartbeatData = JSONObject().apply {
            put("carrotIndex", ++carrotIndex)
            put("epochTime", System.currentTimeMillis() / 1000)
            put("timezone", "Asia/Shanghai")
            put("carrotCmd", "heartbeat")
            put("carrotArg", "")
            put("source", "android_app")
        }
        
        sendDataPacket(heartbeatData)
        ErrorHandler.logVerbose(TAG, "心跳包已发送，索引: $carrotIndex")
    }
    
    // 发送CarrotMan导航数据包
    fun sendCarrotManData(carrotFields: CarrotManFields) {
        if (!isRunning || currentTargetDevice == null) {
            // 降低无连接时的日志级别，避免日志刷屏
            if (System.currentTimeMillis() - lastNoConnectionLogTime > 10000) { // 10秒记录一次
                ErrorHandler.logWarning(TAG, "发送CarrotMan数据", "服务未运行或无连接设备")
                ErrorHandler.logDebug(TAG, "状态检查 - 运行状态: $isRunning, 连接设备: $currentTargetDevice")
                lastNoConnectionLogTime = System.currentTimeMillis()
            }
            return
        }

        // 发送完整导航数据（许可证系统已移除）
        ErrorHandler.logDebug(TAG, "发送完整导航数据")

        networkScope.launch {
            ErrorHandler.executeWithRetry(
                operation = "发送CarrotMan数据包",
                tag = TAG,
                maxRetries = 2
            ) {
                val jsonData = convertCarrotFieldsToJson(carrotFields)
                sendDataPacket(jsonData)
                onDataSent?.invoke(++totalPacketsSent)
                ErrorHandler.logVerbose(TAG, "CarrotMan数据包发送成功 #$totalPacketsSent")
            }
        }
    }
    
    // 转换CarrotManFields为JSON协议格式
    private fun convertCarrotFieldsToJson(fields: CarrotManFields): JSONObject {
        // 获取远程IP地址 (基于Python update_navi逻辑)
        val remoteIP = currentTargetDevice?.ip ?: ""

        return JSONObject().apply {
            // 协议控制字段 (基于Python carrot_man.py逻辑)
            put("carrotIndex", ++carrotIndex)
            put("epochTime", if (fields.epochTime > 0) fields.epochTime else System.currentTimeMillis() / 1000)
            put("timezone", fields.timezone.ifEmpty { "Asia/Shanghai" })
            put("heading", fields.heading.takeIf { it != 0.0 } ?: fields.bearing)
            put("carrotCmd", "navigation_data")
            put("carrotArg", "")
            // 冗余字段已移除 (source, remote)

            // 目标位置信息字段
            put("goalPosX", fields.goalPosX)
            put("goalPosY", fields.goalPosY)
            put("szGoalName", fields.szGoalName)

            // 道路限速信息字段
            put("nRoadLimitSpeed", fields.nRoadLimitSpeed)
            
            // 添加限速变化检测日志
            if (fields.nRoadLimitSpeed > 0) {
                Log.v(TAG, "📤 发送道路限速: ${fields.nRoadLimitSpeed}km/h")
            }

            // 速度控制字段已移除 - Python内部计算

            // SDI摄像头信息字段 (完整字段)
            put("nSdiType", fields.nSdiType)
            put("nSdiSpeedLimit", fields.nSdiSpeedLimit)
            put("nSdiSection", fields.nSdiSection)
            put("nSdiDist", fields.nSdiDist)
            put("nSdiBlockType", fields.nSdiBlockType)
            put("nSdiBlockSpeed", fields.nSdiBlockSpeed)
            put("nSdiBlockDist", fields.nSdiBlockDist)
            put("nSdiPlusType", fields.nSdiPlusType)
            put("nSdiPlusSpeedLimit", fields.nSdiPlusSpeedLimit)
            put("nSdiPlusDist", fields.nSdiPlusDist)
            put("nSdiPlusBlockType", fields.nSdiPlusBlockType)
            put("nSdiPlusBlockSpeed", fields.nSdiPlusBlockSpeed)
            put("nSdiPlusBlockDist", fields.nSdiPlusBlockDist)
            put("roadcate", fields.roadcate)

            // TBT转弯引导信息字段 (完整字段)
            put("nTBTDist", fields.nTBTDist)
            put("nTBTTurnType", fields.nTBTTurnType)
            put("szTBTMainText", fields.szTBTMainText)
            put("szNearDirName", fields.szNearDirName)
            put("szFarDirName", fields.szFarDirName)
            put("nTBTNextRoadWidth", fields.nTBTNextRoadWidth)
            put("nTBTDistNext", fields.nTBTDistNext)
            put("nTBTTurnTypeNext", fields.nTBTTurnTypeNext)
            put("szTBTMainTextNext", fields.szTBTMainTextNext)

            // 导航类型和转弯字段已移除 - Python内部计算



            // 位置和导航状态字段
            put("nGoPosDist", fields.nGoPosDist)
            put("nGoPosTime", fields.nGoPosTime)
            put("szPosRoadName", fields.szPosRoadName)

            // GPS数据字段 (完整字段)
            put("latitude", fields.latitude)                 // GPS纬度
            put("longitude", fields.longitude)               // GPS经度
            put("heading", fields.heading)                   // 方向角
            put("accuracy", fields.accuracy)                 // GPS精度
            put("gps_speed", fields.gps_speed)               // GPS速度 (m/s)

            // 导航位置字段 (comma3需要的兼容字段)
            put("vpPosPointLat", fields.vpPosPointLatNavi)   // 导航纬度
            put("vpPosPointLon", fields.vpPosPointLonNavi)   // 导航经度
            put("nPosAngle", fields.nPosAngle)               // 导航方向角
            put("nPosSpeed", fields.nPosSpeed)               // 导航速度

            // 倒计时字段已移除 - Python内部计算
            // 导航状态字段 (可选)
            put("isNavigating", fields.isNavigating)

            // CarrotMan命令字段
            put("carrotCmd", fields.carrotCmd)
            put("carrotArg", fields.carrotArg)

        }
    }
    
    // 发送UDP数据包到目标设备
    private suspend fun sendDataPacket(jsonData: JSONObject) = withContext(Dispatchers.IO) {
        val device = currentTargetDevice ?: return@withContext
        
        try {
            val dataBytes = jsonData.toString().toByteArray(Charsets.UTF_8)
            
            if (dataBytes.size > MAX_PACKET_SIZE) {
                Log.w(TAG, "数据包过大: ${dataBytes.size} bytes (最大: $MAX_PACKET_SIZE)")
                return@withContext
            }
            
            val packet = DatagramPacket(
                dataBytes,
                dataBytes.size,
                InetAddress.getByName(device.ip),
                device.port
            )
            
            dataSocket?.send(packet)
            lastSendTime = System.currentTimeMillis()
            
            Log.v(TAG, "UDP数据包发送成功 -> ${device.ip}:${device.port} (${dataBytes.size} bytes)")
            
        } catch (e: Exception) {
            Log.e(TAG, "UDP数据包发送失败: ${e.message}", e)
            throw e
        }
    }
    
    // 发送交通灯状态更新到comma3设备
    fun sendTrafficLightUpdate(trafficState: Int, leftSec: Int) {
        if (!isRunning || currentTargetDevice == null) {
            Log.w(TAG, "网络客户端未运行或设备未连接，无法发送交通灯状态")
            return
        }

        networkScope.launch {
            ErrorHandler.executeWithRetry(
                operation = "发送交通灯状态更新",
                tag = TAG,
                maxRetries = 2
            ) {
                val trafficLightMessage = JSONObject().apply {
                    // 基础协议字段 (基于逆向文档)
                    put("carrotIndex", ++carrotIndex)
                    put("epochTime", System.currentTimeMillis() / 1000)
                    put("timezone", "Asia/Shanghai")
                    put("carrotCmd", "traffic_light_update")
                    put("carrotArg", "")
                    put("source", "android_amap")

                    // 交通灯状态字段 (基于逆向文档协议)
                    put("trafficState", trafficState)  // 协议标准字段名
                    put("leftSec", leftSec)           // 协议标准字段名
                    put("traffic_state", trafficState) // 内部兼容字段
                    put("left_sec", leftSec)          // 内部兼容字段

                    // 远程IP地址
                    put("remote", currentTargetDevice?.ip ?: "")
                }

                sendDataPacket(trafficLightMessage)
                totalPacketsSent++

                Log.i(TAG, "🚦 交通灯状态更新已发送: 状态=$trafficState, 倒计时=${leftSec}s")
                onDataSent?.invoke(totalPacketsSent)
            }
        }
    }

    // 发送DETECT命令到comma3设备（只在前方120m内有红灯时发送）
    fun sendDetectCommand(trafficState: Int, leftSec: Int, distance: Int, gpsLat: Double = 0.0, gpsLon: Double = 0.0) {
        if (!isRunning || currentTargetDevice == null) {
            Log.w(TAG, "网络客户端未运行或设备未连接，无法发送DETECT命令")
            return
        }

        networkScope.launch {
            ErrorHandler.executeWithRetry(
                operation = "发送DETECT命令",
                tag = TAG,
                maxRetries = 2
            ) {
                // 🎯 修复：按照Python端期望的格式构造carrotArg
                // 格式: "状态,x坐标,y坐标,置信度"
                val stateString = when (trafficState) {
                    1 -> "Red Light"        // 普通红灯
                    4 -> "Red Light"        // 左转红灯（也映射为红灯）
                    2 -> "Green Light"      // 绿灯
                    3 -> "Yellow Light"     // 黄灯
                    else -> "Red Light"     // 默认红灯
                }
                
                // 🎯 使用真实GPS坐标和高置信度（高德地图数据可信度较高）
                val x = gpsLat  // x坐标 - 使用真实GPS纬度
                val y = gpsLon  // y坐标 - 使用真实GPS经度  
                val confidence = 0.9  // 置信度 - 高德地图数据可信度较高
                
                val detectMessage = JSONObject().apply {
                    // 基础协议字段
                    put("carrotIndex", ++carrotIndex)
                    put("epochTime", System.currentTimeMillis() / 1000)
                    put("timezone", "Asia/Shanghai")
                    put("carrotCmd", "DETECT")
                    
                    put("carrotArg", "$stateString,$x,$y,$confidence")
                    put("source", "android_amap")

                    // 保留用于调试的额外字段
                    put("leftSec", leftSec)           // 剩余倒计时
                    put("distance", distance)         // 距离信息
                    put("androidTrafficState", trafficState) // Android内部状态值

                    // 远程IP地址
                    put("remote", currentTargetDevice?.ip ?: "")
                }

                sendDataPacket(detectMessage)
                totalPacketsSent++

                Log.i(TAG, "🔍 DETECT命令已发送: carrotArg='$stateString,$x,$y,$confidence', 距离=${distance}m")
                onDataSent?.invoke(totalPacketsSent)
            }
        }
    }

    // 发送专门的目的地更新消息到comma3
    suspend fun sendDestinationUpdate(
        goalPosX: Double,
        goalPosY: Double,
        szGoalName: String,
        goalAddress: String = "",
        priority: String = "high"
    ) {
        if (!isRunning || currentTargetDevice == null) {
            Log.w(TAG, "网络客户端未运行或设备未连接，无法发送目的地更新")
            return
        }
        
        try {
            val destinationMessage = JSONObject().apply {
                put("carrotIndex", ++carrotIndex)
                put("epochTime", System.currentTimeMillis() / 1000)
                put("timezone", "Asia/Shanghai")
                put("carrotCmd", "destination_update")
                put("carrotArg", "navigation_destination")
                put("source", "android_amap")
                put("priority", priority)
                
                put("goalPosX", goalPosX)
                put("goalPosY", goalPosY)
                put("szGoalName", szGoalName)
                put("goalAddress", goalAddress)
                
                put("destinationUpdateTime", System.currentTimeMillis())
                put("isNavigating", true)
                put("active_carrot", 1)
                put("dataQuality", "destination_update")
                
                put("coordinateSystem", "WGS84")
                put("coordinatePrecision", 6)
            }
            
            sendDataPacket(destinationMessage)
            totalPacketsSent++
            
            Log.i(TAG, "目的地更新消息已发送: $szGoalName ($goalPosY, $goalPosX)")
            onDataSent?.invoke(totalPacketsSent)
            
        } catch (e: Exception) {
            Log.e(TAG, "发送目的地更新失败: ${e.message}", e)
            throw e
        }
    }

    // 获取网络连接状态信息
    fun getConnectionStatus(): Map<String, Any> {
        return mapOf(
            "isRunning" to isRunning,
            "discoveredDevices" to discoveredDevices.size,
            "currentDevice" to (currentTargetDevice?.toString() ?: "无连接"),
            "totalPacketsSent" to totalPacketsSent,
            "lastSendTime" to lastSendTime,
            "lastDataReceived" to lastDataReceived,
            "carrotIndex" to carrotIndex,
            "deviceList" to discoveredDevices.values.map { it.toString() }
        )
    }
    
    // 获取发现的设备列表
    fun getDiscoveredDevices(): List<DeviceInfo> {
        return discoveredDevices.values.toList()
    }
    
    // 获取当前连接的设备信息
    fun getCurrentDevice(): DeviceInfo? {
        return currentTargetDevice
    }
    
    // 设置设备发现事件回调
    fun setOnDeviceDiscovered(callback: (DeviceInfo) -> Unit) {
        onDeviceDiscovered = callback
        Log.d(TAG, "设备发现回调已设置")
    }
    
    // 设置连接状态变化事件回调
    fun setOnConnectionStatusChanged(callback: (Boolean, String) -> Unit) {
        onConnectionStatusChanged = callback
        Log.d(TAG, "连接状态回调已设置")
    }
    
    // 设置数据发送完成事件回调
    fun setOnDataSent(callback: (Int) -> Unit) {
        onDataSent = callback
        Log.d(TAG, "数据发送回调已设置")
    }

    // 设置OpenpPilot状态数据接收回调
    fun setOnOpenpilotStatusReceived(callback: (String) -> Unit) {
        onOpenpilotStatusReceived = callback
        Log.d(TAG, "OpenpPilot状态接收回调已设置")
    }


    
    // 清理网络客户端资源
    fun cleanup() {
        //Log.i(TAG, "开始清理CarrotMan网络客户端资源")
        
        stop()
        networkScope.cancel()
        discoveredDevices.clear()
        currentTargetDevice = null
        
        carrotIndex = 0L
        totalPacketsSent = 0
        lastSendTime = 0L
        lastDataReceived = 0L
        
        Log.i(TAG, "CarrotMan网络客户端资源清理完成")
    }

    /**
     * 启动自动发送 CarrotMan 导航数据的后台任务
     * @param autoSendEnabled 是否启用自动发送的可变状态
     * @param carrotManFieldsState 当前 CarrotMan 字段的状态容器
     * @param sendInterval      发送间隔，默认为 200ms
     */
    fun startAutoDataSending(
        autoSendEnabled: MutableState<Boolean>,
        carrotManFieldsState: MutableState<CarrotManFields>,
        sendInterval: Long = 200L
    ) {
        Log.i(TAG, "📡 启动自动数据发送任务(客户端)…")

        // 若已有任务在运行，先取消
        autoSendJob?.cancel()

        autoSendJob = networkScope.launch {
            var lastSendTime = 0L
            while (isRunning) {
                try {
                    val currentFields = carrotManFieldsState.value
                    val shouldSend = autoSendEnabled.value && (
                        System.currentTimeMillis() - lastSendTime > sendInterval || 
                        currentFields.needsImmediateSend
                    )
                    
                    if (shouldSend) {
                        // 只在有连接设备时记录详细日志
                        if (currentTargetDevice != null) {
                            if (currentFields.needsImmediateSend) {
                                Log.i(TAG, "🚀 立即发送数据包 (限速变化):")
                            } else {
                                Log.d(TAG, "📤 准备自动发送数据包:")
                            }
                            Log.d(TAG, "   位置: lat=${currentFields.latitude}, lon=${currentFields.longitude}")
                            Log.d(TAG, "  🛣️ 道路: ${currentFields.szPosRoadName}")
                            Log.d(TAG, "  🚦 限速: ${currentFields.nRoadLimitSpeed}km/h")
                            Log.d(TAG, "  🎯 目标: ${currentFields.szGoalName}")
                            Log.d(TAG, "  🧭 导航状态: ${currentFields.isNavigating}")
                            Log.d(TAG, "  🔄 转向信息: 类型=${currentFields.nTBTTurnType}, 距离=${currentFields.nTBTDist}m, 指令=${currentFields.szTBTMainText}")
                            Log.d(TAG, "  🔄 下一转向: 类型=${currentFields.nTBTTurnTypeNext}, 距离=${currentFields.nTBTDistNext}m")
                        }

                        sendCarrotManData(currentFields)
                        lastSendTime = System.currentTimeMillis()
                        
                        // 重置立即发送标记
                        if (currentFields.needsImmediateSend) {
                            carrotManFieldsState.value = currentFields.copy(needsImmediateSend = false)
                        }

                        // 只在有连接设备时记录成功日志
                        if (currentTargetDevice != null) {
                            if (currentFields.needsImmediateSend) {
                                Log.i(TAG, "✅ 立即发送数据包完成 (限速已更新)")
                            } else {
                                Log.i(TAG, "✅ 自动发送数据包完成")
                            }
                        }
                    } else {
                        Log.v(TAG, "⏸️ 自动发送跳过: enabled=${autoSendEnabled.value}, 时间间隔=${System.currentTimeMillis() - lastSendTime}ms, 立即发送=${currentFields.needsImmediateSend}")
                    }
                    delay(sendInterval)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 自动数据发送失败: ${'$'}{e.message}", e)
                    delay(1000)
                }
            }
        }
    }
}

/* =====================================================
   通用目的地与地理计算工具函数 (顶层)  
   提供目的地合法性校验、更新判定以及两点间距离计算，
   抽离自 MainActivity 以减少其代码体积。
   ===================================================== */

/**
 * 验证目的地坐标与名称的合法性。
 * 保证坐标在中国大陆范围内且名称有效。
 */
fun validateDestination(longitude: Double, latitude: Double, name: String): Boolean {
    val isValidLongitude = longitude in 73.0..135.0      // 中国经度范围
    val isValidLatitude = latitude in 18.0..54.0         // 中国纬度范围
    val isValidName = name.isNotEmpty() && name.length <= 100
    val isNonZeroCoordinates = longitude != 0.0 && latitude != 0.0

    return isValidLongitude && isValidLatitude && isValidName && isNonZeroCoordinates
}

/**
 * 判断是否需要更新目的地，避免因坐标微小变化频繁刷新。
 * 若名称不同或距离超过 100 米，或之前目的地尚未设置，则返回 true。
 */
fun shouldUpdateDestination(
    currentLon: Double,
    currentLat: Double,
    currentName: String,
    newLon: Double,
    newLat: Double,
    newName: String,
    distanceThreshold: Double = 100.0
): Boolean {
    val distance = haversineDistance(currentLat, currentLon, newLat, newLon)
    return currentName != newName || distance > distanceThreshold ||
            (currentLon == 0.0 && currentLat == 0.0)
}

/**
 * 计算两点间距离（哈弗辛公式），单位：米。
 */
fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val R = 6371000.0 // 地球半径（米）
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
    val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    return R * c
} 