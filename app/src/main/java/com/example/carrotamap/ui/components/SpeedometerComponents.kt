package com.example.carrotamap.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carrotamap.CarrotManFields
import com.example.carrotamap.NetworkManager
import com.example.carrotamap.OpenpilotStatusData
import com.example.carrotamap.VehicleInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.util.Log
import com.example.carrotamap.AppConstants

/**
 * 速度表卡片组件
 * 显示中央速度表和四个角落的信息元素
 */
@Composable
fun SpeedometerCard(
    carrotManFields: CarrotManFields,
    openpilotData: OpenpilotStatusData,
    networkManager: NetworkManager,
    deviceId: String = "",
    remainingSeconds: Int = 0,
    vehicleInfo: VehicleInfo? = null,
    onNavigateToHome: () -> Unit = {},
    onNavigateToCompany: () -> Unit = {},
    onTutorial: () -> Unit = {},
    onOpenDataPage: () -> Unit = {},
    onSponsor: () -> Unit = {},
    onAdvancedOperation: () -> Unit = {}
) {
    // 注意：不再使用本地状态管理巡航速度
    // 左上角显示的巡航速度直接来自comma3设备的实际数据：openpilotData.vCruiseKph
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2f),//恢复原来的比例
        shape = RoundedCornerShape(24.dp), // 恢复原来的圆角
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp), // 恢复原来的阴影
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF8FAFC) // 更清爽的背景色
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp) // 恢复原来的内边距
        ) {
            // 顶部中间：整合的驾驶辅助状态和跟车模式 + 左右距离徽章（tbt_dist / sdi_dist）
            Row(
                modifier = Modifier.align(Alignment.TopCenter).offset(y = 65.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧：交通灯状态（替换原 tbt_dist 徽章）
                TrafficLightElement(
                    modifier = Modifier,
                    trafficState = carrotManFields.traffic_state,
                    leftSec = carrotManFields.left_sec,
                    direction = carrotManFields.traffic_light_direction
                )

                Spacer(modifier = Modifier.width(8.dp))

                // 中间：原有驾驶辅助状态
                DrivingAssistanceStatus(
                    active = openpilotData.active,
                    isNavigating = carrotManFields.isNavigating,
                    xState = openpilotData.xState
                )

                Spacer(modifier = Modifier.width(8.dp))

                // 右侧：sdi_dist 徽章
                val sdiText = if (openpilotData.sdiDist > 0) "${openpilotData.sdiDist} m" else "00 m"
                DistBadge(text = sdiText)
            }

            // 顶部一排五个圆环：均匀分布
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .offset(y = (-4).dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. 巡航设定速度（蓝色）
                CircularSpeedIndicator(
                    modifier = Modifier.clickable { onOpenDataPage() },
                    value = openpilotData.vCruiseKph.toInt(),
                    maxValue = 120,
                    color = Color(0xFF2196F3),
                    size = 60.dp,
                    showProgress = false,
                    showLabel = false
                )

                // 2. 当前车速（灰色）
                CircularSpeedIndicator(
                    modifier = Modifier,
                    value = carrotManFields.nPosSpeed.toInt(),
                    maxValue = 120,
                    color = Color(0xFF9CA3AF),
                    size = 60.dp,
                    showProgress = false,
                    showLabel = false
                )

                // 3. 手机GPS速度（橘黄色）
                CircularSpeedIndicator(
                    modifier = Modifier,
                    value = ((carrotManFields.gps_speed * 3.6).toInt()).coerceAtLeast(0),
                    maxValue = 120,
                    color = Color(0xFFF59E0B),
                    size = 60.dp,
                    showProgress = false,
                    showLabel = false
                )

                // 4. 车辆巡航速度（绿色）
                CircularSpeedIndicator(
                    modifier = Modifier,
                    value = if (openpilotData.carcruiseSpeed > 0) openpilotData.carcruiseSpeed.toInt() else 0,
                    maxValue = 120,
                    color = Color(0xFF22C55E),
                    size = 60.dp,
                    showProgress = false,
                    showLabel = false
                )

                // 5. 道路限速（红色，可点击傻瓜配置）
                CircularSpeedIndicator(
                    modifier = Modifier.clickable {
                        CoroutineScope(Dispatchers.Main).launch {
                            try {
                                val result = networkManager.sendSettingsToComma3()
                                if (result.isSuccess) {
                                    Log.i(AppConstants.Logging.MAIN_ACTIVITY_TAG, "✅ 右上角限速元素：傻瓜配置发送成功")
                                } else {
                                    Log.e(AppConstants.Logging.MAIN_ACTIVITY_TAG, "❌ 右上角限速元素：傻瓜配置发送失败: ${result.exceptionOrNull()?.message}")
                                }
                            } catch (e: Exception) {
                                Log.e(AppConstants.Logging.MAIN_ACTIVITY_TAG, "❌ 右上角限速元素：傻瓜配置发送异常: ${e.message}", e)
                            }
                        }
                    },
                    value = carrotManFields.nRoadLimitSpeed,
                    maxValue = 120,
                    color = Color(0xFFF44336),
                    size = 60.dp,
                    showProgress = false,
                    showLabel = false
                )
            }

            // 底部：两行按钮布局 - 放在其他元素下方，更紧凑
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 0.dp), // 增加底部边距，留出适当空间
                verticalArrangement = Arrangement.spacedBy(0.dp) // 进一步减少行间距到0
            ) {
                // 第一行移除（原"傻瓜配置"按钮功能已迁移到右上角限速元素的点击）

                // 第二行：回家、赞助、弯道减速、公司 四个按钮一排
                Row(
                    horizontalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    // 回家按钮（调整高度为0.8倍）
                    Button(
                        onClick = onNavigateToHome,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(percent = 50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF22C55E)
                        ),
                        contentPadding = PaddingValues(horizontal = 1.dp, vertical = 5.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "回家",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "回家",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                        }
                    }

                    // 赞助按钮（新增，样式与其他按钮一致）
                    Button(
                        onClick = onSponsor,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(percent = 50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEC4899) // 粉色
                        ),
                        contentPadding = PaddingValues(horizontal = 1.dp, vertical = 5.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "赞助",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "赞助",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                        }
                    }

                    // 高阶操作按钮（新增）
                    Button(
                        onClick = onAdvancedOperation,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(percent = 50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF8B5CF6) // 紫色
                        ),
                        contentPadding = PaddingValues(horizontal = 1.dp, vertical = 5.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "高阶操作",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "高阶操作",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                        }
                    }

                    // 弯道减速按钮（仅在车辆厂商为 Mazda/马自达 时显示）
                    if (vehicleInfo?.manufacturer?.let { it.equals("Mazda", ignoreCase = true) || it.contains("马自达") } == true) {
                        ModeToggleButton(
                            modifier = Modifier
                                .weight(1f),
                            networkManager = networkManager
                        )
                    }

                    // 公司按钮（调整高度为0.8倍）
                    Button(
                        onClick = onNavigateToCompany,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(percent = 50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF3B82F6)
                        ),
                        contentPadding = PaddingValues(horizontal = 1.dp, vertical = 5.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "公司",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "公司",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // 底部中间设备信息已移动到数据页顶部行的反馈按钮后面，此处不再显示
        }
    }
}

/**
 * 圆形速度指示器组件 - 圆环设计，可选择是否显示标签
 */
@Composable
fun CircularSpeedIndicator(
    modifier: Modifier = Modifier,
    value: Int,
    maxValue: Int,
    color: Color,
    size: androidx.compose.ui.unit.Dp,
    showProgress: Boolean = true,
    showLabel: Boolean = true
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val radius = size.toPx() / 2 - 6.dp.toPx()
            val center = this.center

            if (!showProgress && !showLabel) {
                // 简化样式：仅绘制纯色圆环（无底色、无进度），与右上角一致
                drawCircle(
                    color = color,
                    radius = radius,
                    center = center,
                    style = Stroke(width = 6.dp.toPx())
                )
            } else {
                // 保持原有样式：白色底 + 彩色圆环 + 可选进度
                drawCircle(
                    color = Color.White,
                    radius = radius,
                    center = center
                )
                drawCircle(
                    color = color,
                    radius = radius,
                    center = center,
                    style = Stroke(width = 6.dp.toPx())
                )
                if (showProgress) {
                    val progress = (value.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f)
                    val sweepAngle = progress * 360f
                    drawArc(
                        color = color.copy(alpha = 0.3f),
                        startAngle = -90f,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
        }

        // 中央内容
        if (showLabel) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 数字显示
                Text(
                    text = value.toString(),
                    fontSize = 16.sp, // 恢复原字体大小
                    fontWeight = FontWeight.Bold,
                    color = color,
                    letterSpacing = (-0.5).sp
                )

                // 单位或标签
                Text(
                    text = if (showProgress) "设定" else "限速",
                    fontSize = 8.sp, // 恢复原字体大小
                    fontWeight = FontWeight.Medium,
                    color = color.copy(alpha = 0.7f),
                    letterSpacing = 0.sp
                )
            }
        } else {
            // 只显示数字，不显示标签
            Text(
                text = value.toString(),
                fontSize = 18.sp, // 恢复原字体大小
                fontWeight = FontWeight.ExtraBold,
                color = color,
                letterSpacing = 0.5.sp
            )
        }
    }
}

/**
 * 真实交通红绿灯组件 - 仿真交通灯设计
 */
@Composable
fun TrafficLightElement(
    modifier: Modifier = Modifier,
    trafficState: Int,
    leftSec: Int,
    direction: Int = 0
) {
    // 无数据时自动隐藏
    if (trafficState == 0 && leftSec <= 0) return

    // 胶囊背景（浅黑色）
    Row(
        modifier = modifier
            .background(Color(0xFF1F2937).copy(alpha = 0.9f), shape = RoundedCornerShape(percent = 50))
            .padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // 左侧交通灯（颜色依据状态）
        // 注意：协议映射中 2 表示绿灯，3 表示左转绿灯，-1 表示黄灯
        val lightColor = when (trafficState) {
            1 -> Color(0xFFE53E3E) // 红
            -1 -> Color(0xFFD69E2E) // 黄
            2, 3 -> Color(0xFF38A169) // 绿（含左转绿）
            else -> Color(0xFF4A5568) // 暗
        }
        Box(
            modifier = Modifier.size(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(color = lightColor)
            }
            // 红灯时在圆内显示箭头（白色）：根据交通灯控制方向显示
            // dir字段含义：1=左转, 2=右转, 3=左转掉头, 4=直行, 5=右转掉头
            if (trafficState == 1) {
                val arrow = when (direction) {
                    1 -> "←" // 左转方向交通灯
                    2 -> "→" // 右转方向交通灯
                    3 -> "↶" // 左转掉头方向交通灯
                    4 -> "↑" // 直行方向交通灯
                    5 -> "↷" // 右转掉头方向交通灯
                    else -> "?" // 未知方向
                }
                Text(
                    text = arrow,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        // 右侧倒计时（仅在>0时显示，白色）
        if (leftSec > 0) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = leftSec.toString(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

/**
 * 驾驶辅助状态胶囊组件 - 一行显示，调整尺寸
 */
@Composable
fun DrivingAssistanceStatus(
    modifier: Modifier = Modifier,
    active: Boolean,
    isNavigating: Boolean,
    xState: Int
) {
    // 构建整合的文本
    val assistanceText = when {
        isNavigating -> "导航辅助驾驶"
        active -> "车道全时保持"
        else -> ""
    }

    val xStateText = getXStateText(xState)

    // 组合文本：一行显示，用空格分隔
    val combinedText = if (assistanceText.isNotEmpty()) {
        "$assistanceText $xStateText"
    } else {
        xStateText
    }

    val backgroundColor = when {
        isNavigating -> Color(0xFF3B82F6) // 蓝色
        active -> Color(0xFF22C55E) // 绿色
        else -> Color(0xFF6B7280) // 灰色（当只有跟车模式时）
    }

    Surface(
        modifier = modifier
            .width(160.dp), // 保持宽度，移除固定高度让内容自动确定高度
        shape = RoundedCornerShape(8.dp), // 与左右元素一致的圆角
        color = backgroundColor.copy(alpha = 0.9f) // 与左右元素一致的颜色透明度
    ) {
        Text(
            text = combinedText,
            color = Color.White,
            style = MaterialTheme.typography.bodySmall, // 与左右元素一致的字体样式
            fontSize = 10.sp, // 与左右元素一致的字体大小
            textAlign = TextAlign.Center,
            maxLines = 1, // 确保只有一行
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp) // 与左右元素一致的内边距
        )
    }
}

@Composable
fun DistBadge(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF374151).copy(alpha = 0.9f)
    ) {
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/**
 * 获取xState状态文本
 */
fun getXStateText(xState: Int): String {
    return when (xState) {
        0 -> "跟车模式"
        1 -> "巡航模式"
        2 -> "端到端巡航"
        3 -> "端到端停车"
        4 -> "端到端准备"
        5 -> "端到端已停"
        else -> "状态 $xState"
    }
}
