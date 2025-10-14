package com.example.carrotamap

import android.util.Log
import androidx.compose.runtime.MutableState

/**
 * 高德地图数据处理器 (简化版)
 * 负责基础的数据解析和映射，移除复杂的算法计算
 */
class AmapDataProcessor(
    private val carrotManFields: MutableState<CarrotManFields>
) {
    companion object {
        private const val TAG = "AmapDataProcessor"
    }

    /**
     * 简化的倒计时更新 - 只做基础的数据映射
     */
    fun updateTrafficCountdowns(segRemainDis: Int, segRemainTime: Int, totalRemainDis: Int, totalRemainTime: Int, currentSpeed: Double) {
        val f = carrotManFields.value

        // 简化的倒计时计算 - 直接使用导航提供的时间
        val leftTbtSec = if (segRemainTime > 0) segRemainTime else 0
        val leftSpdSec = if (f.nSdiDist > 0 && currentSpeed > 0) (f.nSdiDist / (currentSpeed / 3.6)).toInt() else 0
        val leftSec = if (leftTbtSec > 0) leftTbtSec else leftSpdSec

        // 更新字段 - 移除复杂的状态判断
        carrotManFields.value = f.copy(
            left_tbt_sec = leftTbtSec,
            left_spd_sec = leftSpdSec,
            left_sec = leftSec,
            carrot_left_sec = leftSec
        )

        Log.d(TAG, "⏱️ 倒计时更新: TBT=${leftTbtSec}s, SPD=${leftSpdSec}s")
    }

    /**
     * 简化的速度控制更新 - 只做基础的数据映射
     */
    fun updateSpeedControl() {
        val f = carrotManFields.value

        // 简化的速度选择逻辑 - 优先级：摄像头 > 道路限速
        val (speedLimit, speedDist, speedType) = when {
            f.nSdiType > 0 && f.nSdiSpeedLimit > 0 -> {
                Triple(f.nSdiSpeedLimit, f.nSdiDist, f.nSdiType)
            }
            f.nRoadLimitSpeed > 0 -> {
                Triple(f.nRoadLimitSpeed, 0, -1)
            }
            else -> {
                Triple(0, 0, -1)
            }
        }

        // 距离微调：当距离小于100时，直接减50（允许出现负值）
        val adjustedDist = if (speedDist < 100) speedDist - 50 else speedDist

        // 更新字段
        carrotManFields.value = f.copy(
            xSpdLimit = speedLimit,
            xSpdDist = adjustedDist,
            xSpdType = speedType
        )

        if (speedLimit > 0) {
            Log.d(TAG, "🎯 速度控制更新: 限速=${speedLimit}km/h, 距离原=${speedDist}m, 距离调=${adjustedDist}m, 类型=$speedType")
        }
    }

    /**
     * 智能道路限速更新 - 检测变化并立即更新
     */
    fun updateRoadSpeedLimit(newLimit: Int) {
        if (newLimit <= 0) return

        val currentLimit = carrotManFields.value.nRoadLimitSpeed
        val hasChanged = newLimit != currentLimit

        if (hasChanged) {
            Log.i(TAG, "🚦 限速变化检测: ${currentLimit}km/h -> ${newLimit}km/h")
            
            carrotManFields.value = carrotManFields.value.copy(
                nRoadLimitSpeed = newLimit,
                lastUpdateTime = System.currentTimeMillis()
            )

            // 重新计算速度控制
            updateSpeedControl()
            
            // 标记需要立即发送
            carrotManFields.value = carrotManFields.value.copy(
                needsImmediateSend = true
            )
            
            Log.i(TAG, "✅ 限速已更新并标记立即发送")
        } else {
            Log.v(TAG, "🚦 限速无变化: ${newLimit}km/h")
        }
    }
}
