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
 */
class XiaogeDataReceiver(
    private val context: Context,
    private val onDataReceived: (XiaogeVehicleData?) -> Unit
) {
    companion object {
        private const val TAG = "XiaogeDataReceiver"
        private const val LISTEN_PORT = 7701
        private const val MAX_PACKET_SIZE = 4096
        private const val MIN_DATA_LENGTH = 20 // 最小数据长度（至少需要包含基本 JSON 结构）
        private const val DATA_TIMEOUT_MS = 15000L // 15秒超时清理（增加容错时间，应对网络波动和Python端重启）
        private const val CLEANUP_INTERVAL_MS = 1000L // 1秒检查一次
        private const val LOG_INTERVAL = 100L // 每100个数据包打印一次日志
    }

    private var isRunning = false
    private var listenSocket: DatagramSocket? = null
    private var listenJob: Job? = null
    private var cleanupJob: Job? = null
    private val networkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private var lastDataTime: Long = 0

    /**
     * 启动数据接收服务
     */
    fun start() {
        if (isRunning) {
            Log.w(TAG, "⚠️ 数据接收服务已在运行")
            return
        }

        Log.i(TAG, "🚀 启动小鸽数据接收服务 - 端口: $LISTEN_PORT")
        isRunning = true

        try {
            initializeSocket()
            startListener()
            startCleanupTask()
        } catch (e: Exception) {
            Log.e(TAG, "❌ 启动数据接收服务失败: ${e.message}", e)
            isRunning = false
        }
    }

    /**
     * 停止数据接收服务
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
        networkScope.cancel()

        lastDataTime = 0
        onDataReceived(null)
    }

    /**
     * 初始化UDP Socket
     */
    private fun initializeSocket() {
        try {
            listenSocket = DatagramSocket(LISTEN_PORT).apply {
                soTimeout = 500 // 500ms超时，更快检测连接状态
                reuseAddress = true
                broadcast = true
            }
            Log.i(TAG, "✅ Socket初始化成功 - 监听端口: $LISTEN_PORT")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Socket初始化失败: ${e.message}", e)
            listenSocket?.close()
            listenSocket = null
            throw e
        }
    }

    /**
     * 启动监听任务
     */
    private fun startListener() {
        listenJob = networkScope.launch {
            Log.i(TAG, "✅ 启动数据监听任务")
            val buffer = ByteArray(MAX_PACKET_SIZE)
            val packet = DatagramPacket(buffer, buffer.size)

            var packetCount = 0L
            var successCount = 0L
            var failCount = 0L
            
            while (isRunning) {
                try {
                    listenSocket?.receive(packet)
                    // 性能优化：使用 copyOfRange 减少对象创建
                    val receivedBytes = packet.data.copyOfRange(packet.offset, packet.offset + packet.length)
                    packetCount++
                    
                    // 解析数据包
                    val data = parsePacket(receivedBytes)
                    if (data != null) {
                        // ✅ 只在解析成功时更新 lastDataTime
                        successCount++
                        lastDataTime = System.currentTimeMillis()
                        onDataReceived(data)
                        // 降低日志频率：每50个数据包或每5秒打印一次
                        if (successCount % 50 == 0L || successCount == 1L) {
                            Log.d(TAG, "✅ 解析成功 #$successCount: sequence=${data.sequence}, size=${receivedBytes.size} bytes")
                        }
                    } else {
                        // ❌ 解析失败时不更新 lastDataTime，让超时机制正常工作
                        failCount++
                        // 解析失败时总是记录日志
                        Log.w(TAG, "❌ 解析失败 #$failCount: size=${receivedBytes.size} bytes，请查看上面的错误日志")
                    }
                } catch (e: java.net.SocketTimeoutException) {
                    // 超时是正常的，继续循环（不记录日志，避免刷屏）
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
     * 启动自动清理任务
     */
    private fun startCleanupTask() {
        cleanupJob = networkScope.launch {
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
     */
    private fun parsePacket(packetBytes: ByteArray): XiaogeVehicleData? {
        if (packetBytes.size < 8) {
            Log.w(TAG, "数据包太小: ${packetBytes.size} bytes (需要至少8字节)")
            return null
        }

        try {
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
                radarState = parseRadarState(dataObj.optJSONObject("radarState")),
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
            brakePressed = json.optBoolean("brakePressed", false),
            leftLatDist = json.optDouble("leftLatDist", 0.0).toFloat(),
            rightLatDist = json.optDouble("rightLatDist", 0.0).toFloat(),
            leftLaneLine = json.optInt("leftLaneLine", 0),
            rightLaneLine = json.optInt("rightLaneLine", 0),
            leftBlindspot = json.optBoolean("leftBlindspot", false),
            rightBlindspot = json.optBoolean("rightBlindspot", false),
            standstill = json.optBoolean("standstill", false)
        )
    }

    private fun parseModelV2(json: JSONObject?): ModelV2Data? {
        if (json == null) return null
        
        val lead0Obj = json.optJSONObject("lead0")
        val lead1Obj = json.optJSONObject("lead1")
        val metaObj = json.optJSONObject("meta")
        val curvatureObj = json.optJSONObject("curvature")
        val laneLineProbsArray = json.optJSONArray("laneLineProbs")
        
        val laneLineProbs = mutableListOf<Float>()
        if (laneLineProbsArray != null) {
            for (i in 0 until laneLineProbsArray.length()) {
                laneLineProbs.add(laneLineProbsArray.optDouble(i, 0.0).toFloat())
            }
        }
        
        return ModelV2Data(
            lead0 = parseLeadData(lead0Obj),
            lead1 = parseLeadData(lead1Obj),
            laneLineProbs = laneLineProbs,
            meta = parseMetaData(metaObj),
            curvature = parseCurvatureData(curvatureObj)
        )
    }

    private fun parseLeadData(json: JSONObject?): LeadData? {
        if (json == null) return null
        // 注意：lead0 包含 a 字段（加速度），但 lead1 不包含 a 字段
        // Python 端只对 lead0 发送 a 字段，lead1 只发送 x, v, prob
        // 使用 optDouble 安全解析，如果字段不存在则返回默认值 0.0
        // 因此 lead1.a 将始终为 0.0，这是预期的行为
        return LeadData(
            x = json.optDouble("x", 0.0).toFloat(),
            v = json.optDouble("v", 0.0).toFloat(),
            a = json.optDouble("a", 0.0).toFloat(),  // lead1 没有此字段，始终返回 0.0
            prob = json.optDouble("prob", 0.0).toFloat()
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

    private fun parseCurvatureData(json: JSONObject?): CurvatureData? {
        if (json == null) return null
        return CurvatureData(
            maxOrientationRate = json.optDouble("maxOrientationRate", 0.0).toFloat()
        )
    }

    private fun parseRadarState(json: JSONObject?): RadarStateData? {
        if (json == null) return null
        return RadarStateData(
            leadOne = parseLeadOneData(json.optJSONObject("leadOne")),
            leadLeft = parseSideLeadData(json.optJSONObject("leadLeft")),
            leadRight = parseSideLeadData(json.optJSONObject("leadRight"))
        )
    }

    private fun parseLeadOneData(json: JSONObject?): LeadOneData? {
        if (json == null) return null
        // 只保留 vRel（前车相对速度），其他字段与 modelV2.lead0 重复
        return LeadOneData(
            vRel = json.optDouble("vRel", 0.0).toFloat()
        )
    }

    private fun parseSideLeadData(json: JSONObject?): SideLeadData? {
        if (json == null) return null
        return SideLeadData(
            dRel = json.optDouble("dRel", 0.0).toFloat(),
            vRel = json.optDouble("vRel", 0.0).toFloat(),
            status = json.optBoolean("status", false)
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
    val radarState: RadarStateData?,
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
    val brakePressed: Boolean,    // 刹车状态
    val leftLatDist: Float,       // 到左车道线距离
    val rightLatDist: Float,      // 到右车道线距离
    val leftLaneLine: Int,        // 左车道线类型
    val rightLaneLine: Int,       // 右车道线类型
    val leftBlindspot: Boolean,   // 左盲区
    val rightBlindspot: Boolean,  // 右盲区
    val standstill: Boolean
)

data class ModelV2Data(
    val lead0: LeadData?,         // 第一前车
    val lead1: LeadData?,         // 第二前车
    val laneLineProbs: List<Float>, // [左车道线置信度, 右车道线置信度]
    val meta: MetaData?,
    val curvature: CurvatureData?
)

data class LeadData(
    val x: Float,    // 距离 (m)
    val v: Float,    // 速度 (m/s)
    val a: Float,    // 加速度
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

data class RadarStateData(
    val leadOne: LeadOneData?,
    val leadLeft: SideLeadData?,
    val leadRight: SideLeadData?
)

data class LeadOneData(
    val vRel: Float     // 前车相对速度（唯一不重复的字段，其他字段与 modelV2.lead0 重复）
)

data class SideLeadData(
    val dRel: Float,
    val vRel: Float,
    val status: Boolean
)

data class SystemStateData(
    val enabled: Boolean,
    val active: Boolean
)
