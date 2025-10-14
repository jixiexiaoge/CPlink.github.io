package com.example.carrotamap.ui.components

import android.content.Context
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carrotamap.NetworkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.util.Log

/**
 * 圆形控制按钮组件
 */
@Composable
fun CircularControlButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    backgroundColor: Color,
    iconColor: Color,
    contentDescription: String,
    size: androidx.compose.ui.unit.Dp = 48.dp
) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(size),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor
        ),
        contentPadding = PaddingValues(0.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconColor,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * 紧凑型操作按钮组件
 */
@Composable
fun CompactActionButton(
    onClick: () -> Unit,
    text: String,
    backgroundColor: Color,
    textColor: Color
) {
    Button(
        onClick = onClick,
        modifier = Modifier.height(32.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
    }
}

/**
 * 设置按钮组件 - 发送配置到comma3设备
 */
@Composable
fun SettingsButton(
    modifier: Modifier = Modifier,
    networkManager: NetworkManager
) {
    var isLoading by remember { mutableStateOf(false) }
    var isSuccess by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    // 成功状态自动重置
    LaunchedEffect(isSuccess) {
        if (isSuccess) {
            delay(2000) // 2秒后重置
            isSuccess = false
        }
    }

    // 错误提示自动消失
    LaunchedEffect(showError) {
        if (showError) {
            delay(3000) // 3秒后消失
            showError = false
        }
    }

    Button(
        onClick = {
            if (!isLoading) {
                isLoading = true
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        val result = networkManager.sendSettingsToComma3()
                        if (result.isSuccess) {
                            isSuccess = true
                            Log.i("SettingsButton", "✅ 设置发送成功")
                        } else {
                            errorMessage = result.exceptionOrNull()?.message ?: "发送失败"
                            showError = true
                            Log.e("SettingsButton", "❌ 设置发送失败: $errorMessage")
                        }
                    } catch (e: Exception) {
                        errorMessage = e.message ?: "网络错误"
                        showError = true
                        Log.e("SettingsButton", "❌ 设置发送异常: ${e.message}", e)
                    } finally {
                        isLoading = false
                    }
                }
            }
        },
        modifier = modifier,
        shape = RoundedCornerShape(percent = 50),
        colors = ButtonDefaults.buttonColors(
            containerColor = when {
                isSuccess -> Color(0xFF22C55E) // 成功时绿色
                isLoading -> Color(0xFF6B7280) // 加载时灰色
                else -> Color(0xFF6B7280) // 默认灰色
            }
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        enabled = !isLoading
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text = "傻瓜配置",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }

    // 错误提示横幅
    if (showError) {
        LaunchedEffect(Unit) {
            // 这里可以显示Snackbar或Toast
            // 暂时使用Log输出，实际项目中可以使用SnackbarHost
        }
    }
}

/**
 * 模式切换按钮组件 - 循环切换SpeedFromPCM模式
 */
@Composable
fun ModeToggleButton(
    modifier: Modifier = Modifier,
    networkManager: NetworkManager
) {
    val context = LocalContext.current

    // 从SharedPreferences读取保存的模式状态
    val savedMode = remember {
        val prefs = context.getSharedPreferences("CarrotAmap", Context.MODE_PRIVATE)
        prefs.getInt("speed_from_pcm_mode", 0) // 默认为0（智能控速）
    }

    // 模式状态：0=智能控速, 1=原车巡航, 2=弯道减速
    var currentMode by remember { mutableStateOf(savedMode) }
    var isLoading by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    // 保存模式状态到SharedPreferences
    fun saveMode(mode: Int) {
        val prefs = context.getSharedPreferences("CarrotAmap", Context.MODE_PRIVATE)
        prefs.edit().putInt("speed_from_pcm_mode", mode).apply()
    }

    // 模式配置
    val modeConfigs = listOf(
        ModeConfig(0, "智能控速", Color(0xFF22C55E)), // 绿色
        ModeConfig(1, "原车巡航", Color(0xFF3B82F6)), // 蓝色
        ModeConfig(2, "弯道减速", Color(0xFFF59E0B))  // 橙色
    )

    val currentConfig = modeConfigs[currentMode]

    // 错误提示自动消失
    LaunchedEffect(showError) {
        if (showError) {
            delay(3000) // 3秒后消失
            showError = false
        }
    }

    Button(
        onClick = {
            if (!isLoading) {
                isLoading = true
                val nextMode = (currentMode + 1) % 3 // 循环：0→1→2→0

                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        val result = networkManager.sendModeChangeToComma3(nextMode)
                        if (result.isSuccess) {
                            currentMode = nextMode // 只有成功后才更新状态
                            saveMode(nextMode) // 保存到SharedPreferences
                            Log.i("ModeToggleButton", "✅ 模式切换成功: ${modeConfigs[currentMode].name}")
                        } else {
                            errorMessage = result.exceptionOrNull()?.message ?: "模式切换失败"
                            showError = true
                            Log.e("ModeToggleButton", "❌ 模式切换失败: $errorMessage")
                        }
                    } catch (e: Exception) {
                        errorMessage = e.message ?: "网络错误"
                        showError = true
                        Log.e("ModeToggleButton", "❌ 模式切换异常: ${e.message}", e)
                    } finally {
                        isLoading = false
                    }
                }
            }
        },
        modifier = modifier,
        shape = RoundedCornerShape(percent = 50),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isLoading) Color(0xFF6B7280) else currentConfig.color
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        enabled = !isLoading
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                color = Color.White,
                strokeWidth = 1.5.dp
            )
        } else {
            Text(
                text = currentConfig.name,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 1
            )
        }
    }

    // 错误提示（可以扩展为Snackbar）
    if (showError) {
        LaunchedEffect(Unit) {
            Log.w("ModeToggleButton", "显示错误提示: $errorMessage")
        }
    }
}

/**
 * 模式配置数据类
 */
data class ModeConfig(
    val value: Int,
    val name: String,
    val color: Color
)
