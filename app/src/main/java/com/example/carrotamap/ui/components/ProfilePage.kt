package com.example.carrotamap.ui.components

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.draw.shadow
import com.example.carrotamap.R
import com.example.carrotamap.UsageStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 用户数据模型
 */
data class UserData(
    val deviceId: String,
    val usageCount: Int,
    val usageDuration: Float,
    val totalDistance: Float,
    val sponsorAmount: Float = 0f,
    val userType: Int = 0,
    val carModel: String = "",
    val wechatName: String = ""
)

/**
 * 我的页面组件
 */
@Composable
fun ProfilePage(usageStats: UsageStats, deviceId: String) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    // 用户数据状态
    var userData by remember { mutableStateOf<UserData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showUserForm by remember { mutableStateOf(false) }
    
    // 二维码弹窗状态（独立于编辑表单）
    var showQrCodeDialog by remember { mutableStateOf(false) }
    
    // 表单状态
    var carModel by remember { mutableStateOf("") }
    var wechatName by remember { mutableStateOf("") }
    var sponsorAmount by remember { mutableStateOf("") }
    var isUpdating by remember { mutableStateOf(false) }
    
    // 获取用户数据
    LaunchedEffect(deviceId) {
        try {
            // 首先尝试获取用户信息
            userData = fetchUserData(deviceId)
            if (userData != null) {
                // 用户存在，初始化表单数据
                carModel = userData!!.carModel
                wechatName = userData!!.wechatName
                sponsorAmount = userData!!.sponsorAmount.toString()
                android.util.Log.d("ProfilePage", "用户数据获取成功: ${userData!!.deviceId}, 用户类型: ${userData!!.userType}")
            }
            isLoading = false
        } catch (e: Exception) {
            android.util.Log.w("ProfilePage", "获取用户数据失败: ${e.message}")
            
            // 检查是否是404错误（用户不存在）
            if (e.message?.contains("404") == true || e.message?.contains("用户不存在") == true) {
                android.util.Log.i("ProfilePage", "用户不存在，开始自动注册...")
                
                try {
                    // 用户不存在，自动注册为新用户（用户类型1）
                    userData = registerUser(deviceId, usageStats)
                    if (userData != null) {
                        carModel = userData!!.carModel
                        wechatName = userData!!.wechatName
                        sponsorAmount = userData!!.sponsorAmount.toString()
                        android.util.Log.i("ProfilePage", "用户自动注册成功: ${userData!!.deviceId}, 用户类型: ${userData!!.userType}")
                    }
                } catch (registerError: Exception) {
                    android.util.Log.e("ProfilePage", "用户自动注册失败", registerError)
                    errorMessage = "用户自动注册失败: ${registerError.message}"
                }
            } else {
                // 其他错误（网络问题等）
                android.util.Log.e("ProfilePage", "获取用户数据时发生错误", e)
                errorMessage = "获取用户数据失败: ${e.message}"
            }
            isLoading = false
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 页面标题
        Text(
            text = "进群请加微信：CarrotPilot-JX",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1E293B)
        )
        
        // 重要提醒卡片
        ImportantNoticeCard()
        
        // 用户信息卡片
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                Icon(
                    imageVector = Icons.Default.Person,
                        contentDescription = "用户信息",
                        tint = Color(0xFF3B82F6),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                Text(
                        text = "用户信息",
                        fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )
                }
                
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFF3B82F6)
                            )
                        }
                    }
                    errorMessage != null -> {
                Text(
                            text = errorMessage!!,
                            color = Color.Red,
                    fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                    userData != null -> {
                        // 显示用户信息
                        UserInfoDisplay(
                            userData = userData!!,
                            onEditClick = { 
                                showUserForm = true
                                showQrCodeDialog = true  // 打开编辑表单时同时显示二维码弹窗
                            }
                        )
                    }
                }
            }
        }
        
        // 使用统计卡片
        UsageStatsCard(usageStats = usageStats)
        
        // 用户信息编辑表单 - 根据用户类型决定可编辑字段
        if (showUserForm && userData != null) {
            UserFormCard(
                userType = userData!!.userType,
                carModel = carModel,
                wechatName = wechatName,
                sponsorAmount = sponsorAmount,
                onCarModelChange = { carModel = it },
                onWechatNameChange = { wechatName = it },
                onSponsorAmountChange = { sponsorAmount = it },
                isUpdating = isUpdating,
                onSave = {
                    // 触发保存操作
                    isUpdating = true
                },
                onCancel = { 
                    showUserForm = false
                    showQrCodeDialog = false  // 取消编辑时关闭二维码弹窗
                }
            )
        }
    }
    
    // 二维码弹窗 - 独立于编辑表单，可以单独关闭
    if (showQrCodeDialog) {
        AlertDialog(
            onDismissRequest = { showQrCodeDialog = false },
            title = {
                Text(
                    text = "赞助二维码",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B)
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.wepay),
                        contentDescription = "微信支付赞助二维码",
                        modifier = Modifier
                            .size(250.dp)
                            .shadow(4.dp, RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "扫码赞助",
                        fontSize = 14.sp,
                        color = Color(0xFF64748B)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showQrCodeDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showQrCodeDialog = false }
                ) {
                    Text("关闭")
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }
    
    // 处理保存操作 - 根据用户类型决定更新哪些字段
    LaunchedEffect(isUpdating) {
        if (isUpdating && showUserForm && userData != null) {
            try {
                // 用户类型 0 或 1：允许编辑全部资料
                // 用户类型 >= 2：只允许编辑赞助金额
                val finalCarModel = if (userData!!.userType <= 1) carModel else userData!!.carModel
                val finalWechatName = if (userData!!.userType <= 1) wechatName else userData!!.wechatName
                
                updateUserData(
                    deviceId,
                    finalCarModel,
                    finalWechatName,
                    sponsorAmount.toFloatOrNull() ?: 0f,
                    usageStats
                )
                // 刷新用户数据
                userData = fetchUserData(deviceId)
                showUserForm = false
            } catch (e: Exception) {
                errorMessage = "保存失败: ${e.message}"
            } finally {
                isUpdating = false
            }
        }
    }
}

/**
 * 用户信息显示组件
 */
@Composable
private fun UserInfoDisplay(
    userData: UserData,
    onEditClick: () -> Unit
) {
    Column {
        // 设备ID
        InfoRow("设备ID", userData.deviceId)
        
        // 用户类型
        val userTypeText = when (userData.userType) {
            -1 -> "管理员专用"
            0 -> "未知用户"
            1 -> "新用户"
            2 -> "支持者"
            3 -> "赞助者"
            4 -> "铁粉"
            else -> "未知类型"
        }
        InfoRow("用户类型", userTypeText)
        
        // 车型
        InfoRow("车型", userData.carModel.ifEmpty { "未设置" })
        
        // 微信名
        InfoRow("微信名", userData.wechatName.ifEmpty { "未设置" })
        
        // 赞助金额
        InfoRow("赞助金额", "${userData.sponsorAmount}元")
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 编辑按钮（根据用户类型显示不同文本）
        val buttonText = if (userData.userType <= 1) "编辑用户资料" else "编辑赞助金额"
        Button(
            onClick = onEditClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "编辑",
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(buttonText)
        }
    }
}

/**
 * 信息行组件
 */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color(0xFF64748B)
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF1E293B)
        )
    }
}

/**
 * 使用统计卡片组件
 */
@Composable
private fun UsageStatsCard(usageStats: UsageStats) {
        Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "使用统计",
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                    Text(
                    text = "使用统计",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B)
                )
            }
            
            if (usageStats.usageCount > 0 || usageStats.usageDuration > 0 || usageStats.totalDistance > 0) {
                InfoRow("使用次数", "${usageStats.usageCount}次")
                InfoRow("使用时长", "${usageStats.usageDuration}分钟")
                InfoRow("累计距离", "${String.format("%.2f", usageStats.totalDistance)}km")
            } else {
                    Text(
                    text = "暂无使用数据",
                    fontSize = 14.sp,
                    color = Color(0xFF64748B),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}

/**
 * 用户信息编辑表单卡片
 */
@Composable
private fun UserFormCard(
    userType: Int,
    carModel: String,
    wechatName: String,
    sponsorAmount: String,
    onCarModelChange: (String) -> Unit,
    onWechatNameChange: (String) -> Unit,
    onSponsorAmountChange: (String) -> Unit,
    isUpdating: Boolean,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    // 表单验证状态
    var carModelError by remember { mutableStateOf("") }
    var wechatNameError by remember { mutableStateOf("") }
    var sponsorAmountError by remember { mutableStateOf("") }
    
    // 判断是否可以编辑完整资料（用户类型 0 或 1）
    val canEditFullProfile = userType <= 1
    
    // 验证小数位数（最多一位小数）
    fun isValidDecimal(value: String): Boolean {
        if (value.isEmpty()) return true
        val parts = value.split(".")
        return when (parts.size) {
            1 -> true // 整数
            2 -> parts[1].length <= 1 // 最多一位小数
            else -> false // 多个小数点
        }
    }
    
    // 验证表单
    fun validateForm(): Boolean {
        var isValid = true
        
        // 如果可以编辑完整资料，验证车型和微信名
        if (canEditFullProfile) {
            // 验证车型
            if (carModel.isBlank()) {
                carModelError = "车型不能为空"
                isValid = false
            } else if (carModel.length > 50) {
                carModelError = "车型名称过长"
                isValid = false
            } else {
                carModelError = ""
            }
            
            // 验证微信名
            if (wechatName.isBlank()) {
                wechatNameError = "微信名不能为空"
                isValid = false
            } else if (wechatName.length > 50) {
                wechatNameError = "微信名过长"
                isValid = false
            } else {
                wechatNameError = ""
            }
        }
        
        // 验证赞助金额（所有用户类型都需要）
        val amount = sponsorAmount.toFloatOrNull()
        if (sponsorAmount.isBlank()) {
            sponsorAmountError = "赞助金额不能为空"
            isValid = false
        } else if (amount == null) {
            sponsorAmountError = "请输入有效的数字"
            isValid = false
        } else if (amount < 1) {
            sponsorAmountError = "赞助金额不能小于1元"
            isValid = false
        } else if (amount > 200) {
            sponsorAmountError = "赞助金额不能超过200元"
            isValid = false
        } else if (!isValidDecimal(sponsorAmount)) {
            sponsorAmountError = "最多只能输入一位小数"
            isValid = false
        } else {
            sponsorAmountError = ""
        }
        
        return isValid
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F9FF)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = if (canEditFullProfile) "编辑用户资料" else "编辑赞助金额",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B),
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // 车型输入（仅用户类型 0 或 1 可编辑）
            if (canEditFullProfile) {
                OutlinedTextField(
                    value = carModel,
                    onValueChange = onCarModelChange,
                    label = { Text("车型 *") },
                    placeholder = { Text("请输入车型，如：理想L6") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = carModelError.isNotEmpty(),
                    supportingText = if (carModelError.isNotEmpty()) { 
                        { Text(carModelError, color = Color.Red) } 
                    } else null
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // 微信名输入
                OutlinedTextField(
                    value = wechatName,
                    onValueChange = onWechatNameChange,
                    label = { Text("微信名 *") },
                    placeholder = { Text("请输入微信名") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = wechatNameError.isNotEmpty(),
                    supportingText = if (wechatNameError.isNotEmpty()) { 
                        { Text(wechatNameError, color = Color.Red) } 
                    } else null
                )
                
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            // 赞助金额输入（所有用户类型都可编辑）
            OutlinedTextField(
                value = sponsorAmount,
                onValueChange = onSponsorAmountChange,
                label = { Text("赞助金额 *") },
                placeholder = { Text("请输入赞助金额，务必正确输入") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = sponsorAmountError.isNotEmpty(),
                supportingText = if (sponsorAmountError.isNotEmpty()) { 
                    { Text(sponsorAmountError, color = Color.Red) } 
                } else { 
                    { Text("注意正确填写，最多一位小数", color = Color(0xFF64748B)) }
                }
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // 按钮行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF94A3B8))
                ) {
                    Text("取消")
                }
                
                Button(
                    onClick = {
                        if (validateForm()) {
                            onSave()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isUpdating,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                ) {
                    if (isUpdating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White
                        )
                    } else {
                        Text("保存")
                    }
                }
            }
        }
    }
}

/**
 * 获取用户数据
 */
private suspend fun fetchUserData(deviceId: String): UserData = withContext(Dispatchers.IO) {
    try {
        // 使用 HTTPS 协议访问域名
        val url = URL("https://app.mspa.shop/api/user/$deviceId")
        val connection = url.openConnection() as HttpURLConnection
        
        connection.apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", "CP搭子/1.0")
        }
        
        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(response)
            
            if (jsonObject.getBoolean("success")) {
                val data = jsonObject.getJSONObject("data")
                UserData(
                    deviceId = data.getString("device_id"),
                    usageCount = data.getInt("usage_count"),
                    usageDuration = data.getDouble("usage_duration").toFloat(),
                    totalDistance = data.getDouble("total_distance").toFloat(),
                    sponsorAmount = data.optDouble("sponsor_amount", 0.0).toFloat(),
                    userType = data.optInt("user_type", 0),
                    carModel = data.optString("car_model", ""),
                    wechatName = data.optString("wechat_name", "")
                )
            } else {
                throw Exception("API返回失败: ${jsonObject.optString("message", "未知错误")}")
            }
        } else if (responseCode == 404) {
            throw Exception("404")
        } else {
            throw Exception("HTTP错误: $responseCode")
        }
    } catch (e: Exception) {
        android.util.Log.e("ProfilePage", "获取用户数据失败", e)
        throw e
    }
}

/**
 * 注册新用户
 */
private suspend fun registerUser(deviceId: String, usageStats: UsageStats): UserData = withContext(Dispatchers.IO) {
    try {
        android.util.Log.i("ProfilePage", "开始注册新用户: $deviceId")
        
        // 使用 HTTPS 协议访问域名
        val url = URL("https://app.mspa.shop/api/user/register")
        val connection = url.openConnection() as HttpURLConnection
        
        val requestBody = JSONObject().apply {
            put("device_id", deviceId)
            put("usage_count", usageStats.usageCount)
            put("usage_duration", usageStats.usageDuration / 60.0) // 转换为小时
            put("total_distance", usageStats.totalDistance)
        }.toString()
        
        android.util.Log.d("ProfilePage", "注册请求数据: $requestBody")
        
        connection.apply {
            requestMethod = "POST"
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", "CP搭子/1.0")
            doOutput = true
        }
        
        connection.outputStream.use { outputStream ->
            outputStream.write(requestBody.toByteArray())
        }
        
        val responseCode = connection.responseCode
        android.util.Log.d("ProfilePage", "注册响应码: $responseCode")
        
        if (responseCode == HttpURLConnection.HTTP_OK) {
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            android.util.Log.d("ProfilePage", "注册响应数据: $response")
            
            val jsonObject = JSONObject(response)
            val userType = jsonObject.optInt("user_type", 1) // 新用户默认为1
            
            android.util.Log.i("ProfilePage", "新用户注册成功，用户类型: $userType")
            
            // 返回新创建的用户数据
            UserData(
                deviceId = deviceId,
                usageCount = usageStats.usageCount,
                usageDuration = usageStats.usageDuration / 60.0f, // 转换为小时
                totalDistance = usageStats.totalDistance,
                userType = userType, // 确保新用户类型为1
                sponsorAmount = 0f, // 新用户赞助金额为0
                carModel = "", // 新用户车型为空
                wechatName = "" // 新用户微信名为空
            )
        } else {
            val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "无错误信息"
            android.util.Log.e("ProfilePage", "注册失败，响应码: $responseCode, 错误信息: $errorResponse")
            throw Exception("HTTP错误: $responseCode, $errorResponse")
        }
    } catch (e: Exception) {
        android.util.Log.e("ProfilePage", "用户注册失败", e)
        throw e
    }
}

/**
 * 更新用户数据
 */
private suspend fun updateUserData(
    deviceId: String, 
    carModel: String, 
    wechatName: String, 
    sponsorAmount: Float,
    usageStats: UsageStats
): UserData = withContext(Dispatchers.IO) {
    try {
        // 使用 HTTPS 协议访问域名
        val url = URL("https://app.mspa.shop/api/user/update")
        val connection = url.openConnection() as HttpURLConnection
        
        // 根据赞助金额确定用户类型
        val userType = when {
            sponsorAmount < 35 -> 2  // 支持者
            sponsorAmount < 90 -> 3  // 赞助者
            else -> 4               // 铁粉
        }
        
        val requestBody = JSONObject().apply {
            put("device_id", deviceId)
            put("car_model", carModel)
            put("wechat_name", wechatName)
            put("sponsor_amount", sponsorAmount)
            put("user_type", userType)
            // 添加使用统计数据（转换为整数）
            put("usage_count", usageStats.usageCount)
            put("usage_duration", (usageStats.usageDuration / 60.0).toInt()) // 转换为小时（整数）
            put("total_distance", usageStats.totalDistance.toInt()) // 转换为整数公里
        }.toString()
        
        connection.apply {
            requestMethod = "POST"
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", "CP搭子/1.0")
            doOutput = true
        }
        
        connection.outputStream.use { outputStream ->
            outputStream.write(requestBody.toByteArray())
        }
        
        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(response)
            
            if (jsonObject.getBoolean("success")) {
                val data = jsonObject.getJSONObject("data")
                UserData(
                    deviceId = data.getString("device_id"),
                    usageCount = data.getInt("usage_count"),
                    usageDuration = data.getDouble("usage_duration").toFloat(),
                    totalDistance = data.getDouble("total_distance").toFloat(),
                    sponsorAmount = data.getDouble("sponsor_amount").toFloat(),
                    userType = data.getInt("user_type"),
                    carModel = data.getString("car_model"),
                    wechatName = data.getString("wechat_name")
                )
            } else {
                throw Exception("API返回失败: ${jsonObject.optString("message", "未知错误")}")
            }
        } else {
            throw Exception("HTTP错误: $responseCode")
        }
    } catch (e: Exception) {
        android.util.Log.e("ProfilePage", "更新用户数据失败", e)
        throw e
    }
}

/**
 * 重要提醒卡片组件
 * 整合使用规则、安全提示和注意事项
 */
@Composable
private fun ImportantNoticeCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFF7ED) // 浅橙色背景
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 标题行
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "重要提醒",
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "重要提醒",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF92400E)
                )
            }
            
            // 提醒内容
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "• 本应用仅供已赞助用户使用，请如实填写累计赞助金额，切勿虚报。",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = Color(0xFF78350F)
                )
                
                Text(
                    text = "• 系统将严格审核用户信息，异常用户将被统一处理。",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = Color(0xFF78350F)
                )
                
                Text(
                    text = "• 每位用户最多可使用2台设备，且每台设备填写的用户信息必须完全一致。",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = Color(0xFF78350F)
                )
                
                Text(
                    text = "• 自动超车等功能为实验性功能，请谨慎使用，全程保持对车辆的控制权。",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = Color(0xFF78350F)
                )
            }
        }
    }
}
