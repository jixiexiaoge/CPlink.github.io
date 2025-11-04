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
        private const val DATA_TIMEOUT_MS = 5000L // 5秒超时清理
        private const val CLEANUP_INTERVAL_MS = 1000L // 1秒检查一次
    }

    private var isRunning = false
    private var listenSocket: DatagramSocket? = null
    private var listenJob: Job? = null
    private var cleanupJob: Job? = null
    private val networkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private var lastDataTime: Long = 0
    private var currentData: XiaogeVehicleData? = null

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

        currentData = null
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

            while (isRunning) {
                try {
                    listenSocket?.receive(packet)
                    val receivedBytes = ByteArray(packet.length)
                    System.arraycopy(packet.data, packet.offset, receivedBytes, 0, packet.length)
                    
                    // 解析数据包
                    val data = parsePacket(receivedBytes)
                    if (data != null) {
                        currentData = data
                        lastDataTime = System.currentTimeMillis()
                        onDataReceived(data)
                        Log.v(TAG, "📡 收到数据包: sequence=${data.sequence}, timestamp=${data.timestamp}")
                    }
                } catch (e: java.net.SocketTimeoutException) {
                    // 超时是正常的，继续循环
                } catch (e: Exception) {
                    if (isRunning) {
                        Log.w(TAG, "⚠️ 接收数据异常: ${e.message}")
                        delay(100) // 短暂延迟后重试
                    }
                }
            }
            Log.d(TAG, "数据监听任务已停止")
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
                    Log.d(TAG, "🧹 数据超时，清理数据（${now - lastDataTime}ms未更新）")
                    currentData = null
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
            Log.w(TAG, "⚠️ 数据包太小: ${packetBytes.size} bytes")
            return null
        }

        try {
            val buffer = ByteBuffer.wrap(packetBytes).order(ByteOrder.BIG_ENDIAN)
            
            // 读取CRC32校验和
            val receivedChecksum = buffer.int.toLong() and 0xFFFFFFFFL
            
            // 读取数据长度
            val dataLength = buffer.int
            
            if (dataLength <= 0 || dataLength > MAX_PACKET_SIZE - 8) {
                Log.w(TAG, "⚠️ 无效的数据长度: $dataLength")
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
                Log.w(TAG, "⚠️ CRC32校验失败: 接收=${receivedChecksum.toString(16)}, 计算=${calculatedChecksum.toString(16)}")
                return null
            }

            // 解析JSON
            val jsonString = String(jsonBytes, Charsets.UTF_8)
            val json = JSONObject(jsonString)
            
            return parseJsonData(json)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 解析数据包失败: ${e.message}", e)
            return null
        }
    }

    /**
     * 解析JSON数据
     */
    private fun parseJsonData(json: JSONObject): XiaogeVehicleData? {
        try {
            val dataObj = json.optJSONObject("data") ?: return null
            
            return XiaogeVehicleData(
                sequence = json.optLong("sequence", 0),
                timestamp = json.optDouble("timestamp", 0.0),
                carState = parseCarState(dataObj.optJSONObject("carState")),
                modelV2 = parseModelV2(dataObj.optJSONObject("modelV2")),
                radarState = parseRadarState(dataObj.optJSONObject("radarState")),
                systemState = parseSystemState(dataObj.optJSONObject("systemState")),
                longitudinalPlan = parseLongitudinalPlan(dataObj.optJSONObject("longitudinalPlan")),
                carrotMan = parseCarrotMan(dataObj.optJSONObject("carrotMan"))
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ 解析JSON数据失败: ${e.message}", e)
            return null
        }
    }

    private fun parseCarState(json: JSONObject?): CarStateData? {
        if (json == null) return null
        return CarStateData(
            vEgo = json.optDouble("vEgo", 0.0).toFloat(),
            aEgo = json.optDouble("aEgo", 0.0).toFloat(),
            steeringAngleDeg = json.optDouble("steeringAngleDeg", 0.0).toFloat(),
            leftBlinker = json.optBoolean("leftBlinker", false),
            rightBlinker = json.optBoolean("rightBlinker", false),
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
        return LeadData(
            x = json.optDouble("x", 0.0).toFloat(),
            v = json.optDouble("v", 0.0).toFloat(),
            a = json.optDouble("a", 0.0).toFloat(),
            prob = json.optDouble("prob", 0.0).toFloat()
        )
    }

    private fun parseMetaData(json: JSONObject?): MetaData? {
        if (json == null) return null
        return MetaData(
            laneWidthLeft = json.optDouble("laneWidthLeft", 0.0).toFloat(),
            laneWidthRight = json.optDouble("laneWidthRight", 0.0).toFloat(),
            distanceToRoadEdgeLeft = json.optDouble("distanceToRoadEdgeLeft", 0.0).toFloat(),
            distanceToRoadEdgeRight = json.optDouble("distanceToRoadEdgeRight", 0.0).toFloat(),
            laneChangeState = json.optInt("laneChangeState", 0),
            laneChangeDirection = json.optInt("laneChangeDirection", 0)
        )
    }

    private fun parseCurvatureData(json: JSONObject?): CurvatureData? {
        if (json == null) return null
        return CurvatureData(
            maxOrientationRate = json.optDouble("maxOrientationRate", 0.0).toFloat(),
            direction = json.optInt("direction", 0)
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
        return LeadOneData(
            dRel = json.optDouble("dRel", 0.0).toFloat(),
            vRel = json.optDouble("vRel", 0.0).toFloat(),
            vLead = json.optDouble("vLead", 0.0).toFloat(),
            vLeadK = json.optDouble("vLeadK", 0.0).toFloat(),
            status = json.optBoolean("status", false)
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
            active = json.optBoolean("active", false),
            longControlState = json.optInt("longControlState", 0)
        )
    }

    private fun parseLongitudinalPlan(json: JSONObject?): LongitudinalPlanData? {
        if (json == null) return null
        return LongitudinalPlanData(
            xState = json.optInt("xState", 0),
            trafficState = json.optInt("trafficState", 0),
            cruiseTarget = json.optDouble("cruiseTarget", 0.0).toFloat(),
            hasLead = json.optBoolean("hasLead", false)
        )
    }

    private fun parseCarrotMan(json: JSONObject?): XiaogeCarrotManData? {
        if (json == null) return null
        return XiaogeCarrotManData(
            nRoadLimitSpeed = json.optInt("nRoadLimitSpeed", 0),
            desiredSpeed = json.optInt("desiredSpeed", 0),
            xSpdLimit = json.optInt("xSpdLimit", 0),
            xSpdDist = json.optInt("xSpdDist", 0),
            xSpdType = json.optInt("xSpdType", 0),
            roadcate = json.optInt("roadcate", 0)
        )
    }
}

/**
 * 小鸽车辆数据结构
 */
data class XiaogeVehicleData(
    val sequence: Long,
    val timestamp: Double,
    val carState: CarStateData?,
    val modelV2: ModelV2Data?,
    val radarState: RadarStateData?,
    val systemState: SystemStateData?,
    val longitudinalPlan: LongitudinalPlanData?,
    val carrotMan: XiaogeCarrotManData?
)

data class CarStateData(
    val vEgo: Float,              // 本车速度 (m/s)
    val aEgo: Float,              // 加速度
    val steeringAngleDeg: Float,  // 方向盘角度
    val leftBlinker: Boolean,
    val rightBlinker: Boolean,
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
    val distanceToRoadEdgeLeft: Float,
    val distanceToRoadEdgeRight: Float,
    val laneChangeState: Int,
    val laneChangeDirection: Int
)

data class CurvatureData(
    val maxOrientationRate: Float, // 曲率 (rad/s)
    val direction: Int              // 1=左转, -1=右转
)

data class RadarStateData(
    val leadOne: LeadOneData?,
    val leadLeft: SideLeadData?,
    val leadRight: SideLeadData?
)

data class LeadOneData(
    val dRel: Float,    // 相对距离
    val vRel: Float,    // 相对速度
    val vLead: Float,   // 前车速度
    val vLeadK: Float,
    val status: Boolean
)

data class SideLeadData(
    val dRel: Float,
    val vRel: Float,
    val status: Boolean
)

data class SystemStateData(
    val enabled: Boolean,
    val active: Boolean,
    val longControlState: Int
)

data class LongitudinalPlanData(
    val xState: Int,
    val trafficState: Int,
    val cruiseTarget: Float,
    val hasLead: Boolean
)

data class XiaogeCarrotManData(
    val nRoadLimitSpeed: Int,
    val desiredSpeed: Int,
    val xSpdLimit: Int,
    val xSpdDist: Int,
    val xSpdType: Int,
    val roadcate: Int
)

