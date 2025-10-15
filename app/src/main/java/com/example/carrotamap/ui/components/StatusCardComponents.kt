package com.example.carrotamap.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carrotamap.CarrotManFields

/**
 * 紧凑状态卡片 - 优化版，包含网络状态
 */
@Composable
fun CompactStatusCard(
    receiverStatus: String,
    totalBroadcastCount: Int,
    carrotManFields: CarrotManFields,
    networkStatus: String,
    networkStats: Map<String, Any>,
    onClearDataClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // 第一行：基础状态
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp), // 减少高度使布局更紧凑
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp), // 添加阴影保持一致
            colors = CardDefaults.cardColors(
                containerColor = when (carrotManFields.dataQuality) {
                    "good" -> MaterialTheme.colorScheme.primaryContainer
                    "warning" -> MaterialTheme.colorScheme.tertiaryContainer
                    "error" -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surface // 使用surface保持一致
                }
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (carrotManFields.isNavigating) "导航中" else "待机",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    // 数据质量指示器
                    val qualityColor = when (carrotManFields.dataQuality) {
                        "good" -> Color.Green
                        "warning" -> Color.Yellow
                        "error" -> Color.Red
                        else -> Color.Gray
                    }
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(qualityColor, RoundedCornerShape(3.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${getRoadTypeDescription(carrotManFields.roadType)}-${carrotManFields.roadcate}${if (carrotManFields.nTBTNextRoadWidth > 0) "(${carrotManFields.nTBTNextRoadWidth})" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 10.sp
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (carrotManFields.nSdiType >= 0) {
                            // 显示格式：A:CAMERA_TYPE数值 - X:nSdiType数值（附加距离）
                            val cameraTypeVal = if (carrotManFields.nAmapCameraType >= 0) {
                                carrotManFields.nAmapCameraType.toString()
                            } else {
                                "-" // 高德未提供时显示"-"
                            }
                            val sdiVal = carrotManFields.nSdiType.toString()
                            val distance = if (carrotManFields.nSdiDist > 0) " ${carrotManFields.nSdiDist}m" else ""
                            "A:$cameraTypeVal - X:$sdiVal$distance"
                        } else {
                            "无SDI信息"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 10.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onClearDataClick,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "清空数据",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(3.dp)) // 减少间距

        // 第二行：网络状态
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp), // 减少高度
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp), // 添加阴影保持一致
            colors = CardDefaults.cardColors(
                containerColor = if (networkStatus.startsWith("✅"))
                    MaterialTheme.colorScheme.secondaryContainer
                else
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 地球图标表示网络
                    Text(
                        text = "🌐",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    // 连接状态图标：绿色打勾表示连接成功，红色打叉表示连接失败
                    Text(
                        text = if (networkStatus.startsWith("✅")) "✅" else "❌",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    // 交通灯状态指示器
                    TrafficLightIndicator(
                        trafficState = carrotManFields.trafficState,
                        leftSec = carrotManFields.leftSec,
                        direction = carrotManFields.traffic_light_direction
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    // 交通灯调试信息（小字显示）
                    Text(
                        text = "T:${carrotManFields.trafficState} D:${carrotManFields.traffic_light_direction} G:${carrotManFields.leftSec} W:${carrotManFields.carrot_left_sec}",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 8.sp,
                        color = Color.Gray
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val packetsSent = networkStats["totalPacketsSent"] as? Int ?: 0
                    
                    // 显示高德地图ICON描述和映射后的TurnType值
                    val iconDesc = if (carrotManFields.amapIcon >= 0) {
                        "${carrotManFields.amapIcon}: ${getAmapIconDescription(carrotManFields.amapIcon)}"
                    } else {
                        "无ICON"
                    }
                    val turnTypeDesc = if (carrotManFields.nTBTTurnType >= 0) {
                        val desc = getTurnTypeDescription(carrotManFields.nTBTTurnType)
                        "$desc (${carrotManFields.nTBTTurnType})"
                    } else {
                        "无TurnType"
                    }
                    
                    // 显示限速信息
                    val speedInfo = when {
                        carrotManFields.nSdiSpeedLimit > 0 -> "SDI:${carrotManFields.nSdiSpeedLimit}km/h"
                        carrotManFields.nRoadLimitSpeed > 0 -> "路限:${carrotManFields.nRoadLimitSpeed}km/h"
                        else -> "无限速"
                    }

                    Text(
                        text = "I:$iconDesc TT:$turnTypeDesc 限:$speedInfo 发:$packetsSent",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 8.sp
                    )
                }
            }
        }
    }
}

/**
 * 交通灯状态指示器
 */
@Composable
private fun TrafficLightIndicator(
    trafficState: Int,
    leftSec: Int,
    direction: Int
) {
    val (color, text) = when (trafficState) {
        0 -> Pair(Color.Gray, "无信号")
        1 -> Pair(Color.Red, "红灯")
        2 -> Pair(Color.Green, "绿灯")
        3 -> Pair(Color.Yellow, "左转")
        else -> Pair(Color.Gray, "未知")
    }
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, RoundedCornerShape(4.dp))
        )
        Text(
            text = if (leftSec > 0) "$text($leftSec)" else text,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 8.sp,
            color = color
        )
    }
}

/**
 * 获取高德地图ROAD_TYPE的中文描述
 */
fun getRoadTypeDescription(roadType: Int): String {
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
        else -> "未知道路($roadType)"
    }
}

/**
 * 获取高德地图ICON描述
 * 基于高德官方ICON 0-20映射关系
 */
fun getAmapIconDescription(amapIcon: Int): String {
    return when (amapIcon) {
        0 -> "无转弯/通知指令"
        1 -> "直行"
        2 -> "左转"
        3 -> "右转"
        4 -> "左前方"
        5 -> "右前方"
        6 -> "左后方"
        7 -> "右后方"
        8 -> "左转掉头"
        9 -> "直行"
        10 -> "到达途经点"
        11 -> "进入环岛(逆时针)"
        12 -> "驶出环岛(逆时针)"
        13 -> "到达服务区"
        14 -> "到达收费站"
        15 -> "到达目的地"
        16 -> "进入隧道"
        17 -> "进入环岛(顺时针)"
        18 -> "驶出环岛(顺时针)"
        19 -> "右转掉头"
        20 -> "顺行"
        65 -> "靠左开车"
        66 -> "靠右下匝道"
        else -> "未知ICON($amapIcon)"
    }
}

/**
 * 获取TurnType描述
 * 基于CarrotMan协议的TurnType映射
 */
fun getTurnTypeDescription(turnType: Int): String {
    return when (turnType) {
        12 -> "左转"
        13 -> "右转"
        14 -> "掉头"
        16 -> "急左转"
        19 -> "急右转"
        51 -> "直行/通知"
        52 -> "直行"
        53 -> "直行进入高架"
        54 -> "直行通过桥梁"
        55 -> "直行通过"
        101 -> "右前方"
        102 -> "左前方"
        200 -> "直行"
        201 -> "到达目的地"
        1000 -> "轻微左转"
        1001 -> "轻微右转"
        1002 -> "左侧分岔"
        1003 -> "右侧分岔"
        1006 -> "靠左行驶"
        1007 -> "靠右行驶"
        // 环岛类型
        131, 132 -> "环岛轻微右转"
        140, 141 -> "环岛轻微左转"
        133, 139 -> "环岛转弯"
        134, 135, 136, 137, 138 -> "环岛急转弯"
        142 -> "环岛直行"
        else -> "未知TurnType($turnType)"
    }
}
