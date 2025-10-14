package com.example.carrotamap

import java.text.SimpleDateFormat
import java.util.*

/**
 * 数据字段管理器 (简化版)
 * 负责管理CarrotMan核心字段的显示和格式化
 */
class DataFieldManager {

    /**
     * 基础通信字段（7706发送）：carrotIndex/epochTime/timezone
     */
    fun getBasicStatusFields(carrotManFields: CarrotManFields): List<Triple<String, String, String>> {
        return listOf(
            Triple("carrotIndex", "数据包序号", carrotManFields.carrotIndex.toString()),
            Triple("epochTime", "Unix时间戳", (if (carrotManFields.epochTime > 0) carrotManFields.epochTime else (carrotManFields.lastUpdateTime / 1000)).toString()),
            Triple("timezone", "时区", carrotManFields.timezone.ifEmpty { "Asia/Shanghai" })
        )
    }

    /**
     * 道路信息字段（7706发送）：nRoadLimitSpeed/roadcate/szPosRoadName
     */
    fun getSpeedControlFields(carrotManFields: CarrotManFields): List<Triple<String, String, String>> {
        return listOf(
            Triple("nRoadLimitSpeed", "道路限速", "${carrotManFields.nRoadLimitSpeed} km/h"),
            Triple("roadcate", "道路类别", carrotManFields.roadcate.toString()),
            Triple("szPosRoadName", "当前道路", carrotManFields.szPosRoadName.ifEmpty { "无" })
        )
    }

    /**
     * GPS定位字段（7706发送）：latitude/longitude/heading/accuracy/gps_speed
     */
    fun getGpsLocationFields(carrotManFields: CarrotManFields): List<Triple<String, String, String>> {
        return listOf(
            Triple("latitude", "纬度", String.format("%.6f", carrotManFields.latitude)),
            Triple("longitude", "经度", String.format("%.6f", carrotManFields.longitude)),
            Triple("heading", "方向角", String.format("%.1f°", carrotManFields.heading)),
            Triple("accuracy", "GPS精度", String.format("%.1f m", carrotManFields.accuracy)),
            Triple("gps_speed", "GPS速度", String.format("%.1f m/s", carrotManFields.gps_speed))
        )
    }

    /**
     * TBT转弯导航字段（7706发送）
     */
    fun getTurnGuidanceFields(carrotManFields: CarrotManFields): List<Triple<String, String, String>> {
        return listOf(
            Triple("nTBTDist", "转弯距离", "${carrotManFields.nTBTDist} m"),
            Triple("nTBTTurnType", "转弯类型", carrotManFields.nTBTTurnType.toString()),
            Triple("szTBTMainText", "主要指令", carrotManFields.szTBTMainText.ifEmpty { "无" }),
            Triple("szNearDirName", "近处方向", carrotManFields.szNearDirName.ifEmpty { "无" }),
            Triple("szFarDirName", "远处方向", carrotManFields.szFarDirName.ifEmpty { "无" }),
            Triple("nTBTNextRoadWidth", "下一道路宽度", carrotManFields.nTBTNextRoadWidth.toString()),
            Triple("nTBTDistNext", "下一转弯距离", "${carrotManFields.nTBTDistNext} m"),
            Triple("nTBTTurnTypeNext", "下一转弯类型", carrotManFields.nTBTTurnTypeNext.toString()),
            Triple("szTBTMainTextNext", "下一指令", carrotManFields.szTBTMainTextNext.ifEmpty { "无" })
        )
    }

    /**
     * 目的地信息字段（7706发送）：goalPosX/goalPosY/szGoalName
     */
    fun getRouteTargetFields(carrotManFields: CarrotManFields): List<Triple<String, String, String>> {
        return listOf(
            Triple("goalPosX", "目标经度", String.format("%.6f", carrotManFields.goalPosX)),
            Triple("goalPosY", "目标纬度", String.format("%.6f", carrotManFields.goalPosY)),
            Triple("szGoalName", "目标名称", carrotManFields.szGoalName.ifEmpty { "无" })
        )
    }

    /**
     * SDI 与 SDI Plus 字段（7706发送）
     */
    fun getSdiCameraFields(carrotManFields: CarrotManFields): List<Triple<String, String, String>> {
        return listOf(
            // SDI
            Triple("nSdiType", "SDI类型", carrotManFields.nSdiType.toString()),
            Triple("nSdiSpeedLimit", "SDI限速", "${carrotManFields.nSdiSpeedLimit} km/h"),
            Triple("nSdiDist", "SDI距离", "${carrotManFields.nSdiDist} m"),
            Triple("nSdiSection", "区间测速ID", carrotManFields.nSdiSection.toString()),
            Triple("nSdiBlockType", "区间状态", carrotManFields.nSdiBlockType.toString()),
            Triple("nSdiBlockSpeed", "区间限速", "${carrotManFields.nSdiBlockSpeed} km/h"),
            Triple("nSdiBlockDist", "区间距离", "${carrotManFields.nSdiBlockDist} m"),
            // SDI Plus
            Triple("nSdiPlusType", "SDI Plus类型", carrotManFields.nSdiPlusType.toString()),
            Triple("nSdiPlusSpeedLimit", "SDI Plus限速", "${carrotManFields.nSdiPlusSpeedLimit} km/h"),
            Triple("nSdiPlusDist", "SDI Plus距离", "${carrotManFields.nSdiPlusDist} m"),
            Triple("nSdiPlusBlockType", "Plus区间类型", carrotManFields.nSdiPlusBlockType.toString()),
            Triple("nSdiPlusBlockSpeed", "Plus区间限速", "${carrotManFields.nSdiPlusBlockSpeed} km/h"),
            Triple("nSdiPlusBlockDist", "Plus区间距离", "${carrotManFields.nSdiPlusBlockDist} m")
        )
    }

    /**
     * 目的地剩余信息（7706发送）：nGoPosDist/nGoPosTime
     */
    fun getGoPosRemainFields(carrotManFields: CarrotManFields): List<Triple<String, String, String>> {
        return listOf(
            Triple("nGoPosDist", "剩余距离", "${carrotManFields.nGoPosDist} m"),
            Triple("nGoPosTime", "剩余时间", "${carrotManFields.nGoPosTime} s")
        )
    }

    /**
     * 导航位置字段（7706发送）：vpPosPointLat/vpPosPointLon/nPosAngle/nPosSpeed
     */
    fun getNaviPositionFields(carrotManFields: CarrotManFields): List<Triple<String, String, String>> {
        return listOf(
            Triple("vpPosPointLat", "导航纬度", String.format("%.6f", carrotManFields.vpPosPointLat)),
            Triple("vpPosPointLon", "导航经度", String.format("%.6f", carrotManFields.vpPosPointLon)),
            Triple("nPosAngle", "导航方向角", String.format("%.1f°", carrotManFields.nPosAngle)),
            Triple("nPosSpeed", "导航速度", String.format("%.1f km/h", carrotManFields.nPosSpeed))
        )
    }

    /**
     * 命令控制字段（7706发送）：carrotCmd/carrotArg
     */
    fun getCommandFields(carrotManFields: CarrotManFields): List<Triple<String, String, String>> {
        return listOf(
            Triple("carrotCmd", "命令类型", carrotManFields.carrotCmd.ifEmpty { "无" }),
            Triple("carrotArg", "命令参数", carrotManFields.carrotArg.ifEmpty { "无" })
        )
    }

    /**
     * 系统状态字段（保留旧接口，避免UI引用报错）
     */
    fun getSystemStatusFields(carrotManFields: CarrotManFields): List<Triple<String, String, String>> {
        return listOf(
            Triple("left_sec", "剩余秒数", "${carrotManFields.left_sec} s"),
            Triple("carrot_left_sec", "倒计时", "${carrotManFields.carrot_left_sec} s"),
            Triple("debugText", "调试信息", carrotManFields.debugText.ifEmpty { "无" })
        )
    }

    /**
     * 7705 接收字段（从 OpenPilot 获取）
     */
    fun getOpenpilotReceiveFields(carrotManFields: CarrotManFields): List<Triple<String, String, String>> {
        return listOf(
            Triple("carrot2", "OpenPilot版本", carrotManFields.carrot2.ifEmpty { "" }),
            Triple("isOnroad", "在道路上", carrotManFields.isOnroad.toString()),
            Triple("carrotRouteActive", "路线激活", carrotManFields.carrotRouteActive.toString()),
            Triple("ip", "设备IP", carrotManFields.ip.ifEmpty { "" }),
            Triple("port", "端口", carrotManFields.port.toString()),
            Triple("logCarrot", "日志", carrotManFields.logCarrot.ifEmpty { "" }),
            Triple("vCruiseKph", "巡航速度", String.format("%.1f km/h", carrotManFields.vCruiseKph)),
            Triple("vEgoKph", "当前车速", "${carrotManFields.vEgoKph} km/h"),
            Triple("tbtDist", "转弯距离(来自OP)", "${carrotManFields.tbtDist} m"),
            Triple("sdiDist", "SDI距离(来自OP)", "${carrotManFields.sdiDist} m"),
            Triple("active", "控制激活", carrotManFields.active.toString()),
            Triple("xState", "纵向状态", carrotManFields.xState.toString()),
            Triple("trafficState", "交通灯状态", carrotManFields.trafficState.toString())
        )
    }



    /**
     * 格式化时间戳
     */
    private fun formatTimestamp(timestamp: Long): String {
        if (timestamp == 0L) return "未设置"
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
