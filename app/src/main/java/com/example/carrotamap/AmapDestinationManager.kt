package com.example.carrotamap

import android.content.Intent
import android.util.Log
import androidx.compose.runtime.MutableState
import org.json.JSONObject

/**
 * 高德地图目的地管理器
 * 负责处理目的地信息、收藏点、家庭/公司地址等相关功能
 */
class AmapDestinationManager(
    private val carrotManFields: MutableState<CarrotManFields>,
    private val networkManager: NetworkManager,
    private val updateUI: (String) -> Unit
) {
    companion object {
        private const val TAG = "AmapDestinationManager"
    }

    // 目的地缓存
    private val destinationCache = mutableMapOf<String, Triple<Double, Double, String>>()

    /**
     * 🎯 处理和验证目的地信息
     * 从高德地图获取目的地信息并自动发送给comma3设备
     */
    fun handleDestinationInfo(intent: Intent) {
        // 从高德地图获取目的地信息
        val endPOIName = intent.getStringExtra("endPOIName") ?: ""
        val endPOIAddr = intent.getStringExtra("endPOIAddr") ?: ""
        val endPOILatitude = intent.getDoubleExtra("endPOILatitude", 0.0)
        val endPOILongitude = intent.getDoubleExtra("endPOILongitude", 0.0)

        // 获取导航路线信息
        val destinationName = intent.getStringExtra("DESTINATION_NAME") ?: endPOIName
        val routeRemainDis = intent.getIntExtra("ROUTE_REMAIN_DIS", 0)
        val routeRemainTime = intent.getIntExtra("ROUTE_REMAIN_TIME", 0)

        // 验证目的地信息有效性
        if (validateDestination(endPOILongitude, endPOILatitude, endPOIName)) {
            val currentDestination = carrotManFields.value

            // 检查目的地是否发生变化
            if (shouldUpdateDestination(
                    currentDestination.goalPosX, currentDestination.goalPosY, currentDestination.szGoalName,
                    endPOILongitude, endPOILatitude, endPOIName
                )) {

                Log.i(TAG, "🎯 目的地信息更新:")
                Log.d(TAG, "   名称: $endPOIName")
                Log.d(TAG, "   地址: $endPOIAddr")
                Log.d(TAG, "   坐标: ($endPOILatitude, $endPOILongitude)")
                Log.d(TAG, "   剩余距离: ${routeRemainDis}米")
                Log.d(TAG, "   预计时间: ${routeRemainTime}秒")

                // 更新CarrotMan字段
                carrotManFields.value = carrotManFields.value.copy(
                    goalPosX = endPOILongitude,
                    goalPosY = endPOILatitude,
                    szGoalName = endPOIName.takeIf { it.isNotEmpty() } ?: destinationName,
                    nGoPosDist = routeRemainDis.takeIf { it > 0 } ?: carrotManFields.value.nGoPosDist,
                    nGoPosTime = routeRemainTime.takeIf { it > 0 } ?: carrotManFields.value.nGoPosTime,
                    lastUpdateTime = System.currentTimeMillis(),
                    dataQuality = "good"
                )

                // 🎯 自动发送目的地信息给comma3（修复坐标顺序：经度，纬度）
                sendDestinationToComma3(endPOILongitude, endPOILatitude, endPOIName, endPOIAddr)

                // 缓存目的地信息
                cacheDestination("current_destination", endPOILongitude, endPOILatitude, endPOIName)

                // 更新UI显示
                updateUI("目的地已更新: $endPOIName")
            }
        } else {
            Log.w(TAG, "⚠️ 目的地信息无效: 坐标($endPOILatitude, $endPOILongitude), 名称: $endPOIName")
        }
    }

    /**
     * 处理收藏点数据
     */
    fun handleFavoriteData(favoriteData: String) {
        try {
            val json = JSONObject(favoriteData)
            val latitude = json.optDouble("latitude", 0.0)
            val longitude = json.optDouble("longitude", 0.0)
            val name = json.optString("name", "")
            val type = json.optString("type", "favorite")

            if (validateDestination(longitude, latitude, name)) {
                Log.i(TAG, "🌟 收藏点数据: $name ($latitude, $longitude)")

                carrotManFields.value = carrotManFields.value.copy(
                    goalPosX = longitude,
                    goalPosY = latitude,
                    szGoalName = name,
                    lastUpdateTime = System.currentTimeMillis()
                )

                sendDestinationToComma3(longitude, latitude, name, "收藏点: $type")
                cacheDestination("favorite_$type", longitude, latitude, name)
                updateUI("收藏点已设置: $name")
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析收藏点数据失败: ${e.message}", e)
        }
    }

    /**
     * 处理家庭/公司地址数据
     */
    fun handleHomeCompanyAddress(type: String, intent: Intent) {
        val latitude = intent.getDoubleExtra("latitude", 0.0)
        val longitude = intent.getDoubleExtra("longitude", 0.0)
        val address = intent.getStringExtra("address") ?: ""
        val name = if (type == "home") "家" else "公司"

        if (validateDestination(longitude, latitude, name)) {
            Log.i(TAG, "🏠 ${name}地址: $address ($latitude, $longitude)")

            carrotManFields.value = carrotManFields.value.copy(
                goalPosX = longitude,
                goalPosY = latitude,
                szGoalName = name,
                lastUpdateTime = System.currentTimeMillis()
            )

            sendDestinationToComma3(longitude, latitude, name, address)
            cacheDestination(type + "_address", longitude, latitude, name)

            updateUI("${name}地址已设置: $address")
        }
    }

    /**
     * 处理家庭/公司导航请求
     */
    fun handleHomeCompanyNavigation(intent: Intent) {
        val navigationType = intent.getStringExtra("navigation_type") ?: ""
        when (navigationType.lowercase()) {
            "home" -> {
                Log.i(TAG, "🏠 处理回家导航请求")
                handleHomeCompanyAddress("home", intent)
            }
            "company" -> {
                Log.i(TAG, "🏢 处理到公司导航请求")
                handleHomeCompanyAddress("company", intent)
            }
            else -> {
                Log.w(TAG, "⚠️ 未知的家庭/公司导航类型: $navigationType")
            }
        }
    }

    /**
     * 处理收藏点结果
     */
    fun handleFavoriteResult(intent: Intent) {
        val favoriteData = intent.getStringExtra("FAVORITE_DATA")
        if (!favoriteData.isNullOrEmpty()) {
            Log.i(TAG, "🌟 处理收藏点结果")
            handleFavoriteData(favoriteData)
        } else {
            val name = intent.getStringExtra("favorite_name") ?: ""
            val latitude = intent.getDoubleExtra("favorite_latitude", 0.0)
            val longitude = intent.getDoubleExtra("favorite_longitude", 0.0)

            if (name.isNotEmpty() && latitude != 0.0 && longitude != 0.0) {
                Log.i(TAG, "🌟 从分散字段获取收藏点信息: $name")
                val syntheticJson = JSONObject().apply {
                    put("name", name)
                    put("latitude", latitude)
                    put("longitude", longitude)
                    put("type", "favorite")
                }
                handleFavoriteData(syntheticJson.toString())
            }
        }
    }

    /**
     * 自动发送目的地信息给comma3设备
     */
    private fun sendDestinationToComma3(longitude: Double, latitude: Double, name: String, address: String = "") {
        try {
            networkManager.sendDestinationToComma3(longitude, latitude, name, address)
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ 发送目的地信息失败: ${e.message}")
        }
    }

    /**
     * 缓存目的地信息
     */
    private fun cacheDestination(key: String, longitude: Double, latitude: Double, name: String) {
        destinationCache[key] = Triple(longitude, latitude, name)
        Log.d(TAG, "📝 目的地已缓存: $key -> $name")
    }

    /**
     * 验证目的地坐标和信息的有效性
     */
    private fun validateDestination(longitude: Double, latitude: Double, name: String): Boolean =
        com.example.carrotamap.validateDestination(longitude, latitude, name)

    /**
     * 检查是否需要更新目的地信息
     */
    private fun shouldUpdateDestination(
        currentLon: Double, currentLat: Double, currentName: String,
        newLon: Double, newLat: Double, newName: String
    ): Boolean = com.example.carrotamap.shouldUpdateDestination(
        currentLon, currentLat, currentName, newLon, newLat, newName
    )
}
