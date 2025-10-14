package com.example.carrotamap.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carrotamap.CarrotManFields
import com.example.carrotamap.DataFieldManager
import com.example.carrotamap.NetworkManager
import com.example.carrotamap.OpenpilotStatusData

/**
 * 表格头部
 */
@Composable
fun TableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                RoundedCornerShape(4.dp)
            )
            .padding(8.dp)
        ) {
            Text(
            text = "字段名",
            modifier = Modifier.weight(2f),
            style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        Text(
            text = "中文名称",
            modifier = Modifier.weight(2f),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
            Text(
            text = "数据值",
            modifier = Modifier.weight(1.5f),
            style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
    }
}

/**
 * 表格分组头部
 */
@Composable
fun TableSectionHeader(title: String) {
    Row(
            modifier = Modifier
                .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                RoundedCornerShape(4.dp)
            )
            .padding(6.dp)
        ) {
            Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary
            )
    }
}

/**
 * 表格行
 */
@Composable
fun TableRow(fieldName: String, chineseName: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(vertical = 4.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
            text = fieldName,
            modifier = Modifier.weight(2f),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            fontSize = 10.sp
                )
                Text(
            text = chineseName,
            modifier = Modifier.weight(2f),
                    style = MaterialTheme.typography.bodySmall,
            fontSize = 10.sp
                )
                Text(
            text = value,
            modifier = Modifier.weight(1.5f),
                    style = MaterialTheme.typography.bodySmall,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            fontSize = 10.sp,
            color = if (value == "null" || value == "-1" || value == "0" || value == "false")
                MaterialTheme.colorScheme.outline
            else
                MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * 数据表格组件
 */
@Composable
fun DataTable(
    carrotManFields: CarrotManFields,
    dataFieldManager: DataFieldManager,
    networkManager: NetworkManager
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        // 基础状态和激活信息
        item { TableSectionHeader("基础状态") }
        items(dataFieldManager.getBasicStatusFields(carrotManFields)) { fieldData ->
            TableRow(fieldData.first, fieldData.second, fieldData.third)
        }

        // 速度控制信息
        item { TableSectionHeader("速度控制") }
        items(dataFieldManager.getSpeedControlFields(carrotManFields)) { fieldData ->
            TableRow(fieldData.first, fieldData.second, fieldData.third)
        }

        // GPS和位置信息
        item { TableSectionHeader("GPS位置") }
        items(dataFieldManager.getGpsLocationFields(carrotManFields)) { fieldData ->
            TableRow(fieldData.first, fieldData.second, fieldData.third)
        }

        // 转弯引导信息
        item { TableSectionHeader("转弯引导") }
        items(dataFieldManager.getTurnGuidanceFields(carrotManFields)) { fieldData ->
            TableRow(fieldData.first, fieldData.second, fieldData.third)
        }

        // 目标和路线信息
        item { TableSectionHeader("目标路线") }
        items(dataFieldManager.getRouteTargetFields(carrotManFields)) { fieldData ->
            TableRow(fieldData.first, fieldData.second, fieldData.third)
        }

        // SDI摄像头信息
        item { TableSectionHeader("摄像头信息") }
        items(dataFieldManager.getSdiCameraFields(carrotManFields)) { fieldData ->
            TableRow(fieldData.first, fieldData.second, fieldData.third)
        }

        // 系统状态信息
        item { TableSectionHeader("系统状态") }
        items(dataFieldManager.getSystemStatusFields(carrotManFields)) { fieldData ->
            TableRow(fieldData.first, fieldData.second, fieldData.third)
        }

        // OpenPilot状态信息 - 放在最后面
        item { TableSectionHeader("🚗 OpenPilot状态") }
        
        // 获取OpenPilot数据
        val openpilotData = networkManager.getOpenpilotStatusData()
        
        // 基础系统信息
        item { TableRow("Carrot2", "版本信息", openpilotData.carrot2.ifEmpty { "未知" }) }
        item { TableRow("ip", "设备IP", openpilotData.ip.ifEmpty { "未连接" }) }
        item { TableRow("port", "通信端口", openpilotData.port.toString()) }
        item { TableRow("log_carrot", "系统日志", openpilotData.logCarrot.ifEmpty { "无日志" }) }
        
        // 运行状态
        item { TableRow("IsOnroad", "道路状态", if (openpilotData.isOnroad) "在路上" else "未上路") }
        item { TableRow("active", "自动驾驶", if (openpilotData.active) "激活" else "未激活") }
        item { TableRow("CarrotRouteActive", "导航状态", if (openpilotData.carrotRouteActive) "导航中" else "未导航") }
        
        // 速度信息
        item { TableRow("v_ego_kph", "当前车速", "${openpilotData.vEgoKph} km/h") }
        item { TableRow("v_cruise_kph", "巡航速度", "${openpilotData.vCruiseKph} km/h") }
        
        // 导航距离信息
        item { TableRow("tbt_dist", "转弯距离", "${openpilotData.tbtDist} m") }
        item { TableRow("sdi_dist", "限速距离", "${openpilotData.sdiDist} m") }
        
        // 控制状态
        item { 
            val xStateDesc = when (openpilotData.xState) {
                0 -> "跟车模式"
                1 -> "巡航模式"
                2 -> "端到端巡航"
                3 -> "端到端停车"
                4 -> "端到端准备"
                5 -> "端到端已停"
                else -> "未知状态(${openpilotData.xState})"
            }
            TableRow("xState", "纵向状态", xStateDesc)
        }
        
        item { 
            val trafficDesc = when (openpilotData.trafficState) {
                0 -> "无信号"
                1 -> "红灯"
                2 -> "绿灯"
                3 -> "左转"
                else -> "未知(${openpilotData.trafficState})"
            }
            TableRow("trafficState", "交通状态", trafficDesc)
        }
        
        // 时间信息
        item { 
            val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            val timeStr = sdf.format(java.util.Date(openpilotData.lastUpdateTime))
            TableRow("lastUpdateTime", "更新时间", timeStr)
        }
    }
}
