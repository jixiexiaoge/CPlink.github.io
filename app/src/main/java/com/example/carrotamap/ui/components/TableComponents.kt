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
import kotlinx.coroutines.delay

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
 * 数据表格组件 - 实时显示所有发送和接收的字段
 */
@Composable
fun DataTable(
    carrotManFields: CarrotManFields,
    dataFieldManager: DataFieldManager,
    networkManager: NetworkManager
) {
    // 实时更新指示器
    val currentTime = remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000) // 每秒更新一次
            currentTime.value = System.currentTimeMillis()
        }
    }
    
    Column(
        modifier = Modifier.fillMaxWidth(), // 改为fillMaxWidth，让高度根据内容自适应
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        // SDI摄像头信息（最顶部）
        TableSectionHeader("摄像头信息")
        dataFieldManager.getSdiCameraFields(carrotManFields).forEach { fieldData ->
            TableRow(fieldData.first, fieldData.second, fieldData.third)
        }

        // 转弯引导信息（第二位）
        TableSectionHeader("转弯引导")
        dataFieldManager.getTurnGuidanceFields(carrotManFields).forEach { fieldData ->
            TableRow(fieldData.first, fieldData.second, fieldData.third)
        }

        // NOA 增强与演进字段
        TableSectionHeader("NOA 增强字段")
        dataFieldManager.getNoaAdvFields(carrotManFields).forEach { fieldData ->
            TableRow(fieldData.first, fieldData.second, fieldData.third)
        }

        // 基础状态和激活信息
        TableSectionHeader("基础状态")
        dataFieldManager.getBasicStatusFields(carrotManFields).forEach { fieldData ->
            TableRow(fieldData.first, fieldData.second, fieldData.third)
        }

        // 速度控制信息
        TableSectionHeader("速度控制")
        dataFieldManager.getSpeedControlFields(carrotManFields).forEach { fieldData ->
            TableRow(fieldData.first, fieldData.second, fieldData.third)
        }

        // GPS和位置信息
        TableSectionHeader("GPS位置")
        dataFieldManager.getGpsLocationFields(carrotManFields).forEach { fieldData ->
            TableRow(fieldData.first, fieldData.second, fieldData.third)
        }

        // 目标和路线信息
        TableSectionHeader("目标路线")
        dataFieldManager.getRouteTargetFields(carrotManFields).forEach { fieldData ->
            TableRow(fieldData.first, fieldData.second, fieldData.third)
        }
        
        // 目的地剩余信息
        TableSectionHeader("目的地剩余")
        dataFieldManager.getGoPosRemainFields(carrotManFields).forEach { fieldData ->
            TableRow(fieldData.first, fieldData.second, fieldData.third)
        }
        
        // 导航位置信息
        TableSectionHeader("导航位置")
        dataFieldManager.getNaviPositionFields(carrotManFields).forEach { fieldData ->
            TableRow(fieldData.first, fieldData.second, fieldData.third)
        }
        
        // 命令控制信息
        TableSectionHeader("命令控制")
        dataFieldManager.getCommandFields(carrotManFields).forEach { fieldData ->
            TableRow(fieldData.first, fieldData.second, fieldData.third)
        }

        // 系统状态信息
        TableSectionHeader("系统状态")
        dataFieldManager.getSystemStatusFields(carrotManFields).forEach { fieldData ->
            TableRow(fieldData.first, fieldData.second, fieldData.third)
        }

        // 交通灯相关字段
        TableSectionHeader("交通灯信息")
        dataFieldManager.getTrafficLightFields(carrotManFields).forEach { fieldData ->
            TableRow(fieldData.first, fieldData.second, fieldData.third)
        }

        // 内部处理字段（调试用）
        TableSectionHeader("内部处理字段")
        dataFieldManager.getInternalFields(carrotManFields).forEach { fieldData ->
            TableRow(fieldData.first, fieldData.second, fieldData.third)
        }
    }
}
