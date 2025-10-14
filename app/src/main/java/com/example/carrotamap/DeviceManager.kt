package com.example.carrotamap

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.*
import java.security.MessageDigest
import kotlin.random.Random

/**
 * 设备管理器
 * 负责设备ID生成、存储和倒计时管理
 */
class DeviceManager(private val context: Context) {
    
    companion object {
        private const val TAG = "DeviceManager"
        private const val PREFS_NAME = "CarrotAmap_Device"
        private const val KEY_DEVICE_ID = "device_id"
        private const val DEFAULT_COUNTDOWN_SECONDS = 850
    }
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // 倒计时相关
    private var countdownJob: Job? = null
    private var _remainingSeconds = DEFAULT_COUNTDOWN_SECONDS
    private var _isCountdownActive = false
    
    // 倒计时状态回调
    private var onCountdownUpdate: ((Int) -> Unit)? = null
    private var onCountdownFinished: (() -> Unit)? = null
    
    /**
     * 获取或生成设备ID
     */
    fun getDeviceId(): String {
        val existingId = sharedPreferences.getString(KEY_DEVICE_ID, null)
        
        return if (existingId != null) {
            Log.i(TAG, "📱 使用已存在的设备ID: $existingId")
            existingId
        } else {
            val newId = generateDeviceId()
            sharedPreferences.edit().putString(KEY_DEVICE_ID, newId).apply()
            Log.i(TAG, "🆕 生成新设备ID: $newId")
            newId
        }
    }
    
    /**
     * 生成唯一设备ID
     * 使用时间戳+随机数+设备信息哈希的方式生成8-12位字符
     */
    private fun generateDeviceId(): String {
        try {
            // 获取当前时间戳的后6位
            val timestamp = System.currentTimeMillis().toString().takeLast(6)
            
            // 生成3位随机数
            val random = Random.nextInt(100, 999).toString()
            
            // 获取设备信息并生成哈希
            val deviceInfo = "${android.os.Build.MODEL}_${android.os.Build.MANUFACTURER}_${android.os.Build.DEVICE}"
            val hash = MessageDigest.getInstance("MD5")
                .digest(deviceInfo.toByteArray())
                .joinToString("") { "%02x".format(it) }
                .take(3) // 取前3位
            
            // 组合生成12位ID: 6位时间戳 + 3位随机数 + 3位哈希
            val deviceId = "$timestamp$random$hash".uppercase()
            
            Log.d(TAG, "🔧 设备ID生成详情: timestamp=$timestamp, random=$random, hash=$hash")
            return deviceId
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 设备ID生成失败，使用备用方案: ${e.message}", e)
            // 备用方案：时间戳+随机数
            val timestamp = System.currentTimeMillis().toString().takeLast(8)
            val random = Random.nextInt(1000, 9999).toString()
            return "$timestamp$random".uppercase()
        }
    }
    
    /**
     * 启动倒计时
     */
    fun startCountdown(
        initialSeconds: Int = DEFAULT_COUNTDOWN_SECONDS,
        onUpdate: (Int) -> Unit,
        onFinished: () -> Unit
    ) {
        Log.i(TAG, "⏰ 启动倒计时: ${initialSeconds}秒")
        
        // 停止现有倒计时
        stopCountdown()
        
        _remainingSeconds = initialSeconds
        _isCountdownActive = true
        onCountdownUpdate = onUpdate
        onCountdownFinished = onFinished
        
        countdownJob = CoroutineScope(Dispatchers.Main).launch {
            try {
                while (_remainingSeconds > 0 && _isCountdownActive) {
                    onCountdownUpdate?.invoke(_remainingSeconds)
                    
                    // 倒计时低于60秒时增加日志频率
                    if (_remainingSeconds <= 60) {
                        //Log.w(TAG, "⚠️ 倒计时警告: 剩余${_remainingSeconds}秒")
                    } else if (_remainingSeconds % 60 == 0) {
                        //Log.i(TAG, "⏰ 倒计时状态: 剩余${_remainingSeconds}秒")
                    }
                    
                    delay(1000) // 等待1秒
                    _remainingSeconds--
                }
                
                if (_isCountdownActive && _remainingSeconds <= 0) {
                    //Log.w(TAG, "🚨 倒计时结束，触发应用关闭")
                    onCountdownFinished?.invoke()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ 倒计时异常: ${e.message}", e)
                // 异常时也触发关闭，确保安全
                if (_isCountdownActive) {
                    onCountdownFinished?.invoke()
                }
            }
        }
    }
    
    /**
     * 停止倒计时
     */
    fun stopCountdown() {
        Log.i(TAG, "⏹️ 停止倒计时")
        _isCountdownActive = false
        countdownJob?.cancel()
        countdownJob = null
    }
    
    /**
     * 获取剩余秒数
     */
    fun getRemainingSeconds(): Int = _remainingSeconds
    
    /**
     * 是否正在倒计时
     */
    fun isCountdownActive(): Boolean = _isCountdownActive
    
    /**
     * 清理资源
     */
    fun cleanup() {
        Log.i(TAG, "🧹 清理设备管理器资源")
        stopCountdown()
    }
}

/**
 * 位置上报管理器
 * 负责设备位置的自动上报和倒计时管理
 */
class LocationReportManager(
    private val context: Context,
    private val networkManager: NetworkManager,
    private val deviceManager: DeviceManager
) {
    
    companion object {
        private const val TAG = "LocationReportManager"
        private const val REPORT_TIMEOUT_MS = 5000L
    }
    
    /**
     * 执行位置上报
     */
    suspend fun performLocationReport(
        latitude: Double,
        longitude: Double,
        onCountdownUpdate: (Int) -> Unit,
        onAppShouldClose: () -> Unit,
        manufacturer: String? = null,
        model: String? = null,
        fingerprint: String? = null
    ) {
        Log.i(TAG, "🚀 开始执行位置上报")
        
        val deviceId = deviceManager.getDeviceId()
        
        try {
            // 使用withTimeout确保5秒内完成
            val countdownSeconds = withTimeout(REPORT_TIMEOUT_MS) {
                val result = networkManager.sendDeviceLocationReport(
                    deviceId, 
                    latitude, 
                    longitude,
                    manufacturer,
                    model,
                    fingerprint
                )
                result.getOrElse {
                    Log.w(TAG, "⚠️ 位置上报失败，使用默认倒计时")
                    850
                }
            }
            
            Log.i(TAG, "✅ 位置上报完成，启动倒计时: ${countdownSeconds}秒")
            
            // 启动倒计时
            deviceManager.startCountdown(
                initialSeconds = countdownSeconds,
                onUpdate = onCountdownUpdate,
                onFinished = {
                    Log.w(TAG, "🚨 倒计时结束，触发应用关闭")
                    onAppShouldClose()
                }
            )
            
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "⏰ 位置上报超时，启动默认倒计时")
            deviceManager.startCountdown(
                initialSeconds = 850,
                onUpdate = onCountdownUpdate,
                onFinished = onAppShouldClose
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ 位置上报异常: ${e.message}", e)
            deviceManager.startCountdown(
                initialSeconds = 850,
                onUpdate = onCountdownUpdate,
                onFinished = onAppShouldClose
            )
        }
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        Log.i(TAG, "🧹 清理位置上报管理器资源")
        deviceManager.cleanup()
    }
}
