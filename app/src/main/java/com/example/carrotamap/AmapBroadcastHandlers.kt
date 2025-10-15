package com.example.carrotamap

import android.content.Intent
import android.util.Log
import androidx.compose.runtime.MutableState
import org.json.JSONException
import org.json.JSONObject
import kotlin.math.max

/**
 * 高德地图广播处理器扩展
 * 包含各种类型广播的具体处理逻辑
 */
class AmapBroadcastHandlers(
    private val carrotManFields: MutableState<CarrotManFields>,
    private val networkManager: NetworkManager? = null,
    private val context: android.content.Context? = null,
    private val amapDataProcessor: AmapDataProcessor? = null
) {
    companion object {
        private const val TAG = "AmapBroadcastHandlers"

        // 🎯 已删除多余的计算函数
        // 这些计算应该由comma3设备处理，手机app只负责数据映射

        // 🎯 已删除SDI描述函数
        // carrot_serv.py中已有_get_sdi_descr函数处理SDI描述，手机app不需要重复

        // 🎯 已删除导航路径生成函数
        // 路径数据应该由高德地图直接提供，不需要手机app生成

        /**
         * 更新CarrotMan索引 (基于Python carrot_man.py逻辑)
         * 每次导航数据更新时递增索引
         */
        fun updateCarrotIndex(carrotManFields: MutableState<CarrotManFields>): Long {
            val currentIndex = carrotManFields.value.carrotIndex + 1
            carrotManFields.value = carrotManFields.value.copy(
                carrotIndex = currentIndex
            )
            return currentIndex
        }

        // 🎯 注意：命令执行功能已移至Python端

        // 🎯 注意：DETECT命令处理已移至Python端

        /**
         * 更新远程IP地址 (基于Python update_navi方法)
         * @param remoteIP 远程设备IP地址
         */
        fun updateRemoteIP(carrotManFields: MutableState<CarrotManFields>, remoteIP: String) {
            carrotManFields.value = carrotManFields.value.copy(
                remote = remoteIP
            )
            Log.d(TAG, "🌐 远程IP已更新: $remoteIP")
        }

        /**
         * 简化的调试文本生成
         * @param fields 当前CarrotMan字段
         * @return 调试文本字符串
         */
        private fun generateDebugText(fields: CarrotManFields): String {
            val parts = mutableListOf<String>()

            parts.add("${fields.nRoadLimitSpeed}")
            // 🎯 navType, navModifier 由Python端计算，Android不发送

            if (fields.vTurnSpeed > 0) {
                parts.add("route=${fields.vTurnSpeed}")
            }

            if (fields.xDistToTurn > 0) {
                parts.add("dist:${fields.xDistToTurn}m")
            }

            return parts.joinToString(",")
        }

        /**
         * 更新数据源跟踪 (基于Python source_last逻辑)
         * @param source 数据来源标识
         */
        fun updateDataSource(carrotManFields: MutableState<CarrotManFields>, source: String) {
            carrotManFields.value = carrotManFields.value.copy(
                source_last = source
            )
            Log.d(TAG, "📊 数据源已更新: $source")
        }

        // 🎯 注意：ATC控制功能已移至Python端处理
        // Android只发送原始的nTBTTurnType数据，Python端负责所有计算

        // 🎯 注意：ATC控制功能已移至Python端



        // 🎯 注意：用户接管检测功能已移至Python端处理

        // 🎯 注意：CarrotMan命令处理功能已移至Python端
        // Android只负责发送数据，不处理来自Comma3的命令

        /**
         * 统一映射：高德 CAMERA_TYPE → Python nSdiType
         * 目的：避免将“闯红灯/违停/公交专用道”等错误映射为区间测速三态(2/3/4)
         * 建议初版（可根据路测再调整）：
         *  - 0(测速摄像头/固定测速)   → 1(固定式超速)
         *  - 1(通用监控/非测速)       → 66(空/忽略)
         *  - 2(闯红灯拍照)           → 6(信号抓拍)
         *  - 3(违停拍照)             → 17(违停抓拍点)
         *  - 4(公交专用道摄像头)     → 9(公交专用道区间)
         *  - 其他未知                → 66(空/忽略)
         */
        fun mapAmapCameraTypeToSdi(cameraType: Int): Int {
            return when (cameraType) {
                0 -> 1           // 固定测速 -> 固定式超速
                1 -> 8          // 通用监控 -> 忽略
                2 -> 6           // 闯红灯 -> 信号抓拍
                3 -> 17          // 违停 -> 违停抓拍点  要确认的
                4 -> 9           // 公交专用道 -> 公交专用道区间
                5 -> 11           // 应急车道抓拍
                8 -> 2           // 区间测速摄像头 -> 区间测速开始
                9 -> 3           // 区间测速摄像头 -> 区间测速结束
                10 -> 7           // 移动式超速 测试验证下
                11 -> 26           //  ETC 没有合适的
                12 -> 41           // 人行道拍照 靠右右转车道
                13 -> 41           // 人行道拍照

                else -> 66       // 其他未知 -> 忽略
            }
        }

        /**
         * 映射高德交通灯状态到CarrotMan协议状态 (基于逆向分析文档修正)
         * CarrotMan协议状态: 0=off, 1=red, 2=green, 3=left(左转绿灯)
         * @param amapStatus 高德交通灯状态
         * @param direction 方向信息 (用于区分左转等特殊情况)
         * @return CarrotMan协议交通状态
         */
        private fun mapTrafficLightStatus(amapStatus: Int, direction: Int = 0): Int {
            // 重要修正：基于实际UI观察数据分析
            // trafficLightStatus: 1=红灯, 2=绿灯, -1=黄灯
            // dir: 表示交通灯控制的方向（1=左转, 2=右转, 3=左转掉头, 4=直行, 5=右转掉头）
            // CarrotMan状态：0=off, 1=red, 2=green, 3=left, -1=yellow
            return when (amapStatus) {
                -1 -> when (direction) {
                    0 -> -1     // 直行黄灯（dir=0表示直行黄灯）
                    else -> -1  // 其他方向黄灯
                }
                0 -> 0          // 未知/无信号 -> off
                1 -> when (direction) {
                    1 -> 1      // 左转红灯 -> red
                    2 -> 1      // 右转红灯 -> red
                    3 -> 1      // 左转掉头红灯 -> red
                    4 -> 1      // 直行红灯 -> red
                    5 -> 1      // 右转掉头红灯 -> red
                    else -> 1   // 其他方向红灯 -> red
                }
                2 -> when (direction) {
                    1 -> 3      // 左转绿灯 -> left
                    2 -> 2      // 右转绿灯 -> green
                    3 -> 3      // 左转掉头绿灯 -> left
                    4 -> 2      // 直行绿灯 -> green
                    5 -> 2      // 右转掉头绿灯 -> green
                    else -> 2   // 其他方向绿灯 -> green
                }
                3 -> 1          // 红灯变体 -> red
                4 -> 2          // 绿灯变体 -> green
                else -> 0
            }
        }
    }

    // ===============================
    // 地图状态处理 - KEY_TYPE: 10019
    // ===============================
    fun handleMapState(intent: Intent) {
        //Log.d(TAG, "🗺️ 处理地图状态广播")
        
        val extraState = intent.getIntExtra("EXTRA_STATE", -1)
        //Log.i(TAG, "地图状态: EXTRA_STATE=$extraState")
        
        // 检查是否为到达目的地状态
        if (extraState == AppConstants.AmapBroadcast.NavigationState.ARRIVE_DESTINATION) {
            //Log.i(TAG, "🎯 检测到到达目的地状态！")

            carrotManFields.value = carrotManFields.value.copy(
                // 导航状态
                isNavigating = false,

                // 转弯信息 - 设置为到达目的地
                nTBTTurnType = 201,           // 到达目的地转弯类型
                nTBTDist = 0,                 // 距离设为0
                szTBTMainText = "到达目的地",   // 主要文本
                szNearDirName = "目的地",      // 附近方向名称
                szFarDirName = "",            // 远方向名称清空

                // 🎯 注意：xTurnInfo 由Python端根据nTBTTurnType=201计算
                // Android只设置原始数据

                // 🎯 注意：navType, navModifier 由Python端根据nTBTTurnType=201计算

                // 距离和时间信息
                nGoPosDist = 0,               // 到目的地距离设为0
                nGoPosTime = 0,               // 到目的地时间设为0
                nTBTDistNext = 0,             // 下一段距离清空

                // 系统状态
                active_carrot = 0,            // CarrotMan激活状态设为0
                debugText = "已到达目的地",
                source_last = "amap",
                lastUpdateTime = System.currentTimeMillis(),
                dataQuality = "good"
            )

            //Log.i(TAG, "✅ 已更新CarrotMan字段：导航状态=false，转弯类型=201(到达目的地)")
        }
    }

    // ===============================
    // 引导信息处理 - KEY_TYPE: 10001
    // ===============================
    fun handleGuideInfo(intent: Intent) {
        Log.d(TAG, "🧭 处理引导信息广播 (KEY_TYPE: 10001)")

        try {
            // 基础道路信息
            val currentRoad = intent.getStringExtra("CUR_ROAD_NAME") ?: ""
            val nextRoad = intent.getStringExtra("NEXT_ROAD_NAME") ?: ""
            val nextNextRoad = intent.getStringExtra("NEXT_NEXT_ROAD_NAME") ?: ""
            val speedLimit = intent.getIntExtra("LIMITED_SPEED", 0)
            val currentSpeed = intent.getIntExtra("CUR_SPEED", 0)
            val carDirection = intent.getIntExtra("CAR_DIRECTION", 0)

            // 距离和时间信息
            val remainDistance = intent.getIntExtra("ROUTE_REMAIN_DIS", 0)
            val remainTime = intent.getIntExtra("ROUTE_REMAIN_TIME", 0)
            val remainTimeString = intent.getStringExtra("ROUTE_REMAIN_TIME_STRING") ?: ""
            val routeAllDis = intent.getIntExtra("ROUTE_ALL_DIS", 0)
            val routeAllTime = intent.getIntExtra("ROUTE_ALL_TIME", 0)
            val etaText = intent.getStringExtra("ROUTE_REMAIN_TIME_AUTO") ?: ""
            val segRemainDis = intent.getIntExtra("SEG_REMAIN_DIS", 0)
            val segRemainTime = intent.getIntExtra("SEG_REMAIN_TIME", 0)
            val nextSegRemainDis = intent.getIntExtra("NEXT_SEG_REMAIN_DIS", 0)
            val nextSegRemainTime = intent.getIntExtra("NEXT_SEG_REMAIN_TIME", 0)
            val curSegNum = intent.getIntExtra("CUR_SEG_NUM", 0)
            val curPointNum = intent.getIntExtra("CUR_POINT_NUM", 0)

            // 转向图标和环岛信息
            val icon = intent.getIntExtra("ICON", -1)
            val newIcon = intent.getIntExtra("NEW_ICON", -1)
            val nextNextTurnIcon = intent.getIntExtra("NEXT_NEXT_TURN_ICON", -1)
            val roundAboutNum = intent.getIntExtra("ROUND_ABOUT_NUM", 0)
            val roundAllNum = intent.getIntExtra("ROUND_ALL_NUM", 0)

            // 位置信息
            val carLatitude = intent.getDoubleExtra("CAR_LATITUDE", 0.0)
            val carLongitude = intent.getDoubleExtra("CAR_LONGITUDE", 0.0)

            // 当GPS坐标为0.0时，使用手机GPS或其他可用的位置信息
            val effectiveLatitude = if (carLatitude != 0.0) carLatitude else carrotManFields.value.vpPosPointLat
            val effectiveLongitude = if (carLongitude != 0.0) carLongitude else carrotManFields.value.vpPosPointLon

            // 记录GPS坐标映射情况
            if (carLatitude == 0.0 && carLongitude == 0.0) {
                Log.d(TAG, "📍 GPS坐标为0，使用手机GPS: lat=$effectiveLatitude, lon=$effectiveLongitude")
            } else {
                Log.d(TAG, "📍 使用导航GPS坐标: lat=$effectiveLatitude, lon=$effectiveLongitude")
            }

            // 服务区和电子眼信息
            val sapaDist = intent.getIntExtra("SAPA_DIST", 0)
            val sapaType = intent.getIntExtra("SAPA_TYPE", -1)
            val sapaNum = intent.getIntExtra("SAPA_NUM", 0)
            val sapaName = intent.getStringExtra("SAPA_NAME") ?: ""
            val cameraDist = intent.getIntExtra("CAMERA_DIST", 0)
            val cameraType = intent.getIntExtra("CAMERA_TYPE", -1)
            val cameraSpeed = intent.getIntExtra("CAMERA_SPEED", 0)
            val cameraIndex = intent.getIntExtra("CAMERA_INDEX", -1)
            
            // 记录SDI信息映射
            if (cameraType >= 0 || cameraIndex >= 0) {
                Log.i(TAG, "📷 SDI信息映射: CAMERA_TYPE=$cameraType, CAMERA_INDEX=$cameraIndex, CAMERA_SPEED=$cameraSpeed, CAMERA_DIST=$cameraDist")
            }

            // 导航类型和其他信息
            val naviType = intent.getIntExtra("TYPE", 0)
            val trafficLightNum = intent.getIntExtra("TRAFFIC_LIGHT_NUM", 0)

            // 获取道路类型
            val roadType = intent.getIntExtra("ROAD_TYPE", 8) // 默认为8（未知）
            
            // 🎯 将高德地图的 ROAD_TYPE 映射到 CarrotMan 的 roadcate（简化规则）
            val mappedRoadcate = mapRoadTypeToRoadcate(roadType)
            //Log.d(TAG, "🛣️ 道路类型映射: ROAD_TYPE=$roadType (${getRoadTypeDescription(roadType)}) -> roadcate=$mappedRoadcate (${getRoadcateDescription(mappedRoadcate)})")

            // 目的地信息
            val endPOIName = intent.getStringExtra("endPOIName") ?: ""
            val endPOIAddr = intent.getStringExtra("endPOIAddr") ?: ""
            val endPOILatitude = intent.getDoubleExtra("endPOILatitude", 0.0)
            val endPOILongitude = intent.getDoubleExtra("endPOILongitude", 0.0)

            // 🎯 转弯类型映射和导航类型计算
            val primaryIcon = if (newIcon != -1) newIcon else icon
            val carrotTurnType = if (primaryIcon != -1) {
                val mappedType = mapAmapIconToCarrotTurn(primaryIcon)
                Log.i(TAG, "🔄 转弯映射: 高德图标=$primaryIcon -> CarrotMan类型=$mappedType, 距离=${segRemainDis}m")
                mappedType
            } else {
                carrotManFields.value.nTBTTurnType
            }

            val carrotNextTurnType = if (nextNextTurnIcon != -1) {
                val mappedNextType = mapAmapIconToCarrotTurn(nextNextTurnIcon)
                Log.i(TAG, "🔄 下一转弯映射: 高德图标=$nextNextTurnIcon -> CarrotMan类型=$mappedNextType, 距离=${nextSegRemainDis}m")
                mappedNextType
            } else {
                carrotManFields.value.nTBTTurnTypeNext
            }

            // 🎯 注意：navType, navModifier, xTurnInfo 由Python端计算
            // Android只需要发送原始的nTBTTurnType数据

            // 简化的时间更新
            val currentTime = System.currentTimeMillis()

             //Log.i(TAG, "🧭 引导信息: 道路=$currentRoad->$nextRoad, 转弯类型=$carrotTurnType, 距离=${segRemainDis}m")

            // 更新CarrotMan字段
            carrotManFields.value = carrotManFields.value.copy(
                // 基础导航信息 - 确保关键字段总是被更新
                szPosRoadName = currentRoad.takeIf { it.isNotEmpty() } ?: carrotManFields.value.szPosRoadName,
                szNearDirName = nextRoad,  // 总是更新，即使为空
                szFarDirName = nextNextRoad,  // 总是更新，即使为空
                // 使用智能限速更新机制
                nRoadLimitSpeed = if (speedLimit > 0) {
                    // 通过AmapDataProcessor处理限速变化检测
                    amapDataProcessor?.updateRoadSpeedLimit(speedLimit)
                    speedLimit
                } else {
                    carrotManFields.value.nRoadLimitSpeed
                },
                nGoPosDist = remainDistance.takeIf { it > 0 } ?: carrotManFields.value.nGoPosDist,
                nGoPosTime = remainTime.takeIf { it > 0 } ?: carrotManFields.value.nGoPosTime,
                nPosSpeed = currentSpeed.toDouble(),
                nPosAngle = carDirection.toDouble(),
                // 协议标准字段同步
                xPosSpeed = currentSpeed.toDouble(),
                xPosAngle = carDirection.toDouble(),
                totalDistance = routeAllDis,

                // 转向和导航段信息
                // 🎯 恢复：使用引导信息广播(KEY_TYPE: 10001)的转向距离数据
                nTBTDist = segRemainDis,
                nTBTDistNext = nextSegRemainDis,
                nTBTTurnType = carrotTurnType,
                nTBTTurnTypeNext = carrotNextTurnType,
                
                // 高德地图原始ICON信息
                amapIcon = primaryIcon,
                amapIconNext = nextNextTurnIcon,

                // TBT转弯指令文本
                szTBTMainText = generateTurnInstruction(carrotTurnType, nextRoad, segRemainDis),
                szTBTMainTextNext = generateTurnInstruction(carrotNextTurnType, nextNextRoad, nextSegRemainDis),

                // 🎯 注意：xTurnInfo, navType, navModifier 由Python端计算
                // Android只发送原始数据：nTBTTurnType, nTBTDist等

                // 计算期望速度和来源 (基于多个速度源)
                desiredSpeed = when {
                    speedLimit > 0 -> speedLimit
                    carrotManFields.value.nRoadLimitSpeed > 0 -> carrotManFields.value.nRoadLimitSpeed
                    else -> 0
                },
                desiredSource = when {
                    speedLimit > 0 -> "amap"
                    carrotManFields.value.nRoadLimitSpeed > 0 -> "road"
                    else -> "none"
                },

                // 转弯建议速度 (简化版本)
                vTurnSpeed = carrotManFields.value.vTurnSpeed,

                // 🎯 注意：atcType 由Python端根据nTBTTurnType计算
                // Android只发送原始数据

                // 导航路径数据 (基于当前位置和目标)
                naviPaths = carrotManFields.value.naviPaths,

                // 位置信息 - 高德导航坐标专用于Navi字段，使用有效坐标
                vpPosPointLatNavi = effectiveLatitude,
                vpPosPointLonNavi = effectiveLongitude,

                // 目的地信息 - 确保目的地信息总是被更新
                goalPosX = endPOILongitude.takeIf { it != 0.0 } ?: carrotManFields.value.goalPosX,
                goalPosY = endPOILatitude.takeIf { it != 0.0 } ?: carrotManFields.value.goalPosY,
                szGoalName = endPOIName,  // 总是更新目的地名称

                // 道路和导航状态
                isNavigating = true,
                active_carrot = if (remainDistance > 0 || speedLimit > 0) 1 else carrotManFields.value.active_carrot,
                
                // 🎯 道路类别映射 - 关键修复
                roadcate = mappedRoadcate,
                roadType = roadType,
                
                // 🎯 下一道路宽度映射 - 基于roadcate和车道线信息
                nTBTNextRoadWidth = getTBTNextRoadWidth(),

                // 🎯 恢复：KEY_TYPE=10001 优先处理SDI信息，包含所有SDI相关字段
                // SDI摄像头信息优先由引导信息广播(KEY_TYPE=10001)处理，包含CAMERA_TYPE、CAMERA_SPEED、CAMERA_DIST
                nSdiType = (if (cameraType >= 0) mapAmapCameraTypeToSdi(cameraType) else carrotManFields.value.nSdiType),
                // 注意：10001的CAMERA_SPEED不是测速限速，而是摄像头相关速度，不应用于nSdiSpeedLimit
                // nSdiSpeedLimit现在只来自100001的CAMERA_SPEED（13005已移除）
                nSdiDist = cameraDist.takeIf { it > 0 } ?: carrotManFields.value.nSdiDist,
                nSdiSection = cameraIndex.takeIf { it >= 0 } ?: carrotManFields.value.nSdiSection, // 区间测速ID映射
                nAmapCameraType = cameraType.takeIf { it >= 0 } ?: carrotManFields.value.nAmapCameraType, // 保存高德原始CAMERA_TYPE用于调试
                szSdiDescr = carrotManFields.value.szSdiDescr,

                // 红绿灯数量信息
                traffic_light_count = trafficLightNum.takeIf { it >= 0 } ?: carrotManFields.value.traffic_light_count,

                // 导航GPS时间戳更新
                last_update_gps_time_navi = System.currentTimeMillis(),

                // 时间戳更新
                lastUpdateTime = currentTime
            )

            // 简化的更新逻辑
            Companion.updateCarrotIndex(carrotManFields)
            // 🎯 注意：ATC控制由Python端处理，Android只发送原始数据
            Companion.updateDataSource(carrotManFields, "amap_navi")

            // 更新调试文本
            carrotManFields.value = carrotManFields.value.copy(
                debugText = Companion.generateDebugText(carrotManFields.value)
            )

            //Log.i(TAG, "✅ 引导信息已更新到CarrotMan字段")

        } catch (e: Exception) {
            Log.e(TAG, "处理引导信息失败: ${e.message}", e)
        }
    }



    /**
     * 将高德地图的ICON映射到CarrotMan使用的nTBTTurnType代码
     * 🎯 基于高德官方图标文档和Python代码逆向分析修正
     */
    private fun mapAmapIconToCarrotTurn(amapIcon: Int): Int {
        return when (amapIcon) {
            // 0-9（按编号顺序）
            0 -> 51               // 无转弯/通知指令
            1 -> 200              // 直行（区分通知）
            2 -> 12               // 左转
            3 -> 13               // 右转
            4 -> 102              // 左前方 -> off ramp slight left
            5 -> 101              // 右前方 -> off ramp slight right
            6 -> 17               // 左后方
            7 -> 19               // 右后方
            8 -> 14               // 掉头
            9 -> 200               // 中间岔路上高架

            // 10-19（按编号顺序）
            10 -> 1006            // 靠左行驶 -> off ramp left 10 -> 1006   从200改回去 
            11 -> 1007            // 靠右行驶 -> off ramp right
            12 -> 131             // 右侧通行环岛（进入/驶出统一为轻微右）
            13 -> 51              // 到达服务区 -> 通知
            14 -> 53              // 高架入口 -> 通知（直行）
            15 -> 53              // 过街天桥 -> 通知（直行）
            16 -> 53              // 通过隧道 -> 通知（直行）
            17 -> 140             // 左侧通行环岛（进入/驶出统一为轻微左）
            18 -> 140             // 左侧通行环岛（进入/驶出统一为轻微左）
            19 -> 53              // 通过隧道（参考代码要求）-> 通知（直行）

            // 20-24（按编号顺序）
            20 -> 54              // 通过桥梁 -> 通知（直行）
            21 -> 55              // 通过收费站 -> 通知
            22 -> 55              // 通过服务区 -> 通知
            23 -> 55              // 通过加油站 -> 通知
            24 -> 55              // 通过停车场 -> 通知

            55 -> 55              // 行驶到出口 -> 通知 //手动增加
            65 -> 102              // 高架 靠左行驶测试 改回来测试
            66 -> 101              // 高架 靠右下匝道

            // 其他扩展与兼容
            //65 -> 1006            // 左辅道
            101 -> 1007           // 向右进入辅道

            else -> amapIcon      // 其余保持原值，用于调试
        }
    }



    // ===============================
    // 定位信息处理 - KEY_TYPE: 10065
    // ===============================
    fun handleLocationInfo(intent: Intent) {
        Log.d(TAG, "📍 处理定位信息广播")
        
        try {
            val latitude = intent.getDoubleExtra("LATITUDE", 0.0)
            val longitude = intent.getDoubleExtra("LONGITUDE", 0.0)
            val speed = intent.getFloatExtra("SPEED", 0.0f).toDouble()
            val bearing = intent.getFloatExtra("BEARING", 0.0f).toDouble()
            
            if (latitude != 0.0 && longitude != 0.0) {
                //Log.i(TAG, "定位信息: lat=$latitude, lon=$longitude, speed=${speed}km/h, bearing=${bearing}°")
                
                // 简化的时间更新
                val currentTime = System.currentTimeMillis()
                
                carrotManFields.value = carrotManFields.value.copy(
                    vpPosPointLatNavi = latitude,
                    vpPosPointLonNavi = longitude,
                    // 协议标准位置字段同步
                    xPosLat = latitude,
                    xPosLon = longitude,
                    xPosAngle = bearing,
                    xPosSpeed = speed,
                    nPosSpeed = speed,
                    nPosAngle = bearing,
                    gps_valid = true,
                    last_update_gps_time_navi = System.currentTimeMillis(),
                    lastUpdateTime = currentTime
                )
                
                //Log.i(TAG, "✅ 定位信息已更新到CarrotMan字段")
            } else {
                Log.w(TAG, "⚠️ 定位信息无效: lat=$latitude, lon=$longitude")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "处理定位信息失败: ${e.message}", e)
        }
    }

    // ===============================
    // 转向信息处理 - KEY_TYPE: 10006
    // ===============================
    fun handleTurnInfo(intent: Intent) {
        Log.d(TAG, "🔄 处理转向信息广播")
        
        try {
            val turnDistance = intent.getIntExtra("TURN_DISTANCE", 0)
            val turnType = intent.getIntExtra("TURN_TYPE", -1)
            val turnInstruction = intent.getStringExtra("TURN_INSTRUCTION") ?: ""
            val nextTurnDistance = intent.getIntExtra("NEXT_TURN_DISTANCE", 0)
            val nextTurnType = intent.getIntExtra("NEXT_TURN_TYPE", -1)
            
            Log.i(TAG, "🔄 转向信息: 距离=${turnDistance}m, 类型=$turnType, 指令=$turnInstruction")
            Log.i(TAG, "🔄 下一转向: 距离=${nextTurnDistance}m, 类型=$nextTurnType")
            
            carrotManFields.value = carrotManFields.value.copy(
                nTBTDist = turnDistance,
                nTBTTurnType = turnType,
                szTBTMainText = turnInstruction,
                nTBTDistNext = nextTurnDistance,
                nTBTTurnTypeNext = nextTurnType,
                szTBTMainTextNext = generateTurnInstruction(nextTurnType, "", nextTurnDistance),
                lastUpdateTime = System.currentTimeMillis()
            )
            
            //Log.i(TAG, "✅ 转向信息已更新到CarrotMan字段")
            
        } catch (e: Exception) {
            Log.e(TAG, "处理转向信息失败: ${e.message}", e)
        }
    }

    // ===============================
    // 导航状态处理 - KEY_TYPE: 10042
    // ===============================
    fun handleNavigationStatus(intent: Intent) {
        Log.d(TAG, "🚗 处理导航状态广播")
        
        try {
            val naviStatus = intent.getIntExtra("NAVI_STATUS", -1)
            val isNavigating = naviStatus == 1 // 假设1表示导航中
            
            //Log.i(TAG, "导航状态: status=$naviStatus, 导航中=$isNavigating")
            
            carrotManFields.value = carrotManFields.value.copy(
                isNavigating = isNavigating,
                active_carrot = if (isNavigating) 1 else 0,
                lastUpdateTime = System.currentTimeMillis()
            )
            
            //Log.i(TAG, "✅ 导航状态已更新到CarrotMan字段")
            
        } catch (e: Exception) {
            Log.e(TAG, "处理导航状态失败: ${e.message}", e)
        }
    }

    // ===============================
    // 路线信息处理 - KEY_TYPE: 10003
    // ===============================
    fun handleRouteInfo(intent: Intent) {
        Log.d(TAG, "🛣️ 处理路线信息广播")
        
        try {
            val routeDistance = intent.getIntExtra("ROUTE_DISTANCE", 0)
            val routeTime = intent.getIntExtra("ROUTE_TIME", 0)
            val routeName = intent.getStringExtra("ROUTE_NAME") ?: ""
            
            //Log.i(TAG, "路线信息: 距离=${routeDistance}m, 时间=${routeTime}s, 名称=$routeName")
            
            carrotManFields.value = carrotManFields.value.copy(
                totalDistance = routeDistance,
                nGoPosTime = routeTime,
                szPosRoadName = routeName,
                lastUpdateTime = System.currentTimeMillis()
            )
            
            //Log.i(TAG, "✅ 路线信息已更新到CarrotMan字段")
            
        } catch (e: Exception) {
            Log.e(TAG, "处理路线信息失败: ${e.message}", e)
        }
    }

    // ===============================
    // 限速信息处理 - KEY_TYPE: 12110
    // ===============================
    // 🎯 临时注释：只使用引导信息广播(KEY_TYPE: 10001)的限速数据
    /*
    fun handleSpeedLimit(intent: Intent) {
        Log.d(TAG, "🚦 处理限速信息广播")
        
        try {
            val speedLimit = intent.getIntExtra("SPEED_LIMIT", 0)
            val roadName = intent.getStringExtra("ROAD_NAME") ?: ""
            val distance = intent.getIntExtra("DISTANCE", 0)
            
            if (speedLimit > 0) {
                Log.i(TAG, "限速信息: 限速=${speedLimit}km/h, 道路=$roadName, 距离=${distance}m")

                // 简化的速度倒计时计算
                val xSpdCountDown = carrotManFields.value.xSpdCountDown

                // 简化的限速更新逻辑 - 移除复杂的防抖机制
                val currentSpeedLimit = carrotManFields.value.nRoadLimitSpeed
                val newSpeedLimit = if (speedLimit != currentSpeedLimit) {
                    //Log.i(TAG, "🚦 限速更新: ${currentSpeedLimit}km/h -> ${speedLimit}km/h")
                    speedLimit
                } else {
                    currentSpeedLimit
                }

                carrotManFields.value = carrotManFields.value.copy(
                    nRoadLimitSpeed = newSpeedLimit,
                    xSpdLimit = newSpeedLimit,
                    xSpdDist = distance,
                    xSpdCountDown = xSpdCountDown,
                    xSpdType = 1,
                    szPosRoadName = if (roadName.isNotEmpty()) roadName else carrotManFields.value.szPosRoadName,
                    lastUpdateTime = System.currentTimeMillis()
                )

                // 更新数据源和调试信息
                Companion.updateDataSource(carrotManFields, "amap_speed")
                carrotManFields.value = carrotManFields.value.copy(
                    debugText = Companion.generateDebugText(carrotManFields.value)
                )

                //Log.i(TAG, "✅ 限速信息已更新到CarrotMan字段")
            } else {
                Log.w(TAG, "⚠️ 限速信息无效: speedLimit=$speedLimit")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "处理限速信息失败: ${e.message}", e)
        }
    }
    */

    /**
     * 区间测速信息处理 - KEY_TYPE: 12110
     * LIMITED_SPEED -> nSdiBlockSpeed (km/h)
     * END_DISTANCE  -> nSdiBlockDist (m)
     * INTERVAL_DISTANCE -> 暂存到 nSdiSection
     * START_DISTANCE / AVERAGE_SPEED -> 暂忽略（可扩展）
     * EXTRA_STATE(0/1) -> nSdiBlockType（简化）
     * CAMERA_TYPE -> nAmapCameraType
     */
    fun handleSpeedLimitInterval(intent: Intent) {
        try {
            val limitedSpeed = intent.getIntExtra("LIMITED_SPEED", 0)

            // 类型安全读取（兼容 Float/Double/Int/String）
            fun readNumberAsInt(key: String): Int {
                val extras = intent.extras
                if (extras == null || !extras.containsKey(key)) return 0
                @Suppress("DEPRECATION")
                val raw = extras.get(key)
                return when (raw) {
                    is Int -> raw
                    is Long -> raw.toInt()
                    is Float -> raw.toDouble().toInt()
                    is Double -> raw.toInt()
                    is String -> raw.toDoubleOrNull()?.toInt() ?: 0
                    else -> 0
                }
            }

            // 关键字段读取
            val startDistance = readNumberAsInt("START_DISTANCE")     // 起点距离(进入区间时有值)
            val endDistance = readNumberAsInt("END_DISTANCE")         // 终点距离(接近结束时出现/增大)
            val intervalDistance = readNumberAsInt("INTERVAL_DISTANCE")// 区间总长度/或剩余(按实测)
            val cameraType = intent.getIntExtra("CAMERA_TYPE", -1)
            val extraState = intent.getIntExtra("EXTRA_STATE", -1)

            // 映射规则：
            // - LIMITED_SPEED → nSdiBlockSpeed
            // - INTERVAL_DISTANCE → nSdiBlockDist（按你的需求：显示区间距离）
            // - START/END 的变化 → nSdiBlockType: 1(进入) → 2(进行中) → 3(结束)
            val previous = carrotManFields.value

            // 计算区间状态机
            val newBlockType = when {
                // 明确结束信号：END_DISTANCE 出现正值或 INTERVAL_DISTANCE 归零
                endDistance > 0 || (intervalDistance == 0 && (startDistance > 0 || previous.nSdiBlockType > 0)) -> 3
                // 进入区间：首次收到带 START_DISTANCE/INTERVAL_DISTANCE 的包
                (startDistance > 0 && intervalDistance > 0 && previous.nSdiBlockType <= 0) -> 1
                // 进行中：已进入后持续更新
                (startDistance > 0 && previous.nSdiBlockType in listOf(1, 2)) -> 2
                else -> previous.nSdiBlockType
            }

            // 构造更新
            carrotManFields.value = previous.copy(
                nSdiBlockSpeed = if (limitedSpeed > 0) limitedSpeed else previous.nSdiBlockSpeed,
                // 按需求：区间距离映射到 nSdiBlockDist 显示
                nSdiBlockDist = if (intervalDistance >= 0) intervalDistance else previous.nSdiBlockDist,
                // 可将 START_DISTANCE 暂存到 nSdiSection，便于调试/对照
                nSdiSection = if (startDistance >= 0) startDistance else previous.nSdiSection,
                nAmapCameraType = if (cameraType >= 0) cameraType else previous.nAmapCameraType,
                nSdiBlockType = newBlockType,
                lastUpdateTime = System.currentTimeMillis()
            )

            Log.i(
                TAG,
                "🟧 区间测速(12110): cam=$cameraType, limit=$limitedSpeed, start=$startDistance, end=$endDistance, interval=$intervalDistance, type=${carrotManFields.value.nSdiBlockType} (prev=${previous.nSdiBlockType}, extra=$extraState)"
            )
        } catch (e: Exception) {
            Log.e(TAG, "处理区间测速(12110)失败: ${e.message}", e)
        }
    }

    // ===============================
    // 摄像头信息处理 - KEY_TYPE: 13005（已移除映射）
    // ===============================
    fun handleCameraInfo(intent: Intent) {
        Log.d(TAG, "🧹 忽略摄像头信息(13005)映射：按要求不再更新字段")
        // 若需排查频率，可开启紧凑日志：
        // logIntentExtrasCompact(intent, "KEY_TYPE 13005 跳过映射")
    }

    /**
     * 🎯 处理 SDI Plus 信息 (KEY_TYPE=10007)
     */
    fun handleSdiPlusInfo(intent: Intent) {
        Log.d(TAG, "🧹 忽略SDI Plus(10007)映射：按要求不再更新字段")
        // 若需排查频率，可开启紧凑日志：
        // logIntentExtrasCompact(intent, "KEY_TYPE 10007 跳过映射")
    }

    /** 解析 SDI Plus 广播内容 */
    private fun parseSdiPlusInfoContent(intent: Intent): String {
        val sdiType = intent.getIntExtra("SDI_TYPE", -1)
        val speedLimit = intent.getIntExtra("SPEED_LIMIT", 0)
        val distance = intent.getIntExtra("SDI_DIST", 0)
        return buildString {
            appendLine("类型: ${carrotManFields.value.szSdiDescr}")
            if (speedLimit > 0) appendLine("限速: ${speedLimit}km/h")
            if (distance > 0) appendLine("距离: ${distance}米")
        }.trimEnd()
    }

    /** 新版电子眼信息处理 (KEY_TYPE=100001) */
    fun handleCameraInfoV2(intent: Intent) {
        Log.d(TAG, "📷 处理新版电子眼信息广播 (KEY_TYPE: 100001)")
        // 打印原始广播数据（详细+紧凑形式）
        logIntentExtrasDetailed(intent, "KEY_TYPE 100001 详细原始数据")
        logIntentExtrasCompact(intent, "KEY_TYPE 100001 原始数据(紧凑)")
        
        try {
        val distance = intent.getIntExtra("CAMERA_DIST", -1)
            val cameraType = intent.getIntExtra("CAMERA_TYPE", -1)
        val speedLimit = intent.getIntExtra("CAMERA_SPEED", 0)
        val camIndex = intent.getIntExtra("CAMERA_INDEX", -1)

            // 🎯 使用统一映射：高德 CAMERA_TYPE → Python nSdiType（避免误判为区间测速）
            val sdiType = mapAmapCameraTypeToSdi(cameraType)
            val sdiDescription = ""

        val desc = buildString {
                append(sdiDescription)
            if (distance >= 0) append(" ${distance}米")
            if (speedLimit > 0) append(" 限速${speedLimit}km/h")
            if (camIndex >= 0) append(" #$camIndex")
        }
            
            Log.i(TAG, "📷 新版电子眼: 高德CAMERA_TYPE=$cameraType -> Python SDI类型=$sdiType ($sdiDescription), 限速=${speedLimit}km/h, 距离=${distance}m, 索引=$camIndex")

         carrotManFields.value = carrotManFields.value.copy(
             nSdiType = sdiType,
             nSdiSpeedLimit = speedLimit,
             nSdiDist = distance,
             nAmapCameraType = cameraType,
             szSdiDescr = sdiDescription,
             lastUpdateTime = System.currentTimeMillis()
         )
            
            Log.i(TAG, "✅ 新版电子眼信息已更新到CarrotMan字段")
            
        } catch (e: Exception) {
            Log.e(TAG, "处理新版电子眼信息失败: ${e.message}", e)
        }
    }

    // ===============================
    // 占位符方法 - 其他处理器
    // ===============================
    fun handleTrafficInfo(intent: Intent) {
        Log.d(TAG, "🚦 处理路况信息广播")
    }

    fun handleNaviSituation(intent: Intent) {
        Log.d(TAG, "🎯 处理导航态势广播")
    }

    /**
     * 记录Intent的所有Extra字段 - 专门用于60073红绿灯广播调试
     * @param intent 要记录的Intent对象
     * @param prefix 日志前缀标识
     */
    @Suppress("DEPRECATION")
    private fun logTrafficLightIntentExtras(intent: Intent, prefix: String) {
        try {
            //Log.i(TAG, "🚥 ========== $prefix ==========")
            //Log.i(TAG, "🚥 Intent Action: ${intent.action}")
            //Log.i(TAG, "🚥 Intent Data: ${intent.dataString}")
            //Log.i(TAG, "🚥 Intent Type: ${intent.type}")
            
            // 记录所有Extra字段
            val extras = intent.extras
            if (extras != null) {
                Log.i(TAG, "🚥 Extra字段总数: ${extras.size()}")
                for (key in extras.keySet()) {
                    val value = extras.get(key)
                    val valueType = value?.javaClass?.simpleName ?: "null"
                    val valueStr = when (value) {
                        is String -> "\"$value\""
                        is Int -> value.toString()
                        is Long -> value.toString()
                        is Float -> value.toString()
                        is Double -> value.toString()
                        is Boolean -> value.toString()
                        is ByteArray -> "ByteArray[${value.size}]"
                        is IntArray -> "IntArray[${value.size}]"
                        is LongArray -> "LongArray[${value.size}]"
                        is FloatArray -> "FloatArray[${value.size}]"
                        is DoubleArray -> "DoubleArray[${value.size}]"
                        is BooleanArray -> "BooleanArray[${value.size}]"
                        else -> value?.toString() ?: "null"
                    }
                    Log.i(TAG, "🚥   $key ($valueType) = $valueStr")
                }
            } else {
                Log.i(TAG, "🚥 没有Extra字段")
            }
            Log.i(TAG, "🚥 ========== $prefix 结束 ==========")
        } catch (e: Exception) {
            Log.e(TAG, "🚥 记录Intent Extra字段失败: ${e.message}", e)
        }
    }

    /**
     * 紧凑打印 Intent Extras（键=值，以一行输出），用于SDI调试
     */
    @Suppress("DEPRECATION")
    private fun logIntentExtrasCompact(intent: Intent, prefix: String) {
        try {
            val extras = intent.extras
            if (extras == null || extras.isEmpty) {
                Log.i(TAG, "🔎 $prefix: <no extras>")
                return
            }
            val kvList = mutableListOf<String>()
            for (key in extras.keySet()) {
                @Suppress("DEPRECATION")
                val v = extras.get(key)
                val valueStr = when (v) {
                    is String -> v
                    is Int, is Long, is Float, is Double, is Boolean -> v.toString()
                    is ByteArray -> "ByteArray[${v.size}]"
                    is IntArray -> "IntArray[${v.size}]"
                    is LongArray -> "LongArray[${v.size}]"
                    is FloatArray -> "FloatArray[${v.size}]"
                    is DoubleArray -> "DoubleArray[${v.size}]"
                    is BooleanArray -> "BooleanArray[${v.size}]"
                    else -> v?.toString() ?: "null"
                }
                kvList.add("$key=$valueStr")
            }
            Log.i(TAG, "🔎 $prefix: ${kvList.joinToString(", ")}")
        } catch (e: Exception) {
            Log.w(TAG, "🔎 $prefix 打印失败: ${e.message}")
        }
    }

    /**
     * 详细打印 Intent Extras（多行，含类型），并对可能为 JSON 的字符串做美化
     */
    private fun logIntentExtrasDetailed(intent: Intent, prefix: String) {
        try {
            val extras = intent.extras
            Log.i(TAG, "📄 ========== $prefix ==========")
            if (extras == null || extras.isEmpty) {
                Log.i(TAG, "📄 <no extras>")
                Log.i(TAG, "📄 ========== $prefix 结束 ==========")
                return
            }
            for (key in extras.keySet()) {
                @Suppress("DEPRECATION")
                val v = extras.get(key)
                val valueType = v?.javaClass?.simpleName ?: "null"
                val valueStr = when (v) {
                    is String -> v
                    is Int, is Long, is Float, is Double, is Boolean -> v.toString()
                    is ByteArray -> "ByteArray[${v.size}]"
                    is IntArray -> "IntArray[${v.size}]"
                    is LongArray -> "LongArray[${v.size}]"
                    is FloatArray -> "FloatArray[${v.size}]"
                    is DoubleArray -> "DoubleArray[${v.size}]"
                    is BooleanArray -> "BooleanArray[${v.size}]"
                    else -> v?.toString() ?: "null"
                }
                // 直接输出原始文本，不做JSON美化
                Log.i(TAG, "📄 $key ($valueType) = ${valueStr}")
            }
            Log.i(TAG, "📄 ========== $prefix 结束 ==========")
        } catch (e: Exception) {
            Log.w(TAG, "📄 $prefix 打印失败: ${e.message}")
        }
    }

    

    /**
     * 处理红绿灯信息广播 - KEY_TYPE: 60073
     * 基于JavaScript参考代码实现，使用正确的字段名
     */
    fun handleTrafficLightInfo(intent: Intent) {
        // 记录完整的原始广播内容，便于catlog分析（已注释以减少日志噪声）
        // logTrafficLightIntentExtras(intent, "KEY_TYPE 60073 红绿灯信息广播 - 完整原始数据")

        try {
            // 使用JavaScript参考代码中的正确字段名
            val trafficLightStatus = when {
                intent.hasExtra("trafficLightStatus") -> intent.getIntExtra("trafficLightStatus", 0)
                intent.hasExtra("TRAFFIC_LIGHT_STATUS") -> intent.getIntExtra("TRAFFIC_LIGHT_STATUS", 0)
                intent.hasExtra("LIGHT_STATUS") -> intent.getIntExtra("LIGHT_STATUS", 0)
                else -> 0
            }

            // 根据日志发现，需要分别处理红灯和绿灯倒计时
            val redLightCountDown = intent.getIntExtra("redLightCountDownSeconds", 0)
            val greenLightCountDown = intent.getIntExtra("greenLightLastSecond", 0)  // 关键修复：使用正确的字段名

            // 重大发现：redLightCountDownSeconds 在绿灯状态时存储绿灯倒计时！
            val trafficLightCountDownSeconds = when (trafficLightStatus) {
                -1 -> 0                      // 黄灯状态：通常很短，没有倒计时
                1 -> redLightCountDown       // 红灯状态：redLightCountDownSeconds 是红灯倒计时
                2 -> redLightCountDown       // 绿灯状态：redLightCountDownSeconds 实际是绿灯倒计时！
                3 -> redLightCountDown       // 红灯变体：红灯倒计时
                4 -> redLightCountDown       // 绿灯变体：绿灯倒计时
                else -> redLightCountDown    // 其他状态：使用该字段
            }

            // 关键理解：redLightCountDownSeconds 字段名有误导性，实际存储当前状态的倒计时

            val direction = when {
                intent.hasExtra("dir") -> intent.getIntExtra("dir", 0)
                intent.hasExtra("TRAFFIC_LIGHT_DIRECTION") -> intent.getIntExtra("TRAFFIC_LIGHT_DIRECTION", 0)
                intent.hasExtra("LIGHT_DIRECTION") -> intent.getIntExtra("LIGHT_DIRECTION", 0)
                else -> 0
            }

            // 其他可能的字段
            val trafficLightCount = intent.getIntExtra("TRAFFIC_LIGHT_COUNT", -1)
            val trafficLightDistance = intent.getIntExtra("TRAFFIC_LIGHT_DISTANCE", 0)
            val waitRound = intent.getIntExtra("waitRound", 0)

            // 根据JavaScript参考代码和方向信息映射交通灯状态
            var carrotTrafficState = mapTrafficLightStatus(trafficLightStatus, direction)

            // 使用倒计时秒数作为剩余秒数（支持红灯和绿灯倒计时）
            var leftSec = if (trafficLightCountDownSeconds > 0) {
                trafficLightCountDownSeconds
            } else {
                carrotManFields.value.left_sec
            }

            // 特殊处理：当接收到状态0且倒计时0时，检查是否应该推断为绿灯状态
            val previousTrafficState = carrotManFields.value.traffic_state
            val previousLeftSec = carrotManFields.value.left_sec

            if (carrotTrafficState == 0 && leftSec <= 0) {
                // 如果之前是红灯状态且倒计时接近结束，可能应该转换为绿灯
                if (previousTrafficState == 1 && previousLeftSec <= 3) {
                    Log.w(TAG, "🚦 推断状态转换: 红灯倒计时结束，推断为绿灯状态")
                    carrotTrafficState = 2  // 设置为绿灯
                    leftSec = 30  // 设置默认绿灯倒计时
                    //Log.i(TAG, "🟢 状态推断: 设置为绿灯状态，倒计时30秒")
                }
            }

            // 检测交通灯状态变化（变量已在上面定义）
            val stateChanged = (carrotTrafficState != previousTrafficState) || (leftSec != previousLeftSec)

            // 更新CarrotMan字段
            carrotManFields.value = carrotManFields.value.copy(
                traffic_light_count = if (trafficLightCount >= 0) trafficLightCount else carrotManFields.value.traffic_light_count,
                traffic_state = carrotTrafficState,
                traffic_light_direction = direction,  // 添加方向字段
                left_sec = leftSec,
                max_left_sec = maxOf(leftSec, carrotManFields.value.max_left_sec),
                carrot_left_sec = leftSec,
                // 添加高德地图原始广播字段
                amap_traffic_light_status = trafficLightStatus,
                amap_traffic_light_dir = direction,
                amap_green_light_last_second = greenLightCountDown,
                amap_wait_round = waitRound,
                lastUpdateTime = System.currentTimeMillis()
            )

            // 只在状态变化时记录关键信息
            if (stateChanged) {
                val directionDesc = getTrafficLightDirectionDesc(direction)
                //Log.i(TAG, "🚦 交通灯状态: ${getTrafficLightStatusDesc(trafficLightStatus)} -> ${getCarrotTrafficStateDesc(carrotTrafficState)}, 倒计时: ${leftSec}s, 方向: $directionDesc")
                //Log.i(TAG, "🔍 原始字段分析: trafficLightStatus=$trafficLightStatus, dir=$direction, greenLightLastSecond=$greenLightCountDown, waitRound=$waitRound")
            }

            // 已移除：DETECT 命令发送逻辑（保留在设备端实现）

        } catch (e: Exception) {
            Log.e(TAG, "处理红绿灯信息失败: ${e.message}", e)
        }
    }

    /**
     * 获取交通灯状态描述 (基于实际日志数据修正)
     */
    private fun getTrafficLightStatusDesc(status: Int): String {
        return when (status) {
            -1 -> "黄灯"       // 黄灯状态
            0 -> "未知"        // 未知状态
            1 -> "红灯"        // 红灯状态
            2 -> "绿灯"        // 绿灯状态（重要修正：2是绿灯，不是黄灯）
            3 -> "红灯"        // 红灯变体
            4 -> "绿灯"        // 绿灯变体
            else -> "未知($status)"
        }
    }

    /**
     * 获取CarrotMan交通灯状态描述
     */
    private fun getCarrotTrafficStateDesc(state: Int): String {
        return when (state) {
            -1 -> "黄灯(yellow)"
            0 -> "关闭(off)"
            1 -> "红灯(red)"
            2 -> "绿灯(green)"
            3 -> "左转绿灯(left)"
            else -> "未知($state)"
        }
    }

    /**
     * 获取交通灯方向描述 (基于实际UI观察数据修正)
     * dir字段表示交通灯控制的方向，而不是车辆需要行驶的方向
     */
    private fun getTrafficLightDirectionDesc(direction: Int): String {
        return when (direction) {
            0 -> "直行黄灯"    // 特殊：黄灯状态时dir=0表示直行黄灯
            1 -> "左转"        // 左转方向交通灯
            2 -> "右转"        // 右转方向交通灯
            3 -> "左转掉头"    // 左转掉头方向交通灯
            4 -> "直行"        // 直行方向交通灯
            5 -> "右转掉头"    // 右转掉头方向交通灯
            else -> "方向$direction"
        }
    }


    /**
     * 格式化距离显示
     * 超过10公里显示公里，超过1公里显示几点几公里，1公里内显示米
     * @param distanceMeters 距离（米）
     * @return 格式化的距离字符串
     */
    private fun formatDistance(distanceMeters: Int): String {
        return when {
            distanceMeters >= 10000 -> {
                val kilometers = distanceMeters / 1000
                "${kilometers}公里"
            }
            distanceMeters >= 1000 -> {
                val kilometers = distanceMeters / 1000.0
                "${String.format("%.1f", kilometers)}公里"
            }
            else -> "${distanceMeters}米"
        }
    }

    /**
     * 生成转弯指令文本
     */
    private fun generateTurnInstruction(turnType: Int, roadName: String, distance: Int): String {
        val action = when (turnType) {
            12 -> "左转"
            13 -> "右转"
            14 -> "掉头"
            16 -> "急左转"
            19 -> "急右转"
            51 -> "直行"
            52 -> "直行"
            53 -> "直行进入"  // 高架入口
            54 -> "直行"  // 桥梁
            55 -> "直行"      // 其他通知
            101 -> "右前方"
            102 -> "靠左行驶" //手动纠正
            201 -> "到达目的地"
            1000 -> "轻微左转"
            1001 -> "轻微右转"
            1006 -> "靠左行驶"
            1007 -> "靠右行驶"
            // 分岔路口
            7, 17, 44, 75, 76, 118, 1002 -> "左侧分岔"
            6, 43, 73, 74, 117, 123, 124, 1003 -> "右侧分岔"
            // 环岛
            131, 132, 140, 141 -> "环岛轻微转弯"
            133, 139 -> "环岛转弯"
            134, 135, 136, 137, 138 -> "环岛急转弯"
            142 -> "环岛直行"
            else -> "继续行驶"
        }

        return when {
            turnType == 201 -> "到达目的地"
            roadName.isNotEmpty() && distance > 0 -> "${action}进入${roadName}，${formatDistance(distance)}"
            roadName.isNotEmpty() -> "${action}进入${roadName}"
            distance > 0 -> "${action}，${formatDistance(distance)}"
            else -> action
        }
    }

    /**
     * 处理车道线信息广播 - KEY_TYPE: 13012
     * 根据官方协议EXTRA_DRIVE_WAY字段提取真实的车道数量
     */
    fun handleDriveWayInfo(intent: Intent) {
        try {
            val driveWayJson = intent.getStringExtra("EXTRA_DRIVE_WAY")
            
            if (driveWayJson.isNullOrEmpty()) {
                Log.w(TAG, "⚠️ 车道线数据为空")
                return
            }

            Log.i(TAG, "🛣️ 收到车道线信息:")
            //Log.i(TAG, "  📄 原始JSON: $driveWayJson")

            // 解析车道线JSON数据
            val jsonObject = org.json.JSONObject(driveWayJson)
            
            // 提取关键字段
            val driveWayEnabled = jsonObject.optString("drive_way_enabled", "false")
            val driveWaySize = jsonObject.optInt("drive_way_size", 0)
            
            //Log.i(TAG, "  ✅ 车道线是否有效: $driveWayEnabled")
            Log.i(TAG, "  🔢 车道数量: $driveWaySize")

            // 如果车道线有效且车道数量大于0，则更新字段
            if (driveWayEnabled == "true" && driveWaySize > 0) {
                carrotManFields.value = carrotManFields.value.copy(
                    nLaneCount = driveWaySize,
                    nTBTNextRoadWidth = mapLaneCountToTBTNextRoadWidth(driveWaySize),
                    lastUpdateTime = System.currentTimeMillis()
                )
                
                Log.i(TAG, "  🎯 已更新车道数量到CarrotMan字段: $driveWaySize 车道")
                
                // 详细记录车道信息（如果存在）
                if (jsonObject.has("drive_way_info")) {
                    val driveWayInfo = jsonObject.getJSONArray("drive_way_info")
                    //Log.i(TAG, "  🛣️ 车道详细信息:")
                    for (i in 0 until driveWayInfo.length()) {
                        val laneInfo = driveWayInfo.getJSONObject(i)
                        val laneNumber = laneInfo.optString("drive_way_number", "未知")
                        val laneIcon = laneInfo.optString("drive_way_lane_Back_icon", "未知")
                        //Log.i(TAG, "    车道${laneNumber}: 图标=${laneIcon}")
                    }
                }
            } else {
                Log.w(TAG, "  ❌ 车道线信息无效或车道数量为0")
                // 可选：将车道数量设为0表示无车道信息
                carrotManFields.value = carrotManFields.value.copy(
                    nLaneCount = 0,
                    nTBTNextRoadWidth = getTBTNextRoadWidth(), // 使用roadcate映射
                    lastUpdateTime = System.currentTimeMillis()
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ 解析车道线信息失败: ${e.message}", e)
        }
    }

    /**
     * 🎯 将高德地图的 ROAD_TYPE 映射到 CarrotMan 的 roadcate
     * 重要：roadcate 是道路类别，10,11 表示高速公路，其他值表示非高速公路
     * 基于逆向工程文档和Python代码的映射逻辑
     * 用户说明：roadcate is the width of the road, 10,11 is the highway
     */
    private fun mapRoadTypeToRoadcate(roadType: Int): Int {
        // 简化规则（不依赖车道数）：
        // - 高速(0) → 8车道 → roadcate=10
        // - 国道/省道/主要大街与城市快速道/主要道路(1,2,6,7) → 4车道 → roadcate=10
        // - 其他全部 → 2车道 → roadcate=6
        return when (roadType) {
            0 -> 10
            1, 2, 6, 7 -> 10
            else -> 6
        }
    }
    
    /**
     * 🎯 获取道路类型描述
     * 用于日志记录和调试
     */
    private fun getRoadTypeDescription(roadType: Int): String {
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
            else -> "未知道路类型"
        }
    }
    
    /**
     * 🎯 获取 roadcate 含义描述
     * roadcate 是道路类别参数，10,11 表示高速公路，其他值表示非高速公路
     * 用户说明：roadcate is the width of the road, 10,11 is the highway
     */
    private fun getRoadcateDescription(roadcate: Int): String {
        return when (roadcate) {
            0 -> "默认/未知宽度"
            2 -> "窄道路（单车道）"
            6 -> "中等宽度（双车道）"
            8 -> "宽道路（三车道）"
            10, 11 -> "很宽道路（四车道及以上）"
            else -> "未知 roadcate 值: $roadcate"
        }
    }
    
    /**
     * 🎯 将车道数映射到nTBTNextRoadWidth
     * 基于Python代码的插值逻辑：np.interp(nTBTNextRoadWidth, [5, 10], [43, 60])
     * 车道数 → 道路宽度值
     */
    private fun mapLaneCountToTBTNextRoadWidth(laneCount: Int): Int {
        return when {
            laneCount >= 8 -> 10    // 8+车道 → 很宽道路
            laneCount >= 6 -> 8      // 6-7车道 → 宽道路
            laneCount >= 4 -> 6      // 4-5车道 → 中等宽度
            laneCount >= 2 -> 5      // 2-3车道 → 窄道路
            else -> 5                // 默认窄道路
        }
    }
    
    /**
     * 🎯 将roadcate映射到nTBTNextRoadWidth
     * 基于Python代码的插值逻辑：np.interp(nTBTNextRoadWidth, [5, 10], [43, 60])
     * roadcate值 → 道路宽度值
     */
    private fun mapRoadcateToTBTNextRoadWidth(roadcate: Int): Int {
        return when (roadcate) {
            10, 11 -> 10    // 高速公路 → 很宽道路(10)
            8 -> 8          // 宽道路 → 宽道路(8)  
            6 -> 6          // 中等宽度 → 中等宽度(6)
            2 -> 5          // 窄道路 → 窄道路(5)
            else -> 6       // 默认中等宽度
        }
    }
    
    /**
     * 🎯 获取nTBTNextRoadWidth的最终值
     * 优先级：车道线信息 > roadcate映射 > 默认值
     */
    private fun getTBTNextRoadWidth(): Int {
        // 1. 优先使用车道线信息
        if (carrotManFields.value.nLaneCount > 0) {
            return mapLaneCountToTBTNextRoadWidth(carrotManFields.value.nLaneCount)
        }
        
        // 2. 使用roadcate映射
        if (carrotManFields.value.roadcate > 0) {
            return mapRoadcateToTBTNextRoadWidth(carrotManFields.value.roadcate)
        }
        
        // 3. 默认值
        return 6
    }

    /**
     * 处理地理位置信息广播 - KEY_TYPE: 12205
     */
    fun handleGeolocationInfo(intent: Intent) {
        Log.d(TAG, "🌍 处理地理位置信息广播 (KEY_TYPE: 12205)")
        
        try {
            val extraGeolocation = intent.getIntExtra("EXTRA_GEOLOCATION", -1)
            
            Log.d(TAG, "📍 地理位置信息:")
            Log.d(TAG, "   EXTRA_GEOLOCATION = $extraGeolocation")
            
            // 根据EXTRA_GEOLOCATION的值解释含义
            val geolocationDesc = when (extraGeolocation) {
                0 -> "未知位置状态"
                1 -> "定位成功"
                2 -> "定位失败"
                -1 -> "未设置"
                else -> "未知状态: $extraGeolocation"
            }
            Log.d(TAG, "   📍 位置状态: $geolocationDesc")
            
        } catch (e: Exception) {
            Log.e(TAG, "处理地理位置信息失败: ${e.message}", e)
        }
    }



}
