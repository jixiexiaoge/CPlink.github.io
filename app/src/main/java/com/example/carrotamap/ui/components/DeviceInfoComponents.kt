package com.example.carrotamap.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carrotamap.CarrotManFields
import com.example.carrotamap.VehicleInfo

/**
 * 设备信息显示组件
 * 显示设备ID和剩余倒计时
 */
@Composable
fun DeviceInfoDisplay(
    modifier: Modifier = Modifier,
    deviceId: String,
    remainingSeconds: Int,
    vehicleInfo: VehicleInfo? = null,
    fontSize: androidx.compose.ui.unit.TextUnit = 12.sp
) {
    // 顶部中间一行展示：ID 与 车型合并一行，居中对齐
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        val textColor = when {
            remainingSeconds > 2000 -> Color(0xFF22C55E)
            remainingSeconds == 850 -> Color(0xFFFBBF24)
            else -> Color(0xFFEF4444)
        }

        val idText = if (deviceId.isNotEmpty()) "ID: $deviceId" else "设备初始化中..."
        val modelText = vehicleInfo?.model?.takeIf { it.isNotBlank() } ?: ""
        val context = androidx.compose.ui.platform.LocalContext.current
        val versionText = try {
            val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pkgInfo.versionName ?: ""
        } catch (e: Exception) {
            ""
        }

        val combined = buildString {
            append(idText)
            if (modelText.isNotEmpty()) {
                append("  |  ")
                append(modelText)
            }
            if (!versionText.isNullOrBlank()) {
                append("  |  ")
                append(versionText)
            }
        }

        Text(
            text = combined,
            fontSize = fontSize,
            color = textColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 交通灯状态指示器组件
 * 只有当前方路口出现红灯时才显示（状态1=普通红灯，状态4=左转红灯）
 * 其他状态下完全隐藏，节省UI空间
 */
@Composable
fun TrafficLightIndicator(
    trafficState: Int,
    leftSec: Int,
    direction: Int = 0
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 状态图标渲染逻辑与 TrafficLightElement 保持一致
        val lightColor = when (trafficState) {
            1 -> Color(0xFFE53E3E) // 红
            -1 -> Color(0xFFD69E2E) // 黄
            2, 3 -> Color(0xFF38A169) // 绿（含左转绿）
            else -> Color(0xFF9CA3AF) // 灰（无/未知）
        }
        Box(
            modifier = Modifier.size(12.dp),
            contentAlignment = Alignment.Center
        ) {
            // 背景圆
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(lightColor, CircleShape)
            )
            // 红灯时显示箭头（方向参考 TrafficLightElement）
            if (trafficState == 1) {
                val arrow = when (direction) {
                    3 -> "←"
                    2 -> "→"
                    else -> "↑"
                }
                Text(
                    text = arrow,
                    color = Color.White,
                    fontSize = 6.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 倒计时：有秒数则显示，颜色根据红灯/非红灯区分
        if (leftSec > 0) {
            Spacer(modifier = Modifier.width(4.dp))
            val secColor = if (trafficState == 1 || trafficState == 4) Color.Red else Color(0xFF374151)
            Text(
                text = "${leftSec}s",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 10.sp,
                color = secColor
            )
        }
    }
}
