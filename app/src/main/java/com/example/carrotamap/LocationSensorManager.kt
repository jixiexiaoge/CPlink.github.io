package com.example.carrotamap

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.core.app.ActivityCompat

/**
 * 位置和传感器管理器
 * 负责GPS位置更新、传感器监听和相关数据处理
 */
class LocationSensorManager(
    private val context: Context,
    private val carrotManFields: MutableState<CarrotManFields>
) : SensorEventListener {
    
    companion object {
        private const val TAG = "LocationSensorManager"
    }

    // 位置和传感器管理器
    private lateinit var locationManager: LocationManager
    private lateinit var sensorManager: SensorManager
    private var rotationVectorSensor: Sensor? = null
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    
    // 传感器数据存储
    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    // GPS位置变化监听器
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            try {
                val currentTime = System.currentTimeMillis()

                carrotManFields.value = carrotManFields.value.copy(
                    // 更新手机GPS坐标到所有相关字段
                    vpPosPointLat = location.latitude,
                    vpPosPointLon = location.longitude,
                    vpPosPointLatNavi = location.latitude,  // 导航模式坐标
                    vpPosPointLonNavi = location.longitude, // 导航模式坐标

                    // 协议标准位置字段同步
                    xPosLat = location.latitude,            // 协议标准纬度字段
                    xPosLon = location.longitude,           // 协议标准经度字段
                    xPosAngle = if (location.hasBearing()) location.bearing.toDouble() else carrotManFields.value.xPosAngle,
                    xPosSpeed = if (location.hasSpeed()) location.speed * 3.6 else carrotManFields.value.xPosSpeed, // km/h

                    // 新增GPS字段 - 兼容局域网传输
                    latitude = location.latitude,           // 备用GPS纬度
                    longitude = location.longitude,         // 备用GPS经度
                    accuracy = location.accuracy.toDouble(), // GPS精度
                    gps_speed = if (location.hasSpeed()) location.speed.toDouble() else 0.0, // GPS速度 (m/s)

                    // 时间戳和协议字段
                    epochTime = location.time / 1000,       // Unix时间戳 (秒)
                    heading = if (location.hasBearing()) location.bearing.toDouble() else carrotManFields.value.heading, // 方向角

                    // 更新GPS相关信息
                    nPosSpeed = if (location.hasSpeed()) location.speed * 3.6 else carrotManFields.value.nPosSpeed, // 转换为km/h
                    nPosAngle = if (location.hasBearing()) location.bearing.toDouble() else carrotManFields.value.nPosAngle,
                    nPosAnglePhone = if (location.hasBearing()) location.bearing.toDouble() else carrotManFields.value.nPosAnglePhone,

                    // GPS精度和状态
                    gps_accuracy_phone = location.accuracy.toDouble(),
                    gps_valid = true,

                    // 时间戳更新
                    last_update_gps_time = location.time,
                    last_update_gps_time_phone = location.time,
                    lastUpdateTime = currentTime
                )

                // 🔍 详细GPS数据日志
                //Log.i(TAG, "🌍 GPS位置更新接收:")
                //Log.i(TAG, "  📍 坐标: lat=${String.format("%.6f", location.latitude)}, lon=${String.format("%.6f", location.longitude)}")
                //Log.i(TAG, "  🚀 速度: ${if (location.hasSpeed()) "${String.format("%.1f", location.speed * 3.6)} km/h" else "无速度数据"}")
                //Log.i(TAG, "  🧭 方向: ${if (location.hasBearing()) "${String.format("%.1f", location.bearing)}°" else "无方向数据"}")
                //Log.i(TAG, "  📡 精度: ${location.accuracy}m")
                //Log.i(TAG, "  🔧 提供者: ${location.provider}")
                //Log.i(TAG, "  ⏰ 时间: ${System.currentTimeMillis() - location.time}ms前")

                // 验证坐标有效性
                if (location.latitude == 0.0 && location.longitude == 0.0) {
                    Log.w(TAG, "⚠️ 接收到无效GPS坐标 (0,0)，跳过更新")
                    return
                }

                // 更新后验证
                Log.i(TAG, "✅ GPS字段更新完成:")
                //Log.i(TAG, "  📍 vpPosPointLat: ${carrotManFields.value.vpPosPointLat} -> ${location.latitude}")
                //Log.i(TAG, "  📍 vpPosPointLon: ${carrotManFields.value.vpPosPointLon} -> ${location.longitude}")
                //Log.i(TAG, "  📍 vpPosPointLatNavi: ${carrotManFields.value.vpPosPointLatNavi} -> ${location.latitude}")
                //Log.i(TAG, "  📍 vpPosPointLonNavi: ${carrotManFields.value.vpPosPointLonNavi} -> ${location.longitude}")
                //Log.i(TAG, "  🔄 gps_valid: ${carrotManFields.value.gps_valid} -> true")

            } catch (e: Exception) {
                Log.e(TAG, "GPS位置更新失败: ${e.message}", e)
            }
        }
        
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
            // 使用现代的LocationManager API替代过时的LocationProvider常量
            val statusText = when(status) {
                2 -> "可用"           // LocationProvider.AVAILABLE (2)
                0 -> "服务外"         // LocationProvider.OUT_OF_SERVICE (0)
                1 -> "暂时不可用"     // LocationProvider.TEMPORARILY_UNAVAILABLE (1)
                else -> "未知($status)"
            }
            Log.i(TAG, "📡 位置提供者状态变化: $provider -> $statusText")
            
            // 根据状态更新位置服务可用性
            when(status) {
                2 -> { // 可用
                    Log.d(TAG, "🟢 GPS提供者 $provider 现在可用")
                }
                0, 1 -> { // 不可用
                    Log.d(TAG, "🔴 GPS提供者 $provider 不可用: $statusText")
                }
            }
        }

        override fun onProviderEnabled(provider: String) {
            Log.i(TAG, "✅ 位置提供者已启用: $provider")
            checkLocationProviderStatus()
        }

        override fun onProviderDisabled(provider: String) {
            Log.w(TAG, "⚠️ 位置提供者已禁用: $provider")
            checkLocationProviderStatus()
        }
    }

    /**
     * 初始化传感器系统
     */
    fun initializeSensors() {
        Log.i(TAG, "🧭 初始化传感器系统...")
        
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        
        rotationVectorSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        magnetometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        
        Log.i(TAG, "✅ 传感器系统初始化完成")
    }

    /**
     * 启动位置更新服务
     */
    fun startLocationUpdates() {
        Log.i(TAG, "📍 启动GPS位置更新服务...")

        // 首先检查位置提供者状态
        checkLocationProviderStatus()

        try {
            // 检查位置权限
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

                // 启用GPS定位 - 高精度
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        1000L, // 1秒更新一次
                        1f,    // 1米移动距离触发更新
                        locationListener
                    )
                    Log.i(TAG, "✅ GPS定位已启动")
                } else {
                    Log.w(TAG, "⚠️ GPS提供者未启用，跳过GPS定位")
                }

                // 启用网络定位 - 作为备选方案
                if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        2000L, // 2秒更新一次（网络定位频率稍低）
                        5f,    // 5米移动距离触发更新
                        locationListener
                    )
                    Log.i(TAG, "✅ 网络定位已启动")
                } else {
                    Log.w(TAG, "⚠️ 网络提供者未启用，跳过网络定位")
                }

                Log.i(TAG, "✅ 位置更新服务启动完成")

                // 立即请求一次位置更新来测试
                requestImmediateLocationUpdate()

            } else {
                Log.w(TAG, "⚠️ 缺少位置权限，无法启动位置更新")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 启动位置更新失败: ${e.message}", e)
        }
    }

    /**
     * 立即请求一次位置更新来测试GPS功能
     */
    private fun requestImmediateLocationUpdate() {
        try {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                // 尝试获取最后已知位置
                val lastGpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                val lastNetworkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

                val bestLocation = when {
                    lastGpsLocation != null && lastNetworkLocation != null -> {
                        if (lastGpsLocation.time > lastNetworkLocation.time) lastGpsLocation else lastNetworkLocation
                    }
                    lastGpsLocation != null -> lastGpsLocation
                    lastNetworkLocation != null -> lastNetworkLocation
                    else -> null
                }

                bestLocation?.let { location ->
                    Log.i(TAG, "🎯 使用最后已知位置进行立即更新:")
                    Log.i(TAG, "  📍 坐标: lat=${String.format("%.6f", location.latitude)}, lon=${String.format("%.6f", location.longitude)}")
                    Log.i(TAG, "  📡 精度: ${location.accuracy}m")
                    Log.i(TAG, "  🔧 提供者: ${location.provider}")
                    Log.i(TAG, "  ⏰ 时间: ${System.currentTimeMillis() - location.time}ms前")

                    // 手动触发位置更新
                    locationListener.onLocationChanged(location)
                } ?: run {
                    Log.w(TAG, "⚠️ 没有可用的最后已知位置")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 立即位置更新失败: ${e.message}", e)
        }
    }

    /**
     * 检查位置提供者状态
     */
    private fun checkLocationProviderStatus() {
        try {
            Log.i(TAG, "🔍 检查位置提供者状态:")

            // 检查GPS提供者
            val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            Log.i(TAG, "  📡 GPS提供者: ${if (isGpsEnabled) "✅ 启用" else "❌ 禁用"}")

            // 检查网络提供者
            val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            Log.i(TAG, "  🌐 网络提供者: ${if (isNetworkEnabled) "✅ 启用" else "❌ 禁用"}")

            // 检查被动提供者
            val isPassiveEnabled = locationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)
            Log.i(TAG, "  📱 被动提供者: ${if (isPassiveEnabled) "✅ 启用" else "❌ 禁用"}")

            // 获取所有提供者
            val allProviders = locationManager.allProviders
            Log.i(TAG, "  📋 所有提供者: $allProviders")

            // 获取启用的提供者
            val enabledProviders = locationManager.getProviders(true)
            Log.i(TAG, "  ✅ 启用的提供者: $enabledProviders")

        } catch (e: Exception) {
            Log.e(TAG, "❌ 检查位置提供者状态失败: ${e.message}", e)
        }
    }

    /**
     * 停止位置更新和传感器监听
     */
    fun cleanup() {
        try {
            locationManager.removeUpdates(locationListener)
            sensorManager.unregisterListener(this)
            Log.i(TAG, "✅ 位置和传感器服务已清理")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 清理位置和传感器服务失败: ${e.message}", e)
        }
    }

    // ===============================
    // 传感器事件处理
    // ===============================
    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            when (it.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    System.arraycopy(it.values, 0, accelerometerReading, 0, accelerometerReading.size)
                }
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    System.arraycopy(it.values, 0, magnetometerReading, 0, magnetometerReading.size)
                }
                Sensor.TYPE_ROTATION_VECTOR -> {
                    // 处理旋转向量传感器数据
                    updateOrientationAngles(it.values)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d(TAG, "传感器精度变化: ${sensor?.name} -> $accuracy")
    }

    /**
     * 更新方向角度
     */
    private fun updateOrientationAngles(rotationVector: FloatArray) {
        try {
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVector)
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            
            // 转换为度数
            val azimuth = Math.toDegrees(orientationAngles[0].toDouble())
            val pitch = Math.toDegrees(orientationAngles[1].toDouble())
            val roll = Math.toDegrees(orientationAngles[2].toDouble())
            
            // 更新CarrotMan字段
            carrotManFields.value = carrotManFields.value.copy(
                bearing_measured = azimuth,
                bearing = azimuth,
                lastUpdateTime = System.currentTimeMillis()
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "更新方向角度失败: ${e.message}", e)
        }
    }
}
