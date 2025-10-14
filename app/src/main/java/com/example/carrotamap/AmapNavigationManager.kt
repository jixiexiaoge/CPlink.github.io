package com.example.carrotamap

import android.content.Intent
import android.util.Log
import androidx.compose.runtime.MutableState

/**
 * 高德地图导航管理器
 * 负责处理导航控制相关功能：路线规划、开始导航、停止导航等
 */
class AmapNavigationManager(
    private val carrotManFields: MutableState<CarrotManFields>,
    private val destinationManager: AmapDestinationManager,
    private val updateUI: (String) -> Unit
) {
    companion object {
        private const val TAG = "AmapNavigationManager"
    }

    /**
     * 🎯 处理路线规划
     */
    fun handleRoutePlanning(intent: Intent) {
        Log.i(TAG, "🗺️ 处理路线规划")

        val startLat = intent.getDoubleExtra("start_latitude", 0.0)
        val startLon = intent.getDoubleExtra("start_longitude", 0.0)
        val endLat = intent.getDoubleExtra("end_latitude", 0.0)
        val endLon = intent.getDoubleExtra("end_longitude", 0.0)
        val endName = intent.getStringExtra("end_name") ?: ""

        if (endLat != 0.0 && endLon != 0.0) {
            Log.d(TAG, "   起点: ($startLat, $startLon)")
            Log.d(TAG, "   终点: $endName ($endLat, $endLon)")

            // 创建合成的目的地Intent并处理
            val syntheticIntent = Intent().apply {
                putExtra("endPOIName", endName)
                putExtra("endPOILatitude", endLat)
                putExtra("endPOILongitude", endLon)
                putExtra("ROUTE_REMAIN_DIS", 0)  // 规划阶段暂无距离信息
                putExtra("ROUTE_REMAIN_TIME", 0)
            }

            destinationManager.handleDestinationInfo(syntheticIntent)
        }
    }

    /**
     * 🎯 处理开始导航
     */
    fun handleStartNavigation(intent: Intent) {
        Log.i(TAG, "🚀 开始导航")

        carrotManFields.value = carrotManFields.value.copy(
            isNavigating = true,
            active_carrot = 1,
            lastUpdateTime = System.currentTimeMillis()
        )

        // 如果有目的地信息，重新发送到comma3
        val currentFields = carrotManFields.value
        if (currentFields.goalPosX != 0.0 && currentFields.goalPosY != 0.0 && currentFields.szGoalName.isNotEmpty()) {
            // 通过destinationManager发送目的地信息
            val syntheticIntent = Intent().apply {
                putExtra("endPOIName", currentFields.szGoalName)
                putExtra("endPOILatitude", currentFields.goalPosY)
                putExtra("endPOILongitude", currentFields.goalPosX)
                putExtra("endPOIAddr", "导航开始")
            }
            destinationManager.handleDestinationInfo(syntheticIntent)
        }

        updateUI("导航已开始")
    }

    /**
     * 🎯 处理停止导航
     */
    fun handleStopNavigation(intent: Intent) {
        Log.i(TAG, "🛑 停止导航")

        carrotManFields.value = carrotManFields.value.copy(
            isNavigating = false,
            active_carrot = 0,
            nGoPosDist = 0,
            nGoPosTime = 0,
            nTBTDist = 0,
            szTBTMainText = "",
            lastUpdateTime = System.currentTimeMillis()
        )

        updateUI("导航已停止")
    }
}
