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
        
        // 前车最低速度限制（方案2）
        private const val NORMAL_LEAD_MIN_SPEED_KPH = 20.0f    // 前车最低速度：≥20 km/h（避免堵车误判）
        
        // 远距离超车参数（方案3）
        private const val EARLY_OVERTAKE_SPEED_RATIO = 0.6f   // 前车速度 ≤ 60% 本车速度
        private const val EARLY_OVERTAKE_MIN_LEAD_SPEED_KPH = 50.0f  // 前车速度 ≥ 50 km/h
        private const val EARLY_OVERTAKE_MIN_SPEED_DIFF_KPH = 20.0f  // 速度差 ≥ 20 km/h
        private const val EARLY_OVERTAKE_MIN_DISTANCE = 30.0f  // 最小距离 30m
        private const val EARLY_OVERTAKE_MAX_DISTANCE = 100.0f // 最大距离 100m
        
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
        
        // 时间参数
        private const val DEBOUNCE_FRAMES = 3             // 防抖帧数
        
        // 动态冷却时间（方案1）
        private const val COOLDOWN_BASE_MS = 8000L        // 基础冷却时间 8秒
        private const val COOLDOWN_SUCCESS_MS = 15000L    // 成功超车后冷却 15秒
        private const val COOLDOWN_FAILED_MS = 3000L      // 超车失败后冷却 3秒（快速重试）
        private const val COOLDOWN_CONDITION_MS = 5000L    // 条件不满足冷却 5秒
        
        // 返回原车道参数（方案5）
        private const val MAX_LANE_MEMORY_TIME_MS = 30000L  // 30秒超时
        private const val RETURN_MIN_SPEED_ADVANTAGE_KPH = 8.0f  // 返回需要至少8 km/h速度优势
        
        // 单位转换（km/h -> m/s）
        private const val MS_PER_KMH = 0.2777778f
        
        // 声音播放（SoundPool）
        private var soundPool: android.media.SoundPool? = null
        private var soundIdLeft: Int? = null
        private var soundIdRight: Int? = null
        private var soundIdLeftConfirm: Int? = null
        private var soundIdRightConfirm: Int? = null
    }
    
    // 防抖和冷却状态
    private var debounceCounter = 0
    private var lastCommandTimeLeft = 0L
    private var lastCommandTimeRight = 0L
    private var lastOvertakeDirection: String? = null
    
    // 动态冷却机制（方案1）
    private enum class OvertakeResult { NONE, PENDING, SUCCESS, FAILED, CONDITION_NOT_MET }
    private var lastOvertakeResult = OvertakeResult.NONE
    private var consecutiveFailures = 0
    private var pendingOvertakeStartTime = 0L  // 待确认超车开始时间
    private val PENDING_TIMEOUT_MS = 3000L  // 待确认超车超时时间（3秒）
    
    // 返回原车道策略（方案5）
    private var originalLanePosition = 0f  // 原始车道位置（使用横向距离）
    private var netLaneChanges = 0  // 净变道数：>0表示在左侧，<0表示在右侧
    private var laneMemoryStartTime = 0L
    private var overtakeCompleteTimer = 0L
    private val OVERTAKE_COMPLETE_DURATION_MS = 2000L  // 超越完成后等待2秒再返回
    
    /**
     * 更新数据并判断是否需要超车
     * @param data 车辆数据
     * @param roadType 高德地图道路类型（0=高速公路, 6=快速道, 其他=普通道路）
     * @return 更新后的超车状态数据，用于更新 XiaogeVehicleData
     */
    fun update(data: XiaogeVehicleData?, roadType: Int = -1): OvertakeStatusData? {
        if (data == null) {
            return null
        }
        
        // 🆕 检查超车模式状态：模式0直接返回；模式1仅播放确认音；模式2自动超车并播放方向音
        val overtakeMode = getOvertakeMode()
        if (overtakeMode == 0) {
            // 禁止超车
            debounceCounter = 0
            resetLaneMemory()
            return createOvertakeStatus(data, "禁止超车", false, null)
        }
        
        // 🆕 车道变更状态监控：如果正在变道中，等待完成
        val laneChangeState = data.modelV2?.meta?.laneChangeState ?: 0
        if (laneChangeState != 0) {
            // 正在变道中，根据状态更新超车结果
            updateOvertakeResultFromLaneChangeState(laneChangeState)
            val direction = when (data.modelV2?.meta?.laneChangeDirection) {
                -1 -> "LEFT"
                1 -> "RIGHT"
                else -> null
            }
            return createOvertakeStatus(data, "变道中", false, direction)
        }
        
        // 🆕 如果变道完成（从非0变为0），更新成功状态
        if (lastOvertakeResult == OvertakeResult.PENDING) {
            val now = System.currentTimeMillis()
            if (now - pendingOvertakeStartTime > PENDING_TIMEOUT_MS) {
                // 超时未完成，标记为失败
                lastOvertakeResult = OvertakeResult.FAILED
                consecutiveFailures++
                Log.w(TAG, "⏱️ 超车超时未完成，标记为失败")
            } else {
                // 变道完成，标记为成功
                lastOvertakeResult = OvertakeResult.SUCCESS
                consecutiveFailures = 0
                Log.i(TAG, "✅ 变道完成，标记为成功")
            }
        }
        
        // 方案5：检查返回原车道条件
        if (checkReturnConditions(data)) {
            val returnDirection = if (netLaneChanges > 0) "RIGHT" else "LEFT"
            if (overtakeMode == 2) {
                sendLaneChangeCommand(returnDirection)
                Log.i(TAG, "🔄 返回原车道: $returnDirection")
                resetLaneMemory()
            }
            return createOvertakeStatus(data, "返回原车道", false, returnDirection)
        }
        
        // 检查前置条件
        val (prerequisitesMet, prerequisiteReason) = checkPrerequisites(data, roadType)
        if (!prerequisitesMet) {
            // 前置条件短暂不满足时，不清零计数，保留防抖累积
            lastOvertakeResult = OvertakeResult.CONDITION_NOT_MET
            return createOvertakeStatus(data, "监控中", false, null, blockingReason = prerequisiteReason)
        }
        
        // 检查是否需要超车
        val (shouldOvertake, shouldOvertakeReason) = shouldOvertake(data)
        if (!shouldOvertake) {
            // 只有明确判断不需要超车时才重置计数
            debounceCounter = 0
            lastOvertakeResult = OvertakeResult.CONDITION_NOT_MET
            return createOvertakeStatus(data, "监控中", false, null, blockingReason = shouldOvertakeReason)
        }
        
        // 防抖机制
        debounceCounter++
        if (debounceCounter < DEBOUNCE_FRAMES) {
            return createOvertakeStatus(data, "监控中", true, null)
        }
        
        // 评估超车方向
        val decision = checkOvertakeConditions(data)
        if (decision != null) {
            val now = System.currentTimeMillis()
            val isLeft = decision.direction.equals("LEFT", ignoreCase = true)
            val lastTime = if (isLeft) lastCommandTimeLeft else lastCommandTimeRight
            val cooldown = calculateDynamicCooldown(data)
            val cooldownRemaining = (cooldown - (now - lastTime)).coerceAtLeast(0L)
            
            if (now - lastTime < cooldown) {
                // 当前方向仍在冷却中，尝试另一方向（若可行）
                val other = if (isLeft) "RIGHT" else "LEFT"
                val carStateSafe = data.carState ?: return createOvertakeStatus(data, "冷却中", false, lastOvertakeDirection, cooldownRemaining)
                val modelV2Safe = data.modelV2 ?: return createOvertakeStatus(data, "冷却中", false, lastOvertakeDirection, cooldownRemaining)
                val radarStateSafe = data.radarState ?: return createOvertakeStatus(data, "冷却中", false, lastOvertakeDirection, cooldownRemaining)
                val canOther = if (isLeft) checkRightOvertakeFeasibility(carStateSafe, modelV2Safe, radarStateSafe) else checkLeftOvertakeFeasibility(carStateSafe, modelV2Safe, radarStateSafe)
                if (canOther != null) {
                    val lead0Safe = modelV2Safe?.lead0
                    if (overtakeMode == 2) {
                        sendLaneChangeCommand(other)
                        recordOvertakeStart(other, data)
                        // 方案1：记录超车为待确认状态，等待变道状态反馈
                        lastOvertakeResult = OvertakeResult.PENDING
                        pendingOvertakeStartTime = System.currentTimeMillis()
                    } else {
                        playConfirmSound(other)
                    }
                    if (isLeft) lastCommandTimeRight = now else lastCommandTimeLeft = now
                    lastOvertakeDirection = other
                    debounceCounter = 0
                    val logContext = if (carStateSafe != null && lead0Safe != null) {
                        ", 本车${(carStateSafe.vEgo * 3.6f).toInt()}km/h, 前车${(lead0Safe.v * 3.6f).toInt()}km/h, 距离${lead0Safe.x.toInt()}m"
                    } else {
                        ""
                    }
                    Log.i(TAG, if (overtakeMode == 2) "✅ 发送超车命令(备用方向): $other, 原因: ${canOther.reason}$logContext" else "🔔 拨杆模式播放确认音(备用方向): $other, 原因: ${canOther.reason}$logContext")
                    return createOvertakeStatus(data, if (overtakeMode == 2) "变道中" else "可超车", true, other)
                }
                return createOvertakeStatus(data, "冷却中", false, lastOvertakeDirection, cooldownRemaining)
            }
            
            val carState = data.carState
            val lead0 = data.modelV2?.lead0
            if (overtakeMode == 2) {
                sendLaneChangeCommand(decision.direction)
                recordOvertakeStart(decision.direction, data)
                // 方案1：记录超车为待确认状态，等待变道状态反馈
                lastOvertakeResult = OvertakeResult.PENDING
                pendingOvertakeStartTime = System.currentTimeMillis()
            } else {
                playConfirmSound(decision.direction)
            }
            if (isLeft) lastCommandTimeLeft = now else lastCommandTimeRight = now
            lastOvertakeDirection = decision.direction
            debounceCounter = 0
            val logContext = if (carState != null && lead0 != null) {
                ", 本车${(carState.vEgo * 3.6f).toInt()}km/h, 前车${(lead0.v * 3.6f).toInt()}km/h, 距离${lead0.x.toInt()}m"
            } else {
                ""
            }
            Log.i(TAG, if (overtakeMode == 2) "✅ 发送超车命令: ${decision.direction}, 原因: ${decision.reason}$logContext" else "🔔 拨杆模式播放确认音: ${decision.direction}, 原因: ${decision.reason}$logContext")
            return createOvertakeStatus(data, if (overtakeMode == 2) "变道中" else "可超车", true, decision.direction)
        } else {
            debounceCounter = 0
            lastOvertakeResult = OvertakeResult.CONDITION_NOT_MET
            consecutiveFailures++
            // 🆕 生成阻止原因：检查为什么左右都不能超车
            val blockingReason = generateBlockingReason(data)
            return createOvertakeStatus(data, "监控中", false, null, blockingReason = blockingReason)
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
     * @param data 车辆数据
     * @param roadType 高德地图道路类型（0=高速公路, 6=快速道, 其他=普通道路）
     * @return Pair<Boolean, String?> 第一个值表示是否满足条件，第二个值表示不满足时的原因
     */
    private fun checkPrerequisites(data: XiaogeVehicleData, roadType: Int): Pair<Boolean, String?> {
        // 1. 系统已启用且激活
        val systemState = data.systemState
        if (systemState == null || !systemState.enabled || !systemState.active) {
            return Pair(false, "系统未激活")
        }
        
        // 2. 速度满足要求 (>= 60 km/h)
        val carState = data.carState ?: return Pair(false, "车辆状态缺失")
        val vEgoKmh = carState.vEgo * 3.6f
        if (carState.vEgo < MIN_OVERTAKE_SPEED_MS) {
            return Pair(false, "速度过低 (< ${MIN_OVERTAKE_SPEED_MS * 3.6f.toInt()} km/h)")
        }
        
        // 3. 不在静止状态
        if (carState.standstill) {
            return Pair(false, "车辆静止")
        }
        
        // 4. 道路类型检查（只允许高速公路和快速道）
        // 高德地图道路类型：0=高速公路, 6=快速道
        if (roadType != 0 && roadType != 6) {
            return Pair(false, "道路类型不允许（仅高速/快速道）")
        }
        
        // 5. 前车存在且距离较近
        val lead0 = data.modelV2?.lead0
        if (lead0 == null || lead0.x >= MAX_LEAD_DISTANCE || lead0.prob < 0.5f) {
            return Pair(false, "前车距离过远或置信度不足")
        }
        
        // 方案2：前车最低速度限制（避免堵车误判）- 内联检查
        val leadSpeedKmh = lead0.v * 3.6f
        if (leadSpeedKmh < NORMAL_LEAD_MIN_SPEED_KPH) {
            return Pair(false, "前车速度过低 (< ${NORMAL_LEAD_MIN_SPEED_KPH.toInt()} km/h)")
        }
        
        // 前车加速度为正（加速中）时，暂缓超车（优化：阈值从0.5改为0.2）
        val lead0Accel = lead0.a
        if (lead0Accel > 0.2f) {
            return Pair(false, "前车加速中")
        }
        
        // 安全检查：刹车时禁止超车
        if (carState.brakePressed) {
            return Pair(false, "刹车中")
        }
        
        // 6. 第二前车检查 - 确保超车空间
        val lead1 = data.modelV2?.lead1
        if (lead1 != null && lead1.prob > 0.5f && lead1.x < MIN_LEAD1_DISTANCE) {
            return Pair(false, "第二前车距离过近")
        }
        
        // 7. 不在弯道 (使用更严格的阈值)
        val curvature = data.modelV2?.curvature
        if (curvature != null && kotlin.math.abs(curvature.maxOrientationRate) >= MAX_CURVATURE) {
            return Pair(false, "弯道中 (曲率过大)")
        }
        // 若系统正在变道，禁止新的超车（已在update()开始处检查，这里保留作为双重检查）
        val laneChangeState = data.modelV2?.meta?.laneChangeState ?: 0
        if (laneChangeState != 0) {
            return Pair(false, "变道中")
        }
        
        // 8. 方向盘角度检查
        if (kotlin.math.abs(carState.steeringAngleDeg) > MAX_STEERING_ANGLE) {
            return Pair(false, "方向盘角度过大")
        }
        
        return Pair(true, null)
    }
    
    /**
     * 判断是否需要超车
     * @return Pair<Boolean, String?> 第一个值表示是否需要超车，第二个值表示不需要超车的原因
     */
    private fun shouldOvertake(data: XiaogeVehicleData): Pair<Boolean, String?> {
        val carState = data.carState ?: return Pair(false, "车辆状态缺失")
        val lead0 = data.modelV2?.lead0 ?: return Pair(false, "前车数据缺失")
        val radarState = data.radarState
        
        // 方案3：远距离超车支持（优先检查）
        if (checkEarlyOvertakeConditions(data)) {
            return Pair(true, null)
        }
        
        val vEgo = carState.vEgo
        val vLead = lead0.v
        val vRel = radarState?.leadOne?.vRel ?: (vLead - vEgo)
        
        // 限速检查已移除（carrotMan.nRoadLimitSpeed 不再可用）
        
        // 前车速度明显低于本车
        val speedDiff = vEgo - vLead
        val speedRatio = if (vEgo > 0.1f) vLead / vEgo else 0f
        
        // 第二前车速度检查：超车道有快车接近
        val lead1 = data.modelV2?.lead1
        if (lead1 != null && lead1.prob > 0.5f) {
            val lead1Speed = lead1.v
            if ((lead1Speed - vEgo) > 5f) {
                return Pair(false, "第二前车快速接近")
            }
        }

        val needsOvertake = speedDiff >= SPEED_DIFF_THRESHOLD || speedRatio < SPEED_RATIO_THRESHOLD
        return if (needsOvertake) {
            Pair(true, null)
        } else {
            Pair(false, "前车速度正常 (≥ ${(SPEED_RATIO_THRESHOLD * 100).toInt()}%)")
        }
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
        
        // 弯道方向：左弯时禁止左超车（使用maxOrientationRate符号判断）
        val curveRate = modelV2.curvature?.maxOrientationRate ?: 0f
        if (curveRate < 0f) { // 左弯
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
        
        // 左侧无近距离车辆，且无快速接近车辆（动态调整接近速度阈值）
        val leadLeft = radarState.leadLeft
        if (leadLeft != null && leadLeft.status) {
            if (leadLeft.dRel < MIN_SAFE_DISTANCE) return null
            // 根据本车速度动态调整安全相对速度阈值
            val safeVrel = -kotlin.math.max(5f, carState.vEgo * 0.3f)
            if (leadLeft.vRel < safeVrel) return null
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
        
        // 弯道方向：右弯时禁止右超车（使用maxOrientationRate符号判断）
        val curveRate = modelV2.curvature?.maxOrientationRate ?: 0f
        if (curveRate > 0f) { // 右弯
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
        
        // 右侧无近距离车辆，且无快速接近车辆（动态调整接近速度阈值）
        val leadRight = radarState.leadRight
        if (leadRight != null && leadRight.status) {
            if (leadRight.dRel < MIN_SAFE_DISTANCE) return null
            // 根据本车速度动态调整安全相对速度阈值
            val safeVrel = -kotlin.math.max(5f, carState.vEgo * 0.3f)
            if (leadRight.vRel < safeVrel) return null
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
            ensureSoundPool()
            val (idOpt, label) = when (direction.uppercase()) {
                "LEFT" -> (soundIdLeft to "LEFT")
                "RIGHT" -> (soundIdRight to "RIGHT")
                else -> {
                    Log.w(TAG, "⚠️ 未知的变道方向: $direction，不播放音效")
                    return
                }
            }
            val id = idOpt ?: return
            soundPool?.play(id, 1f, 1f, 1, 0, 1f)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 播放${direction}变道提示音失败: ${e.message}", e)
        }
    }

    private fun playConfirmSound(direction: String) {
        try {
            ensureSoundPool()
            val idOpt = when (direction.uppercase()) {
                "LEFT" -> soundIdLeftConfirm
                "RIGHT" -> soundIdRightConfirm
                else -> null
            }
            val id = idOpt ?: return
            soundPool?.play(id, 1f, 1f, 1, 0, 1f)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 播放确认音失败(${direction}): ${e.message}", e)
        }
    }

    private fun ensureSoundPool() {
        if (soundPool != null) return
        soundPool = android.media.SoundPool.Builder().setMaxStreams(2).build()
        soundIdLeft = soundPool?.load(context, R.raw.left, 1)
        soundIdRight = soundPool?.load(context, R.raw.right, 1)
        soundIdLeftConfirm = soundPool?.load(context, R.raw.left_confirm, 1)
        soundIdRightConfirm = soundPool?.load(context, R.raw.right_confirm, 1)
    }
    
    /**
     * 方案1：动态冷却机制 - 计算动态冷却时间
     * 优化：支持PENDING状态
     */
    private fun calculateDynamicCooldown(data: XiaogeVehicleData?): Long {
        val baseCooldown = when (lastOvertakeResult) {
            OvertakeResult.SUCCESS -> COOLDOWN_SUCCESS_MS      // 成功：15秒
            OvertakeResult.FAILED -> COOLDOWN_FAILED_MS        // 失败：3秒（快速重试）
            OvertakeResult.PENDING -> COOLDOWN_BASE_MS         // 待确认：8秒（等待变道完成）
            OvertakeResult.CONDITION_NOT_MET -> COOLDOWN_CONDITION_MS  // 条件不满足：5秒
            else -> COOLDOWN_BASE_MS                         // 基础：8秒
        }
        
        // 连续失败惩罚
        var cooldown = baseCooldown
        if (consecutiveFailures > 3) {
            cooldown += minOf(10000L, consecutiveFailures * 2000L)
        }
        
        // 道路类型调整已移除（carrotMan.roadcate 不再可用）
        // 统一使用基础冷却时间，不再根据道路类型调整
        
        return cooldown
    }
    
    /**
     * 方案3：远距离超车支持（提前超车，提高通行效率）
     */
    private fun checkEarlyOvertakeConditions(data: XiaogeVehicleData): Boolean {
        // 道路类型检查已移除（carrotMan.roadcate 不再可用）
        // 远距离超车功能在所有道路类型上启用
        
        val carState = data.carState ?: return false
        val lead0 = data.modelV2?.lead0 ?: return false
        
        val vEgoKph = carState.vEgo * 3.6f
        val leadSpeedKph = lead0.v * 3.6f
        val leadDistance = lead0.x
        
        // 条件1：前车最低速度检查（避免堵车）
        if (leadSpeedKph < EARLY_OVERTAKE_MIN_LEAD_SPEED_KPH) return false
        
        // 条件2：前车速度 ≤ 60% 本车速度
        val speedRatio = if (vEgoKph > 0.1f) leadSpeedKph / vEgoKph else 1.0f
        if (speedRatio > EARLY_OVERTAKE_SPEED_RATIO) return false
        
        // 条件3：速度差 ≥ 20 km/h
        val speedDiff = vEgoKph - leadSpeedKph
        if (speedDiff < EARLY_OVERTAKE_MIN_SPEED_DIFF_KPH) return false
        
        // 条件4：距离在 30-100 米范围内
        if (leadDistance < EARLY_OVERTAKE_MIN_DISTANCE || leadDistance > EARLY_OVERTAKE_MAX_DISTANCE) return false
        
        Log.i(TAG, "🚀 远距离超车触发: 前车${leadSpeedKph.toInt()}km/h vs 本车${vEgoKph.toInt()}km/h (慢${speedDiff.toInt()}km/h, 距离${leadDistance.toInt()}m)")
        return true
    }
    
    /**
     * 方案5：记录超车开始（用于返回原车道策略）
     * 优化：使用横向距离而非绝对车道号
     */
    private fun recordOvertakeStart(direction: String, data: XiaogeVehicleData) {
        // 记录原车道位置（使用横向距离，更准确）
        if (originalLanePosition == 0f) {
            val carState = data.carState
            // 使用 leftLatDist 和 rightLatDist 计算相对位置（这些字段在 CarStateData 中）
            val leftLatDist = carState?.leftLatDist ?: 0f
            val rightLatDist = carState?.rightLatDist ?: 0f
            // 使用左侧距离作为参考位置（正值表示在车道中心左侧）
            originalLanePosition = leftLatDist
            laneMemoryStartTime = System.currentTimeMillis()
            Log.d(TAG, "🎯 开始原车道记忆: 位置${originalLanePosition.toInt()}cm, 方向: $direction")
        }
        
        // 更新净变道数
        when (direction.uppercase()) {
            "LEFT" -> netLaneChanges++
            "RIGHT" -> netLaneChanges--
        }
        
        // 重置超越完成计时器
        overtakeCompleteTimer = 0L
    }
    
    /**
     * 方案5：检查返回原车道条件
     */
    private fun checkReturnConditions(data: XiaogeVehicleData): Boolean {
        // 如果没有记录原车道位置，不需要返回
        if (originalLanePosition == 0f || netLaneChanges == 0) {
            return false
        }
        
        // 检查超时
        if (laneMemoryStartTime > 0 && 
            System.currentTimeMillis() - laneMemoryStartTime > MAX_LANE_MEMORY_TIME_MS) {
            Log.d(TAG, "⏰ 返回超时(30秒)，重置状态")
            resetLaneMemory()
            return false
        }
        
        // 检查是否完全超越
        if (!hasCompletelyOvertaken(data)) {
            return false
        }
        
        // 检查返回效率（需要8 km/h速度优势）
        if (!isReturnEfficient(data)) {
            return false
        }
        
        // 检查返回安全
        if (!isReturnSafe(data)) {
            return false
        }
        
        return true
    }
    
    /**
     * 方案5：判断是否完全超越
     * 优化：不仅检查侧方车辆，还检查原车道前车是否已在后方
     */
    private fun hasCompletelyOvertaken(data: XiaogeVehicleData): Boolean {
        val targetSide = if (netLaneChanges > 0) "right" else "left"
        val targetLead = if (targetSide == "right") {
            data.radarState?.leadRight
        } else {
            data.radarState?.leadLeft
        }
        
        // 检查原车道前车是否已在后方（优化：添加前车位置检查）
        val lead0 = data.modelV2?.lead0
        if (lead0 != null && lead0.prob > 0.5f && lead0.x < 20f) {
            // 前车仍在前方20米内，未完全超越
            overtakeCompleteTimer = 0L
            return false
        }
        
        // 目标侧无车或距离很远，已超越
        if (targetLead == null || !targetLead.status || targetLead.dRel > 50f) {
            // 等待一段时间确保完全超越
            if (overtakeCompleteTimer == 0L) {
                overtakeCompleteTimer = System.currentTimeMillis()
            }
            return System.currentTimeMillis() - overtakeCompleteTimer >= OVERTAKE_COMPLETE_DURATION_MS
        }
        
        // 重置计时器
        overtakeCompleteTimer = 0L
        return false
    }
    
    /**
     * 方案5：检查返回效率
     */
    private fun isReturnEfficient(data: XiaogeVehicleData): Boolean {
        val carState = data.carState ?: return false
        val currentSpeed = carState.vEgo * 3.6f
        // 巡航速度已移除（carrotMan.desiredSpeed 不再可用），使用当前速度作为参考
        
        // 获取目标车道（返回方向）的速度预期
        val targetSide = if (netLaneChanges > 0) "right" else "left"
        val targetLead = if (targetSide == "right") {
            data.radarState?.leadRight
        } else {
            data.radarState?.leadLeft
        }
        
        val targetSpeed = if (targetLead == null || !targetLead.status) {
            // 目标车道无车，假设可以达到更高速度（当前速度 + 10 km/h）
            currentSpeed + 10f
        } else {
            // 目标车道有车，预期速度受前车限制
            currentSpeed + targetLead.vRel * 3.6f
        }
        
        // 当前车道的预期速度
        val lead0 = data.modelV2?.lead0
        val currentSpeedExpected = if (lead0 == null) {
            currentSpeed  // 无前车时使用当前速度
        } else {
            lead0.v * 3.6f
        }
        
        // 需要至少8 km/h的速度优势
        val speedAdvantage = targetSpeed - currentSpeedExpected
        return speedAdvantage >= RETURN_MIN_SPEED_ADVANTAGE_KPH
    }
    
    /**
     * 方案5：检查返回安全
     */
    private fun isReturnSafe(data: XiaogeVehicleData): Boolean {
        val targetSide = if (netLaneChanges > 0) "right" else "left"
        val targetLead = if (targetSide == "right") {
            data.radarState?.leadRight
        } else {
            data.radarState?.leadLeft
        }
        
        val blindspot = if (targetSide == "right") {
            data.carState?.rightBlindspot ?: false
        } else {
            data.carState?.leftBlindspot ?: false
        }
        
        // 盲区检查
        if (blindspot) {
            return false
        }
        
        // 目标车道无车，安全返回
        if (targetLead == null || !targetLead.status) {
            return true
        }
        
        // 目标车道有车，判断是否安全
        val carState = data.carState ?: return false
        val currentSpeed = carState.vEgo * 3.6f
        val targetRelativeSpeed = targetLead.vRel * 3.6f
        
        // 目标车道车辆比我们快+5km/h以上，且距离安全
        if (targetRelativeSpeed > 5f) {
            val safeDistance = kotlin.math.max(30f, currentSpeed * 0.4f)
            return targetLead.dRel > safeDistance
        }
        
        // 目标车道车辆距离超过50米，安全返回
        if (targetLead.dRel > 50f) {
            return true
        }
        
        return false
    }
    
    /**
     * 方案5：重置车道记忆
     */
    private fun resetLaneMemory() {
        originalLanePosition = 0f
        netLaneChanges = 0
        laneMemoryStartTime = 0L
        overtakeCompleteTimer = 0L
    }
    
    /**
     * 🆕 创建超车状态数据
     * @param data 车辆数据
     * @param statusText 状态文本
     * @param canOvertake 是否可以超车
     * @param lastDirection 最后超车方向
     * @param cooldownRemaining 剩余冷却时间（毫秒）
     */
    private fun createOvertakeStatus(
        data: XiaogeVehicleData,
        statusText: String,
        canOvertake: Boolean,
        lastDirection: String?,
        cooldownRemaining: Long? = null,
        blockingReason: String? = null  // 🆕 阻止超车的原因
    ): OvertakeStatusData {
        val now = System.currentTimeMillis()
        val actualCooldown = cooldownRemaining ?: run {
            val isLeft = lastDirection?.equals("LEFT", ignoreCase = true) == true
            val lastTime = if (isLeft) lastCommandTimeLeft else lastCommandTimeRight
            val cooldown = calculateDynamicCooldown(data)
            (cooldown - (now - lastTime)).coerceAtLeast(0L).takeIf { it > 0 }
        }
        
        return OvertakeStatusData(
            statusText = statusText,
            canOvertake = canOvertake,
            cooldownRemaining = actualCooldown,
            lastDirection = lastDirection ?: lastOvertakeDirection,
            blockingReason = blockingReason
        )
    }
    
    /**
     * 🆕 根据变道状态更新超车结果
     * @param laneChangeState 变道状态：0=未变道, 1=变道中, 2=变道完成, 3=变道取消
     */
    private fun updateOvertakeResultFromLaneChangeState(laneChangeState: Int) {
        when (laneChangeState) {
            1 -> {
                // 变道中，保持PENDING状态
                if (lastOvertakeResult == OvertakeResult.PENDING) {
                    Log.d(TAG, "🔄 变道进行中...")
                }
            }
            2, 3 -> {
                // 变道完成或取消，根据状态更新
                if (lastOvertakeResult == OvertakeResult.PENDING) {
                    if (laneChangeState == 2) {
                        lastOvertakeResult = OvertakeResult.SUCCESS
                        consecutiveFailures = 0
                        Log.i(TAG, "✅ 变道完成，标记为成功")
                    } else {
                        lastOvertakeResult = OvertakeResult.FAILED
                        consecutiveFailures++
                        Log.w(TAG, "❌ 变道取消，标记为失败")
                    }
                    pendingOvertakeStartTime = 0L
                }
            }
        }
    }
    
    /**
     * 🆕 生成阻止超车的原因（当左右都不能超车时）
     */
    private fun generateBlockingReason(data: XiaogeVehicleData): String? {
        val carState = data.carState ?: return "车辆状态缺失"
        val modelV2 = data.modelV2 ?: return "模型数据缺失"
        val radarState = data.radarState ?: return "雷达数据缺失"
        
        // 检查左超车失败原因
        val leftLaneProb = modelV2.laneLineProbs.getOrNull(0) ?: 0f
        if (leftLaneProb < MIN_LANE_PROB) {
            return "左侧车道线置信度不足"
        }
        
        if (carState.leftLaneLine != ALLOWED_LANE_LINE_TYPE) {
            return "左侧实线禁止变道"
        }
        
        if (carState.leftBlindspot) {
            return "左侧盲区有车"
        }
        
        val leadLeft = radarState.leadLeft
        if (leadLeft != null && leadLeft.status && leadLeft.dRel < MIN_SAFE_DISTANCE) {
            return "左侧车辆距离过近"
        }
        
        // 检查右超车失败原因
        val rightLaneProb = modelV2.laneLineProbs.getOrNull(1) ?: 0f
        if (rightLaneProb < MIN_LANE_PROB) {
            return "右侧车道线置信度不足"
        }
        
        if (carState.rightLaneLine != ALLOWED_LANE_LINE_TYPE) {
            return "右侧实线禁止变道"
        }
        
        if (carState.rightBlindspot) {
            return "右侧盲区有车"
        }
        
        val leadRight = radarState.leadRight
        if (leadRight != null && leadRight.status && leadRight.dRel < MIN_SAFE_DISTANCE) {
            return "右侧车辆距离过近"
        }
        
        return "左右车道均不可用"
    }
    
    /**
     * 超车决策数据类
     */
    private data class OvertakeDecision(
        val direction: String,  // "LEFT" 或 "RIGHT"
        val reason: String      // 决策原因
    )
}

