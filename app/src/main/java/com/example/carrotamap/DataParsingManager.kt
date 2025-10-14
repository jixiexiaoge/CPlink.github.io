package com.example.carrotamap

import android.content.Intent
import android.util.Log
import org.json.JSONException
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * 数据解析管理器
 * 负责处理所有的数据解析、格式化和映射方法
 */
class DataParsingManager {
    companion object {
        private const val TAG = "DataParsingManager"
    }

    // ===============================
    // 数据解析方法 - parse*Content
    // ===============================

    /**
     * 解析引导信息内容
     */
    fun parseGuideInfoContent(intent: Intent): String {
        val currentRoad = intent.getStringExtra("CUR_ROAD_NAME") ?: ""
        val nextRoad = intent.getStringExtra("NEXT_ROAD_NAME") ?: ""
        val remainDistance = intent.getIntExtra("ROUTE_REMAIN_DIS", 0)
        val remainTime = intent.getIntExtra("ROUTE_REMAIN_TIME", 0)
        val segRemainDis = intent.getIntExtra("SEG_REMAIN_DIS", 0)
        
        return buildString {
            appendLine("当前道路: $currentRoad")
            appendLine("下一道路: $nextRoad")
            appendLine("剩余距离: ${remainDistance}m")
            appendLine("剩余时间: ${formatSeconds(remainTime)}")
            appendLine("段剩余距离: ${segRemainDis}m")
        }
    }

    /**
     * 解析定位信息内容
     */
    fun parseLocationInfoContent(intent: Intent): String {
        val latitude = intent.getDoubleExtra("CAR_LATITUDE", 0.0)
        val longitude = intent.getDoubleExtra("CAR_LONGITUDE", 0.0)
        val bearing = intent.getIntExtra("CAR_DIRECTION", -1)
        val speed = intent.getDoubleExtra("CAR_SPEED", 0.0)
        
        return buildString {
            appendLine("纬度: ${String.format("%.6f", latitude)}")
            appendLine("经度: ${String.format("%.6f", longitude)}")
            if (bearing >= 0) appendLine("方向: ${bearing}°")
            if (speed > 0) appendLine("速度: ${String.format("%.1f", speed)} km/h")
        }
    }

    /**
     * 解析转弯信息内容
     */
    fun parseTurnInfoContent(intent: Intent): String {
        val turnType = intent.getIntExtra("TURN_TYPE", -1)
        val remainDis = intent.getIntExtra("REMAIN_DIS", 0)
        val nextRoadName = intent.getStringExtra("NEXT_ROAD_NAME") ?: ""
        val icon = intent.getIntExtra("ICON", -1)
        
        return buildString {
            appendLine("转弯类型: ${mapTurnTypeToAction(turnType)}")
            appendLine("剩余距离: ${remainDis}m")
            appendLine("下一道路: $nextRoadName")
            appendLine("转弯图标: ${getTurnIconDescription(icon)}")
        }
    }

    /**
     * 解析导航状态内容
     */
    fun parseNavigationStatusContent(intent: Intent): String {
        val naviState = intent.getIntExtra("NAVI_STATE", -1)
        return when (naviState) {
            1 -> "导航状态: 准备导航"
            2 -> "导航状态: 导航中"
            3 -> "导航状态: 导航暂停"
            4 -> "导航状态: 导航结束"
            else -> "导航状态: 未知($naviState)"
        }
    }

    /**
     * 解析路线信息内容
     */
    fun parseRouteInfoContent(intent: Intent): String {
        val routeRemainDis = intent.getIntExtra("ROUTE_REMAIN_DIS", 0)
        val routeRemainTime = intent.getIntExtra("ROUTE_REMAIN_TIME", 0)
        val destinationName = intent.getStringExtra("DESTINATION_NAME") ?: ""
        
        return buildString {
            appendLine("剩余距离: ${routeRemainDis}m")
            appendLine("剩余时间: ${formatSeconds(routeRemainTime)}")
            if (destinationName.isNotEmpty()) appendLine("目的地: $destinationName")
        }
    }

    /**
     * 解析限速信息内容
     */
    fun parseSpeedLimitContent(intent: Intent): String {
        val limitedSpeed = intent.getIntExtra("LIMITED_SPEED", 0)
        return if (limitedSpeed > 0) "限速: ${limitedSpeed}km/h" else "限速信息"
    }

    /**
     * 解析地图状态内容
     */
    fun parseMapStateContent(intent: Intent): String {
        val state = intent.getIntExtra("EXTRA_STATE", -1)
        return when (state) {
            0 -> "开始运行"
            1 -> "暂停运行"
            2 -> "停止运行"
            3 -> "退出应用"
            39 -> "到达目的地"
            else -> "未知状态($state)"
        }
    }

    /**
     * 解析电子眼信息内容
     */
    fun parseCameraInfoContent(intent: Intent): String {
        val cameraInfoJson = intent.getStringExtra("CAMERA_INFO")
        return if (cameraInfoJson != null) {
            try {
                val cameraJson = JSONObject(cameraInfoJson)
                val distance = cameraJson.optInt("distance", -1)
                val type = cameraJson.optInt("type", -1)
                val speedLimit = cameraJson.optInt("speedLimit", 0)
                
                buildString {
                    appendLine("摄像头类型: ${mapCameraType(type)}")
                    if (distance > 0) appendLine("距离: ${distance}m")
                    if (speedLimit > 0) appendLine("限速: ${speedLimit}km/h")
                }
            } catch (e: JSONException) {
                "电子眼信息解析失败: ${e.message}"
            }
        } else {
            // 尝试直接从Intent获取
            val distance = intent.getIntExtra("CAMERA_DIST", -1)
            val type = intent.getIntExtra("CAMERA_TYPE", -1)
            val speedLimit = intent.getIntExtra("CAMERA_SPEED", 0)
            
            buildString {
                if (type >= 0) appendLine("摄像头类型: ${mapCameraType(type)}")
                if (distance > 0) appendLine("距离: ${distance}m")
                if (speedLimit > 0) appendLine("限速: ${speedLimit}km/h")
            }
        }
    }

    // ===============================
    // 数据映射方法 - map*
    // ===============================

    /**
     * 映射转弯类型到动作描述
     */
    fun mapTurnTypeToAction(turnType: Int): String {
        return when (turnType) {
            1 -> "直行"
            2 -> "左转"
            3 -> "右转"
            4 -> "左前方"
            5 -> "右前方"
            6 -> "左后方"
            7 -> "右后方"
            8 -> "左转掉头"
            9 -> "右转掉头"
            10 -> "直行掉头"
            11 -> "进入环岛"
            12 -> "驶出环岛"
            13 -> "靠左"
            14 -> "靠右"
            15 -> "左侧并道"
            16 -> "右侧并道"
            17 -> "进入隧道"
            18 -> "驶出隧道"
            19 -> "进入桥梁"
            20 -> "驶出桥梁"
            55 -> "行驶到出口"
            else -> "未知转弯($turnType)"
        }
    }

    /**
     * 映射摄像头类型
     */
    fun mapCameraType(type: Int): String {
        return when (type) {
            0 -> "测速摄像头(限速拍照)"
            1 -> "监控摄像头(治安监控)"
            2 -> "闯红灯拍照(红绿灯路口)"
            3 -> "违章拍照(压线/禁停等)"
            4 -> "公交专用道摄像头(公交车道监控)"
            5 -> "区间测速结束"
            else -> "未知摄像头($type)"
        }
    }

    /**
     * 获取转弯图标描述
     */
    fun getTurnIconDescription(icon: Int): String {
        return when (icon) {
            0 -> "通知"
            1 -> "直行"
            2 -> "左转"
            3 -> "右转"
            4 -> "左前方"
            5 -> "右前方"
            6 -> "左后方"
            7 -> "右后方"
            8 -> "左转掉头"
            9 -> "右转掉头"
            10 -> "直行掉头"
            11 -> "进入环岛"
            12 -> "驶出环岛"
            13 -> "靠左"
            14 -> "靠右"
            15 -> "左侧并道"
            16 -> "右侧并道"
            17 -> "进入隧道"
            18 -> "驶出隧道"
            19 -> "进入桥梁"
            20 -> "驶出桥梁"
            else -> "未知图标($icon)"
        }
    }

    /**
     * 获取导航类型描述
     */
    fun getNaviTypeDescription(type: Int): String {
        return when (type) {
            0 -> "GPS导航"
            1 -> "模拟导航"
            2 -> "离线导航"
            else -> "未知类型($type)"
        }
    }

    // ===============================
    // 格式化方法 - format*
    // ===============================

    /**
     * 格式化秒数为可读时间
     */
    fun formatSeconds(seconds: Int): String {
        if (seconds <= 0) return "0秒"
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        
        return buildString {
            if (hours > 0) append("${hours}小时")
            if (minutes > 0) append("${minutes}分钟")
            if (secs > 0 || (hours == 0 && minutes == 0)) append("${secs}秒")
        }
    }

    /**
     * 格式化时间戳
     */
    fun formatTimestamp(timestamp: Long): String {
        if (timestamp == 0L) return "未设置"
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    /**
     * 格式化距离
     */
    fun formatDistance(meters: Int): String {
        return when {
            meters < 1000 -> "${meters}m"
            meters < 10000 -> "${String.format("%.1f", meters / 1000.0)}km"
            else -> "${meters / 1000}km"
        }
    }

    /**
     * 格式化速度
     */
    fun formatSpeed(kmh: Double): String {
        return "${String.format("%.1f", kmh)} km/h"
    }
}
