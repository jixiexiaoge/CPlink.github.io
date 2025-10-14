package com.example.carrotamap

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.window.Dialog

/**
 * 指纹选择对话框
 * 包含厂商选择、车型选择和指纹显示
 */
@Composable
fun FingerprintSelectionDialog(
    onDismiss: () -> Unit,
    onConfirm: (VehicleInfo) -> Unit,
    deviceId: String
) {
    val context = LocalContext.current
    val vehicleInfoManager = remember { VehicleInfoManager(context) }
    
    var selectedManufacturer by remember { mutableStateOf<Manufacturer?>(null) }
    var selectedModel by remember { mutableStateOf<Model?>(null) }
    var currentStep by remember { mutableStateOf(SelectionStep.MANUFACTURER) }
    
    val manufacturers = remember { vehicleInfoManager.getAllManufacturers() }
    val models = remember(selectedManufacturer) {
        selectedManufacturer?.let { vehicleInfoManager.getModelsByManufacturer(it.name) } ?: emptyList()
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // 标题栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "选择车辆指纹",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 设备ID显示
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Phone,
                            contentDescription = "设备ID",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "设备ID: $deviceId",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 步骤指示器
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StepIndicator(
                        step = 1,
                        title = "厂商",
                        isActive = currentStep == SelectionStep.MANUFACTURER,
                        isCompleted = selectedManufacturer != null
                    )
                    StepIndicator(
                        step = 2,
                        title = "车型",
                        isActive = currentStep == SelectionStep.MODEL,
                        isCompleted = selectedModel != null
                    )
                    StepIndicator(
                        step = 3,
                        title = "指纹",
                        isActive = currentStep == SelectionStep.FINGERPRINT,
                        isCompleted = selectedModel != null
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 内容区域
                when (currentStep) {
                    SelectionStep.MANUFACTURER -> {
                        ManufacturerSelectionStep(
                            manufacturers = manufacturers,
                            onManufacturerSelected = { manufacturer ->
                                selectedManufacturer = manufacturer
                                selectedModel = null
                                currentStep = SelectionStep.MODEL
                            }
                        )
                    }
                    SelectionStep.MODEL -> {
                        ModelSelectionStep(
                            manufacturer = selectedManufacturer!!,
                            models = models,
                            onModelSelected = { model ->
                                selectedModel = model
                                currentStep = SelectionStep.FINGERPRINT
                            },
                            onBack = {
                                currentStep = SelectionStep.MANUFACTURER
                                selectedManufacturer = null
                            }
                        )
                    }
                    SelectionStep.FINGERPRINT -> {
                        FingerprintConfirmationStep(
                            manufacturer = selectedManufacturer!!,
                            model = selectedModel!!,
                            onBack = {
                                currentStep = SelectionStep.MODEL
                                selectedModel = null
                            },
                            onConfirm = {
                                val vehicleInfo = VehicleInfo(
                                    manufacturer = selectedManufacturer!!.name,
                                    model = selectedModel!!.name,
                                    fingerprint = selectedModel!!.fingerprint
                                )
                                onConfirm(vehicleInfo)
                            }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 底部按钮区域
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 跳过按钮
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "跳过")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("跳过")
                    }
                    
                    // 确认按钮（仅在完成选择后显示）
                    if (currentStep == SelectionStep.FINGERPRINT) {
                        Button(
                            onClick = {
                                val vehicleInfo = VehicleInfo(
                                    manufacturer = selectedManufacturer!!.name,
                                    model = selectedModel!!.name,
                                    fingerprint = selectedModel!!.fingerprint
                                )
                                onConfirm(vehicleInfo)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "确认")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("确认选择")
                        }
                    } else {
                        // 占位符，保持按钮对齐
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * 步骤指示器组件
 */
@Composable
private fun StepIndicator(
    step: Int,
    title: String,
    isActive: Boolean,
    isCompleted: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    color = when {
                        isCompleted -> MaterialTheme.colorScheme.primary
                        isActive -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    },
                    shape = RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isCompleted) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "完成",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    text = step.toString(),
                    color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = title,
            fontSize = 12.sp,
            color = if (isActive || isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            fontWeight = if (isActive || isCompleted) FontWeight.Medium else FontWeight.Normal
        )
    }
}

/**
 * 厂商选择步骤
 */
@Composable
private fun ManufacturerSelectionStep(
    manufacturers: List<Manufacturer>,
    onManufacturerSelected: (Manufacturer) -> Unit
) {
    Column {
        Text(
            text = "请选择车辆厂商",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(manufacturers) { manufacturer ->
                ManufacturerItem(
                    manufacturer = manufacturer,
                    onClick = { onManufacturerSelected(manufacturer) }
                )
            }
        }
    }
}

/**
 * 厂商项目组件
 */
@Composable
private fun ManufacturerItem(
    manufacturer: Manufacturer,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = manufacturer.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${manufacturer.models.size} 款车型",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "选择",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * 车型选择步骤
 */
@Composable
private fun ModelSelectionStep(
    manufacturer: Manufacturer,
    models: List<Model>,
    onModelSelected: (Model) -> Unit,
    onBack: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = "选择 ${manufacturer.name} 车型",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(models) { model ->
                ModelItem(
                    model = model,
                    onClick = { onModelSelected(model) }
                )
            }
        }
    }
}

/**
 * 车型项目组件
 */
@Composable
private fun ModelItem(
    model: Model,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "指纹: ${model.fingerprint}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "选择",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * 指纹确认步骤
 */
@Composable
private fun FingerprintConfirmationStep(
    manufacturer: Manufacturer,
    model: Model,
    onBack: () -> Unit,
    onConfirm: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = "确认车辆信息",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 车辆信息卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                InfoRow("厂商", manufacturer.name)
                InfoRow("车型", model.name)
                InfoRow("指纹", model.fingerprint, isMonospace = true)
            }
        }
        
        // 移除这里的确认按钮，使用底部的按钮
    }
}

/**
 * 信息行组件
 */
@Composable
private fun InfoRow(
    label: String,
    value: String,
    isMonospace: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontFamily = if (isMonospace) androidx.compose.ui.text.font.FontFamily.Monospace else MaterialTheme.typography.bodyMedium.fontFamily,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 选择步骤枚举
 */
private enum class SelectionStep {
    MANUFACTURER,
    MODEL,
    FINGERPRINT
}
