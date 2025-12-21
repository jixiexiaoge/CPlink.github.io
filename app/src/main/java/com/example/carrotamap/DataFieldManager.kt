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
     * GPS定位字段（已移除发送）：latitude/longitude/heading/accuracy/gps_speed
     * 注意：这些字段已从JSON发送中移除，仅用于内部显示
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
            Triple("debugText", "调试信息", carrotManFields.debugText.ifEmpty { "无" }),
            Triple("isNavigating", "导航状态", if (carrotManFields.isNavigating) "导航中" else "待机"),
            Triple("dataQuality", "数据质量", carrotManFields.dataQuality),
            Triple("source_last", "最后数据源", carrotManFields.source_last),
            Triple("remote", "远程IP", carrotManFields.remote.ifEmpty { "无" }),
            Triple("lastUpdateTime", "最后更新", formatTimestamp(carrotManFields.lastUpdateTime))
        )
    }

    /**
     * 7705 接收字段（从 OpenPilot 获取）
     */
    fun getOpenpilotReceiveFields(carrotManFields: CarrotManFields): List<Triple<String, String, String>> {
        return listOf(
            Triple("carrot2", "OpenPilot版本", carrotManFields.carrot2.ifEmpty { "未知" }),
            Triple("isOnroad", "在道路上", if (carrotManFields.isOnroad) "是" else "否"),
            Triple("carrotRouteActive", "路线激活", if (carrotManFields.carrotRouteActive) "是" else "否"),
            Triple("ip", "设备IP", carrotManFields.ip.ifEmpty { "未连接" }),
            Triple("port", "端口", carrotManFields.port.toString()),
            Triple("logCarrot", "日志", carrotManFields.logCarrot.ifEmpty { "无日志" }),
            Triple("vCruiseKph", "巡航速度", String.format("%.1f km/h", carrotManFields.vCruiseKph)),
            Triple("vEgoKph", "当前车速", "${carrotManFields.vEgoKph} km/h"),
            Triple("tbtDist", "转弯距离(来自OP)", "${carrotManFields.tbtDist} m"),
            Triple("sdiDist", "SDI距离(来自OP)", "${carrotManFields.sdiDist} m"),
            Triple("active", "控制激活", if (carrotManFields.active) "激活" else "未激活"),
            Triple("xState", "纵向状态", getXStateDescription(carrotManFields.xState)),
            Triple("trafficState", "交通灯状态", getTrafficStateDescription(carrotManFields.trafficState)),
            Triple("carcruiseSpeed", "车辆巡航速度", String.format("%.1f km/h", carrotManFields.carcruiseSpeed))
        )
    }
    
    /**
     * 内部处理字段（用于调试）
     */
    fun getInternalFields(carrotManFields: CarrotManFields): List<Triple<String, String, String>> {
        return listOf(
            Triple("xSpdLimit", "当前限速", "${carrotManFields.xSpdLimit} km/h"),
            Triple("xSpdDist", "限速距离", "${carrotManFields.xSpdDist} m"),
            Triple("xSpdType", "限速类型", carrotManFields.xSpdType.toString()),
            Triple("xTurnInfo", "转弯信息", carrotManFields.xTurnInfo.toString()),
            Triple("xDistToTurn", "转弯距离", "${carrotManFields.xDistToTurn} m"),
            Triple("active_carrot", "Carrot状态", carrotManFields.active_carrot.toString()),
            Triple("navType", "导航类型", carrotManFields.navType),
            Triple("navModifier", "导航修饰符", carrotManFields.navModifier),
            Triple("atcType", "ATC类型", carrotManFields.atcType.ifEmpty { "无" }),
            Triple("desiredSpeed", "期望速度", "${carrotManFields.desiredSpeed} km/h"),
            Triple("desiredSource", "速度来源", carrotManFields.desiredSource),
            Triple("vTurnSpeed", "转弯速度", "${carrotManFields.vTurnSpeed} km/h"),
            Triple("totalDistance", "总距离", "${carrotManFields.totalDistance} m"),
            Triple("naviPaths", "导航路径", if (carrotManFields.naviPaths.isNotEmpty()) "有路径" else "无路径")
        )
    }
    
    /**
     * NOA 增强与演进字段 (7706发送)
     */
    fun getNoaAdvFields(carrotManFields: CarrotManFields): List<Triple<String, String, String>> {
        return listOf(
            Triple("exitNameInfo", "出口名称", carrotManFields.exitNameInfo.ifEmpty { "无" }),
            Triple("exitDirectionInfo", "出口方向", carrotManFields.exitDirectionInfo.ifEmpty { "无" }),
            Triple("roundAboutNum", "环岛出口序号", if (carrotManFields.roundAboutNum >= 0) carrotManFields.roundAboutNum.toString() else "无"),
            Triple("roundAllNum", "环岛出口总数", if (carrotManFields.roundAllNum >= 0) carrotManFields.roundAllNum.toString() else "无"),
            Triple("segAssistantAction", "段辅助动作", carrotManFields.segAssistantAction.toString()),
            Triple("sapaName", "服务区/设施", carrotManFields.sapaName.ifEmpty { "无" }),
            Triple("sapaDist", "设施距离", if (carrotManFields.sapaDist >= 0) "${carrotManFields.sapaDist} m" else "无"),
            Triple("nextNextAddIcon", "下下个指令", carrotManFields.nextNextAddIcon.ifEmpty { "无" }),
            Triple("viaPOIdistance", "途径点距离", if (carrotManFields.viaPOIdistance >= 0) "${carrotManFields.viaPOIdistance} m" else "无"),
            Triple("nextRoadNOAOrNot", "下段NOA", if (carrotManFields.nextRoadNOAOrNot) "可用" else "不可用"),
            Triple("curSegNum", "当前段号", carrotManFields.curSegNum.toString()),
            Triple("curPointNum", "当前点号", carrotManFields.curPointNum.toString())
        )
    }

    /**
     * 交通灯相关字段
     */
    fun getTrafficLightFields(carrotManFields: CarrotManFields): List<Triple<String, String, String>> {
        return listOf(
            Triple("trafficState", "交通灯状态", getTrafficStateDescription(carrotManFields.trafficState)),
            Triple("leftSec", "剩余秒数", "${carrotManFields.leftSec} s"),
            Triple("traffic_light_direction", "交通灯方向", carrotManFields.traffic_light_direction.toString()),
            Triple("traffic_light_count", "交通灯数量", carrotManFields.traffic_light_count.toString()),
            Triple("max_left_sec", "最大剩余秒数", "${carrotManFields.max_left_sec} s"),
            Triple("carrot_left_sec", "Carrot倒计时", "${carrotManFields.carrot_left_sec} s")
        )
    }

    /**
     * 获取XState描述
     */
    private fun getXStateDescription(xState: Int): String {
        return when (xState) {
            0 -> "跟车模式"
            1 -> "巡航模式"
            2 -> "端到端巡航"
            3 -> "端到端停车"
            4 -> "端到端准备"
            5 -> "端到端已停"
            else -> "未知状态($xState)"
        }
    }
    
    /**
     * 获取交通灯状态描述
     */
    private fun getTrafficStateDescription(trafficState: Int): String {
        return when (trafficState) {
            0 -> "无信号"
            1 -> "红灯"
            2 -> "绿灯"
            3 -> "左转"
            else -> "未知($trafficState)"
        }
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
