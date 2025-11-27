package com.example.carrotamap.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carrotamap.NetworkManager
import com.example.carrotamap.ZmqClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun CommandPage(
    networkManager: NetworkManager? = null,
    zmqClient: ZmqClient? = null
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val parameterManager = remember(networkManager, zmqClient) {
        CarrotParameterManager(networkManager, zmqClient)
    }

    var connectionStatus by remember { mutableStateOf("正在获取设备IP...") }
    var isConnected by remember { mutableStateOf(false) }
    var currentDeviceIP by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isApplying by remember { mutableStateOf(false) }
    val parameterStates = remember { mutableStateListOf<CarrotParameterState>() }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        if (parameterStates.isEmpty()) {
            parameterStates.clear()
            parameterStates.addAll(parameterManager.getDefaultStates())
        }
    }

    // 定期获取设备IP并自动连接（每1秒检查一次）
    LaunchedEffect(networkManager, zmqClient) {
        while (true) {
            val deviceIP = networkManager?.getCurrentDeviceIP()

            if (deviceIP != currentDeviceIP) {
                currentDeviceIP = deviceIP

                if (deviceIP != null && zmqClient != null) {
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
                    isConnected = false
                    connectionStatus = "等待设备连接..."
                }
            } else if (deviceIP != null && zmqClient != null) {
                val wasConnected = isConnected
                val actuallyConnected = zmqClient.isConnected() && zmqClient.getCurrentDeviceIP() == deviceIP

                if (wasConnected != actuallyConnected) {
                    isConnected = actuallyConnected
                    if (actuallyConnected) {
                        connectionStatus = "已连接到: $deviceIP:7710"
                    } else {
                        connectionStatus = "连接已断开，正在重连..."
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

            delay(1000)
        }
    }

    fun loadParameters(showFeedback: Boolean = false) {
        scope.launch {
            isLoading = true
            try {
                val states = parameterManager.loadParameterStates()
                parameterStates.clear()
                parameterStates.addAll(states)
                if (showFeedback) {
                    snackbarHostState.showSnackbar("参数已刷新")
                }
            } catch (e: Exception) {
                // 如果加载失败，使用默认值
                parameterStates.clear()
                parameterStates.addAll(parameterManager.getDefaultStates())
                if (showFeedback) {
                    snackbarHostState.showSnackbar("参数加载失败，已使用默认值")
                } else {
                    // 首次加载失败时也提示用户
                    snackbarHostState.showSnackbar("无法从设备获取参数，请检查连接")
                }
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(isConnected) {
        if (isConnected) {
            loadParameters()
        } else {
            parameterStates.clear()
            parameterStates.addAll(parameterManager.getDefaultStates())
        }
    }

    val hasPendingChanges by remember {
        derivedStateOf { parameterStates.any { it.isModified } }
    }

    fun updateParameterValue(name: String, value: Int) {
        val index = parameterStates.indexOfFirst { it.definition.name == name }
        if (index >= 0) {
            val definition = parameterStates[index].definition
            val sanitized = value.coerceIn(definition.minValue, definition.maxValue)
            parameterStates[index] = parameterStates[index].copy(editedValue = sanitized)
        }
    }

    fun resetToDefault() {
        if (parameterStates.isEmpty()) return
        parameterStates.indices.forEach { idx ->
            val state = parameterStates[idx]
            parameterStates[idx] = state.copy(editedValue = state.definition.defaultValue)
        }
    }

    fun applyChanges() {
        if (!isConnected) {
            scope.launch { snackbarHostState.showSnackbar("设备未连接，无法发送参数") }
            return
        }
        val pending = parameterStates
            .filter { it.isModified }
            .associate { it.definition.name to it.editedValue }

        if (pending.isEmpty()) {
            scope.launch { snackbarHostState.showSnackbar("没有需要提交的更改") }
            return
        }

        scope.launch {
            isApplying = true
            val result = parameterManager.applyParameterChanges(pending)
            if (result.isSuccess) {
                // 更新界面中的currentValue为editedValue，确保界面显示最新值
                parameterStates.indices.forEach { idx ->
                    val state = parameterStates[idx]
                    if (pending.containsKey(state.definition.name)) {
                        parameterStates[idx] = state.copy(currentValue = state.editedValue)
                    }
                }
                snackbarHostState.showSnackbar("参数更新成功")
                
                // 可选：延迟一小段时间后重新从设备获取参数以验证更新
                // 这里暂时不自动刷新，因为用户可以通过刷新按钮手动刷新
            } else {
                val error = result.exceptionOrNull()?.message ?: "参数更新失败"
                snackbarHostState.showSnackbar(error)
            }
            isApplying = false
        }
    }

    fun refreshParameters() {
        if (!isConnected) {
            scope.launch { snackbarHostState.showSnackbar("设备未连接，无法刷新") }
            return
        }
        loadParameters(showFeedback = true)
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF8FAFC))
                    .padding(paddingValues)
            ) {
                // 顶部留出连接状态指示器的高度空间（约36dp）
                Spacer(modifier = Modifier.height(1.dp))

                // 中间: 参数列表占据大部分空间
                ParameterListSection(
                    parameterStates = parameterStates,
                    listState = listState,
                    isConnected = isConnected,
                    isLoading = isLoading,
                    onValueChange = { name, value -> updateParameterValue(name, value) },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp, vertical = 8.dp)
                )

                // 底部: 三个按钮一排显示
                ActionButtonsRow(
                    hasPendingChanges = hasPendingChanges,
                    isApplying = isApplying,
                    isConnected = isConnected,
                    onApply = { applyChanges() },
                    onRefresh = { refreshParameters() },
                    onReset = { resetToDefault() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = (8.dp + paddingValues.calculateBottomPadding())
                        )
                )
            }
        }
        
        // 连接状态指示器：完全贴顶，不受Scaffold padding影响
        ConnectionStatusIndicator(
            connectionStatus = connectionStatus,
            isConnected = isConnected,
            currentDeviceIP = currentDeviceIP,
            onReconnect = {
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
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun ConnectionStatusIndicator(
    connectionStatus: String,
    isConnected: Boolean,
    currentDeviceIP: String?,
    onReconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isConnected) Icons.Filled.CheckCircle else Icons.Filled.Close,
                contentDescription = null,
                tint = if (isConnected) Color(0xFF4CAF50) else Color(0xFFF44336),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = connectionStatus,
                fontSize = 13.sp,
                color = if (isConnected) Color(0xFF2E7D32) else Color(0xFFC62828)
            )
        }
        
        if (!isConnected && currentDeviceIP != null) {
            TextButton(
                onClick = onReconnect,
                modifier = Modifier.height(32.dp)
            ) {
                Text("重连", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ParameterListSection(
    parameterStates: List<CarrotParameterState>,
    listState: LazyListState,
    isConnected: Boolean,
    isLoading: Boolean,
    onValueChange: (String, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp)
    ) {
        when {
            isLoading && parameterStates.isEmpty() -> {
                item { LoadingState() }
            }

            parameterStates.isEmpty() -> {
                item {
                        EmptyStateCard(
                            title = "未加载到参数",
                            description = "请点击下方刷新按钮或检查设备状态。"
                        )
                }
            }

            else -> {
                items(parameterStates, key = { it.definition.name }) { state ->
                    ParameterItem(
                        state = state,
                        onValueChange = { value -> onValueChange(state.definition.name, value) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionButtonsRow(
    hasPendingChanges: Boolean,
    isApplying: Boolean,
    isConnected: Boolean,
    onApply: () -> Unit,
    onRefresh: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 应用更改按钮
        Button(
            onClick = onApply,
            enabled = hasPendingChanges && !isApplying && isConnected,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
            modifier = Modifier
                .weight(1f)
                .height(40.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) {
            if (isApplying) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("应用中...", fontSize = 12.sp)
            } else {
                Text("应用更改", fontSize = 12.sp)
            }
        }

        // 刷新按钮
        OutlinedButton(
            onClick = onRefresh,
            enabled = isConnected,
            modifier = Modifier
                .weight(1f)
                .height(40.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("刷新", fontSize = 12.sp)
        }

        // 恢复默认按钮
        OutlinedButton(
            onClick = onReset,
            enabled = hasPendingChanges && isConnected,
            modifier = Modifier
                .weight(1f)
                .height(40.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text("恢复默认", fontSize = 12.sp)
        }
    }
}

@Composable
private fun ParameterItem(
    state: CarrotParameterState,
    onValueChange: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.definition.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1E293B)
                )
                Text(
                    text = state.definition.description,
                    fontSize = 12.sp,
                    color = Color(0xFF64748B),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (state.isModified) {
                AssistChip(
                    onClick = {},
                    label = { Text("待应用") }
                )
            }
        }

            Spacer(modifier = Modifier.height(8.dp))
            when {
                state.definition.options.isNotEmpty() -> ParameterDropdown(state, onValueChange)
                state.definition.isBoolean -> ParameterSwitch(state, onValueChange)
                state.definition.prefersSlider -> ParameterSlider(state, onValueChange)
                else -> ParameterStepper(state, onValueChange)
            }
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = Color(0xFFF1F5F9))
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "当前 ${state.currentValue} ｜ 编辑 ${state.editedValue} ｜ 默认 ${state.definition.defaultValue}",
                fontSize = 11.sp,
                color = Color(0xFF94A3B8)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("DEPRECATION")
@Composable
private fun ParameterDropdown(
    state: CarrotParameterState,
    onValueChange: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = state.definition.options.find { it.value == state.editedValue }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selected?.label ?: state.editedValue.toString(),
            onValueChange = {},
            readOnly = true,
            label = { Text("选择值") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            state.definition.options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        expanded = false
                        onValueChange(option.value)
                    }
                )
            }
        }
    }
}

@Composable
private fun ParameterSwitch(
    state: CarrotParameterState,
    onValueChange: (Int) -> Unit
) {
    val checked = state.editedValue >= (state.definition.maxValue)
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Switch(
            checked = checked,
            onCheckedChange = {
                onValueChange(if (it) state.definition.maxValue else state.definition.minValue)
            }
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(if (checked) "开启" else "关闭")
    }
}

@Composable
private fun ParameterSlider(
    state: CarrotParameterState,
    onValueChange: (Int) -> Unit
) {
    val stepCount = ((state.definition.maxValue - state.definition.minValue) / state.definition.step)
        .coerceAtLeast(1) - 1
    androidx.compose.material3.Slider(
        value = state.editedValue.toFloat(),
        onValueChange = { value ->
            onValueChange(value.roundToInt())
        },
        valueRange = state.definition.minValue.toFloat()..state.definition.maxValue.toFloat(),
        steps = stepCount.coerceAtLeast(0)
    )
}

@Composable
private fun ParameterStepper(
    state: CarrotParameterState,
    onValueChange: (Int) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = { onValueChange(state.editedValue - state.definition.step) }) {
            Text("－", fontSize = 18.sp)
        }
        Text(
            text = state.editedValue.toString(),
            fontSize = 16.sp,
            modifier = Modifier.width(60.dp),
            color = Color(0xFF1E293B)
        )
        TextButton(onClick = { onValueChange(state.editedValue + state.definition.step) }) {
            Text("＋", fontSize = 18.sp)
        }
    }
}

@Composable
private fun EmptyStateCard(
    title: String,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(description, fontSize = 13.sp, color = Color(0xFF94A3B8))
        }
    }
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(12.dp))
        Text("正在加载参数...", color = Color(0xFF546E7A))
    }
}

