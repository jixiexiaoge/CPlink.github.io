package com.example.carrotamap

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONArray
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

/**
 * 赞助弹窗组件
 * 显示：排行榜（5行假数据）+ 二维码图片 + 金额下拉与提交按钮
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SponsorshipDialog(
    isVisible: Boolean,
    deviceId: String,
    onDismiss: () -> Unit
) {
    if (!isVisible) return

    // 金额选项（字符串显示，实际上传仍为字符串）
    val moneyOptions = listOf("6.6", "18.8", "38.8", "66.6", "88.8", "188")
    var selectedMoney by remember { mutableStateOf(moneyOptions.first()) }
    var isSubmitting by remember { mutableStateOf(false) }
    var submitMessage by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    if (!isSubmitting) {
                        isSubmitting = true
                        submitMessage = ""
                        // 在IO线程提交HTTP请求
                        CoroutineScope(Dispatchers.IO).launch {
                            val ok = postSponsorship(deviceId, selectedMoney)
                            submitMessage = if (ok) "提交成功，感谢支持！" else "提交失败，请稍后重试"
                            isSubmitting = false
                        }
                    }
                },
                enabled = !isSubmitting
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("登记上榜")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("下次一定") }
        },
        title = {
            Text(text = "赞助-持续开发的动力！", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 1) 排行榜（5行假数据）
                //Text(text = "赞助排行榜", style = MaterialTheme.typography.labelMedium)
                // 实时数据状态（device_id 与 赞助内容）
                var donationsAll by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
                var currentPage by remember { mutableStateOf(0) }
                val pageSize = 10
                val totalPages = maxOf(1, (donationsAll.size + pageSize - 1) / pageSize)
                val donations: List<Pair<String, String>> = if (donationsAll.isEmpty()) emptyList() else donationsAll.drop(currentPage * pageSize).take(pageSize)
                var loading by remember { mutableStateOf(true) }
                var loadError by remember { mutableStateOf("") }
                LaunchedEffect(Unit) {
                    loading = true
                    loadError = ""
                    try {
                        val result = kotlinx.coroutines.withContext(Dispatchers.IO) { fetchDonations() }
                        Log.i("SponsorshipDialog", "获取到赞助条目数量: ${result.size}")
                        donationsAll = result.map { d ->
                            val who = d.deviceId?.takeIf { it.isNotBlank() } ?: "匿名"
                            val label = mapAmountToLabel(d.amount)
                            who to label
                        }
                        currentPage = 0
                        if (donationsAll.isEmpty()) {
                            Log.w("SponsorshipDialog", "捐赠列表为空，UI将显示占位行")
                        }
                    } catch (e: Exception) {
                        loadError = e.message ?: "加载失败"
                        Log.e("SponsorshipDialog", "获取赞助数据失败: ${e.message}", e)
                    } finally {
                        loading = false
                    }
                }
                Card {
                    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                            Text("编号", modifier = Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("赞助内容", modifier = Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        if (loading) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("加载中…", fontSize = 12.sp)
                            }
                        } else if (loadError.isNotEmpty()) {
                            Text("加载失败：$loadError", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        } else {
                            (if (donations.isEmpty()) listOf("-" to "-") else donations).forEach { (id, label) ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                    Text(id, modifier = Modifier.weight(1f), fontSize = 12.sp)
                                    Text(label, modifier = Modifier.weight(1f), fontSize = 12.sp)
                                }
                            }
                            // 分页控制
                            if (donationsAll.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(onClick = { if (currentPage > 0) currentPage -= 1 }, enabled = currentPage > 0) {
                                        Text("上一页")
                                    }
                                    Text("${currentPage + 1} / $totalPages", fontSize = 12.sp)
                                    TextButton(onClick = { if (currentPage < totalPages - 1) currentPage += 1 }, enabled = currentPage < totalPages - 1) {
                                        Text("下一页")
                                    }
                                }
                            }
                        }
                    }
                }

                // 2) 二维码图片（从 drawable/wepay.png 读取）
                Text(text = "说明：请先使用上方二维码完成微信赞助，然后在下方选择金额并提交登记（仅需登记一次）", style = MaterialTheme.typography.labelMedium)
                Card {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        // 这里直接使用资源引用
                        Image(
                            painter = painterResource(id = R.drawable.wepay),
                            contentDescription = "赞助二维码",
                            modifier = Modifier.size(220.dp)
                        )
                    }
                }

                // 3) 金额下拉 + 提交结果提示
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "选择金额：", fontSize = 12.sp)
                    Spacer(Modifier.width(8.dp))
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                        OutlinedTextField(
                            value = selectedMoney,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("金额") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor().width(140.dp)
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            moneyOptions.forEach { opt ->
                                DropdownMenuItem(text = { Text(opt) }, onClick = {
                                    selectedMoney = opt
                                    expanded = false
                                })
                            }
                        }
                    }
                }

                if (submitMessage.isNotEmpty()) {
                    Text(text = submitMessage, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                }

                // 设备ID展示（只读）
                Text(text = "当前设备ID：$deviceId", fontSize = 11.sp)
            }
        }
    )
}

/** 数据模型：接口返回的单条捐赠记录 */
data class DonationDto(val deviceId: String?, val amount: Double)

/** 从接口获取捐赠数据（最多取前若干条） */
private fun fetchDonations(): List<DonationDto> {
    return try {
        val url = URL("https://app.mspa.shop/api/donations")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("Accept", "application/json")
        }
        val code = conn.responseCode
        if (code == HttpURLConnection.HTTP_OK) {
            val text = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
            conn.disconnect()
            val root = JSONObject(text)
            val arr = root.optJSONArray("data") ?: JSONArray()
            val list = mutableListOf<DonationDto>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val amount = o.optDouble("amount", 0.0)
                val deviceId = o.optString("device_id", "") ?: ""
                list.add(DonationDto(deviceId, amount))
            }
            list
        } else {
            conn.disconnect()
            emptyList()
        }
    } catch (e: Exception) {
        Log.e("SponsorshipDialog", "GET失败: ${e.message}", e)
        emptyList()
    }
}

/**
 * 执行HTTP POST：发送 id/money/time 到指定URL
 * 返回：true 表示成功（2xx），否则失败
 */
private fun postSponsorship(deviceId: String, money: String): Boolean {
    return try {
        val url = URL("https://defaulte3f0b629b0b043238be4e8c5116552.ba.environment.api.powerplatform.com:443/powerautomate/automations/direct/workflows/cca13a7df36a4a3c83ec429667266e5d/triggers/manual/paths/invoke?api-version=1&sp=%2Ftriggers%2Fmanual%2Frun&sv=1.0&sig=ziCc9xEKsb7cDV8woSoteEzN954BALOkxs2hxmustCo")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 15000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Accept", "application/json")
        }

        // 生成时间字符串
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val now = sdf.format(Date())

        // 构造JSON
        val body = JSONObject().apply {
            put("id", deviceId)
            put("money", money)
            put("time", now)
        }.toString()

        BufferedOutputStream(conn.outputStream).use { it.write(body.toByteArray(Charsets.UTF_8)) }

        val code = conn.responseCode
        val success = code in 200..299
        val resp = try {
            BufferedReader(InputStreamReader(if (success) conn.inputStream else conn.errorStream)).use { br ->
                br.readText()
            }
        } catch (e: Exception) { "" }
        Log.i("SponsorshipDialog", "POST code=$code, resp=$resp")
        conn.disconnect()
        success
    } catch (e: Exception) {
        Log.e("SponsorshipDialog", "POST失败: ${e.message}", e)
        false
    }
}

/** 根据金额映射赞助内容标签（含容差匹配） */
private fun mapAmountToLabel(amount: Double): String {
    fun near(target: Double, tol: Double = 0.05) = kotlin.math.abs(amount - target) <= tol
    return when {
        near(6.6) -> "一瓶快乐水"
        near(18.8) || near(18.88) -> "一个大鸡腿"
        near(38.8) -> "豪华外卖"
        near(66.6) -> "一包华子"
        near(88.8) -> "大猪蹄"
        near(188.0) -> "请吃饭了"
        else -> "赞助￥${"%.2f".format(amount)} 根棒棒糖"
    }
}
