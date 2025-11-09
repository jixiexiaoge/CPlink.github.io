package com.example.carrotamap

import android.content.Intent
import android.util.Log
import androidx.compose.runtime.MutableState

/**
 * 高德地图广播处理器（交通/限速/电子眼/红绿灯 等拆分）
 */
class AmapTrafficHandlers(
    private val carrotManFields: MutableState<CarrotManFields>,
    private val networkManager: NetworkManager? = null,
    private val context: android.content.Context? = null
) {
    companion object {
        private const val TAG = "AmapTrafficHandlers"

        /**
         * 统一映射：高德 CAMERA_TYPE → Python nSdiType
         * 基于Python代码中的SDI类型定义进行修正
         * 参考carrot_serv.py中的_get_sdi_descr函数和SDI类型定义
         * 与 AmapBroadcastHandlers 中的映射保持一致
         */
        fun mapAmapCameraTypeToSdi(cameraType: Int): Int {
            return when (cameraType) {
                0 -> 1           // 超速拍照 -> 固定测速摄像头 (1)
                1 -> 14          // 道路拍照 -> 治安监控 (14)
                2 -> 6           // 闯红灯拍照 -> 闯红灯拍照 (6)
                3 -> 17          // 违章拍照 -> 违停拍照点 (17)
                4 -> 9           // 公交专用道摄像头 -> 公交专用车道区间 (9)
                5 -> 11          // 应急车道拍照 -> 应急车道拍照 (11)
                6 -> 8           // 测速拍照 -> 测速拍照 (8)
                7 -> 7           // 非机动车道拍照 -> 流动测速摄像头 (7) ⚠️ 描述不匹配
                8 -> 2           // 区间限速启点 -> 区间测速开始 (2)
                9 -> 3           // 区间限速终点 -> 区间测速结束 (3)
                10 -> 7          // 流动测速电子眼 -> 流动测速摄像头 (7)
                11 -> 26         // ECT计费拍照 -> ETC计费拍照 (26)
                12 -> 41         // 人行道拍照 -> 行人乱穿马路多发处 (41)
                13 -> 41         // 礼让行人拍照 -> 行人乱穿马路多发处 (41)
                14 -> 14         // ⚠️ 待确认 -> 治安监控 (14)
                15 -> 15         // 超载车辆风险区 -> 超载车辆风险区 (15)
                16 -> 16         // 装载不当拍照 -> 装载不当拍照 (16)
                17 -> 17         // 违停拍照点 -> 违停拍照点 (17)
                18 -> 18         // 未系安全带拍照 -> 单行道 (18) ⚠️ 描述不匹配
                19 -> 19         // 接打手机拍照 -> 铁路道口 (19) ⚠️ 描述不匹配
                20 -> 20         // 学校区域开始 -> 学校区域开始 (20)
                21 -> 21         // 学校区域结束 -> 学校区域结束 (21)
                22 -> 22         // ⚠️ 待确认 -> 减速带 (22)
                23 -> 23         // LPG加气站 -> LPG加气站 (23)
                24 -> 24         // 隧道区间 -> 隧道区间 (24)
                25 -> 25         // 服务区 -> 服务区 (25)
                26 -> 26         // 收费站 -> ETC计费拍照 (26)
                27 -> 27         // 多雾路段 -> 多雾路段 (27)
                28 -> 28         // 危险品区域 -> 危险品区域 (28)
                29 -> 29         // 事故多发路段 -> 事故多发路段 (29)
                30 -> 30         // ⚠️ 待确认 -> 急弯路段 (30)
                31 -> 31         // 急弯区段1 -> 急弯区段1 (31)
                32 -> 32         // 陡坡路段 -> 陡坡路段 (32)
                33 -> 33         // 野生动物出没路段 -> 野生动物出没路段 (33)
                34 -> 34         // 右侧视野不良点 -> 右侧视野不良点 (34)
                35 -> 35         // 视野不良点 -> 视野不良点 (35)
                36 -> 36         // 左侧视野不良点 -> 左侧视野不良点 (36)
                37 -> 37         // 闯红灯多发 -> 闯红灯多发 (37)
                38 -> 38         // 超速多发 -> 超速多发 (38)
                39 -> 39         // 交通拥堵区域 -> 交通拥堵区域 (39)
                40 -> 40         // 按方向选择车道点 -> 按方向选择车道点 (40)
                41 -> 41         // 行人乱穿马路多发处 -> 行人乱穿马路多发处 (41)
                42 -> 42         // 应急车道事故多发 -> 应急车道事故多发 (42)
                43 -> 43         // 超速事故多发 -> 超速事故多发 (43)
                44 -> 44         // 疲劳驾驶事故多发 -> 疲劳驾驶事故多发 (44)
                45 -> 45         // 事故多发点 -> 事故多发点 (45)
                46 -> 46         // 行人事故多发点 -> 行人事故多发点 (46)
                47 -> 47         // 车辆盗窃多发点 -> 车辆盗窃多发点 (47)
                48 -> 48         // 落石危险路段 -> 落石危险路段 (48)
                49 -> 49         // 路面结冰危险 -> 路面结冰危险 (49)
                50 -> 50         // 瓶颈路段 -> 瓶颈路段 (50)
                51 -> 51         // 汇入道路 -> 汇入道路 (51)
                52 -> 52         // 坠落危险路段 -> 坠落危险路段 (52)
                53 -> 53         // 地下车道区间 -> 地下车道区间 (53)
                54 -> 54         // 居民区（交通缓和） -> 居民区（交通缓和） (54)
                55 -> 55         // 立交 -> 立交 (55)
                56 -> 56         // 分岔点 -> 分岔点 (56)
                57 -> 57         // 服务区（可加气） -> 服务区（可加气） (57)
                58 -> 58         // 桥梁 -> 桥梁 (58)
                59 -> 59         // 制动故障事故多发点 -> 制动故障事故多发点 (59)
                60 -> 60         // 越线事故多发点 -> 越线事故多发点 (60)
                61 -> 61         // 违法通行事故多发点 -> 违法通行事故多发点 (61)
                62 -> 62         // 目的地在对面 -> 目的地在对面 (62)
                63 -> 63         // 瞌睡停车区 -> 瞌睡停车区 (63)
                64 -> 64         // 老旧柴油车管制 -> 老旧柴油车管制 (64)
                65 -> 65         // 隧道内变道拍照 -> 隧道内变道拍照 (65)
                else -> 66       // 其他未知 -> 空/忽略 (66)
            }
        }

        private fun mapTrafficLightStatus(amapStatus: Int, direction: Int = 0): Int {
            return when (amapStatus) {
                -1 -> -1
                0 -> 0
                1 -> 1
                2 -> if (direction == 1 || direction == 3) 3 else 2
                3 -> 1
                4 -> 2
                else -> 0
            }
        }

        private fun getTrafficLightDirectionDesc(direction: Int): String {
            return when (direction) {
                0 -> "直行黄灯"
                1 -> "左转"
                2 -> "右转"
                3 -> "左转掉头"
                4 -> "直行"
                5 -> "右转掉头"
                else -> "方向$direction"
            }
        }
    }

    /**
     * 处理限速信息广播 (KEY_TYPE: 12110)
     */
    fun handleSpeedLimit(intent: Intent) {
        Log.i(TAG, "🚦 开始处理限速信息广播 (KEY_TYPE: 12110)")

        try {
            val speedLimit = intent.getIntExtra("LIMITED_SPEED", 0)
            val roadName = intent.getStringExtra("ROAD_NAME") ?: ""
            val speedLimitType = intent.getIntExtra("SPEED_LIMIT_TYPE", -1)

            @Suppress("DEPRECATION")
            fun readNumberAsInt(key: String): Int {
                val extras = intent.extras
                if (extras == null || !extras.containsKey(key)) return 0
                val raw = extras.get(key)
                return when (raw) {
                    is Int -> raw
                    is Long -> raw.toInt()
                    is Float -> raw.toInt()
                    is Double -> raw.toInt()
                    is String -> raw.toDoubleOrNull()?.toInt() ?: 0
                    else -> 0
                }
            }

            @Suppress("DEPRECATION")
            fun readNumberAsDouble(key: String): Double {
                val extras = intent.extras
                if (extras == null || !extras.containsKey(key)) return 0.0
                val raw = extras.get(key)
                return when (raw) {
                    is Int -> raw.toDouble()
                    is Long -> raw.toDouble()
                    is Float -> raw.toDouble()
                    is Double -> raw
                    is String -> raw.toDoubleOrNull() ?: 0.0
                    else -> 0.0
                }
            }

            val hasStartDistance = intent.hasExtra("START_DISTANCE")
            val hasEndDistance = intent.hasExtra("END_DISTANCE")
            val hasIntervalDistance = intent.hasExtra("INTERVAL_DISTANCE")
            val hasAverageSpeed = intent.hasExtra("AVERAGE_SPEED")

            val startDistanceInt = if (hasStartDistance) readNumberAsInt("START_DISTANCE") else 0
            val endDistanceInt = if (hasEndDistance) readNumberAsInt("END_DISTANCE") else 0
            val intervalDistanceInt = if (hasIntervalDistance) readNumberAsInt("INTERVAL_DISTANCE") else 0
            val isInSectionSpeedControl = hasStartDistance || hasEndDistance || hasIntervalDistance

            val nSdiType = if (isInSectionSpeedControl) 4 else carrotManFields.value.nSdiType
            val nSdiDist = if (isInSectionSpeedControl && hasEndDistance && endDistanceInt > 0) endDistanceInt else carrotManFields.value.nSdiDist
            val nSdiBlockType = if (isInSectionSpeedControl) 2 else carrotManFields.value.nSdiBlockType
            val nSdiSection = if (isInSectionSpeedControl && hasStartDistance && startDistanceInt >= 0) startDistanceInt else carrotManFields.value.nSdiSection
            val nSdiBlockDist = if (isInSectionSpeedControl && hasIntervalDistance && intervalDistanceInt > 0) intervalDistanceInt else carrotManFields.value.nSdiBlockDist
            val nSdiBlockSpeed = if (isInSectionSpeedControl && speedLimit > 0) speedLimit else carrotManFields.value.nSdiBlockSpeed.takeIf { it > 0 } ?: 0
            val nSdiSpeedLimit = if (isInSectionSpeedControl && speedLimit > 0) speedLimit else carrotManFields.value.nSdiSpeedLimit.takeIf { it > 0 } ?: 0

            carrotManFields.value = carrotManFields.value.copy(
                nRoadLimitSpeed = speedLimit.takeIf { it > 0 } ?: carrotManFields.value.nRoadLimitSpeed,
                szPosRoadName = roadName.takeIf { it.isNotEmpty() } ?: carrotManFields.value.szPosRoadName,
                speedLimitType = speedLimitType.takeIf { it >= 0 } ?: carrotManFields.value.speedLimitType,
                nSdiType = if (isInSectionSpeedControl) nSdiType else carrotManFields.value.nSdiType,
                nSdiSpeedLimit = if (isInSectionSpeedControl) nSdiSpeedLimit else carrotManFields.value.nSdiSpeedLimit,
                nSdiDist = if (isInSectionSpeedControl) nSdiDist else carrotManFields.value.nSdiDist,
                nSdiSection = if (isInSectionSpeedControl) nSdiSection else carrotManFields.value.nSdiSection,
                nSdiBlockType = if (isInSectionSpeedControl) nSdiBlockType else carrotManFields.value.nSdiBlockType,
                nSdiBlockSpeed = if (isInSectionSpeedControl) nSdiBlockSpeed else carrotManFields.value.nSdiBlockSpeed,
                nSdiBlockDist = if (isInSectionSpeedControl) nSdiBlockDist else carrotManFields.value.nSdiBlockDist,
                lastUpdateTime = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ 处理限速信息失败: ${e.message}", e)
        }
    }

    /**
     * 处理电子眼信息广播 (KEY_TYPE: 13005)
     */
    fun handleCameraInfo(intent: Intent) {
        try {
            val cameraType = intent.getIntExtra("CAMERA_TYPE", -1)
            val cameraDistance = intent.getIntExtra("CAMERA_DISTANCE", 0)
            val cameraSpeedLimit = intent.getIntExtra("CAMERA_SPEED_LIMIT", 0)

            val mappedSdiType = if (cameraType >= 0) mapAmapCameraTypeToSdi(cameraType) else carrotManFields.value.nSdiType
            val shouldClearSdi = cameraDistance <= 20

            carrotManFields.value = carrotManFields.value.copy(
                nAmapCameraType = if (cameraType >= 0) cameraType else carrotManFields.value.nAmapCameraType,
                nSdiType = if (shouldClearSdi) -1 else mappedSdiType,
                nSdiDist = if (shouldClearSdi) 0 else cameraDistance,
                nSdiSpeedLimit = if (shouldClearSdi) 0 else cameraSpeedLimit,
                lastUpdateTime = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ 处理电子眼信息失败: ${e.message}", e)
        }
    }

    /**
     * 处理SDI Plus信息广播 (KEY_TYPE: 10007)
     */
    fun handleSdiPlusInfo(intent: Intent) {
        try {
            val sdiPlusType = intent.getIntExtra("SDI_PLUS_TYPE", -1)
            val sdiPlusDistance = intent.getIntExtra("SDI_PLUS_DISTANCE", 0)
            val sdiPlusSpeedLimit = intent.getIntExtra("SDI_PLUS_SPEED_LIMIT", 0)

            carrotManFields.value = carrotManFields.value.copy(
                nSdiPlusType = sdiPlusType,
                nSdiPlusDist = sdiPlusDistance,
                nSdiPlusSpeedLimit = sdiPlusSpeedLimit,
                lastUpdateTime = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ 处理SDI Plus信息失败: ${e.message}", e)
        }
    }

    /**
     * 处理路况信息广播 (KEY_TYPE: 10070)
     */
    fun handleTrafficInfo(intent: Intent) {
        try {
            val trafficLevel = intent.getIntExtra("TRAFFIC_LEVEL", -1)
            val trafficDescription = intent.getStringExtra("TRAFFIC_DESCRIPTION") ?: ""

            carrotManFields.value = carrotManFields.value.copy(
                trafficLevel = trafficLevel,
                trafficDescription = trafficDescription,
                lastUpdateTime = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ 处理路况信息失败: ${e.message}", e)
        }
    }

    /**
     * 处理导航态势广播 (KEY_TYPE: 13003)
     */
    fun handleNaviSituation(intent: Intent) {
        try {
            val situationType = intent.getIntExtra("SITUATION_TYPE", -1)
            val situationDistance = intent.getIntExtra("SITUATION_DISTANCE", 0)
            val situationDescription = intent.getStringExtra("SITUATION_DESCRIPTION") ?: ""

            carrotManFields.value = carrotManFields.value.copy(
                situationType = situationType,
                situationDistance = situationDistance,
                situationDescription = situationDescription,
                lastUpdateTime = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ 处理导航态势失败: ${e.message}", e)
        }
    }

    /**
     * 处理红绿灯信息广播 - KEY_TYPE: 60073
     */
    fun handleTrafficLightInfo(intent: Intent) {
        try {
            val trafficLightStatus = when {
                intent.hasExtra("trafficLightStatus") -> intent.getIntExtra("trafficLightStatus", 0)
                intent.hasExtra("TRAFFIC_LIGHT_STATUS") -> intent.getIntExtra("TRAFFIC_LIGHT_STATUS", 0)
                intent.hasExtra("LIGHT_STATUS") -> intent.getIntExtra("LIGHT_STATUS", 0)
                else -> 0
            }

            val redLightCountDown = intent.getIntExtra("redLightCountDownSeconds", 0)
            val greenLightCountDown = intent.getIntExtra("greenLightLastSecond", 0)
            val direction = when {
                intent.hasExtra("dir") -> intent.getIntExtra("dir", 0)
                intent.hasExtra("TRAFFIC_LIGHT_DIRECTION") -> intent.getIntExtra("TRAFFIC_LIGHT_DIRECTION", 0)
                intent.hasExtra("LIGHT_DIRECTION") -> intent.getIntExtra("LIGHT_DIRECTION", 0)
                else -> 0
            }
            val waitRound = intent.getIntExtra("waitRound", 0)

            var carrotTrafficState = mapTrafficLightStatus(trafficLightStatus, direction)
            var leftSec = if (trafficLightStatus == 1 || trafficLightStatus == 3 || trafficLightStatus == 2 || trafficLightStatus == 4) redLightCountDown else redLightCountDown

            val previousTrafficState = carrotManFields.value.traffic_state
            val previousLeftSec = carrotManFields.value.left_sec

            if (carrotTrafficState == 0 && leftSec <= 0) {
                if (previousTrafficState == 1 && previousLeftSec <= 3) {
                    carrotTrafficState = 2
                    leftSec = 30
                }
            }

            val stateChanged = (carrotTrafficState != previousTrafficState) || (leftSec != previousLeftSec)

            carrotManFields.value = carrotManFields.value.copy(
                traffic_light_count = intent.getIntExtra("TRAFFIC_LIGHT_COUNT", -1).takeIf { it >= 0 }
                    ?: carrotManFields.value.traffic_light_count,
                traffic_state = carrotTrafficState,
                traffic_light_direction = direction,
                left_sec = leftSec,
                max_left_sec = maxOf(leftSec, carrotManFields.value.max_left_sec),
                carrot_left_sec = leftSec,
                amap_traffic_light_status = trafficLightStatus,
                amap_traffic_light_dir = direction,
                amap_green_light_last_second = greenLightCountDown,
                amap_wait_round = waitRound,
                lastUpdateTime = System.currentTimeMillis()
            )

            if (stateChanged) {
                val directionDesc = getTrafficLightDirectionDesc(direction)
               // Log.v(TAG, "🚦 交通灯状态变化: state=$carrotTrafficState, left=$leftSec, dir=$directionDesc")
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理红绿灯信息失败: ${e.message}", e)
        }
    }
}


