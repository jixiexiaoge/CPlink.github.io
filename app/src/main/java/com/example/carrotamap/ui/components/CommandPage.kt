package com.example.carrotamap.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carrotamap.NetworkManager
import com.example.carrotamap.ZmqClient
import com.example.carrotamap.ZmqCommandResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * 命令历史记录数据类
 */
data class CommandHistory(
    val command: String,
    val result: ZmqCommandResult,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 快捷命令项数据类
 */
data class QuickCommandItem(
    val command: String,
    val displayName: String,
    val description: String = ""
)

/**
 * 快捷命令列表
 * 可以轻松添加更多命令
 */
val QUICK_COMMANDS = listOf(
    QuickCommandItem(
        command = "sudo reboot",
        displayName = "重启设备",
        description = "重启Comma3设备"
    ),
    QuickCommandItem(
        command = "cd /data/openpilot && git pull && sudo reboot",
        displayName = "更新并重启",
        description = "拉取最新代码并重启设备"
    ),
    QuickCommandItem(
        command = "pip install flask shapely",
        displayName = "安装Flask",
        description = "安装Flask Python包"
    ),
    QuickCommandItem(
        command = "ps aux | grep openpilot",
        displayName = "查看进程",
        description = "查看openpilot相关进程"
    ),
    QuickCommandItem(
        command = "echo \"sk.eyJ1Ijoic2FkbWVubWVuIiwiYSI6ImNrdnFmZnNhbjFlMzMydW1vdXVuanMzN3cifQ.5LFqEgszx-mIQirN0L0Cbw\" > /data/params_cp/d/MapboxSecretKey",
        displayName = "设置Mapbox密钥",
        description = "设置Mapbox Secret Key"
    ),
    QuickCommandItem(
        command = "echo \"pk.eyJ1Ijoic2FkbWVubWVuIiwiYSI6ImNrdmxweDg5NzVzNDQybnFwd3g4OWFwYW0ifQ.s-KR7at1WB6-NR7BTbSPPA\" > /data/params_cp/d/MapboxPublicKey",
        displayName = "设置Mapbox公钥",
        description = "设置Mapbox Public Key"
    ),
    QuickCommandItem(
        command = "sudo rm -rf /data/media/0/realdata",
        displayName = "删除视频",
        description = "删除realdata目录下的所有视频文件"
    ),
    QuickCommandItem(
        command = "sudo systemctl restart comma",
        displayName = "重启OpenPilot",
        description = "重启openpilot服务"
    ),
    QuickCommandItem(
        command = "sudo systemctl stop comma",
        displayName = "停止OpenPilot",
        description = "停止openpilot服务"
    ),
    // 可以在这里添加更多命令
    // QuickCommandItem(
    //     command = "cat /data/params/d/CarName",
    //     displayName = "查看车辆名称",
    //     description = "显示当前车辆名称"
    // ),
)

/**
 * 命令页面组件
 * 允许用户输入Shell命令并查看设备回显信息
 */
@Composable
fun CommandPage(
    networkManager: NetworkManager? = null,
    zmqClient: ZmqClient? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // 状态管理
    var commandText by remember { mutableStateOf("") }
    var isExecuting by remember { mutableStateOf(false) }
    var currentResult by remember { mutableStateOf<ZmqCommandResult?>(null) }
    var commandHistory by remember { mutableStateOf<List<CommandHistory>>(emptyList()) }
    var connectionStatus by remember { mutableStateOf("正在获取设备IP...") }
    var isConnected by remember { mutableStateOf(false) }
    var currentDeviceIP by remember { mutableStateOf<String?>(null) }
    
    // 定期获取设备IP并自动连接（每1秒检查一次）
    LaunchedEffect(networkManager, zmqClient) {
        while (true) {
            // 从NetworkManager获取最新的设备IP
            val deviceIP = networkManager?.getCurrentDeviceIP()
            
            // 如果设备IP发生变化，更新并尝试连接
            if (deviceIP != currentDeviceIP) {
                currentDeviceIP = deviceIP
                
                if (deviceIP != null && zmqClient != null) {
                    // 检查是否已连接到该设备
                    val alreadyConnected = zmqClient.isConnected() && zmqClient.getCurrentDeviceIP() == deviceIP
                    
                    if (!alreadyConnected) {
                        connectionStatus = "正在连接到: $deviceIP:7710"
                        val connected = zmqClient.connect(deviceIP)
                        isConnected = connected
                        connectionStatus = if (connected) {
                            "已连接到: $deviceIP:7710"
                        } else {
                            "连接失败，请检查设备是否在线"
                        }
                    } else {
                        isConnected = true
                        connectionStatus = "已连接到: $deviceIP:7710"
                    }
                } else if (deviceIP == null) {
                    // 设备IP未获取到
                    isConnected = false
                    connectionStatus = "等待设备连接..."
                }
            } else if (deviceIP != null && zmqClient != null) {
                // 设备IP没有变化，但需要检查连接状态
                val wasConnected = isConnected
                val actuallyConnected = zmqClient.isConnected() && zmqClient.getCurrentDeviceIP() == deviceIP
                
                if (wasConnected != actuallyConnected) {
                    isConnected = actuallyConnected
                    if (actuallyConnected) {
                        connectionStatus = "已连接到: $deviceIP:7710"
                    } else {
                        connectionStatus = "连接已断开，正在重连..."
                        // 自动重连
                        val connected = zmqClient.connect(deviceIP)
                        isConnected = connected
                        connectionStatus = if (connected) {
                            "已连接到: $deviceIP:7710"
                        } else {
                            "重连失败"
                        }
                    }
                } else if (actuallyConnected) {
                    connectionStatus = "已连接到: $deviceIP:7710"
                }
            }
            
            delay(1000) // 每1秒检查一次
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .padding(16.dp)
    ) {
        // 连接状态显示
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isConnected) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isConnected) Icons.Default.CheckCircle else Icons.Default.Close,
                    contentDescription = null,
                    tint = if (isConnected) Color(0xFF4CAF50) else Color(0xFFF44336),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = connectionStatus,
                    fontSize = 14.sp,
                    color = if (isConnected) Color(0xFF2E7D32) else Color(0xFFC62828)
                )
                if (currentDeviceIP != null && !isConnected) {
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = {
                            scope.launch {
                                if (zmqClient != null && currentDeviceIP != null) {
                                    connectionStatus = "正在重连..."
                                    val connected = zmqClient.connect(currentDeviceIP!!)
                                    isConnected = connected
                                    connectionStatus = if (connected) {
                                        "已连接到: $currentDeviceIP:7710"
                                    } else {
                                        "连接失败，请检查设备是否在线"
                                    }
                                }
                            }
                        },
                        modifier = Modifier.height(32.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2196F3)
                        )
                    ) {
                        Text("重连", fontSize = 12.sp)
                    }
                }
            }
        }
        
        // 命令输入区域
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "输入命令",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                OutlinedTextField(
                    value = commandText,
                    onValueChange = { commandText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("例如: cat /data/params/d/CarName") },
                    enabled = !isExecuting,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (commandText.isNotBlank() && !isExecuting) {
                                scope.launch {
                                    executeCommand(
                                        commandText,
                                        zmqClient,
                                        currentDeviceIP,
                                        onStart = { isExecuting = true },
                                        onResult = { result ->
                                            currentResult = result
                                            commandHistory = listOf(
                                                CommandHistory(commandText, result)
                                            ) + commandHistory.take(49) // 保留最近50条
                                            isExecuting = false
                                        }
                                    )
                                }
                            }
                        }
                    ),
                    trailingIcon = {
                        if (isExecuting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else if (commandText.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        executeCommand(
                                            commandText,
                                            zmqClient,
                                            currentDeviceIP,
                                            onStart = { isExecuting = true },
                                            onResult = { result ->
                                                currentResult = result
                                                commandHistory = listOf(
                                                    CommandHistory(commandText, result)
                                                ) + commandHistory.take(49)
                                                isExecuting = false
                                            }
                                        )
                                    }
                                },
                                enabled = !isExecuting
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "执行命令",
                                    tint = if (isConnected) Color(0xFF2196F3) else Color(0xFF9E9E9E)
                                )
                            }
                        }
                    }
                )
                
                // 快捷命令下拉菜单
                QuickCommandDropdown(
                    commands = QUICK_COMMANDS,
                    onCommandSelected = { command ->
                        commandText = command
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }
        }
        
        // 结果显示区域
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "执行结果",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )
                    
                    if (commandHistory.isNotEmpty()) {
                        TextButton(
                            onClick = { commandHistory = emptyList() }
                        ) {
                            Text("清空历史", fontSize = 12.sp)
                        }
                    }
                }
                
                if (currentResult == null && commandHistory.isEmpty()) {
                    // 空状态
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Build,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = Color(0xFF9E9E9E)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "暂无执行结果",
                                fontSize = 14.sp,
                                color = Color(0xFF9E9E9E)
                            )
                            Text(
                                text = "输入命令后点击发送或按回车键执行",
                                fontSize = 12.sp,
                                color = Color(0xFF9E9E9E),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                } else {
                    // 显示结果
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // 显示当前结果
                        currentResult?.let { result ->
                            CommandResultItem(
                                command = commandHistory.firstOrNull()?.command ?: "",
                                result = result,
                                isLatest = true
                            )
                        }
                        
                        // 显示历史记录
                        commandHistory.drop(if (currentResult != null) 1 else 0).forEach { history ->
                            CommandResultItem(
                                command = history.command,
                                result = history.result,
                                isLatest = false
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 快捷命令下拉菜单组件
 * ✅ 修复：确保下拉菜单可以正常点击和选择
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickCommandDropdown(
    commands: List<QuickCommandItem>,
    onCommandSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedCommand by remember { mutableStateOf<QuickCommandItem?>(null) }
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedCommand?.displayName ?: "",
            onValueChange = { },
            readOnly = true,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            placeholder = {
                Text(
                    text = "选择快捷命令",
                    color = Color(0xFF9E9E9E)
                )
            },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()  // ✅ 关键修复：必须添加menuAnchor()修饰符，否则点击无反应（使用默认参数）
        )
        
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 300.dp)
        ) {
            commands.forEach { commandItem ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = commandItem.displayName,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                            if (commandItem.description.isNotEmpty()) {
                                Text(
                                    text = commandItem.description,
                                    fontSize = 12.sp,
                                    color = Color(0xFF757575)
                                )
                            }
                            Text(
                                text = commandItem.command,
                                fontSize = 11.sp,
                                color = Color(0xFF9E9E9E),
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    },
                    onClick = {
                        selectedCommand = commandItem
                        onCommandSelected(commandItem.command)
                        expanded = false
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * 命令结果项组件
 */
@Composable
private fun CommandResultItem(
    command: String,
    result: ZmqCommandResult,
    isLatest: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isLatest) Color(0xFFE3F2FD) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // 命令显示
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color(0xFF2196F3)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = command,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1976D2),
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = if (result.success) "✓" else "✗",
                    fontSize = 16.sp,
                    color = if (result.success) Color(0xFF4CAF50) else Color(0xFFF44336)
                )
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            // 结果显示
            Text(
                text = result.getFormattedOutput(),
                fontSize = 12.sp,
                color = Color(0xFF424242),
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Color(0xFFF5F5F5),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(8.dp)
            )
            
            // 状态码显示
            if (result.exitStatus != 0) {
                Text(
                    text = "退出码: ${result.exitStatus}",
                    fontSize = 11.sp,
                    color = Color(0xFF757575),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/**
 * 执行命令的协程函数
 */
private suspend fun executeCommand(
    command: String,
    zmqClient: ZmqClient?,
    deviceIP: String?,
    onStart: () -> Unit,
    onResult: (ZmqCommandResult) -> Unit
) {
    onStart()
    
    if (zmqClient == null) {
        onResult(
            ZmqCommandResult(
                success = false,
                exitStatus = -1,
                result = "",
                error = "ZMQ客户端未初始化"
            )
        )
        return
    }
    
    // 如果设备IP未设置或未连接，尝试连接
    if (deviceIP == null) {
        onResult(
            ZmqCommandResult(
                success = false,
                exitStatus = -1,
                result = "",
                error = "设备IP未获取，请等待设备连接"
            )
        )
        return
    }
    
    // 确保已连接
    if (!zmqClient.isConnected() || zmqClient.getCurrentDeviceIP() != deviceIP) {
        val connected = zmqClient.connect(deviceIP)
        if (!connected) {
            onResult(
                ZmqCommandResult(
                    success = false,
                    exitStatus = -1,
                    result = "",
                    error = "无法连接到设备 $deviceIP:7710，请检查设备是否在线"
                )
            )
            return
        }
    }
    
    // 执行命令
    val result = zmqClient.executeCommand(command)
    onResult(result)
}

