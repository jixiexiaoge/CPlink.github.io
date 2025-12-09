package com.example.carrotamap

import android.content.Context
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
        
        // 道路类型常量
        private const val HIGHWAY_ROAD_TYPE = 0      // 高速公路
        private const val EXPRESSWAY_ROAD_TYPE = 6  // 快速路
        
        // 速度阈值
        private const val MIN_OVERTAKE_SPEED_MS = 16.67f  // 60 km/h = 16.67 m/s
        private const val SPEED_DIFF_THRESHOLD = 2.78f    // 速度差阈值 (10 km/h = 2.78 m/s)
        
        // 远距离超车参数（方案3）
        private const val EARLY_OVERTAKE_SPEED_RATIO = 0.8f   // 前车速度 ≤ 80% 本车速度
        private const val EARLY_OVERTAKE_MIN_LEAD_SPEED_KPH = 50.0f  // 前车速度 ≥ 50 km/h
        private const val EARLY_OVERTAKE_MIN_SPEED_DIFF_KPH = 20.0f  // 速度差 ≥ 20 km/h
        private const val EARLY_OVERTAKE_MIN_DISTANCE = 30.0f  // 最小距离 30m
        private const val EARLY_OVERTAKE_MAX_DISTANCE = 100.0f // 最大距离 100m
        
        // 距离阈值
        private const val MAX_LEAD_DISTANCE = 80.0f       // 最大前车距离 (m)
        
        // 车道线阈值
        private const val MIN_LANE_PROB = 0.6f            // 最小车道线置信度 (60%)
        private const val MIN_LANE_WIDTH = 2.8f           // 最小车道宽度 (m)
        // 注意：车道线类型检查已移除，允许实线变道（由openpilot系统自行判断）
        
        // 曲率阈值
        private const val MAX_CURVATURE = 0.02f            // 最大曲率 (rad/s) - 更严格的直道判断
        
        // 方向盘角度阈值
        private const val MAX_STEERING_ANGLE = 15.0f       // 最大方向盘角度 (度)
        
        // 时间参数
        private const val DEBOUNCE_FRAMES = 3             // 防抖帧数（需要连续3帧满足条件才确认超车，防止误判）
        private const val CONFIRM_SOUND_COOLDOWN_MS = 2500L  // 🆕 确认音冷却时间（2.5秒）
        private const val LANE_CHANGE_DELAY_MS = 2500L    // 🆕 变道延迟时间（2.5秒）
        private const val OVERTAKE_ACTION_COOLDOWN_MS = 20000L  // 🆕 超车操作冷却时间（20秒）
        
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
    
    // ===============================
    // 状态变量
    // ===============================
    
    // 防抖状态
    private var debounceCounter = 0
    private var lastOvertakeDirection: String? = null
    
    // 确认音冷却机制（用于拨杆模式）
    private var lastConfirmSoundTime = 0L
    
    // 🆕 超车操作冷却机制（20秒，用于所有超车相关操作）
    private var lastOvertakeActionTime = 0L  // 最后一次超车操作时间（播放提示音、确认音或发送命令）
    
    // 超车结果跟踪
    private enum class OvertakeResult { NONE, PENDING, SUCCESS, FAILED, CONDITION_NOT_MET }
    private var lastOvertakeResult = OvertakeResult.NONE
    private var pendingOvertakeStartTime = 0L  // 待确认超车开始时间
    private val PENDING_TIMEOUT_MS = 2500L  // 待确认超车超时时间（2.5秒）
    
    // 返回原车道策略（方案5）
    private var originalLanePosition = 0f  // 原始车道位置（使用横向距离）
    private var netLaneChanges = 0  // 净变道数：>0表示在左侧，<0表示在右侧
    private var laneMemoryStartTime = 0L
    private var overtakeCompleteTimer = 0L
    private val OVERTAKE_COMPLETE_DURATION_MS = 2000L  // 超越完成后等待2秒再返回
    
    // 待执行变道状态（延迟执行机制）
    private data class PendingLaneChange(
        val direction: String,      // 变道方向 "LEFT" 或 "RIGHT"
        val startTime: Long         // 开始时间（毫秒）
    )
    private var pendingLaneChange: PendingLaneChange? = null  // 待执行的变道
    
    // 🆕 性能优化：缓存超车模式和配置参数
    private var cachedOvertakeMode: Int? = null
    private var cachedOvertakeModeTime = 0L
    private val OVERTAKE_MODE_CACHE_DURATION_MS = 1000L  // 缓存1秒，减少SharedPreferences读取
    
    private var cachedMinOvertakeSpeedKph: Float? = null
    private var cachedSpeedDiffThresholdKph: Float? = null
    
    /**
     * 更新数据并判断是否需要超车
     * ✅ 优化：拆分逻辑，提高可读性和可维护性
     * @param data 车辆数据
     * @param roadType 道路类型（高德地图 ROAD_TYPE：0=高速公路，6=快速路，8=未知等）。如果为null，则不检查道路类型（向后兼容）
     * @return 更新后的超车状态数据，用于更新 XiaogeVehicleData
     */
    fun update(data: XiaogeVehicleData?, roadType: Int? = null): OvertakeStatusData? {
        // 快速失败：空数据检查
        if (data == null) return null
        
        // 获取超车模式（使用缓存优化）
        val overtakeMode = getOvertakeModeCached()
        
        // 1. 处理禁止超车模式
        if (overtakeMode == 0) {
            return handleOvertakeModeDisabled(data)
        }
        
        // 2. 处理待执行的变道（延迟执行机制）
        val pendingCheck = checkPendingLaneChange(data, overtakeMode, roadType)
        if (pendingCheck != null) return pendingCheck
        
        // 3. 处理变道中状态
        val laneChangeState = data.modelV2?.meta?.laneChangeState ?: 0
        if (laneChangeState != 0) {
            return handleLaneChangeInProgress(data, laneChangeState)
        }
        
        // 4. 处理变道完成状态
        handleLaneChangeCompleted()
        
        // 5. 检查返回原车道条件
        val returnCheck = checkReturnToOriginalLane(data, overtakeMode)
        if (returnCheck != null) return returnCheck
        
        // 6. 评估超车条件并执行决策
        return evaluateOvertakeConditions(data, overtakeMode, roadType)
    }
    
    /**
     * ✅ 优化：处理禁止超车模式
     */
    private fun handleOvertakeModeDisabled(data: XiaogeVehicleData): OvertakeStatusData {
        debounceCounter = 0
        resetLaneMemory()
        cancelPendingLaneChange()
        return createOvertakeStatus(data, "禁止超车", false, null)
    }
    
    /**
     * ✅ 优化：处理变道中状态
     */
    private fun handleLaneChangeInProgress(
        data: XiaogeVehicleData,
        laneChangeState: Int
    ): OvertakeStatusData {
            updateOvertakeResultFromLaneChangeState(laneChangeState)
            val direction = when (data.modelV2?.meta?.laneChangeDirection) {
                -1 -> "LEFT"
                1 -> "RIGHT"
                else -> null
            }
            return createOvertakeStatus(data, "变道中", false, direction)
    }
    
    /**
     * ✅ 优化：处理变道完成状态
     */
    private fun handleLaneChangeCompleted() {
        if (lastOvertakeResult == OvertakeResult.PENDING) {
            val now = System.currentTimeMillis()
            if (now - pendingOvertakeStartTime > PENDING_TIMEOUT_MS) {
                lastOvertakeResult = OvertakeResult.FAILED
                Log.w(TAG, "⏱️ 超车超时未完成，标记为失败")
            } else {
                lastOvertakeResult = OvertakeResult.SUCCESS
                Log.i(TAG, "✅ 变道完成，标记为成功")
            }
        }
        }
        
    /**
     * ✅ 优化：检查返回原车道条件
     */
    private fun checkReturnToOriginalLane(
        data: XiaogeVehicleData,
        overtakeMode: Int
    ): OvertakeStatusData? {
        if (!checkReturnConditions(data)) return null
        
            val returnDirection = if (netLaneChanges > 0) "RIGHT" else "LEFT"
            if (overtakeMode == 2) {
                sendLaneChangeCommand(returnDirection)
                Log.i(TAG, "🔄 返回原车道: $returnDirection")
                resetLaneMemory()
            }
            return createOvertakeStatus(data, "返回原车道", false, returnDirection)
        }
        
    /**
     * ✅ 优化：评估超车条件并执行决策
     */
    private fun evaluateOvertakeConditions(
        data: XiaogeVehicleData,
        overtakeMode: Int,
        roadType: Int?
    ): OvertakeStatusData {
        // 🆕 检查超车操作冷却时间（20秒）
        val now = System.currentTimeMillis()
        val timeSinceLastAction = now - lastOvertakeActionTime
        if (lastOvertakeActionTime > 0 && timeSinceLastAction < OVERTAKE_ACTION_COOLDOWN_MS) {
            val remainingCooldown = OVERTAKE_ACTION_COOLDOWN_MS - timeSinceLastAction
            return createOvertakeStatus(
                data,
                "冷却中",
                false,
                null,
                blockingReason = "超车操作冷却中，剩余 ${String.format("%.1f", remainingCooldown / 1000.0)} 秒",
                cooldownRemaining = remainingCooldown
            )
        }
        
        // 如果有待执行的变道，检查条件是否仍满足
        if (pendingLaneChange != null) {
            cancelPendingLaneChangeIfConditionsChanged(data, roadType)
        }
        
        // 检查前置条件
        val (prerequisitesMet, prerequisiteReason) = checkPrerequisites(data, roadType)
        if (!prerequisitesMet) {
            lastOvertakeResult = OvertakeResult.CONDITION_NOT_MET
            return createOvertakeStatus(data, "监控中", false, null, blockingReason = prerequisiteReason)
        }
        
        // 检查是否需要超车
        val (shouldOvertake, shouldOvertakeReason) = shouldOvertake(data)
        if (!shouldOvertake) {
            debounceCounter = 0
            lastOvertakeResult = OvertakeResult.CONDITION_NOT_MET
            return createOvertakeStatus(data, "监控中", false, null, blockingReason = shouldOvertakeReason)
        }
        
        // 防抖机制：需要连续3帧满足条件才确认超车，防止误判
        // 逻辑说明：
        // 1. 每次满足前置条件和超车条件时，debounceCounter++
        // 2. 只有当 debounceCounter >= 3 时，才真正执行超车决策
        // 3. 如果条件不满足，debounceCounter 会被重置为 0
        // 4. 这样可以避免因单帧数据异常导致的误判
        debounceCounter++
        if (debounceCounter < DEBOUNCE_FRAMES) {
            return createOvertakeStatus(data, "监控中", true, null)
        }
        
        // 评估超车方向（已通过3帧验证）
        val decision = checkOvertakeConditions(data)
        if (decision != null) {
            return handleOvertakeDecision(data, decision, overtakeMode)
        } else {
            // 超车方向不可行，重置防抖计数
            debounceCounter = 0
            lastOvertakeResult = OvertakeResult.CONDITION_NOT_MET
            val blockingReason = generateBlockingReason(data)
            return createOvertakeStatus(data, "监控中", false, null, blockingReason = blockingReason)
        }
    }
    
    /**
     * ✅ 优化：处理超车决策
     */
    private fun handleOvertakeDecision(
        data: XiaogeVehicleData,
        decision: OvertakeDecision,
        overtakeMode: Int
    ): OvertakeStatusData {
            val carState = data.carState
            val lead0 = data.modelV2?.lead0
        
            if (overtakeMode == 2) {
            // 自动超车模式：先播放提示音，记录待执行状态，2.5秒后再执行
            handleAutoOvertakeMode(decision)
        } else {
            // 拨杆模式：检查冷却时间，只播放一次确认音
            handleManualOvertakeMode(decision)
        }
        
        lastOvertakeDirection = decision.direction
        debounceCounter = 0
        
        // 记录日志
        logOvertakeDecision(decision, carState, lead0, overtakeMode)
        
        return createOvertakeStatus(
            data,
            if (overtakeMode == 2) "准备变道" else "可超车",
            true,
            decision.direction
        )
    }
    
    /**
     * ✅ 优化：处理自动超车模式
     */
    private fun handleAutoOvertakeMode(decision: OvertakeDecision) {
                if (pendingLaneChange == null) {
                    // 第一次检测到可超车，播放提示音并记录待执行状态
                    playLaneChangeSound(decision.direction)
                    // 🆕 记录超车操作时间（播放提示音）
                    lastOvertakeActionTime = System.currentTimeMillis()
                    pendingLaneChange = PendingLaneChange(
                        direction = decision.direction,
                        startTime = System.currentTimeMillis()
                    )
                    Log.i(TAG, "🔔 检测到可超车，播放提示音: ${decision.direction}, 2.5秒后执行")
                } else if (pendingLaneChange!!.direction != decision.direction) {
            // 如果方向改变，取消旧的待执行变道，开始新的
                    Log.i(TAG, "🔄 变道方向改变: ${pendingLaneChange!!.direction} -> ${decision.direction}")
                    cancelPendingLaneChange()
                    playLaneChangeSound(decision.direction)
                    // 🆕 记录超车操作时间（重新播放提示音）
                    lastOvertakeActionTime = System.currentTimeMillis()
                    pendingLaneChange = PendingLaneChange(
                        direction = decision.direction,
                        startTime = System.currentTimeMillis()
                    )
                    Log.i(TAG, "🔔 重新播放提示音: ${decision.direction}, 2.5秒后执行")
                }
    }
    
    /**
     * ✅ 优化：处理拨杆超车模式
     */
    private fun handleManualOvertakeMode(decision: OvertakeDecision) {
                val now = System.currentTimeMillis()
                if (now - lastConfirmSoundTime >= CONFIRM_SOUND_COOLDOWN_MS) {
                playConfirmSound(decision.direction)
                    lastConfirmSoundTime = now
                    // 🆕 记录超车操作时间（播放确认音）
                    lastOvertakeActionTime = now
                    Log.i(TAG, "🔔 拨杆模式播放确认音: ${decision.direction}, 原因: ${decision.reason}")
                } else {
                    val remainingCooldown = (CONFIRM_SOUND_COOLDOWN_MS - (now - lastConfirmSoundTime)) / 1000
                    Log.d(TAG, "⏱️ 拨杆模式冷却中，剩余${remainingCooldown}秒")
                }
            }
    
    /**
     * ✅ 优化：记录超车决策日志
     */
    private fun logOvertakeDecision(
        decision: OvertakeDecision,
        carState: CarStateData?,
        lead0: LeadData?,
        overtakeMode: Int
    ) {
            val logContext = if (carState != null && lead0 != null) {
                ", 本车${(carState.vEgo * 3.6f).toInt()}km/h, 前车${(lead0.v * 3.6f).toInt()}km/h, 距离${lead0.x.toInt()}m"
            } else {
                ""
            }
        
            if (overtakeMode == 2) {
                val remainingTime = if (pendingLaneChange != null) {
                    val elapsed = System.currentTimeMillis() - pendingLaneChange!!.startTime
                    val remaining = (LANE_CHANGE_DELAY_MS - elapsed) / 1000
                    if (remaining > 0) " (${remaining}秒后执行)" else " (即将执行)"
                } else {
                    ""
                }
                Log.i(TAG, "⏳ 待执行超车: ${decision.direction}, 原因: ${decision.reason}$logContext$remainingTime")
            }
    }
    
    /**
     * ✅ 优化：如果条件改变，取消待执行变道
     */
    private fun cancelPendingLaneChangeIfConditionsChanged(data: XiaogeVehicleData, roadType: Int?) {
        val (prerequisitesMet, _) = checkPrerequisites(data, roadType)
        val (shouldOvertake, _) = shouldOvertake(data)
        val decision = checkOvertakeConditions(data)
        
        if (!prerequisitesMet || !shouldOvertake || decision == null || 
            decision.direction != pendingLaneChange!!.direction) {
            cancelPendingLaneChange()
        }
    }
    
    /**
     * ✅ 优化：获取当前超车模式（带缓存）
     * @return 0=禁止超车, 1=拨杆超车, 2=自动超车
     */
    private fun getOvertakeModeCached(): Int {
        val now = System.currentTimeMillis()
        if (cachedOvertakeMode != null && 
            (now - cachedOvertakeModeTime) < OVERTAKE_MODE_CACHE_DURATION_MS) {
            return cachedOvertakeMode!!
        }
        
        val mode = try {
            context.getSharedPreferences("CarrotAmap", Context.MODE_PRIVATE)
                .getInt("overtake_mode", 0)
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ 获取超车模式失败，使用默认值0: ${e.message}")
            0
        }
        
        cachedOvertakeMode = mode
        cachedOvertakeModeTime = now
        return mode
    }
    
    /**
     * ✅ 优化：获取可配置参数：最小超车速度 (km/h)（带缓存）
     * 默认值：60 km/h，范围：40-100 km/h
     */
    private fun getMinOvertakeSpeedKph(): Float {
        if (cachedMinOvertakeSpeedKph != null) {
            return cachedMinOvertakeSpeedKph!!
        }
        
        val value = try {
            val prefs = context.getSharedPreferences("CarrotAmap", Context.MODE_PRIVATE)
            val defaultValue = MIN_OVERTAKE_SPEED_MS * 3.6f  // 从常量计算默认值 (60 km/h)
            val v = prefs.getFloat("overtake_param_min_speed_kph", defaultValue)
            v.coerceIn(40f, 100f)  // 限制范围
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ 获取最小超车速度失败，使用默认值60: ${e.message}")
            MIN_OVERTAKE_SPEED_MS * 3.6f  // 使用常量作为后备值
        }
        
        cachedMinOvertakeSpeedKph = value
        return value
    }
    
    /**
     * ✅ 优化：获取可配置参数：速度差阈值 (km/h)（带缓存）
     * 默认值：10 km/h，范围：5-30 km/h
     */
    private fun getSpeedDiffThresholdKph(): Float {
        if (cachedSpeedDiffThresholdKph != null) {
            return cachedSpeedDiffThresholdKph!!
        }
        
        val value = try {
            val prefs = context.getSharedPreferences("CarrotAmap", Context.MODE_PRIVATE)
            val defaultValue = SPEED_DIFF_THRESHOLD * 3.6f  // 从常量计算默认值 (10 km/h)
            val v = prefs.getFloat("overtake_param_speed_diff_kph", defaultValue)
            v.coerceIn(5f, 30f)  // 限制范围
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ 获取速度差阈值失败，使用默认值10: ${e.message}")
            SPEED_DIFF_THRESHOLD * 3.6f  // 使用常量作为后备值
        }
        
        cachedSpeedDiffThresholdKph = value
        return value
    }
    
    /**
     * ✅ 优化：检查前置条件（必须全部满足）
     * 简化版：只保留6项必要检查
     * 优化：使用快速失败原则，先检查最可能失败的条件
     * @param data 车辆数据
     * @param roadType 道路类型（高德地图 ROAD_TYPE）。如果为null，则不检查道路类型（向后兼容）
     * @return Pair<Boolean, String?> 第一个值表示是否满足条件，第二个值表示不满足时的原因
     */
    private fun checkPrerequisites(data: XiaogeVehicleData, roadType: Int?): Pair<Boolean, String?> {
        val carState = data.carState ?: return Pair(false, "车辆状态缺失")
        val modelV2 = data.modelV2 ?: return Pair(false, "模型数据缺失")
        
        // ✅ 优化：快速失败 - 先检查最可能失败的条件
        
        // 0. 🆕 检查道路类型：只有高速公路（0）或快速路（6）才允许超车
        if (roadType != null) {
            if (roadType != HIGHWAY_ROAD_TYPE && roadType != EXPRESSWAY_ROAD_TYPE) {
                val roadTypeDesc = getRoadTypeDescriptionInternal(roadType)
                return Pair(false, "非高速公路或快速路 (当前: $roadTypeDesc)")
            }
        }
        
        // 1. 🆕 检查转弯距离：如果距离转弯点小于2000米，禁止超车
        if (data.tbtDist > 0 && data.tbtDist < 2000) {
            return Pair(false, "接近转弯点 (< ${data.tbtDist}m)")
        }
        
        // 2. 若系统正在变道，禁止新的超车（快速失败）
        val laneChangeState = modelV2.meta?.laneChangeState ?: 0
        if (laneChangeState != 0) {
            return Pair(false, "变道中")
        }
        
        // 3. 前车存在且距离较近（快速失败）
        val lead0 = modelV2.lead0
        if (lead0 == null || lead0.x >= MAX_LEAD_DISTANCE || lead0.prob < 0.5f) {
            return Pair(false, "前车距离过远或置信度不足")
        }
        
        // 4. 速度满足要求（使用可配置参数）
        val minOvertakeSpeedKph = getMinOvertakeSpeedKph()
        val minOvertakeSpeedMs = minOvertakeSpeedKph * MS_PER_KMH
        if (carState.vEgo < minOvertakeSpeedMs) {
            return Pair(false, "速度过低 (< ${minOvertakeSpeedKph.toInt()} km/h)")
        }
        
        // 5. 前车最低速度限制（避免堵车误判）
        val leadSpeedKmh = lead0.v * 3.6f
        val minLeadSpeed = 50.0f  // 统一使用50 km/h作为最低速度阈值
        if (leadSpeedKmh < minLeadSpeed) {
            return Pair(false, "前车速度过低 (< ${minLeadSpeed.toInt()} km/h)")
        }
        
        // 6. 不在弯道 (使用更严格的阈值)
        val curvature = modelV2.curvature
        if (curvature != null && kotlin.math.abs(curvature.maxOrientationRate) >= MAX_CURVATURE) {
            return Pair(false, "弯道中 (曲率过大)")
        }
        
        // 7. 方向盘角度检查
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
        
        // 方案3：远距离超车支持（优先检查）
        if (checkEarlyOvertakeConditions(data)) {
            return Pair(true, null)
        }
        
        val vEgo = carState.vEgo
        val vLead = lead0.v
        
        // 前车速度明显低于本车（只检查速度差，移除速度比例检查）
        val speedDiff = vEgo - vLead

        // 使用可配置参数（只检查速度差）
        val speedDiffThreshold = getSpeedDiffThresholdKph() * MS_PER_KMH  // 转换为 m/s
        val needsOvertake = speedDiff >= speedDiffThreshold
        return if (needsOvertake) {
            Pair(true, null)
        } else {
            Pair(false, "速度差不足 (< ${getSpeedDiffThresholdKph().toInt()} km/h)")
        }
    }
    
    /**
     * 检查超车条件并返回决策
     */
    private fun checkOvertakeConditions(data: XiaogeVehicleData): OvertakeDecision? {
        val carState = data.carState ?: return null
        val modelV2 = data.modelV2 ?: return null
        
        // 检查左超车可行性（使用 modelV2 数据，纯视觉方案）
        val leftOvertake = checkLeftOvertakeFeasibility(carState, modelV2)
        
        // 检查右超车可行性（使用 modelV2 数据，纯视觉方案）
        val rightOvertake = checkRightOvertakeFeasibility(carState, modelV2)
        
        // 选择最优方向（优先左超车，符合中国交通规则）
        return when {
            leftOvertake != null -> leftOvertake
            rightOvertake != null -> rightOvertake
            else -> null
        }
    }
    
    /**
     * ✅ 优化：检查左超车可行性（纯视觉方案）
     * 简化版：只保留车道线置信度、车道宽度、盲区检查
     */
    private fun checkLeftOvertakeFeasibility(
        carState: CarStateData,
        modelV2: ModelV2Data
    ): OvertakeDecision? {
        return checkOvertakeFeasibility(
            direction = "LEFT",
            laneProb = modelV2.laneLineProbs.getOrNull(0),
            laneWidth = modelV2.meta?.laneWidthLeft,
            hasBlindspot = carState.leftBlindspot
        )
    }
    
    /**
     * ✅ 优化：检查右超车可行性（纯视觉方案）
     * 简化版：只保留车道线置信度、车道宽度、盲区检查
     */
    private fun checkRightOvertakeFeasibility(
        carState: CarStateData,
        modelV2: ModelV2Data
    ): OvertakeDecision? {
        return checkOvertakeFeasibility(
            direction = "RIGHT",
            laneProb = modelV2.laneLineProbs.getOrNull(1),
            laneWidth = modelV2.meta?.laneWidthRight,
            hasBlindspot = carState.rightBlindspot
        )
    }
    
    /**
     * ✅ 优化：提取左右超车检查的公共逻辑，减少代码重复
     */
    private fun checkOvertakeFeasibility(
        direction: String,
        laneProb: Float?,
        laneWidth: Float?,
        hasBlindspot: Boolean
    ): OvertakeDecision? {
        // 1. 车道线置信度检查
        if (laneProb == null || laneProb < MIN_LANE_PROB) {
            return null
        }
        
        // 2. 车道宽度检查
        if (laneWidth == null || laneWidth < MIN_LANE_WIDTH) {
            return null
        }
        
        // 3. 盲区检查
        if (hasBlindspot) {
            return null
        }
        
        return OvertakeDecision(direction, "${if (direction == "LEFT") "左" else "右"}超车条件满足")
    }
    
    /**
     * 发送变道命令
     * 发送命令给comma3（不播放提示音，因为已在2.5秒前播放）
     */
    private fun sendLaneChangeCommand(direction: String, playSound: Boolean = false) {
        try {
            // 发送变道命令给comma3
            networkManager.sendControlCommand("LANECHANGE", direction)
            // 🆕 记录超车操作时间（发送变道命令）
            lastOvertakeActionTime = System.currentTimeMillis()
            Log.i(TAG, "📤 已发送变道命令: $direction")
            
            // 🆕 可选：播放变道提示音（默认不播放，因为已在2.5秒前播放过）
            if (playSound) {
            playLaneChangeSound(direction)
            }
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
            val idOpt = when (direction.uppercase()) {
                "LEFT" -> soundIdLeft
                "RIGHT" -> soundIdRight
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
        
        // 条件2：前车速度 ≤ 80% 本车速度
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
            // 使用左侧距离作为参考位置（正值表示在车道中心左侧）
            val leftLatDist = carState?.leftLatDist ?: 0f
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
        // 使用 modelV2 数据（纯视觉方案）
        val targetSide = if (netLaneChanges > 0) "right" else "left"
        val targetLead = if (targetSide == "right") {
            data.modelV2?.leadRight
        } else {
            data.modelV2?.leadLeft
        }
        
        // 检查原车道前车是否已在后方（优化：结合横向位置判断）
        val lead0 = data.modelV2?.lead0
        if (lead0 != null && lead0.prob > 0.5f) {
            // ✅ 修复：使用横向位置判断前车是否在原车道
            // 根据 Python 端定义：y > 0 表示车辆在右侧，y < 0 表示车辆在左侧
            // netLaneChanges > 0 表示在左侧，原车道在右侧（y > 0）
            // netLaneChanges < 0 表示在右侧，原车道在左侧（y < 0）
            val targetY = if (netLaneChanges > 0) {
                // 在左侧，原车道在右侧，y 应该 > 0
                1.5f  // 假设车道宽度约 3.5m，车道中心偏移约 1.5m
            } else {
                // 在右侧，原车道在左侧，y 应该 < 0
                -1.5f
            }
            
            // 检查前车是否在原车道（横向位置接近 targetY）
            // 如果前车距离较近（< 20m）且横向位置接近原车道（|y - targetY| < 1.0m），说明仍在原车道前方
            if (lead0.x < 20f && kotlin.math.abs(lead0.y - targetY) < 1.0f) {
                // 前车仍在前方20米内且在原车道，未完全超越
                overtakeCompleteTimer = 0L
                return false
            }
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
        // 使用 modelV2 数据（纯视觉方案）
        val targetSide = if (netLaneChanges > 0) "right" else "left"
        val targetLead = if (targetSide == "right") {
            data.modelV2?.leadRight
        } else {
            data.modelV2?.leadLeft
        }
        
        val targetSpeed = if (targetLead == null || !targetLead.status) {
            // 目标车道无车，假设可以达到更高速度（当前速度 + 10 km/h）
            currentSpeed + 10f
        } else {
            // ✅ 修复：目标车道有车，预期速度受前车限制
            // vRel 是相对速度 (m/s)，需要加上本车速度才是目标车道前车的绝对速度
            (carState.vEgo + targetLead.vRel) * 3.6f
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
        // 使用 modelV2 数据（纯视觉方案）
        val targetSide = if (netLaneChanges > 0) "right" else "left"
        val targetLead = if (targetSide == "right") {
            data.modelV2?.leadRight
        } else {
            data.modelV2?.leadLeft
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
     * @param statusText 状态文本
     * @param canOvertake 是否可以超车
     * @param lastDirection 最后超车方向
     * @param blockingReason 阻止超车的原因
     * @param cooldownRemaining 冷却剩余时间（毫秒），如果为null则自动计算
     */
    private fun createOvertakeStatus(
        data: XiaogeVehicleData,
        statusText: String,
        canOvertake: Boolean,
        lastDirection: String?,
        blockingReason: String? = null,  // 🆕 阻止超车的原因
        cooldownRemaining: Long? = null  // 🆕 冷却剩余时间（毫秒），如果为null则自动计算
    ): OvertakeStatusData {
        // 🆕 自动计算冷却剩余时间（如果未指定）
        val calculatedCooldown = cooldownRemaining ?: run {
            if (lastOvertakeActionTime > 0) {
                val elapsed = System.currentTimeMillis() - lastOvertakeActionTime
                if (elapsed < OVERTAKE_ACTION_COOLDOWN_MS) {
                    OVERTAKE_ACTION_COOLDOWN_MS - elapsed
                } else {
                    null  // 冷却已完成
                }
            } else {
                null  // 没有操作记录
            }
        }
        
        return OvertakeStatusData(
            statusText = statusText,
            canOvertake = canOvertake,
            cooldownRemaining = calculatedCooldown,
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
                        Log.i(TAG, "✅ 变道完成，标记为成功")
                    } else {
                        lastOvertakeResult = OvertakeResult.FAILED
                        Log.w(TAG, "❌ 变道取消，标记为失败")
                    }
                    pendingOvertakeStartTime = 0L
                }
            }
        }
    }
    
    /**
     * 🆕 检查待执行的变道（延迟执行机制）
     * 如果超过2.5秒且条件仍满足，则执行变道；如果条件不满足，则取消
     * @param data 车辆数据
     * @param overtakeMode 超车模式
     * @param roadType 道路类型（高德地图 ROAD_TYPE）。如果为null，则不检查道路类型（向后兼容）
     * @return 如果有待执行变道，返回状态数据；否则返回null
     */
    private fun checkPendingLaneChange(data: XiaogeVehicleData, overtakeMode: Int, roadType: Int?): OvertakeStatusData? {
        val pending = pendingLaneChange ?: return null
        
        val now = System.currentTimeMillis()
        val elapsed = now - pending.startTime
        
        // 如果还未到2.5秒，继续等待
        if (elapsed < LANE_CHANGE_DELAY_MS) {
            val remainingSeconds = (LANE_CHANGE_DELAY_MS - elapsed) / 1000
            return createOvertakeStatus(
                data,
                "准备变道 (${remainingSeconds}秒)",
                true,
                pending.direction
            )
        }
        
        // 已超过2.5秒，检查条件是否仍满足
        // 1. 检查前置条件
        val (prerequisitesMet, prerequisiteReason) = checkPrerequisites(data, roadType)
        if (!prerequisitesMet) {
            // 前置条件不满足，取消变道
            Log.w(TAG, "❌ 待执行变道取消：前置条件不满足 - $prerequisiteReason")
            cancelPendingLaneChange()
            return createOvertakeStatus(
                data,
                "监控中",
                false,
                null,
                blockingReason = prerequisiteReason
            )
        }
        
        // 2. 检查是否需要超车
        val (shouldOvertake, shouldOvertakeReason) = shouldOvertake(data)
        if (!shouldOvertake) {
            // 不需要超车，取消变道
            Log.w(TAG, "❌ 待执行变道取消：不需要超车 - $shouldOvertakeReason")
            cancelPendingLaneChange()
            return createOvertakeStatus(
                data,
                "监控中",
                false,
                null,
                blockingReason = shouldOvertakeReason
            )
        }
        
        // 3. 检查变道方向是否仍然可行
        val decision = checkOvertakeConditions(data)
        if (decision == null || decision.direction != pending.direction) {
            // 变道方向不可行或方向改变，取消变道
            val reason = if (decision == null) {
                "变道条件不满足"
            } else {
                "变道方向改变 (${pending.direction} -> ${decision.direction})"
            }
            Log.w(TAG, "❌ 待执行变道取消：$reason")
            cancelPendingLaneChange()
            return createOvertakeStatus(
                data,
                "监控中",
                false,
                null,
                blockingReason = reason
            )
        }
        
        // 4. 所有条件满足，执行变道
        val direction = pending.direction
        sendLaneChangeCommand(direction, playSound = false)  // 不播放音效，因为已在2.5秒前播放
        recordOvertakeStart(direction, data)
        lastOvertakeResult = OvertakeResult.PENDING
        pendingOvertakeStartTime = now
        cancelPendingLaneChange()  // 清除待执行状态
        
        val carState = data.carState
        val lead0 = data.modelV2?.lead0
        val logContext = if (carState != null && lead0 != null) {
            ", 本车${(carState.vEgo * 3.6f).toInt()}km/h, 前车${(lead0.v * 3.6f).toInt()}km/h, 距离${lead0.x.toInt()}m"
        } else {
            ""
        }
        Log.i(TAG, "✅ 执行变道命令: $direction, 原因: ${decision.reason}$logContext")
        
        return createOvertakeStatus(data, "变道中", false, direction)
    }
    
    /**
     * 🆕 取消待执行的变道
     */
    private fun cancelPendingLaneChange() {
        if (pendingLaneChange != null) {
            Log.d(TAG, "🔄 取消待执行变道: ${pendingLaneChange!!.direction}")
            pendingLaneChange = null
        }
    }
    
    /**
     * 🆕 生成阻止超车的原因（当左右都不能超车时）
     * 简化版：与 checkLeftOvertakeFeasibility 和 checkRightOvertakeFeasibility 保持一致
     */
    private fun generateBlockingReason(data: XiaogeVehicleData): String? {
        val carState = data.carState ?: return "车辆状态缺失"
        val modelV2 = data.modelV2 ?: return "模型数据缺失"
        
        // 检查左超车失败原因（只检查简化后的3项）
        val leftLaneProb = modelV2.laneLineProbs.getOrNull(0) ?: 0f
        if (leftLaneProb < MIN_LANE_PROB) {
            return "左侧车道线置信度不足"
        }
        
        val laneWidthLeft = modelV2.meta?.laneWidthLeft
        if (laneWidthLeft == null || laneWidthLeft < MIN_LANE_WIDTH) {
            return "左侧车道宽度不足"
        }
        
        if (carState.leftBlindspot) {
            return "左侧盲区有车"
        }
        
        // 检查右超车失败原因（只检查简化后的3项）
        val rightLaneProb = modelV2.laneLineProbs.getOrNull(1) ?: 0f
        if (rightLaneProb < MIN_LANE_PROB) {
            return "右侧车道线置信度不足"
        }
        
        val laneWidthRight = modelV2.meta?.laneWidthRight
        if (laneWidthRight == null || laneWidthRight < MIN_LANE_WIDTH) {
            return "右侧车道宽度不足"
        }
        
        if (carState.rightBlindspot) {
            return "右侧盲区有车"
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
    
    /**
     * 获取道路类型描述（内部使用）
     * @param roadType 道路类型（高德地图 ROAD_TYPE）
     * @return 道路类型的中文描述
     */
    private fun getRoadTypeDescriptionInternal(roadType: Int): String {
        return when (roadType) {
            0 -> "高速公路"
            1 -> "国道"
            2 -> "省道"
            3 -> "县道"
            4 -> "乡公路"
            5 -> "县乡村内部道路"
            6 -> "快速道"
            7 -> "主要道路"
            8 -> "次要道路"
            9 -> "普通道路"
            10 -> "非导航道路"
            else -> "未知道路类型($roadType)"
        }
    }
}

