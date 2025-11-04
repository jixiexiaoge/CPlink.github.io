package com.example.carrotamap

import android.content.Context
import android.media.MediaPlayer
import android.util.Log

/**
 * 自动超车管理器
 * 分析车辆数据，判断超车条件，发送变道命令
 * 根据超车模式状态决定是否执行自动超车：
 * - 0: 禁止超车 - 不执行任何超车操作
 * - 1: 拨杆超车 - 需要用户手动拨杆触发（暂不实现）
 * - 2: 自动超车 - 系统自动检测并执行超车
 */
class AutoOvertakeManager(
    private val context: Context,
    private val networkManager: NetworkManager
) {
    companion object {
        private const val TAG = "AutoOvertakeManager"
        
        // 速度阈值
        private const val MIN_OVERTAKE_SPEED_MS = 16.67f  // 60 km/h = 16.67 m/s
        private const val SPEED_DIFF_THRESHOLD = 2.78f    // 速度差阈值 (10 km/h = 2.78 m/s)
        private const val SPEED_RATIO_THRESHOLD = 0.8f    // 前车速度/本车速度阈值
        private const val SPEED_LIMIT_RATIO = 0.9f        // 限速比例阈值（前车速度不应超过限速的90%）
        
        // 距离阈值
        private const val MAX_LEAD_DISTANCE = 80.0f       // 最大前车距离 (m)
        private const val MIN_SAFE_DISTANCE = 30.0f       // 侧方最小安全距离 (m)
        private const val MIN_LEAD1_DISTANCE = 150.0f     // 第二前车最小距离 (m)
        
        // 车道线阈值
        private const val MIN_LANE_PROB = 0.7f            // 最小车道线置信度
        private const val MIN_LANE_WIDTH = 3.0f           // 最小车道宽度 (m)
        private const val ALLOWED_LANE_LINE_TYPE = 0      // 允许变道的车道线类型（0=虚线）
        
        // 曲率阈值
        private const val MAX_CURVATURE = 0.02f            // 最大曲率 (rad/s) - 更严格的直道判断
        
        // 方向盘角度阈值
        private const val MAX_STEERING_ANGLE = 15.0f       // 最大方向盘角度 (度)
        
        // 道路类型
        private val ALLOWED_ROAD_TYPES = listOf(1, 2)      // 1=高速, 2=快速路
        
        // 时间参数
        private const val DEBOUNCE_FRAMES = 3             // 防抖帧数
        private const val COOLDOWN_TIME_MS = 5000L        // 冷却时间 (毫秒)
        
        // 单位转换
        private const val KMH_TO_MS = 3.6f                 // km/h 转 m/s
    }
    
    private var debounceCounter = 0
    private var lastCommandTime = 0L
    private var lastOvertakeDirection: String? = null
    
    /**
     * 更新数据并判断是否需要超车
     */
    fun update(data: XiaogeVehicleData?) {
        if (data == null) {
            return
        }
        
        // 🆕 检查超车模式状态：只有模式2（自动超车）才执行自动超车
        val overtakeMode = getOvertakeMode()
        if (overtakeMode != 2) {
            // 模式0（禁止超车）或模式1（拨杆超车）时不执行自动超车
            debounceCounter = 0
            return
        }
        
        // 检查前置条件
        if (!checkPrerequisites(data)) {
            debounceCounter = 0
            return
        }
        
        // 检查是否需要超车
        if (!shouldOvertake(data)) {
            debounceCounter = 0
            return
        }
        
        // 防抖机制
        debounceCounter++
        if (debounceCounter < DEBOUNCE_FRAMES) {
            return
        }
        
        // 检查冷却时间
        val now = System.currentTimeMillis()
        if (now - lastCommandTime < COOLDOWN_TIME_MS) {
            return
        }
        
        // 评估超车方向
        val decision = checkOvertakeConditions(data)
        if (decision != null) {
            sendLaneChangeCommand(decision.direction)
            lastCommandTime = now
            lastOvertakeDirection = decision.direction
            debounceCounter = 0
            Log.i(TAG, "✅ 发送超车命令: ${decision.direction}, 原因: ${decision.reason}")
        } else {
            debounceCounter = 0
        }
    }
    
    /**
     * 获取当前超车模式
     * @return 0=禁止超车, 1=拨杆超车, 2=自动超车
     */
    private fun getOvertakeMode(): Int {
        return try {
            context.getSharedPreferences("CarrotAmap", Context.MODE_PRIVATE)
                .getInt("overtake_mode", 0)
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ 获取超车模式失败，使用默认值0: ${e.message}")
            0
        }
    }
    
    /**
     * 检查前置条件（必须全部满足）
     */
    private fun checkPrerequisites(data: XiaogeVehicleData): Boolean {
        // 1. 系统已启用且激活
        val systemState = data.systemState
        if (systemState == null || !systemState.enabled || !systemState.active) {
            return false
        }
        
        // 2. 速度满足要求 (>= 60 km/h)
        val carState = data.carState ?: return false
        if (carState.vEgo < MIN_OVERTAKE_SPEED_MS) {
            return false
        }
        
        // 3. 不在静止状态
        if (carState.standstill) {
            return false
        }
        
        // 4. 道路类型检查 (只允许高速或快速路)
        val carrotMan = data.carrotMan
        if (carrotMan == null || carrotMan.roadcate !in ALLOWED_ROAD_TYPES) {
            return false
        }
        
        // 5. 前车存在且距离较近
        val lead0 = data.modelV2?.lead0
        if (lead0 == null || lead0.x >= MAX_LEAD_DISTANCE || lead0.prob < 0.5f) {
            return false
        }
        
        // 6. 第二前车检查 - 确保超车空间
        val lead1 = data.modelV2?.lead1
        if (lead1 != null && lead1.prob > 0.5f && lead1.x < MIN_LEAD1_DISTANCE) {
            return false
        }
        
        // 7. 不在弯道 (使用更严格的阈值)
        val curvature = data.modelV2?.curvature
        if (curvature != null && kotlin.math.abs(curvature.maxOrientationRate) >= MAX_CURVATURE) {
            return false
        }
        
        // 8. 方向盘角度检查
        if (kotlin.math.abs(carState.steeringAngleDeg) > MAX_STEERING_ANGLE) {
            return false
        }
        
        return true
    }
    
    /**
     * 判断是否需要超车
     */
    private fun shouldOvertake(data: XiaogeVehicleData): Boolean {
        val carState = data.carState ?: return false
        val lead0 = data.modelV2?.lead0 ?: return false
        val radarState = data.radarState
        val carrotMan = data.carrotMan ?: return false
        
        val vEgo = carState.vEgo
        val vLead = lead0.v
        val vRel = radarState?.leadOne?.vRel ?: (vLead - vEgo)
        
        // 检查前车是否低于限速
        val speedLimit = carrotMan.nRoadLimitSpeed / KMH_TO_MS  // km/h -> m/s
        if (speedLimit > 0.1f && vLead >= speedLimit * SPEED_LIMIT_RATIO) {
            // 前车速度接近限速，不需要超车
            return false
        }
        
        // 前车速度明显低于本车
        val speedDiff = vEgo - vLead
        val speedRatio = if (vEgo > 0.1f) vLead / vEgo else 0f
        
        return speedDiff >= SPEED_DIFF_THRESHOLD || speedRatio < SPEED_RATIO_THRESHOLD
    }
    
    /**
     * 检查超车条件并返回决策
     */
    private fun checkOvertakeConditions(data: XiaogeVehicleData): OvertakeDecision? {
        val carState = data.carState ?: return null
        val modelV2 = data.modelV2 ?: return null
        val radarState = data.radarState ?: return null
        
        // 检查左超车可行性
        val leftOvertake = checkLeftOvertakeFeasibility(carState, modelV2, radarState)
        
        // 检查右超车可行性
        val rightOvertake = checkRightOvertakeFeasibility(carState, modelV2, radarState)
        
        // 选择最优方向（优先左超车，符合中国交通规则）
        return when {
            leftOvertake != null -> leftOvertake
            rightOvertake != null -> rightOvertake
            else -> null
        }
    }
    
    /**
     * 检查左超车可行性
     */
    private fun checkLeftOvertakeFeasibility(
        carState: CarStateData,
        modelV2: ModelV2Data,
        radarState: RadarStateData
    ): OvertakeDecision? {
        // 左车道线置信度
        val leftLaneProb = modelV2.laneLineProbs.getOrNull(0) ?: return null
        if (leftLaneProb < MIN_LANE_PROB) {
            return null
        }
        
        // 车道线类型检查（实线不能变道）
        if (carState.leftLaneLine != ALLOWED_LANE_LINE_TYPE) {
            return null
        }
        
        // 左车道宽度
        val laneWidthLeft = modelV2.meta?.laneWidthLeft ?: return null
        if (laneWidthLeft < MIN_LANE_WIDTH) {
            return null
        }
        
        // 左盲区无车辆
        if (carState.leftBlindspot) {
            return null
        }
        
        // 左侧无近距离车辆
        val leadLeft = radarState.leadLeft
        if (leadLeft != null && leadLeft.status && leadLeft.dRel < MIN_SAFE_DISTANCE) {
            return null
        }
        
        return OvertakeDecision("LEFT", "左超车条件满足")
    }
    
    /**
     * 检查右超车可行性
     */
    private fun checkRightOvertakeFeasibility(
        carState: CarStateData,
        modelV2: ModelV2Data,
        radarState: RadarStateData
    ): OvertakeDecision? {
        // 右车道线置信度
        val rightLaneProb = modelV2.laneLineProbs.getOrNull(1) ?: return null
        if (rightLaneProb < MIN_LANE_PROB) {
            return null
        }
        
        // 车道线类型检查（实线不能变道）
        if (carState.rightLaneLine != ALLOWED_LANE_LINE_TYPE) {
            return null
        }
        
        // 右车道宽度
        val laneWidthRight = modelV2.meta?.laneWidthRight ?: return null
        if (laneWidthRight < MIN_LANE_WIDTH) {
            return null
        }
        
        // 右盲区无车辆
        if (carState.rightBlindspot) {
            return null
        }
        
        // 右侧无近距离车辆
        val leadRight = radarState.leadRight
        if (leadRight != null && leadRight.status && leadRight.dRel < MIN_SAFE_DISTANCE) {
            return null
        }
        
        return OvertakeDecision("RIGHT", "右超车条件满足")
    }
    
    /**
     * 发送变道命令
     * 发送命令给comma3，并播放相应的提示音
     */
    private fun sendLaneChangeCommand(direction: String) {
        try {
            // 发送变道命令给comma3
            networkManager.sendControlCommand("LANECHANGE", direction)
            Log.i(TAG, "📤 已发送变道命令: $direction")
            
            // 🆕 播放变道提示音
            playLaneChangeSound(direction)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 发送变道命令失败: ${e.message}", e)
        }
    }
    
    /**
     * 播放变道提示音
     * 左变道播放left音效，右变道播放right音效
     */
    private fun playLaneChangeSound(direction: String) {
        try {
            val soundResourceId = when (direction.uppercase()) {
                "LEFT" -> R.raw.left
                "RIGHT" -> R.raw.right
                else -> {
                    Log.w(TAG, "⚠️ 未知的变道方向: $direction，不播放音效")
                    return
                }
            }
            
            MediaPlayer.create(context, soundResourceId)?.apply {
                setOnCompletionListener { release() }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "❌ 音频播放错误($direction): what=$what, extra=$extra")
                    release()
                    true
                }
                start()
                Log.d(TAG, "🔊 开始播放${direction}变道提示音")
            } ?: Log.w(TAG, "⚠️ 无法创建音频播放器($direction)")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 播放${direction}变道提示音失败: ${e.message}", e)
        }
    }
    
    /**
     * 超车决策数据类
     */
    private data class OvertakeDecision(
        val direction: String,  // "LEFT" 或 "RIGHT"
        val reason: String      // 决策原因
    )
}

