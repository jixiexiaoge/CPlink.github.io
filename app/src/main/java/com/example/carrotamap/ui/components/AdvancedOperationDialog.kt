package com.example.carrotamap.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 高阶操作弹窗组件
 * 参考HTML设计，提供方向控制、数值调节、巡航控制、检测控制等功能
 */
@Composable
fun AdvancedOperationDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit = {},
    onCarrotCommand: (String, String) -> Unit = { _, _ -> } // 新增：CarrotCmd和CarrotArg回调
) {
    if (isVisible) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .wrapContentHeight()
                    .padding(8.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // 标题栏
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "高阶操作面板",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF333333)
                        )
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "关闭",
                                tint = Color(0xFF999999),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 数值调节区域（滑动条设置速度）
                    SpeedSliderSection(onCarrotCommand)

                    Spacer(modifier = Modifier.height(16.dp))

                    // 巡航控制区域（4个一排按钮）
                    CruiseControlSection(onCarrotCommand)

                    Spacer(modifier = Modifier.height(16.dp))

                    // 检测控制区域（DETECT按钮）
                    DetectionControlSection(onCarrotCommand)

                    Spacer(modifier = Modifier.height(16.dp))

                    // 方向控制区域（4个一排按钮）- 移动到底部
                    SpeedControlSection(onCarrotCommand)

                }
            }
        }
    }
}

/**
 * 方向控制区域（4个一排按钮）
 */
@Composable
private fun SpeedControlSection(
    onCarrotCommand: (String, String) -> Unit
) {
    // 4个按钮一排
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        SpeedButton("加速", "", Color(0xFF22C55E)) { onCarrotCommand("SPEED", "UP") }
        SpeedButton("左变道", "", Color(0xFF3B82F6)) { onCarrotCommand("LANECHANGE", "LEFT") }
        SpeedButton("右变道", "", Color(0xFF3B82F6)) { onCarrotCommand("LANECHANGE", "RIGHT") }
        SpeedButton("减速", "", Color(0xFFEF4444)) { onCarrotCommand("SPEED", "DOWN") }
    }
}

/**
 * 速度按钮组件
 */
@Composable
private fun SpeedButton(
    text: String,
    description: String,
    backgroundColor: Color,
    onClick: () -> Unit = {}
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .size(70.dp, 48.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor
        ),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = text,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                lineHeight = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 速度滑动条区域
 */
@Composable
private fun SpeedSliderSection(
    onCarrotCommand: (String, String) -> Unit
) {
    var speedValue by remember { mutableStateOf(90f) }
    
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "速度范围 (30-150 km/h)",
                fontSize = 14.sp,
                color = Color(0xFF555555)
            )
            Text(
                text = "${speedValue.toInt()} km/h",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF4DABF7)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Slider(
            value = speedValue,
            onValueChange = { speedValue = it },
            valueRange = 30f..150f,
            steps = 23, // (150-30)/5-1 = 23
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF4DABF7),
                activeTrackColor = Color(0xFF4DABF7),
                inactiveTrackColor = Color(0xFFE9ECEF)
            )
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 设置速度按钮
        Button(
            onClick = { onCarrotCommand("SPEED", speedValue.toInt().toString()) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4DABF7)
            )
        ) {
            Text(
                text = "设置速度为 ${speedValue.toInt()} km/h",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }
    }
}

/**
 * 巡航控制区域（4个一排按钮）
 */
@Composable
private fun CruiseControlSection(
    onCarrotCommand: (String, String) -> Unit
) {
    // 4个按钮一排
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        CruiseButton("OFF", "", Color(0xFFEF4444)) { onCarrotCommand("CRUISE", "OFF") }
        CruiseButton("ON", "", Color(0xFF22C55E)) { onCarrotCommand("CRUISE", "ON") }
        CruiseButton("GO", "", Color(0xFF3B82F6)) { onCarrotCommand("CRUISE", "GO") }
        CruiseButton("STOP", "", Color(0xFFF59E0B)) { onCarrotCommand("CRUISE", "STOP") }
    }
}

/**
 * 检测控制区域（DETECT按钮）
 */
@Composable
private fun DetectionControlSection(
    onCarrotCommand: (String, String) -> Unit
) {
    Button(
        onClick = { onCarrotCommand("DETECT", "red,100.5,200.3,0.85") },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFCC5DE8)
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = "发送 DETECT 命令",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White
        )
    }
}

/**
 * 巡航按钮组件
 */
@Composable
private fun CruiseButton(
    text: String,
    description: String,
    backgroundColor: Color,
    onClick: () -> Unit = {}
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .size(70.dp, 48.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor
        ),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = text,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                lineHeight = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}