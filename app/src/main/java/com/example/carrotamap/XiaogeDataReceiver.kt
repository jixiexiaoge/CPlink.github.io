package com.example.carrotamap

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

/**
 * 小鸽数据接收器
 * 监听7701端口UDP广播，解析数据包，存储到内存，自动清理过期数据
 * ✅ 增强：自动从UDP数据包中提取设备IP地址并通知NetworkManager连接
 */
class XiaogeDataReceiver(
    private val context: Context,
    private val onDataReceived: (XiaogeVehicleData?) -> Unit,
    private val onDeviceIPDetected: ((String) -> Unit)? = null  // 🆕 设备IP检测回调
) {
    companion object {
        private const val TAG = "XiaogeDataReceiver"
        private const val LISTEN_PORT = 7701
        private const val MAX_PACKET_SIZE = 4096
        private const val MIN_DATA_LENGTH = 20 // 最小数据长度（至少需要包含基本 JSON 结构）
        private const val DATA_TIMEOUT_MS = 15000L // 15秒超时清理（增加容错时间，应对网络波动和Python端重启）
        private const val CLEANUP_INTERVAL_MS = 1000L // 1秒检查一次
        private const val LOG_INTERVAL = 100L // 每100个数据包打印一次日志
        private const val RECONNECT_DELAY_MS = 2000L // Socket错误后重连延迟（2秒）
        private const val MAX_RECONNECT_ATTEMPTS = 0 // 最大重连尝试次数（0=无限重试，只要在局域网就持续尝试）
    }

    private var isRunning = false
    private var listenSocket: DatagramSocket? = null
    private var listenJob: Job? = null
    private var cleanupJob: Job? = null
    private var networkScope: CoroutineScope? = null  // 优化：改为可空类型，支持重新创建
    
    private var lastDataTime: Long = 0
    private var reconnectAttempts = 0  // 重连尝试次数

    /**
     * 启动数据接收服务
     * 优化：每次启动时重新创建 networkScope，支持多次启动/停止
     */
    fun start() {
        if (isRunning) {
            Log.w(TAG, "⚠️ 数据接收服务已在运行")
            return
        }

        Log.i(TAG, "🚀 启动小鸽数据接收服务 - 端口: $LISTEN_PORT")
        isRunning = true

        try {
            // 优化：重新创建 networkScope，支持多次启动/停止
            networkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            initializeSocket()
            startListener()
            startCleanupTask()
        } catch (e: Exception) {
            Log.e(TAG, "❌ 启动数据接收服务失败: ${e.message}", e)
            isRunning = false
            networkScope?.cancel()
            networkScope = null
        }
    }

    /**
     * 停止数据接收服务
     * 优化：取消 networkScope 并置空，支持重新启动
     */
    fun stop() {
        if (!isRunning) {
            return
        }

        Log.i(TAG, "🛑 停止小鸽数据接收服务")
        isRunning = false

        listenJob?.cancel()
        cleanupJob?.cancel()
        listenSocket?.close()
        listenSocket = null
        networkScope?.cancel()  // 优化：安全取消
        networkScope = null  // 优化：置空，支持重新创建

        lastDataTime = 0
        reconnectAttempts = 0  // 重置重连计数
        onDataReceived(null)
    }

    /**
     * 初始化UDP Socket
     * ✅ 恢复旧版本的简单方式：直接使用端口号创建Socket（已验证可工作）
     * 保留新功能：IP检测和自动连接
     */
    private fun initializeSocket() {
        try {
            // 使用旧版本的简单方式：直接传入端口号（已验证可以接收数据）
            listenSocket = DatagramSocket(LISTEN_PORT).apply {
                soTimeout = 500 // 500ms超时，更快检测连接状态（与旧版本一致）
                reuseAddress = true
                broadcast = true
            }
            
            // 获取Android设备IP地址用于调试
            val deviceIP = getDeviceIPAddress()
            Log.i(TAG, "✅ Socket初始化成功 - 监听端口: $LISTEN_PORT")
            Log.i(TAG, "📱 Android设备IP地址: $deviceIP (Python端应广播到同一网段的255地址)")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Socket初始化失败: ${e.message}", e)
            listenSocket?.close()
            listenSocket = null
            throw e
        }
    }
    
    /**
     * 获取Android设备的IP地址（用于调试）
     */
    private fun getDeviceIPAddress(): String {
        return try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (!networkInterface.isLoopback && networkInterface.isUp) {
                    val addresses = networkInterface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val address = addresses.nextElement()
                        if (address is java.net.Inet4Address && !address.isLoopbackAddress) {
                            return address.hostAddress ?: "未知"
                        }
                    }
                }
            }
            "未获取"
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ 获取设备IP地址失败: ${e.message}")
            "获取失败"
        }
    }

    /**
     * 启动监听任务
     * 增强：添加自动重连机制，确保只要在局域网就能自动连接
     */
    private fun startListener() {
        listenJob = networkScope?.launch {
            Log.i(TAG, "✅ 启动数据监听任务")
            val buffer = ByteArray(MAX_PACKET_SIZE)
            // ✅ 恢复旧版本：在循环外创建一次packet（已验证可工作）
            val packet = DatagramPacket(buffer, buffer.size)

            var packetCount = 0L
            var successCount = 0L
            var failCount = 0L
            var timeoutCount = 0L  // 超时计数
            
            while (isRunning) {
                try {
                    // 检查 socket 是否有效
                    val socket = listenSocket
                    if (socket == null || socket.isClosed) {
                        Log.w(TAG, "⚠️ Socket已关闭，尝试重新初始化...")
                        if (reconnectSocket()) {
                            reconnectAttempts = 0  // 重置重连计数
                            Log.i(TAG, "✅ Socket重新初始化成功，继续监听")
                            continue
                        } else {
                            // 重连失败，等待后重试
                            delay(RECONNECT_DELAY_MS)
                            continue
                        }
                    }
                    
                    socket.receive(packet)
                    packetCount++
                    reconnectAttempts = 0  // 成功接收数据，重置重连计数
                    timeoutCount = 0  // 重置超时计数
                    
                    // ✅ 恢复旧版本：复制数据到新数组（已验证可工作）
                    val receivedBytes = ByteArray(packet.length)
                    System.arraycopy(packet.data, packet.offset, receivedBytes, 0, packet.length)
                    
                    // 🆕 从UDP数据包中提取发送方IP地址（设备IP）
                    val deviceIP = packet.address.hostAddress
                    val packetSize = packet.length
                    
                    // 首次收到数据包时详细记录
                    if (packetCount == 1L) {
                        Log.i(TAG, "🎉 首次收到UDP数据包！")
                        Log.i(TAG, "   📍 发送方IP: $deviceIP")
                        Log.i(TAG, "   📦 数据包大小: $packetSize bytes")
                    }
                    
                    if (deviceIP != null && deviceIP.isNotEmpty()) {
                        // 通知NetworkManager自动连接设备（每次收到数据都通知，确保连接）
                        onDeviceIPDetected?.invoke(deviceIP)
                        // 降低日志频率：每100个数据包打印一次IP信息
                        if (packetCount % 100 == 0L) {
                            Log.i(TAG, "📍 收到数据包 #$packetCount: 设备IP=$deviceIP, 大小=$packetSize bytes")
                        }
                    }
                    
                    // ✅ 恢复旧版本：使用复制的数据数组解析（已验证可工作）
                    val data = parsePacket(receivedBytes)
                    if (data != null) {
                        // ✅ 只在解析成功时更新 lastDataTime
                        successCount++
                        lastDataTime = System.currentTimeMillis()
                        onDataReceived(data)
                        // 降低日志频率：每50个数据包或每5秒打印一次
                        if (successCount % 50 == 0L || successCount == 1L) {
                            Log.i(TAG, "✅ 解析成功 #$successCount: sequence=${data.sequence}, size=${packet.length} bytes, deviceIP=$deviceIP, receiveTime=${data.receiveTime}")
                        }
                    } else {
                        // ❌ 解析失败时不更新 lastDataTime，让超时机制正常工作
                        failCount++
                        // 解析失败时总是记录日志（前10次详细记录，之后降低频率）
                        if (failCount <= 10 || failCount % 50 == 0L) {
                            Log.w(TAG, "❌ 解析失败 #$failCount: size=${packet.length} bytes, deviceIP=$deviceIP，请查看上面的错误日志")
                        }
                    }
                } catch (e: java.net.SocketTimeoutException) {
                    // 超时是正常的，继续循环（每10次超时记录一次，便于调试）
                    timeoutCount++
                    if (timeoutCount == 1L || timeoutCount % 10 == 0L) {
                        val deviceIP = getDeviceIPAddress()
                        Log.d(TAG, "⏱️ Socket超时（正常），继续等待数据... (已超时 ${timeoutCount} 次, 设备IP: $deviceIP)")
                        // 如果等待超过30次（30秒）还没有收到数据，给出提示
                        if (timeoutCount == 30L) {
                            Log.w(TAG, "⚠️ 已等待30秒仍未收到数据，请检查：")
                            Log.w(TAG, "   1. Python端是否正在运行并广播到 192.168.10.255:7701")
                            Log.w(TAG, "   2. Android设备IP是否在 192.168.10.x 网段（当前: $deviceIP）")
                            Log.w(TAG, "   3. 网络是否在同一局域网")
                            Log.w(TAG, "   4. 防火墙是否阻止UDP广播")
                            Log.w(TAG, "   5. Python端应广播到与Android设备同一网段的255地址")
                        }
                    }
                } catch (e: java.net.SocketException) {
                    // Socket 错误，尝试重新初始化
                    if (isRunning) {
                        Log.w(TAG, "⚠️ Socket错误: ${e.message}，尝试重新初始化...")
                        if (reconnectSocket()) {
                            reconnectAttempts = 0
                            Log.i(TAG, "✅ Socket重新初始化成功")
                        } else {
                            reconnectAttempts++
                            if (MAX_RECONNECT_ATTEMPTS == 0 || reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                                Log.w(TAG, "⚠️ Socket重连失败，${RECONNECT_DELAY_MS}ms后重试 (尝试 $reconnectAttempts/${if (MAX_RECONNECT_ATTEMPTS == 0) "∞" else MAX_RECONNECT_ATTEMPTS})")
                                delay(RECONNECT_DELAY_MS)
                            } else {
                                Log.e(TAG, "❌ 达到最大重连次数，停止重连")
                                break
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (isRunning) {
                        Log.w(TAG, "⚠️ 接收数据异常: ${e.message}", e)
                        delay(100) // 短暂延迟后重试
                    }
                }
            }
            Log.i(TAG, "数据监听任务已停止 - 总计: $packetCount, 成功: $successCount, 失败: $failCount")
        }
    }
    
    /**
     * 重新初始化 Socket（自动重连机制）
     * @return true 如果成功，false 如果失败
     */
    private fun reconnectSocket(): Boolean {
        return try {
            // 关闭旧 socket
            listenSocket?.close()
            listenSocket = null
            
            // 重新初始化
            initializeSocket()
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Socket重新初始化失败: ${e.message}", e)
            listenSocket = null
            false
        }
    }

    /**
     * 启动自动清理任务
     */
    private fun startCleanupTask() {
        cleanupJob = networkScope?.launch {
            while (isRunning) {
                delay(CLEANUP_INTERVAL_MS)
                
                val now = System.currentTimeMillis()
                if (lastDataTime > 0 && (now - lastDataTime) > DATA_TIMEOUT_MS) {
                    Log.w(TAG, "🧹 数据超时，清理数据（${now - lastDataTime}ms未更新）")
                    lastDataTime = 0
                    onDataReceived(null)
                }
            }
        }
    }

    /**
     * 解析数据包
     * 格式: [CRC32校验(4字节)][数据长度(4字节)][JSON数据]
     * ✅ 恢复旧版本：直接接受完整的数据数组（已验证可工作）
     * 
     * @param packetBytes 数据包字节数组（已复制，包含完整数据）
     * @return 解析后的车辆数据，如果解析失败则返回 null
     */
    private fun parsePacket(packetBytes: ByteArray): XiaogeVehicleData? {
        if (packetBytes.size < 8) {
            Log.w(TAG, "数据包太小: ${packetBytes.size} bytes (需要至少8字节)")
            return null
        }

        try {
            // ✅ 恢复旧版本：直接使用完整数组创建ByteBuffer
            val buffer = ByteBuffer.wrap(packetBytes).order(ByteOrder.BIG_ENDIAN)
            
            // 读取CRC32校验和
            val receivedChecksum = buffer.int.toLong() and 0xFFFFFFFFL
            
            // 读取数据长度
            val dataLength = buffer.int
            
            // 数据包大小检查
            if (dataLength < MIN_DATA_LENGTH || dataLength > MAX_PACKET_SIZE - 8) {
                Log.w(TAG, "无效的数据长度: $dataLength (有效范围: $MIN_DATA_LENGTH - ${MAX_PACKET_SIZE - 8}), 数据包总大小: ${packetBytes.size}")
                return null
            }

            // 检查剩余数据是否足够
            if (buffer.remaining() < dataLength) {
                Log.w(TAG, "数据包不完整: 需要 $dataLength 字节，但只有 ${buffer.remaining()} 字节，数据包总大小: ${packetBytes.size}")
                return null
            }

            // 读取JSON数据
            val jsonBytes = ByteArray(dataLength)
            buffer.get(jsonBytes)
            
            // 验证CRC32
            val crc32 = CRC32()
            crc32.update(jsonBytes)
            val calculatedChecksum = crc32.value and 0xFFFFFFFFL
            
            if (receivedChecksum != calculatedChecksum) {
                Log.w(TAG, "CRC32校验失败: 接收=0x${receivedChecksum.toString(16)}, 计算=0x${calculatedChecksum.toString(16)}, 数据长度=$dataLength")
                return null
            }

            // 解析JSON
            val jsonString = String(jsonBytes, Charsets.UTF_8)
            val json = JSONObject(jsonString)
            
            // Python端已移除心跳包，直接解析数据
            return parseJsonData(json)
        } catch (e: Exception) {
            Log.w(TAG, "解析数据包失败: ${e.message}, 数据包大小: ${packetBytes.size}", e)
            return null
        }
    }

    /**
     * 解析JSON数据
     */
    private fun parseJsonData(json: JSONObject): XiaogeVehicleData? {
        try {
            val dataObj = json.optJSONObject("data")
            if (dataObj == null) {
                Log.w(TAG, "JSON中缺少 'data' 字段, sequence=${json.optLong("sequence", -1)}")
                return null
            }
            
            val sequence = json.optLong("sequence", 0)
            val timestamp = json.optDouble("timestamp", 0.0)
            
            return XiaogeVehicleData(
                sequence = sequence,
                timestamp = timestamp,
                receiveTime = System.currentTimeMillis(), // Android端接收时间（毫秒）
                carState = parseCarState(dataObj.optJSONObject("carState")),
                modelV2 = parseModelV2(dataObj.optJSONObject("modelV2")),
                systemState = parseSystemState(dataObj.optJSONObject("systemState")),
                overtakeStatus = parseOvertakeStatus(dataObj.optJSONObject("overtakeStatus"))
            )
        } catch (e: Exception) {
            Log.w(TAG, "解析JSON数据失败: ${e.message}", e)
            return null
        }
    }

    private fun parseCarState(json: JSONObject?): CarStateData? {
        if (json == null) return null
        return CarStateData(
            vEgo = json.optDouble("vEgo", 0.0).toFloat(),
            steeringAngleDeg = json.optDouble("steeringAngleDeg", 0.0).toFloat(),
            leftLatDist = json.optDouble("leftLatDist", 0.0).toFloat(),
            leftBlindspot = json.optBoolean("leftBlindspot", false),
            rightBlindspot = json.optBoolean("rightBlindspot", false)
        )
    }

    /**
     * 解析模型数据 (modelV2)
     * ✅ 已更新：与修复后的 Python 端 (xiaoge_data.py) 完全匹配
     * Python 端修复：
     * - modelVEgo: 优先使用 carState.vEgo（来自CAN总线，更准确）
     * - laneWidth: 使用插值方法在指定距离处计算，而不是使用固定索引
     * - 所有字段都经过验证和优化
     */
    private fun parseModelV2(json: JSONObject?): ModelV2Data? {
        if (json == null) return null
        
        val lead0Obj = json.optJSONObject("lead0")
        val leadLeftObj = json.optJSONObject("leadLeft")
        val leadRightObj = json.optJSONObject("leadRight")
        val metaObj = json.optJSONObject("meta")
        val curvatureObj = json.optJSONObject("curvature")
        val laneLineProbsArray = json.optJSONArray("laneLineProbs")
        
        // 解析车道线置信度数组 [左车道线置信度, 右车道线置信度]
        val laneLineProbs = mutableListOf<Float>()
        if (laneLineProbsArray != null) {
            for (i in 0 until laneLineProbsArray.length()) {
                laneLineProbs.add(laneLineProbsArray.optDouble(i, 0.0).toFloat())
            }
        }
        
        return ModelV2Data(
            lead0 = parseLeadData(lead0Obj),  // 第一前车
            leadLeft = parseSideLeadDataExtended(leadLeftObj),  // 左侧车辆（纯视觉方案）
            leadRight = parseSideLeadDataExtended(leadRightObj), // 右侧车辆（纯视觉方案）
            laneLineProbs = laneLineProbs,  // [左车道线置信度, 右车道线置信度]
            meta = parseMetaData(metaObj),  // 车道宽度和变道状态
            curvature = parseCurvatureData(curvatureObj)  // 曲率信息（用于判断弯道）
        )
    }

    private fun parseLeadData(json: JSONObject?): LeadData? {
        if (json == null) return null
        // 简化版：只保留超车决策必需的字段
        return LeadData(
            x = json.optDouble("x", 0.0).toFloat(),  // 相对于相机的距离 (m)
            y = json.optDouble("y", 0.0).toFloat(),  // 横向位置（用于返回原车道判断）
            v = json.optDouble("v", 0.0).toFloat(),  // 速度 (m/s)
            prob = json.optDouble("prob", 0.0).toFloat()  // 置信度
        )
    }

    private fun parseMetaData(json: JSONObject?): MetaData? {
        if (json == null) return null
        return MetaData(
            laneWidthLeft = json.optDouble("laneWidthLeft", 0.0).toFloat(),
            laneWidthRight = json.optDouble("laneWidthRight", 0.0).toFloat(),
            laneChangeState = json.optInt("laneChangeState", 0),
            laneChangeDirection = json.optInt("laneChangeDirection", 0)
        )
    }

    /**
     * 解析曲率数据
     * ✅ 已更新：与修复后的 Python 端 (xiaoge_data.py) 完全匹配
     * Python 端修复：改进空列表检查逻辑，使代码更清晰
     */
    private fun parseCurvatureData(json: JSONObject?): CurvatureData? {
        if (json == null) return null
        return CurvatureData(
            maxOrientationRate = json.optDouble("maxOrientationRate", 0.0).toFloat()  // 最大方向变化率 (rad/s)，方向可从符号推导（>0=左转，<0=右转）
        )
    }


    /**
     * 解析扩展的侧方车辆数据（纯视觉方案）
     * 简化版：只保留超车决策必需的字段
     */
    private fun parseSideLeadDataExtended(json: JSONObject?): SideLeadDataExtended? {
        if (json == null) return null
        return SideLeadDataExtended(
            dRel = json.optDouble("dRel", 0.0).toFloat(), // 相对于雷达的距离
            vRel = json.optDouble("vRel", 0.0).toFloat(), // 相对速度 (m/s)
            status = json.optBoolean("status", false)  // 是否有车辆
        )
    }


    private fun parseSystemState(json: JSONObject?): SystemStateData? {
        if (json == null) return null
        return SystemStateData(
            enabled = json.optBoolean("enabled", false),
            active = json.optBoolean("active", false)
        )
    }

    /**
     * 🆕 解析超车状态数据
     * 从 JSON 中解析超车状态信息，用于在 UI 中显示
     * 注意：此数据由 Android 端的 AutoOvertakeManager 生成，Python 端不发送此数据
     * 如果 Python 端未来发送此数据，此函数可以正确解析
     */
    private fun parseOvertakeStatus(json: JSONObject?): OvertakeStatusData? {
        if (json == null) return null
        
        val lastDirectionStr = json.optString("lastDirection", "")
        val blockingReasonStr = json.optString("blockingReason", "")
        
        return OvertakeStatusData(
            statusText = json.optString("statusText", "监控中"),
            canOvertake = json.optBoolean("canOvertake", false),
            cooldownRemaining = if (json.has("cooldownRemaining")) {
                json.optLong("cooldownRemaining", 0)
            } else {
                null
            },
            lastDirection = lastDirectionStr.takeIf { it.isNotEmpty() },
            blockingReason = blockingReasonStr.takeIf { it.isNotEmpty() }
        )
    }
}

/**
 * 小鸽车辆数据结构
 */
data class XiaogeVehicleData(
    val sequence: Long,
    val timestamp: Double,  // Python端时间戳（秒）
    val receiveTime: Long = 0L,  // Android端接收时间（毫秒），用于计算数据年龄
    val carState: CarStateData?,
    val modelV2: ModelV2Data?,
    val systemState: SystemStateData?,
    val overtakeStatus: OvertakeStatusData? = null  // 超车状态（可选，由 AutoOvertakeManager 更新）
)

/**
 * 超车状态数据
 * 用于在 UI 中显示超车系统的实时状态
 * 注意：此数据需要在 openpilot 端的数据发送器中包含超车状态信息
 */
data class OvertakeStatusData(
    val statusText: String,           // 状态文本描述："监控中"/"可超车"/"冷却中"
    val canOvertake: Boolean,         // 是否可以超车
    val cooldownRemaining: Long?,     // 剩余冷却时间（毫秒），可选
    val lastDirection: String?,       // 上次超车方向（LEFT/RIGHT），可选
    val blockingReason: String? = null // 🆕 阻止超车的原因（可选）
)

data class CarStateData(
    val vEgo: Float,              // 本车速度 (m/s)
    val steeringAngleDeg: Float,  // 方向盘角度
    val leftLatDist: Float,       // 到左车道线距离（返回原车道）
    val leftBlindspot: Boolean,   // 左盲区
    val rightBlindspot: Boolean   // 右盲区
)

/**
 * 模型数据 (modelV2)
 * 简化版：只保留超车决策必需的字段
 */
data class ModelV2Data(
    val lead0: LeadData?,         // 第一前车
    val leadLeft: SideLeadDataExtended?,  // 左侧车辆（纯视觉方案）
    val leadRight: SideLeadDataExtended?, // 右侧车辆（纯视觉方案）
    val laneLineProbs: List<Float>, // [左车道线置信度, 右车道线置信度]
    val meta: MetaData?,          // 车道宽度和变道状态
    val curvature: CurvatureData? // 曲率信息（用于判断弯道）
)

/**
 * 前车数据（lead0）
 * 简化版：只保留超车决策必需的字段
 */
data class LeadData(
    val x: Float,    // 距离 (m) - 相对于相机的距离
    val y: Float,    // 横向位置（用于返回原车道判断）
    val v: Float,    // 速度 (m/s)
    val prob: Float  // 置信度
)

data class MetaData(
    val laneWidthLeft: Float,
    val laneWidthRight: Float,
    val laneChangeState: Int,
    val laneChangeDirection: Int
)

data class CurvatureData(
    val maxOrientationRate: Float  // 曲率 (rad/s)，方向可从符号推导（>0=左转，<0=右转）
)

/**
 * 扩展的侧方车辆数据（纯视觉方案）
 * 简化版：只保留超车决策必需的字段
 */
data class SideLeadDataExtended(
    val dRel: Float,           // 相对于雷达的距离
    val vRel: Float,           // 相对速度 (m/s)
    val status: Boolean        // 是否有车辆
)

data class SystemStateData(
    val enabled: Boolean,
    val active: Boolean
)
