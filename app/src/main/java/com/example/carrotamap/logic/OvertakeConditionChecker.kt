package com.example.carrotamap.logic

import com.example.carrotamap.XiaogeVehicleData
import com.example.carrotamap.ui.components.CheckCondition
import kotlin.math.abs

/**
 * 超车条件检查逻辑类
 * 将业务逻辑从 UI 组件中解耦，便于测试和维护
 */
class OvertakeConditionChecker {
    
    // 常量定义（与 AutoOvertakeManager.kt 保持一致）
    companion object {
        const val MAX_LEAD_DISTANCE = 80.0f
        const val MIN_LEAD_PROB = 0.5f
        const val MIN_LEAD_SPEED_KPH = 50.0f
        const val MAX_CURVATURE = 0.02f
        const val MAX_STEERING_ANGLE = 15.0f
        const val MIN_LANE_PROB = 0.7f
        const val MIN_LANE_WIDTH = 3.0f
    }

    /**
     * 数据信息面板需要的检查条件列表
     */
    fun getCheckConditions(
        data: XiaogeVehicleData?,
        minOvertakeSpeedKph: Float,
        speedDiffThresholdKph: Float
    ): List<CheckCondition> {
        val carState = data?.carState
        val modelV2 = data?.modelV2
        val lead0 = modelV2?.lead0
        
        return buildList {
            // 1. 本车速度
            val vEgoKmh = (carState?.vEgo ?: 0f) * 3.6f
            add(CheckCondition(
                name = "① 本车速度",
                threshold = "≥ ${minOvertakeSpeedKph.toInt()} km/h",
                actual = "${String.format("%.1f", vEgoKmh)} km/h",
                isMet = vEgoKmh >= minOvertakeSpeedKph
            ))
            
            // 2. 方向盘角度
            val steeringAngle = abs(carState?.steeringAngleDeg ?: 0f)
            add(CheckCondition(
                name = "② 方向盘角度",
                threshold = "≤ ${MAX_STEERING_ANGLE.toInt()}°",
                actual = "${String.format("%.1f", steeringAngle)}°",
                isMet = steeringAngle <= MAX_STEERING_ANGLE
            ))
            
            // 3. 前车距离
            val leadDistance = lead0?.x ?: 0f
            val leadProb = lead0?.prob ?: 0f
            val hasValidLead = lead0 != null && leadDistance < MAX_LEAD_DISTANCE && leadProb >= MIN_LEAD_PROB
            add(CheckCondition(
                name = "③ 前车距离",
                threshold = "< ${MAX_LEAD_DISTANCE.toInt()}m",
                actual = if (lead0 != null) "${String.format("%.1f", leadDistance)}m" else "无车",
                isMet = hasValidLead
            ))
            
            // 4. 前车速度
            val leadSpeedKmh = (lead0?.v ?: 0f) * 3.6f
            add(CheckCondition(
                name = "④ 前车速度",
                threshold = "≥ ${MIN_LEAD_SPEED_KPH.toInt()} km/h",
                actual = if (lead0 != null) "${String.format("%.1f", leadSpeedKmh)} km/h" else "N/A",
                isMet = leadSpeedKmh >= MIN_LEAD_SPEED_KPH
            ))
            
            // 5. 速度差
            val speedDiff = vEgoKmh - leadSpeedKmh
            add(CheckCondition(
                name = "⑤ 速度差",
                threshold = "≥ ${speedDiffThresholdKph.toInt()} km/h",
                actual = if (lead0 != null) "${String.format("%.1f", speedDiff)} km/h" else "N/A",
                isMet = speedDiff >= speedDiffThresholdKph
            ))
            
            // 6. 道路曲率
            val curvature = abs(modelV2?.curvature?.maxOrientationRate ?: 0f)
            add(CheckCondition(
                name = "⑥ 道路曲率",
                threshold = "< ${(MAX_CURVATURE * 1000).toInt()} mrad/s",
                actual = "${String.format("%.3f", curvature)} rad/s",
                isMet = curvature < MAX_CURVATURE
            ))

            // -- 左侧条件 --
            val leftLaneProb = modelV2?.laneLineProbs?.getOrNull(0) ?: 0f
            add(CheckCondition(
                name = "⑦ 左车道线",
                threshold = "≥ ${(MIN_LANE_PROB * 100).toInt()}%",
                actual = "${String.format("%.0f", leftLaneProb * 100)}%",
                isMet = leftLaneProb >= MIN_LANE_PROB
            ))
            
            val leftBlindspot = carState?.leftBlindspot == true
            add(CheckCondition(
                name = "⑧ 左盲区",
                threshold = "无车",
                actual = if (leftBlindspot) "有车" else "无车",
                isMet = !leftBlindspot
            ))
            
            // -- 右侧条件 --
            val rightLaneProb = modelV2?.laneLineProbs?.getOrNull(1) ?: 0f
            add(CheckCondition(
                name = "⑨ 右车道线",
                threshold = "≥ ${(MIN_LANE_PROB * 100).toInt()}%",
                actual = "${String.format("%.0f", rightLaneProb * 100)}%",
                isMet = rightLaneProb >= MIN_LANE_PROB
            ))
            
            val rightBlindspot = carState?.rightBlindspot == true
            add(CheckCondition(
                name = "⑩ 右盲区",
                threshold = "无车",
                actual = if (rightBlindspot) "有车" else "无车",
                isMet = !rightBlindspot
            ))

            val roadEdgeLeft = modelV2?.meta?.distanceToRoadEdgeLeft ?: 0f
            add(CheckCondition(
                name = "⑪ 左路边缘",
                threshold = "> 0.5m",
                actual = "${String.format("%.2f", roadEdgeLeft)}m",
                isMet = roadEdgeLeft > 0.5f
            ))

            val roadEdgeRight = modelV2?.meta?.distanceToRoadEdgeRight ?: 0f
            add(CheckCondition(
                name = "⑫ 右路边缘",
                threshold = "> 0.5m",
                actual = "${String.format("%.2f", roadEdgeRight)}m",
                isMet = roadEdgeRight > 0.5f
            ))
        }
    }
}
