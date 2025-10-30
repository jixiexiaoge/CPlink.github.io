package com.example.carrotamap

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
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
        private const val PREFS_NAME = "CPlink_Device"
        
        /**
         * 获取设备序列号 - 兼容不同Android版本
         */
        private fun getDeviceSerial(): String {
            return try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    // Android 8.0及以上版本
                    android.os.Build.getSerial()
                } else {
                    // Android 8.0以下版本
                    @Suppress("DEPRECATION")
                    android.os.Build.SERIAL
                }
            } catch (e: Exception) {
                "unknown"
            }
        }
        private const val KEY_DEVICE_ID = "device_id"
        
        // 使用统计相关常量
        private const val KEY_USAGE_COUNT = "usage_count"
        private const val KEY_USAGE_DURATION = "usage_duration"
        private const val KEY_TOTAL_DISTANCE = "total_distance"
        private const val KEY_APP_START_TIME = "app_start_time"
        private const val KEY_LAST_POSITION_LAT = "last_position_lat"
        private const val KEY_LAST_POSITION_LON = "last_position_lon"
        private const val KEY_LAST_UPDATE_TIME = "last_update_time"
        
        // 距离统计优化参数
        private const val MIN_DISTANCE_THRESHOLD = 0.05  // 最小距离阈值：50米（过滤GPS漂移）
        private const val MAX_DISTANCE_THRESHOLD = 2.0   // 最大距离阈值：2公里（过滤GPS跳变）
        private const val MIN_UPDATE_INTERVAL = 5000L    // 最小更新间隔：5秒（避免频繁计算）
        private const val MIN_SPEED_THRESHOLD = 5.0      // 最小速度阈值：5 km/h（判断车辆是否真的在移动）
    }
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * 获取或生成设备ID
     * 使用持久化方案，确保卸载重装后ID不变
     */
    fun getDeviceId(): String {
        val existingId = sharedPreferences.getString(KEY_DEVICE_ID, null)
        
        return if (existingId != null) {
            Log.i(TAG, "📱 使用已存在的设备ID: $existingId")
            existingId
        } else {
            val newId = generatePersistentDeviceId()
            sharedPreferences.edit().putString(KEY_DEVICE_ID, newId).apply()
            Log.i(TAG, "🆕 生成持久化设备ID: $newId")
            newId
        }
    }
    
    /**
     * 生成持久化设备ID
     * 基于Android ID和设备硬件信息，确保卸载重装后ID不变
     */
    private fun generatePersistentDeviceId(): String {
        return try {
            // 获取Android ID（系统级唯一标识）
            val androidId = Settings.Secure.getString(
                context.contentResolver, 
                Settings.Secure.ANDROID_ID
            )
            
            // 获取设备硬件信息
            val deviceInfo = "${android.os.Build.MODEL}_${android.os.Build.MANUFACTURER}_${android.os.Build.DEVICE}_${getDeviceSerial()}"
            
            // 组合生成唯一标识
            val combined = "${androidId}_${deviceInfo}"
            
            // 使用SHA-256生成哈希
            val hash = MessageDigest.getInstance("SHA-256")
                .digest(combined.toByteArray())
                .joinToString("") { "%02x".format(it) }
                .take(12) // 取前12位
            
            val deviceId = hash.uppercase()
            
            Log.d(TAG, "🔧 持久化设备ID生成详情:")
            Log.d(TAG, "   Android ID: $androidId")
            Log.d(TAG, "   设备信息: $deviceInfo")
            Log.d(TAG, "   生成ID: $deviceId")
            
            deviceId
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 持久化设备ID生成失败，使用备用方案: ${e.message}", e)
            // 备用方案：使用设备信息哈希
            generateFallbackDeviceId()
        }
    }
    
    /**
     * 备用设备ID生成方案
     * 当Android ID不可用时使用
     */
    private fun generateFallbackDeviceId(): String {
        return try {
            // 使用设备硬件信息
            val deviceInfo = "${android.os.Build.MODEL}_${android.os.Build.MANUFACTURER}_${android.os.Build.DEVICE}_${getDeviceSerial()}_${android.os.Build.BOARD}_${android.os.Build.HARDWARE}"
            
            val hash = MessageDigest.getInstance("SHA-256")
                .digest(deviceInfo.toByteArray())
                .joinToString("") { "%02x".format(it) }
                .take(12)
            
            val deviceId = hash.uppercase()
            Log.d(TAG, "🔧 备用设备ID生成: $deviceId")
            deviceId
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 备用设备ID生成也失败，使用最终方案: ${e.message}", e)
            // 最终备用方案：时间戳+随机数
            val timestamp = System.currentTimeMillis().toString().takeLast(8)
            val random = Random.nextInt(1000, 9999).toString()
            "$timestamp$random".uppercase()
        }
    }
    
    /**
     * 记录应用启动
     */
    fun recordAppStart() {
        val appStartTime = System.currentTimeMillis()
        
        // 使用commit()确保数据立即写入
        val editor = sharedPreferences.edit()
        editor.putLong(KEY_APP_START_TIME, appStartTime)
        
        // 增加使用次数
        val currentCount = sharedPreferences.getInt(KEY_USAGE_COUNT, 0)
        val newCount = currentCount + 1
        editor.putInt(KEY_USAGE_COUNT, newCount)
        
        val success = editor.commit()
        Log.i(TAG, "📊 记录应用启动，使用次数: $newCount，保存成功: $success")
    }
    
    /**
     * 记录应用使用时长
     */
    fun recordAppUsage() {
        // 从SharedPreferences读取启动时间，确保数据一致性
        val storedStartTime = sharedPreferences.getLong(KEY_APP_START_TIME, 0)
        val currentTime = System.currentTimeMillis()
        
        if (storedStartTime > 0) {
            val sessionDuration = (currentTime - storedStartTime) / (1000 * 60) // 转换为分钟
            
            if (sessionDuration > 0) {
                val totalDuration = sharedPreferences.getLong(KEY_USAGE_DURATION, 0)
                val newTotalDuration = totalDuration + sessionDuration
                
                val success = sharedPreferences.edit()
                    .putLong(KEY_USAGE_DURATION, newTotalDuration)
                    .commit()
                
                Log.i(TAG, "📊 记录使用时长: ${sessionDuration}分钟，累计: ${newTotalDuration}分钟，保存成功: $success")
            } else {
                Log.w(TAG, "⚠️ 使用时长太短，未记录: ${sessionDuration}分钟")
            }
        } else {
            Log.w(TAG, "⚠️ 未找到应用启动时间，无法计算使用时长")
        }
    }
    
    /**
     * 更新位置并计算距离（优化版：多重过滤，防止GPS漂移和跳变）
     * 
     * 优化策略：
     * 1. 时间间隔过滤：至少5秒更新一次
     * 2. 距离阈值过滤：50米-2公里之间才记录
     * 3. 速度合理性检查：速度必须≥5km/h
     * 4. GPS精度检查：过滤明显的异常值
     */
    fun updateLocationAndDistance(latitude: Double, longitude: Double) {
        val currentTime = System.currentTimeMillis()
        val lastUpdateTime = sharedPreferences.getLong(KEY_LAST_UPDATE_TIME, 0L)
        
        // 检查1：时间间隔过滤（避免频繁计算）
        val timeDiff = currentTime - lastUpdateTime
        if (lastUpdateTime != 0L && timeDiff < MIN_UPDATE_INTERVAL) {
            Log.v(TAG, "⏱️ 距离统计：更新间隔太短 (${timeDiff}ms)，跳过")
            return
        }
        
        val lastLat = sharedPreferences.getFloat(KEY_LAST_POSITION_LAT, 0f).toDouble()
        val lastLon = sharedPreferences.getFloat(KEY_LAST_POSITION_LON, 0f).toDouble()
        
        // 如果有上次位置记录，计算距离
        if (lastLat != 0.0 && lastLon != 0.0 && lastUpdateTime != 0L) {
            val distance = calculateDistance(lastLat, lastLon, latitude, longitude)
            
            // 检查2：距离阈值过滤
            if (distance < MIN_DISTANCE_THRESHOLD) {
                Log.v(TAG, "📍 距离统计：移动距离太小 (${String.format("%.3f", distance)}km < ${MIN_DISTANCE_THRESHOLD}km)，可能是GPS漂移，跳过")
                // 更新时间但不更新位置，避免漂移累积
                sharedPreferences.edit()
                    .putLong(KEY_LAST_UPDATE_TIME, currentTime)
                    .apply()
                return
            }
            
            if (distance > MAX_DISTANCE_THRESHOLD) {
                Log.w(TAG, "⚠️ 距离统计：移动距离异常 (${String.format("%.2f", distance)}km > ${MAX_DISTANCE_THRESHOLD}km)，可能是GPS跳变，跳过")
                // 更新位置和时间，但不累计距离
                sharedPreferences.edit()
                    .putFloat(KEY_LAST_POSITION_LAT, latitude.toFloat())
                    .putFloat(KEY_LAST_POSITION_LON, longitude.toFloat())
                    .putLong(KEY_LAST_UPDATE_TIME, currentTime)
                    .apply()
                return
            }
            
            // 检查3：速度合理性（距离/时间）
            val timeInHours = timeDiff / (1000.0 * 60.0 * 60.0) // 转换为小时
            val speed = distance / timeInHours // km/h
            
            if (speed < MIN_SPEED_THRESHOLD) {
                Log.v(TAG, "🐌 距离统计：速度太慢 (${String.format("%.1f", speed)}km/h < ${MIN_SPEED_THRESHOLD}km/h)，可能是缓慢漂移，跳过")
                // 更新时间但不更新位置
                sharedPreferences.edit()
                    .putLong(KEY_LAST_UPDATE_TIME, currentTime)
                    .apply()
                return
            }
            
            // 通过所有检查，记录有效距离
            val currentDistance = sharedPreferences.getFloat(KEY_TOTAL_DISTANCE, 0f)
            val newTotalDistance = currentDistance + distance.toFloat()
            
            sharedPreferences.edit()
                .putFloat(KEY_TOTAL_DISTANCE, newTotalDistance)
                .putFloat(KEY_LAST_POSITION_LAT, latitude.toFloat())
                .putFloat(KEY_LAST_POSITION_LON, longitude.toFloat())
                .putLong(KEY_LAST_UPDATE_TIME, currentTime)
                .apply()
            
            Log.i(TAG, "✅ 距离统计：移动 ${String.format("%.2f", distance)}km，速度 ${String.format("%.1f", speed)}km/h，累计 ${String.format("%.2f", newTotalDistance)}km")
            
        } else {
            // 首次记录位置
            sharedPreferences.edit()
                .putFloat(KEY_LAST_POSITION_LAT, latitude.toFloat())
                .putFloat(KEY_LAST_POSITION_LON, longitude.toFloat())
                .putLong(KEY_LAST_UPDATE_TIME, currentTime)
                .apply()
            Log.i(TAG, "📍 距离统计：初始化位置 (${String.format("%.6f", latitude)}, ${String.format("%.6f", longitude)})")
        }
    }
    
    /**
     * 计算两点间距离（使用Haversine公式）
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371.0 // 地球半径（公里）
        
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
                kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
                kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        
        return earthRadius * c
    }
    
    /**
     * 获取使用统计
     */
    fun getUsageStats(): UsageStats {
        val usageCount = sharedPreferences.getInt(KEY_USAGE_COUNT, 0)
        val usageDuration = sharedPreferences.getLong(KEY_USAGE_DURATION, 0)
        val totalDistance = sharedPreferences.getFloat(KEY_TOTAL_DISTANCE, 0f)
        
        Log.d(TAG, "📊 获取使用统计: 次数=$usageCount, 时长=${usageDuration}分钟, 距离=${totalDistance}km")
        
        return UsageStats(usageCount, usageDuration, totalDistance)
    }
    
    /**
     * 强制刷新当前会话的使用时长
     */
    fun refreshCurrentSessionDuration(): Long {
        val storedStartTime = sharedPreferences.getLong(KEY_APP_START_TIME, 0)
        val currentTime = System.currentTimeMillis()
        
        if (storedStartTime > 0) {
            val currentSessionDuration = (currentTime - storedStartTime) / (1000 * 60) // 转换为分钟
            Log.d(TAG, "📊 当前会话时长: ${currentSessionDuration}分钟")
            return currentSessionDuration
        }
        
        Log.w(TAG, "⚠️ 无法获取当前会话时长，启动时间未记录")
        return 0
    }
    
    /**
     * 获取总使用时长（包括当前会话）
     */
    fun getTotalUsageDuration(): Long {
        val storedDuration = sharedPreferences.getLong(KEY_USAGE_DURATION, 0)
        val currentSessionDuration = refreshCurrentSessionDuration()
        val totalDuration = storedDuration + currentSessionDuration
        
        Log.d(TAG, "📊 总使用时长: 已保存=${storedDuration}分钟, 当前会话=${currentSessionDuration}分钟, 总计=${totalDuration}分钟")
        
        return totalDuration
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        Log.i(TAG, "🧹 清理设备管理器资源")
        // 目前没有需要清理的资源
    }
}

/**
 * 使用统计数据类
 */
data class UsageStats(
    val usageCount: Int,        // 使用次数
    val usageDuration: Long,   // 使用时长（分钟）
    val totalDistance: Float    // 累计距离（公里）
)

