package com.example.carrotamap

import android.media.MediaPlayer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * MainActivity UI组件 - 辅助组件和工具函数
 * 包含车辆控制按钮、高阶功能弹窗、导航相关函数等
 */
object MainActivityUIComponents {
    
    /**
     * 车辆控制按钮组件 - 带速度圆环显示
     */
    @Composable
    fun VehicleControlButtons(
        core: MainActivityCore,
        onPageChange: (Int) -> Unit,
        onSendCommand: (String, String) -> Unit,
        onSendRoadLimitSpeed: () -> Unit,
        onLaunchAmap: () -> Unit,
        onSendNavConfirmation: () -> Unit,
        userType: Int,
        carrotManFields: CarrotManFields,
        onShowVehicleLaneDialog: () -> Unit = {} // 🆕 显示车道可视化弹窗的回调
    ) {
        var showAdvancedDialog by remember { mutableStateOf(false) }
        val context = androidx.compose.ui.platform.LocalContext.current
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
        ) {
            // 控制按钮行 - 2个速度圆环 + 3个按钮（紧凑布局）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧速度圆环 - 巡航设定速度（蓝色）- 点击启动调试/模拟导航
                SpeedIndicatorCompose(
                    value = try { carrotManFields.vCruiseKph?.toInt() ?: 0 } catch (e: Exception) { 0 },
                    color = Color(0xFF2196F3),
                    label = "",
                    onClick = {
                        android.util.Log.i("MainActivity", "🔧 主页：用户点击蓝色速度圆环，启动模拟导航")
                        MainActivityUIComponents.startSimulatedNavigation(context, carrotManFields)
                    }
                )
                
                // 回家按钮（只显示图标，不显示文字）
                ControlButton(
                    icon = "🏠",
                    label = "",
                    color = Color(0xFFFFD700),
                    onClick = {
                        android.util.Log.i("MainActivity", "🏠 主页：用户点击回家按钮")
                        MainActivityUIComponents.sendHomeNavigationToAmap(context)
                    }
                )
                
                // 高阶按钮（打开高阶功能弹窗 - 需要用户类型3或4）
                // 点击：显示高级功能弹窗，长按：显示车道可视化弹窗
                ControlButton(
                    icon = "🔧",
                    label = "", // 高阶 按钮
                    color = Color(0xFFF59E0B),
                    onClick = {
                        android.util.Log.i("MainActivity", "🚀 主页：用户点击高阶按钮，用户类型: $userType")
                        
                        // 检查用户类型：只有赞助者(3)或铁粉(4)才能使用高阶功能
                        if (userType == 3 || userType == 4) {
                            android.util.Log.i("MainActivity", "✅ 用户类型验证通过，打开高阶功能弹窗")
                            showAdvancedDialog = true
                        } else {
                            android.util.Log.w("MainActivity", "⚠️ 用户类型不足，无法使用高阶功能")
                            // 显示Toast提示用户
                            android.widget.Toast.makeText(
                                context,
                                "⭐ 高阶功能需要赞助者权限\n请前往「我的」页面\n检查信息并更新用户类型",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    onLongClick = {
                        android.util.Log.i("MainActivity", "🔍 主页：用户长按高阶按钮，显示车道可视化弹窗")
                        // 检查用户类型：只有赞助者(3)或铁粉(4)才能显示车道可视化
                        if (userType == 3 || userType == 4) {
                            android.util.Log.i("MainActivity", "✅ 用户类型验证通过，显示车道可视化弹窗")
                            onShowVehicleLaneDialog()
                        } else {
                            android.util.Log.w("MainActivity", "⚠️ 用户类型不足，无法显示车道可视化")
                            android.widget.Toast.makeText(
                                context,
                                "⭐ 车道可视化需要赞助者权限\n请前往「我的」页面\n检查信息并更新用户类型",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )
                
                // 公司按钮（只显示图标，不显示文字）
                ControlButton(
                    icon = "🏢",
                    label = "",
                    color = Color(0xFFFF8C00),
                    onClick = {
                        android.util.Log.i("MainActivity", "🏢 主页：用户点击公司按钮")
                        MainActivityUIComponents.sendCompanyNavigationToAmap(context)
                    }
                )
                
                // 右侧速度圆环 - 车辆巡航速度（绿色）- 点击启动高德地图
                SpeedIndicatorCompose(
                    value = try { carrotManFields.carcruiseSpeed?.toInt() ?: 0 } catch (e: Exception) { 0 },
                    color = Color(0xFF22C55E),
                    label = "",
                    onClick = {
                        android.util.Log.i("MainActivity", "🗺️ 主页：用户点击绿色速度圆环，启动高德地图")
                        onLaunchAmap()
                    }
                )
            }
        }
        
        // 高阶功能弹窗
        if (showAdvancedDialog) {
            AdvancedFunctionsDialog(
                onDismiss = { showAdvancedDialog = false },
                onSendCommand = onSendCommand,
                onSendRoadLimitSpeed = onSendRoadLimitSpeed,
                onLaunchAmap = onLaunchAmap,
                onSendNavConfirmation = onSendNavConfirmation,
                isOpenpilotActive = carrotManFields.active,
                carrotManFields = carrotManFields,
                context = context
            )
        }
    }

    /**
     * 控制按钮组件（紧凑版）
     */
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun ControlButton(
        icon: String,
        label: String,
        color: Color,
        onClick: () -> Unit,
        onLongClick: (() -> Unit)? = null // 🆕 长按事件回调
    ) {
        val context = LocalContext.current
        
        // 使用 Box + Surface 替代 Button，确保事件能正确触发
        val buttonModifier = if (onLongClick != null) {
            Modifier
                .width(48.dp)
                .height(42.dp)
                .combinedClickable(
                    onClick = {
                        // 短按：执行 onClick
                        android.util.Log.i("MainActivity", "🔍 ControlButton: 检测到点击事件")
                        onClick()
                    },
                    onLongClick = {
                        // 长按：执行 onLongClick
                        android.util.Log.i("MainActivity", "🔍 ControlButton: 检测到长按事件")
                        onLongClick()
                        android.widget.Toast.makeText(
                            context,
                            "车道可视化",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                )
        } else {
            Modifier
                .width(48.dp)
                .height(42.dp)
                .clickable(
                    onClick = {
                        android.util.Log.i("MainActivity", "🔍 ControlButton: 检测到点击事件（无长按）")
                        onClick()
                    }
                )
        }
        
        Box(
            modifier = buttonModifier
                .background(
                    color = color,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            when {
                // 情况1: 只有图标，没有文字（图标居中显示）
                icon.isNotEmpty() && label.isEmpty() -> {
                    Text(
                        text = icon,
                        fontSize = 20.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
                // 情况2: 既有图标又有文字（垂直排列）
                icon.isNotEmpty() && label.isNotEmpty() -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = icon,
                            fontSize = 14.sp
                        )
                        Text(
                            text = label,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                // 情况3: 只有文字，没有图标（文字居中）
                else -> {
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }

    /**
     * 高阶功能弹窗 - 3x3九宫格按钮（集成加速/减速/变道/控速/设置功能）
     */
    @Composable
    private fun AdvancedFunctionsDialog(
        onDismiss: () -> Unit,
        onSendCommand: (String, String) -> Unit,
        onSendRoadLimitSpeed: () -> Unit,
        onLaunchAmap: () -> Unit,
        onSendNavConfirmation: () -> Unit,
        isOpenpilotActive: Boolean,
        carrotManFields: CarrotManFields,
        context: android.content.Context
    ) {
        // 🆕 通用音频播放函数 - 减少重复代码
        fun playSound(resourceId: Int, soundName: String) {
            try {
                MediaPlayer.create(context, resourceId)?.apply {
                    setOnCompletionListener { release() }
                    setOnErrorListener { _, what, extra ->
                        android.util.Log.e("MainActivity", "❌ 音频播放错误($soundName): what=$what, extra=$extra")
                        release()
                        true
                    }
                    start()
                    android.util.Log.d("MainActivity", "🔊 开始播放${soundName}提示音")
                } ?: android.util.Log.w("MainActivity", "⚠️ 无法创建音频播放器($soundName)")
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "❌ 播放${soundName}提示音失败: ${e.message}", e)
            }
        }
        
        // 智能控速模式状态：0=智能控速, 1=原车巡航, 2=弯道减速
        var speedControlMode by remember { 
            mutableStateOf(
                context.getSharedPreferences("CarrotAmap", android.content.Context.MODE_PRIVATE)
                    .getInt("speed_from_pcm_mode", 0)
            ) 
        }
        var isSpeedModeLoading by remember { mutableStateOf(false) }
        
        // 自动转向控制模式状态：0=禁用控制, 1=自动变道, 2=控速变道, 3=导航限速（默认值2）
        var autoTurnControlMode by remember { 
            mutableStateOf(
                context.getSharedPreferences("CarrotAmap", android.content.Context.MODE_PRIVATE)
                    .getInt("auto_turn_control_mode", 2)
            ) 
        }
        var isAutoTurnModeLoading by remember { mutableStateOf(false) }
        
        // 🆕 超车模式状态：0=禁止超车, 1=拨杆超车, 2=自动超车（默认值0）
        var overtakeMode by remember { 
            mutableStateOf(
                context.getSharedPreferences("CarrotAmap", android.content.Context.MODE_PRIVATE)
                    .getInt("overtake_mode", 0)
            ) 
        }
        var isOvertakeModeLoading by remember { mutableStateOf(false) }
        
        val coroutineScope = rememberCoroutineScope()
        // 计算弹窗宽度：九宫格宽度（56dp * 3 + 6dp * 2 + 8dp * 2 = 196dp）+ 额外宽度
        val dialogWidth = 56.dp * 3 + 6.dp * 2 + 8.dp * 2 + 20.dp  // 196dp + 20dp = 216dp（比九宫格宽20dp）
        
        androidx.compose.ui.window.Dialog(
            onDismissRequest = onDismiss
        ) {
            Card(
                modifier = Modifier
                    .width(dialogWidth)
                    .wrapContentHeight()
                    .padding(0.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 3x3 九宫格按钮（居中布置）
                    for (row in 0..2) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            for (col in 0..2) {
                                val buttonNumber = row * 3 + col + 1
                                
                                when (buttonNumber) {
                                    // 1号按钮 - 自动转向控制模式
                                    1 -> {
                                        val turnControlModeNames = arrayOf("禁用\n控制", "自动\n变道", "控速\n变道", "导航\n限速")
                                        val turnControlModeColors = arrayOf(
                                            Color(0xFF94A3B8),
                                            Color(0xFF3B82F6),
                                            Color(0xFF22C55E),
                                            Color(0xFFF59E0B)
                                        )
                                        
                                        Button(
                                            onClick = {
                                                if (!isAutoTurnModeLoading) {
                                                    isAutoTurnModeLoading = true
                                                    coroutineScope.launch {
                                                        val nextMode = (autoTurnControlMode + 1) % 4
                                                        val intent = android.content.Intent("com.example.cplink.CHANGE_AUTO_TURN_CONTROL").apply {
                                                            putExtra("mode", nextMode)
                                                            setPackage(context.packageName)
                                                        }
                                                        context.sendBroadcast(intent)
                                                        context.getSharedPreferences("CarrotAmap", android.content.Context.MODE_PRIVATE)
                                                            .edit()
                                                            .putInt("auto_turn_control_mode", nextMode)
                                                            .apply()
                                                        kotlinx.coroutines.delay(500)
                                                        autoTurnControlMode = nextMode
                                                        isAutoTurnModeLoading = false
                                                    }
                                                }
                                            },
                                            modifier = Modifier.size(56.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isAutoTurnModeLoading) {
                                                    Color(0xFF6B7280)
                                                } else {
                                                    turnControlModeColors[autoTurnControlMode]
                                                }
                                            ),
                                            contentPadding = PaddingValues(0.dp),
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                            enabled = !isAutoTurnModeLoading
                                        ) {
                                            Text(
                                                text = if (isAutoTurnModeLoading) {
                                                    "切换\n中..."
                                                } else {
                                                    turnControlModeNames[autoTurnControlMode]
                                                },
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                lineHeight = 12.sp
                                            )
                                        }
                                    }
                                    // 2号按钮 - 加速（绿色）
                                    2 -> {
                                        val currentCruiseSpeed = carrotManFields.vCruiseKph.toInt()
                                        val newSpeed = if (currentCruiseSpeed > 0) {
                                            minOf(currentCruiseSpeed + 10, 150)
                                        } else {
                                            50
                                        }
                                        
                                        Button(
                                            onClick = {
                                                android.util.Log.i("MainActivity", "🎮 高阶弹窗：用户点击加速按钮")
                                                onSendCommand("SPEED", newSpeed.toString())
                                                onDismiss()
                                            },
                                            modifier = Modifier.size(56.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF22C55E)
                                            ),
                                            contentPadding = PaddingValues(0.dp),
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = "加速",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                    // 3号按钮 - 超车模式切换
                                    3 -> {
                                        val overtakeModeNames = arrayOf("禁止\n超车", "拨杆\n超车", "自动\n超车")
                                        val overtakeModeColors = arrayOf(
                                            Color(0xFF94A3B8),
                                            Color(0xFF3B82F6),
                                            Color(0xFF22C55E)
                                        )
                                        
                                        Button(
                                            onClick = {
                                                if (!isOvertakeModeLoading) {
                                                    isOvertakeModeLoading = true
                                                    coroutineScope.launch {
                                                    val prefs = context.getSharedPreferences("CarrotAmap", android.content.Context.MODE_PRIVATE)
                                                    val userType = prefs.getInt("userType", 0)
                                                    val nextMode = if (userType == 4) {
                                                        (overtakeMode + 1) % 3
                                                    } else {
                                                        // 用户类型3：只在0和1之间切换
                                                        if (overtakeMode == 0) 1 else 0
                                                    }
                                                    prefs
                                                            .edit()
                                                            .putInt("overtake_mode", nextMode)
                                                            .apply()
                                                        kotlinx.coroutines.delay(300)
                                                        overtakeMode = nextMode
                                                        isOvertakeModeLoading = false
                                                    }
                                                }
                                            },
                                            modifier = Modifier.size(56.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isOvertakeModeLoading) {
                                                    Color(0xFF6B7280)
                                                } else {
                                                    overtakeModeColors[overtakeMode]
                                                }
                                            ),
                                            contentPadding = PaddingValues(0.dp),
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                            enabled = !isOvertakeModeLoading
                                        ) {
                                            Text(
                                                text = if (isOvertakeModeLoading) {
                                                    "切换\n中..."
                                                } else {
                                                    overtakeModeNames[overtakeMode]
                                                },
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                lineHeight = 12.sp
                                            )
                                        }
                                    }
                                    // 4号按钮 - 左变道
                                    4 -> {
                                        Button(
                                            onClick = {
                                                playSound(R.raw.left, "左变道")
                                                onSendCommand("LANECHANGE", "LEFT")
                                                onDismiss()
                                            },
                                            modifier = Modifier.size(56.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF3B82F6)
                                            ),
                                            contentPadding = PaddingValues(0.dp),
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = "左变道",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                        }
                                    }
                                    // 5号按钮 - 智能控速
                                    5 -> {
                                        val modeNames = arrayOf("智能\n控速", "原车\n巡航", "弯道\n减速")
                                        val modeColors = arrayOf(
                                            Color(0xFF22C55E),
                                            Color(0xFF3B82F6),
                                            Color(0xFFF59E0B)
                                        )
                                        
                                        Button(
                                            onClick = {
                                                if (!isSpeedModeLoading) {
                                                    isSpeedModeLoading = true
                                                    coroutineScope.launch {
                                                        val nextMode = (speedControlMode + 1) % 3
                                                        val intent = android.content.Intent("com.example.cplink.CHANGE_SPEED_MODE").apply {
                                                            putExtra("mode", nextMode)
                                                            setPackage(context.packageName)
                                                        }
                                                        context.sendBroadcast(intent)
                                                        context.getSharedPreferences("CarrotAmap", android.content.Context.MODE_PRIVATE)
                                                            .edit()
                                                            .putInt("speed_from_pcm_mode", nextMode)
                                                            .apply()
                                                        kotlinx.coroutines.delay(500)
                                                        speedControlMode = nextMode
                                                        isSpeedModeLoading = false
                                                    }
                                                }
                                            },
                                            modifier = Modifier.size(56.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isSpeedModeLoading) {
                                                    Color(0xFF6B7280)
                                                } else {
                                                    modeColors[speedControlMode]
                                                }
                                            ),
                                            contentPadding = PaddingValues(0.dp),
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                            enabled = !isSpeedModeLoading
                                        ) {
                                            Text(
                                                text = if (isSpeedModeLoading) {
                                                    "切换\n中..."
                                                } else {
                                                    modeNames[speedControlMode]
                                                },
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                lineHeight = 12.sp
                                            )
                                        }
                                    }
                                    // 6号按钮 - 右变道
                                    6 -> {
                                        Button(
                                            onClick = {
                                                playSound(R.raw.right, "右变道")
                                                onSendCommand("LANECHANGE", "RIGHT")
                                                onDismiss()
                                            },
                                            modifier = Modifier.size(56.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF3B82F6)
                                            ),
                                            contentPadding = PaddingValues(0.dp),
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = "右变道",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                        }
                                    }
                                    // 7号按钮 - 设置限速
                                    7 -> {
                                        val roadLimitSpeed = carrotManFields.nRoadLimitSpeed
                                        val buttonText = if (roadLimitSpeed > 0) {
                                            "$roadLimitSpeed\n限速"
                                        } else {
                                            "设置\n限速"
                                        }
                                        
                                        Button(
                                            onClick = {
                                                if (roadLimitSpeed > 0) {
                                                    onSendRoadLimitSpeed()
                                                    android.widget.Toast.makeText(
                                                        context,
                                                        "✅ 已将道路限速 ${roadLimitSpeed}km/h 设为巡航速度",
                                                        android.widget.Toast.LENGTH_SHORT
                                                    ).show()
                                                } else {
                                                    android.widget.Toast.makeText(
                                                        context,
                                                        "⚠️ 当前无道路限速信息\n请先启动导航",
                                                        android.widget.Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            },
                                            modifier = Modifier.size(56.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (roadLimitSpeed > 0) Color(0xFF8B5CF6) else Color(0xFF94A3B8),
                                                disabledContainerColor = Color(0xFF94A3B8),
                                                contentColor = Color.White,
                                                disabledContentColor = Color.White.copy(alpha = 0.9f)
                                            ),
                                            enabled = roadLimitSpeed > 0,
                                            contentPadding = PaddingValues(0.dp),
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = buttonText,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                lineHeight = 12.sp
                                            )
                                        }
                                    }
                                    // 8号按钮 - 减速
                                    8 -> {
                                        val currentCruiseSpeed = carrotManFields.vCruiseKph.toInt()
                                        val newSpeed = if (currentCruiseSpeed > 20) {
                                            maxOf(currentCruiseSpeed - 10, 20)
                                        } else {
                                            currentCruiseSpeed
                                        }
                                        
                                        Button(
                                            onClick = {
                                                if (newSpeed < currentCruiseSpeed) {
                                                    onSendCommand("SPEED", newSpeed.toString())
                                                } else {
                                                    android.widget.Toast.makeText(
                                                        context,
                                                        "⚠️ 已是最低速度（${currentCruiseSpeed}km/h）",
                                                        android.widget.Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            },
                                            modifier = Modifier.size(56.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFFEF4444)
                                            ),
                                            contentPadding = PaddingValues(0.dp),
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = "减速",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                    // 9号按钮 - 启用路径
                                    9 -> {
                                        Button(
                                            onClick = {
                                                if (isOpenpilotActive) {
                                                    playSound(R.raw.noo, "NOO")
                                                    onSendNavConfirmation()
                                                    onDismiss()
                                                } else {
                                                    android.widget.Toast.makeText(
                                                        context,
                                                        "⚠️ OpenpPilot未激活\n请先启动车辆系统",
                                                        android.widget.Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            },
                                            modifier = Modifier.size(56.dp),
                                            enabled = isOpenpilotActive,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isOpenpilotActive) Color(0xFF10B981) else Color(0xFF94A3B8),
                                                disabledContainerColor = Color(0xFF94A3B8),
                                                contentColor = Color.White,
                                                disabledContentColor = Color.White.copy(alpha = 0.9f)
                                            ),
                                            contentPadding = PaddingValues(0.dp),
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = "启用\n路径",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                lineHeight = 12.sp
                                            )
                                        }
                                    }
                                    // 其他未定义的按钮（应该不会出现，因为1-9都已定义）
                                    else -> {
                                        Button(
                                            onClick = {
                                                onDismiss()
                                            },
                                            modifier = Modifier.size(56.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF94A3B8)
                                            ),
                                            contentPadding = PaddingValues(0.dp),
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = "$buttonNumber",
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    // 🆕 超车参数调节区域（4行参数）
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 6.dp),
                        color = Color(0xFFE5E7EB),
                        thickness = 1.dp
                    )
                    
                    // 参数调节区域（紧凑布局）
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // 参数1：最小超车速度
                        OvertakeParameterRow(
                            label = "最小超车速度",
                            unit = "kph",
                            defaultValue = 60f,
                            minValue = 40f,
                            maxValue = 100f,
                            step = 5f,
                            prefKey = "overtake_param_min_speed_kph",
                            context = context
                        )
                        
                        // 参数2：速度差阈值
                        OvertakeParameterRow(
                            label = "速度差阈值",
                            unit = "kph",
                            defaultValue = 10f,
                            minValue = 5f,
                            maxValue = 30f,
                            step = 1f,
                            prefKey = "overtake_param_speed_diff_kph",
                            context = context
                        )
                        
                        // 参数3：速度比例阈值
                        OvertakeParameterRow(
                            label = "速度比例阈值",
                            unit = "%",
                            defaultValue = 0.8f,
                            minValue = 0.5f,
                            maxValue = 0.95f,
                            step = 0.05f,
                            prefKey = "overtake_param_speed_ratio",
                            context = context,
                            displayMultiplier = 100f  // 显示为百分比
                        )
                        
                        // 参数4：侧方安全距离
                        OvertakeParameterRow(
                            label = "侧方安全距离",
                            unit = "m",
                            defaultValue = 30f,
                            minValue = 20f,
                            maxValue = 50f,
                            step = 1f,
                            prefKey = "overtake_param_side_safe_distance_m",
                            context = context
                        )
                    }
                }
            }
        }
    }
    
    /**
     * 🆕 超车参数调节行组件
     * 显示参数名称、当前值，并提供加减按钮
     */
    @Composable
    private fun OvertakeParameterRow(
        label: String,
        unit: String,
        defaultValue: Float,
        minValue: Float,
        maxValue: Float,
        step: Float,
        prefKey: String,
        context: android.content.Context,
        displayMultiplier: Float = 1f  // 显示倍数（用于百分比等）
    ) {
        val prefs = remember { context.getSharedPreferences("CarrotAmap", android.content.Context.MODE_PRIVATE) }
        var currentValue by remember { 
            mutableStateOf(prefs.getFloat(prefKey, defaultValue).coerceIn(minValue, maxValue))
        }
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 参数名称（左侧）
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF1F2937),
                modifier = Modifier.weight(1f)
            )
            
            // 减号按钮、数值、加号按钮（右侧，紧凑排列）
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 减号按钮
                Button(
                    onClick = {
                        val newValue = (currentValue - step).coerceAtLeast(minValue)
                        currentValue = newValue
                        prefs.edit().putFloat(prefKey, newValue).apply()
                        android.util.Log.d("MainActivity", "🔧 调整参数 $label: $newValue")
                    },
                    modifier = Modifier.size(28.dp),
                    enabled = currentValue > minValue,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (currentValue > minValue) Color(0xFFEF4444) else Color(0xFF9CA3AF)
                    ),
                    contentPadding = PaddingValues(0.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(5.dp)
                ) {
                    Text(
                        text = "−",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                
                // 当前值显示（放在中间，单行显示）
                Text(
                    text = "${(currentValue * displayMultiplier).toInt()} $unit",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF3B82F6),
                    modifier = Modifier.width(55.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Clip
                )
                
                // 加号按钮
                Button(
                    onClick = {
                        val newValue = (currentValue + step).coerceAtMost(maxValue)
                        currentValue = newValue
                        prefs.edit().putFloat(prefKey, newValue).apply()
                        android.util.Log.d("MainActivity", "🔧 调整参数 $label: $newValue")
                    },
                    modifier = Modifier.size(28.dp),
                    enabled = currentValue < maxValue,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (currentValue < maxValue) Color(0xFF22C55E) else Color(0xFF9CA3AF)
                    ),
                    contentPadding = PaddingValues(0.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(5.dp)
                ) {
                    Text(
                        text = "+",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
    
    /**
     * 发送回家导航指令给高德地图
     */
    fun sendHomeNavigationToAmap(context: android.content.Context) {
        try {
            android.util.Log.i("MainActivity", "🏠 发送一键回家指令给高德地图")
            val homeIntent = android.content.Intent("AUTONAVI_STANDARD_BROADCAST_RECV").apply {
                putExtra("KEY_TYPE", 10040)
                putExtra("SOURCE_APP", "CPlink")
                putExtra("DEST", 0)
                putExtra("IS_START_NAVI", 0)
                setPackage("com.autonavi.amapauto")
                flags = android.content.Intent.FLAG_INCLUDE_STOPPED_PACKAGES
            }
            context.sendBroadcast(homeIntent)
            android.util.Log.i("MainActivity", "✅ 一键回家导航广播已发送")
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "❌ 发送一键回家指令失败: ${e.message}", e)
        }
    }
    
    /**
     * 发送导航到公司指令给高德地图
     */
    fun sendCompanyNavigationToAmap(context: android.content.Context) {
        try {
            android.util.Log.i("MainActivity", "🏢 发送导航到公司指令给高德地图")
            val companyIntent = android.content.Intent("AUTONAVI_STANDARD_BROADCAST_RECV").apply {
                putExtra("KEY_TYPE", 10040)
                putExtra("SOURCE_APP", "CPlink")
                putExtra("DEST", 1)
                putExtra("IS_START_NAVI", 0)
                setPackage("com.autonavi.amapauto")
                flags = android.content.Intent.FLAG_INCLUDE_STOPPED_PACKAGES
            }
            context.sendBroadcast(companyIntent)
            android.util.Log.i("MainActivity", "✅ 导航到公司广播已发送")
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "❌ 发送导航到公司指令失败: ${e.message}", e)
        }
    }
    
    /**
     * 启动模拟导航功能
     */
    fun startSimulatedNavigation(context: android.content.Context, carrotManFields: CarrotManFields) {
        try {
            android.util.Log.i("MainActivity", "🔧 启动模拟导航功能")
            
            val currentLat = when {
                carrotManFields.vpPosPointLat != 0.0 -> {
                    android.util.Log.i("MainActivity", "✅ 使用实时GPS坐标（vpPosPointLat）: ${carrotManFields.vpPosPointLat}")
                    carrotManFields.vpPosPointLat
                }
                carrotManFields.latitude != 0.0 -> {
                    android.util.Log.i("MainActivity", "✅ 使用备用GPS坐标（latitude）: ${carrotManFields.latitude}")
                    carrotManFields.latitude
                }
                else -> {
                    val fallbackLat = getCurrentLocationLatitude(context)
                    android.util.Log.w("MainActivity", "⚠️ GPS坐标不可用，使用SharedPreferences坐标: $fallbackLat")
                    fallbackLat
                }
            }
            
            val currentLon = when {
                carrotManFields.vpPosPointLon != 0.0 -> {
                    android.util.Log.i("MainActivity", "✅ 使用实时GPS坐标（vpPosPointLon）: ${carrotManFields.vpPosPointLon}")
                    carrotManFields.vpPosPointLon
                }
                carrotManFields.longitude != 0.0 -> {
                    android.util.Log.i("MainActivity", "✅ 使用备用GPS坐标（longitude）: ${carrotManFields.longitude}")
                    carrotManFields.longitude
                }
                else -> {
                    val fallbackLon = getCurrentLocationLongitude(context)
                    android.util.Log.w("MainActivity", "⚠️ GPS坐标不可用，使用SharedPreferences坐标: $fallbackLon")
                    fallbackLon
                }
            }
            
            if (currentLat == 0.0 || currentLon == 0.0) {
                android.util.Log.w("MainActivity", "⚠️ GPS坐标无效，使用默认起点坐标（北京）")
                sendSimulatedNavigationIntent(context, 39.9042, 116.4074, 31.2397, 121.4998)
                return
            }
            
            val destLat = 31.2397
            val destLon = 121.4998
            
            if (kotlin.math.abs(currentLat - destLat) < 0.001 && kotlin.math.abs(currentLon - destLon) < 0.001) {
                android.util.Log.w("MainActivity", "⚠️ 起点和终点坐标过于接近，调整目的地位置")
                sendSimulatedNavigationIntent(context, currentLat, currentLon, 22.5431, 114.0579)
            } else {
                sendSimulatedNavigationIntent(context, currentLat, currentLon, destLat, destLon)
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "❌ 启动模拟导航失败: ${e.message}", e)
        }
    }
    
    /**
     * 发送模拟导航Intent
     */
    private fun sendSimulatedNavigationIntent(
        context: android.content.Context,
        startLat: Double, 
        startLon: Double, 
        destLat: Double, 
        destLon: Double
    ) {
        try {
            val intent = android.content.Intent("AUTONAVI_STANDARD_BROADCAST_RECV").apply {
                putExtra("KEY_TYPE", 10076)
                putExtra("SOURCE_APP", "CPlink")
                putExtra("EXTRA_SLAT", startLat)
                putExtra("EXTRA_SLON", startLon)
                putExtra("EXTRA_SNAME", "当前位置")
                putExtra("EXTRA_DLAT", destLat)
                putExtra("EXTRA_DLON", destLon)
                putExtra("EXTRA_DNAME", "上海东方明珠")
                putExtra("EXTRA_DEV", 0)
                putExtra("EXTRA_M", 0)
                putExtra("KEY_RECYLE_SIMUNAVI", true)
                setPackage("com.autonavi.amapauto")
                flags = android.content.Intent.FLAG_INCLUDE_STOPPED_PACKAGES
            }
            context.sendBroadcast(intent)
            android.util.Log.i("MainActivity", "✅ 模拟导航广播已发送给高德地图车机版")
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "❌ 发送模拟导航广播失败: ${e.message}", e)
        }
    }
    
    /**
     * 获取当前位置纬度
     */
    private fun getCurrentLocationLatitude(context: android.content.Context): Double {
        return try {
            val carrotPrefs = context.getSharedPreferences("CarrotAmap", android.content.Context.MODE_PRIVATE)
            val devicePrefs = context.getSharedPreferences("device_prefs", android.content.Context.MODE_PRIVATE)
            var lat = carrotPrefs.getFloat("vpPosPointLat", 0.0f).toDouble()
            if (lat == 0.0) {
                lat = devicePrefs.getFloat("vpPosPointLat", 0.0f).toDouble()
            }
            if (lat != 0.0) {
                android.util.Log.i("MainActivity", "✅ 获取到当前位置纬度: $lat")
                lat
            } else {
                android.util.Log.w("MainActivity", "⚠️ 未找到当前位置，使用默认起点坐标（北京）")
                39.9042
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "❌ 获取当前位置纬度失败: ${e.message}", e)
            39.9042
        }
    }
    
    /**
     * 获取当前位置经度
     */
    private fun getCurrentLocationLongitude(context: android.content.Context): Double {
        return try {
            val carrotPrefs = context.getSharedPreferences("CarrotAmap", android.content.Context.MODE_PRIVATE)
            val devicePrefs = context.getSharedPreferences("device_prefs", android.content.Context.MODE_PRIVATE)
            var lon = carrotPrefs.getFloat("vpPosPointLon", 0.0f).toDouble()
            if (lon == 0.0) {
                lon = devicePrefs.getFloat("vpPosPointLon", 0.0f).toDouble()
            }
            if (lon != 0.0) {
                android.util.Log.i("MainActivity", "✅ 获取到当前位置经度: $lon")
                lon
            } else {
                android.util.Log.w("MainActivity", "⚠️ 未找到当前位置，使用默认起点坐标（北京）")
                116.4074
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "❌ 获取当前位置经度失败: ${e.message}", e)
            116.4074
        }
    }
}

/**
 * 速度圆环Compose组件（紧凑版）
 */
@Composable
fun SpeedIndicatorCompose(
    value: Int,
    color: Color,
    label: String,
    onClick: (() -> Unit)? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(48.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(42.dp)
                .clickable(enabled = onClick != null) { onClick?.invoke() }
        ) {
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                val radius = size.minDimension / 2f - 5.dp.toPx()
                drawCircle(
                    color = Color.White,
                    radius = radius,
                    center = center
                )
                drawCircle(
                    color = color,
                    radius = radius,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5.dp.toPx())
                )
            }
            
            Text(
                text = value.toString(),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        
        if (label.isNotEmpty()) {
            Text(
                text = label,
                fontSize = 7.sp,
                color = Color(0xFF64748B),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 9.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

