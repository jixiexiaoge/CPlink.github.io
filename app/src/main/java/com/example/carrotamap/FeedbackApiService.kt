package com.example.carrotamap

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * 反馈API服务类
 * 用于提交用户反馈到服务器
 */
class FeedbackApiService(private val context: Context) {
    
    companion object {
        private const val TAG = "FeedbackApiService"
        private const val BASE_URL = "https://app.mspa.shop"
        private const val API_ENDPOINT = "/api/feedback"
    }
    
    // 创建HTTP客户端
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    /**
     * 提交反馈数据到服务器
     * @param deviceId 设备唯一ID
     * @param feedback 反馈内容
     * @param imageUris 图片URI列表（最多2张）
     * @return Pair<Boolean, String> 成功状态和消息
     */
    suspend fun submitFeedback(
        deviceId: String,
        feedback: String,
        imageUris: List<Uri>? = null
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "开始提交反馈: deviceId=$deviceId, feedback=$feedback")
            
            // 构建multipart请求体
            val formBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("id", deviceId)
                .addFormDataPart("time", getCurrentTime())
                .addFormDataPart("feedback", feedback)
            
            // 添加图片文件
            imageUris?.take(2)?.forEach { uri ->
                try {
                    val imageFile = uriToFile(uri)
                    if (imageFile != null && imageFile.exists()) {
                        val requestFile = imageFile.asRequestBody("image/*".toMediaType())
                        formBuilder.addFormDataPart(
                            "images",
                            imageFile.name,
                            requestFile
                        )
                        Log.i(TAG, "添加图片: ${imageFile.name}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "处理图片失败: ${e.message}")
                }
            }
            
            val requestBody = formBuilder.build()
            
            // 构建请求
            val request = Request.Builder()
                .url("$BASE_URL$API_ENDPOINT")
                .post(requestBody)
                .build()
            
            Log.i(TAG, "发送请求到: $BASE_URL$API_ENDPOINT")
            
            // 执行请求
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            
            Log.i(TAG, "服务器响应: code=${response.code}, body=$responseBody")
            
            if (response.isSuccessful) {
                Pair(true, "反馈提交成功")
            } else {
                Pair(false, "提交失败: HTTP ${response.code} - $responseBody")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "提交反馈异常: ${e.message}", e)
            Pair(false, "网络错误: ${e.message}")
        }
    }
    
    /**
     * 将URI转换为File对象
     */
    private fun uriToFile(uri: Uri): File? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Log.e(TAG, "无法打开输入流")
                return null
            }
            
            // 创建临时文件
            val tempFile = File(context.cacheDir, "temp_image_${System.currentTimeMillis()}.jpg")
            val outputStream = FileOutputStream(tempFile)
            
            // 复制数据
            inputStream.copyTo(outputStream)
            
            inputStream.close()
            outputStream.close()
            
            Log.i(TAG, "图片保存到: ${tempFile.absolutePath}")
            tempFile
            
        } catch (e: Exception) {
            Log.e(TAG, "URI转File失败: ${e.message}", e)
            null
        }
    }
    
    /**
     * 获取当前时间戳字符串
     */
    private fun getCurrentTime(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date())
    }
    
}
