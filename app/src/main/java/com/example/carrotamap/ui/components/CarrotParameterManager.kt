package com.example.carrotamap.ui.components

import android.util.Log
import com.example.carrotamap.NetworkManager
import com.example.carrotamap.ZmqClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class ParameterGroup(val displayName: String) {
    START("启动设置"),
    TUNING("转向调优"),
    DISP("显示设置"),
    SPEED("速度控制"),
    FDIST("车辆间距"),
    CRUISE("巡航控制"),
    ACCEL("加速度设置"),
    DECEL("减速控制")
}

data class ParameterOption(
    val value: Int,
    val label: String
)

data class CarrotParameterDefinition(
    val name: String,
    val title: String,
    val description: String,
    val group: ParameterGroup,
    val minValue: Int,
    val maxValue: Int,
    val defaultValue: Int,
    val step: Int = 1,
    val options: List<ParameterOption> = emptyList()
) {
    val isBoolean: Boolean
        get() = minValue == 0 && maxValue == 1 && options.isEmpty()

    val prefersSlider: Boolean
        get() = options.isEmpty() && (maxValue - minValue) > 1
}

data class CarrotParameterState(
    val definition: CarrotParameterDefinition,
    val currentValue: Int,
    val editedValue: Int
) {
    val isModified: Boolean get() = currentValue != editedValue
}

class CarrotParameterManager(
    private val networkManager: NetworkManager?,
    private val zmqClient: ZmqClient?
) {
    companion object {
        private const val TAG = "CarrotParameterMgr"
    }

    suspend fun loadParameterStates(): List<CarrotParameterState> {
        var httpSuccess = false
        var zmqSuccess = false
        
        // 优先尝试使用HTTP API获取所有参数
        val httpResult = networkManager?.getToggleValuesFromComma3()
        val toggleValues = if (httpResult?.isSuccess == true) {
            httpSuccess = true
            val values = httpResult.getOrNull() ?: emptyMap()
            Log.i(TAG, "✅ 使用HTTP API获取参数，共 ${values.size} 个参数")
            values
        } else {
            // HTTP API失败，记录错误但继续尝试ZMQ
            val httpError = httpResult?.exceptionOrNull()
            if (httpError != null) {
                Log.w(TAG, "⚠️ HTTP API获取参数失败: ${httpError.message}，尝试ZMQ方式")
            } else if (networkManager == null) {
                Log.w(TAG, "⚠️ NetworkManager未初始化，尝试ZMQ方式")
            }
            emptyMap()
        }
        
        // 创建参数状态列表
        return CARROT_PARAMETER_DEFINITIONS.map { definition ->
            var value: Int? = null
            
            // 如果HTTP API成功，优先使用HTTP API的值
            if (httpSuccess) {
                val valueStr = toggleValues[definition.name]
                if (valueStr != null) {
                    value = valueStr.toIntOrNull()
                    if (value != null) {
                        zmqSuccess = true // 至少有一个参数成功获取
                    }
                }
            }
            
            // 如果HTTP API失败或该参数不在响应中，尝试ZMQ
            if (value == null) {
                val zmqValue = readParameterValue(definition)
                if (zmqValue != null) {
                    value = zmqValue
                    zmqSuccess = true
                }
            }
            
            // 如果两种方式都失败，使用默认值
            val finalValue = value ?: definition.defaultValue
            
            CarrotParameterState(
                definition = definition,
                currentValue = finalValue,
                editedValue = finalValue
            )
        }.also {
            // 记录最终结果
            if (!httpSuccess && !zmqSuccess) {
                Log.e(TAG, "❌ HTTP API和ZMQ都失败，使用默认值")
            } else if (httpSuccess) {
                Log.i(TAG, "✅ 参数加载完成（HTTP API）")
            } else if (zmqSuccess) {
                Log.i(TAG, "✅ 参数加载完成（ZMQ）")
            }
        }
    }

    fun getDefaultStates(): List<CarrotParameterState> {
        return CARROT_PARAMETER_DEFINITIONS.map { definition ->
            CarrotParameterState(
                definition = definition,
                currentValue = definition.defaultValue,
                editedValue = definition.defaultValue
            )
        }
    }

    suspend fun applyParameterChanges(changes: Map<String, Int>): Result<String> {
        if (changes.isEmpty()) {
            return Result.success("无需更新参数")
        }

        val payload = changes.mapValues { it.value.toString() }
        return networkManager?.sendParameterSettingsToComma3(payload)
            ?: Result.failure(Exception("NetworkManager 未初始化"))
    }

    private suspend fun readParameterValue(definition: CarrotParameterDefinition): Int? {
        val client = zmqClient ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val result = client.executeCommand("cat /data/params/d/${definition.name}")
                if (result.success && result.result.isNotBlank()) {
                    result.result.trim().toIntOrNull()
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.w(TAG, "读取参数失败: ${definition.name}", e)
                null
            }
        }
    }
}

private val CARROT_PARAMETER_DEFINITIONS = listOf(
    CarrotParameterDefinition(
        name = "AutoEngage",
        title = "Auto Engage control on start",
        description = "启动时自动启用控制: 0=禁用 1=仅转向 2=转向+巡航",
        group = ParameterGroup.START,
        minValue = 0,
        maxValue = 2,
        defaultValue = 0,
        options = listOf(
            ParameterOption(0, "禁用"),
            ParameterOption(1, "仅转向"),
            ParameterOption(2, "转向+巡航")
        )
    ),
    CarrotParameterDefinition(
        name = "SpeedFromPCM",
        title = "Read Cruise Speed from PCM",
        description = "从PCM读取巡航速度: 丰田=1, 本田=3",
        group = ParameterGroup.START,
        minValue = 0,
        maxValue = 3,
        defaultValue = 0,
        options = listOf(
            ParameterOption(0, "默认"),
            ParameterOption(1, "丰田"),
            ParameterOption(2, "模式2"),
            ParameterOption(3, "本田")
        )
    ),
    CarrotParameterDefinition(
        name = "MapboxStyle",
        title = "Mapbox Style",
        description = "地图样式: 0=默认 1=夜间 2=卫星",
        group = ParameterGroup.START,
        minValue = 0,
        maxValue = 2,
        defaultValue = 0,
        options = listOf(
            ParameterOption(0, "默认"),
            ParameterOption(1, "夜间"),
            ParameterOption(2, "卫星")
        )
    ),
    CarrotParameterDefinition(
        name = "AutoGasSyncSpeed",
        title = "Auto update Cruise speed",
        description = "自动更新巡航速度: 0=禁用 1=启用",
        group = ParameterGroup.START,
        minValue = 0,
        maxValue = 1,
        defaultValue = 0
    ),
    CarrotParameterDefinition(
        name = "DisableMinSteerSpeed",
        title = "Disable Min.SteerSpeed",
        description = "禁用最小转向速度限制: 0=使用限制 1=禁用限制",
        group = ParameterGroup.START,
        minValue = 0,
        maxValue = 1,
        defaultValue = 0
    ),
    CarrotParameterDefinition(
        name = "DisableDM",
        title = "Disable DM",
        description = "禁用驾驶员监控: 0=启用DM 1=禁用DM",
        group = ParameterGroup.START,
        minValue = 0,
        maxValue = 1,
        defaultValue = 0
    ),
    CarrotParameterDefinition(
        name = "PathOffset",
        title = "Lane Deviation Left/Right Correction",
        description = "车道偏移左右校正 (-)左 (+)右",
        group = ParameterGroup.TUNING,
        minValue = -150,
        maxValue = 150,
        defaultValue = 0
    ),
    CarrotParameterDefinition(
        name = "AdjustLaneOffset",
        title = "AdjustLaneOffset(0)cm",
        description = "车道偏移调整(厘米)",
        group = ParameterGroup.TUNING,
        minValue = 0,
        maxValue = 500,
        defaultValue = 0,
        step = 5
    ),
    CarrotParameterDefinition(
        name = "LaneChangeNeedTorque",
        title = "LaneChange need torque",
        description = "变道扭矩要求: -1=禁用 0=无需扭矩 1=需要扭矩",
        group = ParameterGroup.TUNING,
        minValue = -1,
        maxValue = 1,
        defaultValue = 0,
        options = listOf(
            ParameterOption(-1, "禁用"),
            ParameterOption(0, "无需扭矩"),
            ParameterOption(1, "需要扭矩")
        )
    ),
    CarrotParameterDefinition(
        name = "LaneChangeDelay",
        title = "LaneChange delay",
        description = "变道延迟时间 (x0.1秒)",
        group = ParameterGroup.TUNING,
        minValue = 0,
        maxValue = 100,
        defaultValue = 0
    ),
    CarrotParameterDefinition(
        name = "LaneChangeBsd",
        title = "LaneChange BSD",
        description = "变道盲区检测: -1=忽略 0=检测 1=阻止转向",
        group = ParameterGroup.TUNING,
        minValue = -1,
        maxValue = 1,
        defaultValue = 0,
        options = listOf(
            ParameterOption(-1, "忽略"),
            ParameterOption(0, "允许扭矩"),
            ParameterOption(1, "阻止扭矩")
        )
    ),
    CarrotParameterDefinition(
        name = "AutoTurnControl",
        title = "Auto Turn Control",
        description = "自动转向控制模式: 0=不使用 1=车道变更 2=车道变更+速度 3=速度",
        group = ParameterGroup.TUNING,
        minValue = 0,
        maxValue = 3,
        defaultValue = 0,
        options = listOf(
            ParameterOption(0, "不使用"),
            ParameterOption(1, "车道变更"),
            ParameterOption(2, "车道变更+速度"),
            ParameterOption(3, "速度")
        )
    ),
    CarrotParameterDefinition(
        name = "ShowDebugUI",
        title = "Show Debug Info",
        description = "调试信息显示等级",
        group = ParameterGroup.DISP,
        minValue = 0,
        maxValue = 2,
        defaultValue = 1,
        options = listOf(
            ParameterOption(0, "隐藏"),
            ParameterOption(1, "基础"),
            ParameterOption(2, "详细")
        )
    ),
    CarrotParameterDefinition(
        name = "ShowLaneInfo",
        title = "Lane Info",
        description = "车道信息显示: -1无 0路径 1路径+车道 2路径+车道+道路边缘",
        group = ParameterGroup.DISP,
        minValue = -1,
        maxValue = 2,
        defaultValue = 1,
        options = listOf(
            ParameterOption(-1, "无"),
            ParameterOption(0, "路径"),
            ParameterOption(1, "路径+车道"),
            ParameterOption(2, "路径+车道+道路边缘")
        )
    ),
    CarrotParameterDefinition(
        name = "AutoNaviSpeedCtrlEnd",
        title = "SpeedCamDecelEnd",
        description = "过速摄像头减速完成时间 (秒)",
        group = ParameterGroup.SPEED,
        minValue = 3,
        maxValue = 20,
        defaultValue = 6
    ),
    CarrotParameterDefinition(
        name = "AutoNaviSpeedCtrlMode",
        title = "NaviSpeedControlMode",
        description = "导航速度控制: 0不减速 1摄像头 2+减速带 3+移动摄像头",
        group = ParameterGroup.SPEED,
        minValue = 0,
        maxValue = 3,
        defaultValue = 2,
        options = listOf(
            ParameterOption(0, "不启用"),
            ParameterOption(1, "摄像头"),
            ParameterOption(2, "摄像头+减速带"),
            ParameterOption(3, "全部")
        )
    ),
    CarrotParameterDefinition(
        name = "AutoRoadSpeedLimitOffset",
        title = "RoadSpeedLimitOffset",
        description = "道路限速偏移: -1不使用",
        group = ParameterGroup.SPEED,
        minValue = -1,
        maxValue = 100,
        defaultValue = -1,
        step = 1
    ),
    CarrotParameterDefinition(
        name = "TurnSpeedControlMode",
        title = "Turn Speed control mode",
        description = "转弯速度控制: 0关 1视觉 2视觉+路线 3路线",
        group = ParameterGroup.SPEED,
        minValue = 0,
        maxValue = 3,
        defaultValue = 1,
        options = listOf(
            ParameterOption(0, "关闭"),
            ParameterOption(1, "视觉"),
            ParameterOption(2, "视觉+路线"),
            ParameterOption(3, "路线")
        )
    ),
    CarrotParameterDefinition(
        name = "MapTurnSpeedFactor",
        title = "Map TurnSpeed Factor",
        description = "地图转弯速度因子(%)",
        group = ParameterGroup.SPEED,
        minValue = 50,
        maxValue = 300,
        defaultValue = 100,
        step = 5
    ),
    CarrotParameterDefinition(
        name = "ModelTurnSpeedFactor",
        title = "Model TurnSpeed Factor",
        description = "模型转弯速度因子 (x0.1秒)",
        group = ParameterGroup.SPEED,
        minValue = 0,
        maxValue = 80,
        defaultValue = 0
    ),
    CarrotParameterDefinition(
        name = "AutoNaviSpeedSafetyFactor",
        title = "SpeedCamSafetyFactor",
        description = "过速摄像头安全因子(%)",
        group = ParameterGroup.SPEED,
        minValue = 80,
        maxValue = 120,
        defaultValue = 105
    ),
    CarrotParameterDefinition(
        name = "DynamicTFollow",
        title = "Dynamic TFollow",
        description = "动态跟车距离设置(%)",
        group = ParameterGroup.FDIST,
        minValue = 0,
        maxValue = 100,
        defaultValue = 0
    ),
    CarrotParameterDefinition(
        name = "AutoSpeedUptoRoadSpeedLimit",
        title = "Auto Speed Up ratio",
        description = "自动速度增加比例(%)",
        group = ParameterGroup.CRUISE,
        minValue = 0,
        maxValue = 200,
        defaultValue = 0,
        step = 5
    ),
    CarrotParameterDefinition(
        name = "AutoRoadSpeedAdjust",
        title = "AutoRoadLimitSpeedAdjust",
        description = "自动道路限速调整 (-1~100)",
        group = ParameterGroup.CRUISE,
        minValue = -1,
        maxValue = 100,
        defaultValue = 0
    ),
    CarrotParameterDefinition(
        name = "MyDrivingModeAuto",
        title = "DrivingMode: Auto",
        description = "驾驶模式自动切换: 0=关闭 1=开启",
        group = ParameterGroup.ACCEL,
        minValue = 0,
        maxValue = 1,
        defaultValue = 0
    ),
    CarrotParameterDefinition(
        name = "AutoCurveSpeedFactor",
        title = "CurveSpeedFactor",
        description = "弯道速度调节比例(%)",
        group = ParameterGroup.DECEL,
        minValue = 50,
        maxValue = 300,
        defaultValue = 100,
        step = 5
    ),
    CarrotParameterDefinition(
        name = "TrafficLightDetectMode",
        title = "TrafficLightDetectMode",
        description = "信号灯检测功能: 0无 1仅停止 2停止+起步",
        group = ParameterGroup.DECEL,
        minValue = 0,
        maxValue = 2,
        defaultValue = 2,
        options = listOf(
            ParameterOption(0, "不使用"),
            ParameterOption(1, "仅停止"),
            ParameterOption(2, "停止+起步")
        )
    )
)

