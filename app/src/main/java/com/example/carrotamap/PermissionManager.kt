package com.example.carrotamap

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * 权限管理器
 * 负责应用权限的请求、检查和管理
 */
class PermissionManager(
    private val activity: ComponentActivity,
    private val locationSensorManager: LocationSensorManager
) {
    companion object {
        private const val TAG = "PermissionManager"
        
        // GPS测试权限 - 仅包含位置权限，用于GPS功能测试
        private val GPS_TEST_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    // 权限请求启动器
    private var gpsPermissionLauncher: ActivityResultLauncher<Array<String>>? = null
    private var fullPermissionLauncher: ActivityResultLauncher<Array<String>>? = null

    /**
     * 初始化权限管理器
     */
    fun initialize() {
        Log.i(TAG, "🔐 初始化权限管理器...")
        
        // 注册GPS权限请求回调
        gpsPermissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            handleGpsPermissionResult(permissions)
        }
        
        // 注册完整权限请求回调
        fullPermissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            handleFullPermissionResult(permissions)
        }
        
        Log.i(TAG, "✅ 权限管理器初始化完成")
    }

    /**
     * 设置权限和位置服务
     */
    fun setupPermissionsAndLocation() {
        // 首先尝试简化的GPS权限请求
        setupGpsPermissionsOnly()
    }

    /**
     * 仅设置GPS相关权限 - 简化版本用于测试GPS功能
     */
    private fun setupGpsPermissionsOnly() {
        Log.i(TAG, "🔍 开始GPS权限设置（简化版本）")

        // 检查GPS权限状态
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            activity, 
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        val coarseLocationGranted = ContextCompat.checkSelfPermission(
            activity, 
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        Log.i(TAG, "📍 当前GPS权限状态:")
        Log.i(TAG, "  ${if (fineLocationGranted) "✅" else "❌"} ACCESS_FINE_LOCATION: ${if (fineLocationGranted) "已授予" else "需要请求"}")
        Log.i(TAG, "  ${if (coarseLocationGranted) "✅" else "❌"} ACCESS_COARSE_LOCATION: ${if (coarseLocationGranted) "已授予" else "需要请求"}")

        if (fineLocationGranted || coarseLocationGranted) {
            Log.i(TAG, "✅ GPS权限检查通过，直接启动位置更新")
            locationSensorManager.startLocationUpdates()
            startGpsStatusMonitoring()
        } else {
            Log.i(TAG, "⚠️ 需要请求GPS权限")
            gpsPermissionLauncher?.launch(GPS_TEST_PERMISSIONS)
        }
    }

    /**
     * 设置完整权限 - 包含所有功能权限
     */
    fun setupFullPermissions() {
        Log.i(TAG, "🔍 开始完整权限设置...")
        
        // 首先检查核心权限
        val corePermissionStatus = AppConstants.Permissions.CORE_PERMISSIONS.map { permission ->
            val granted = ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
            Log.i(TAG, "  ${if (granted) "✅" else "❌"} [核心] $permission: ${if (granted) "已授予" else "需要请求"}")
            permission to granted
        }.toMap()

        // 然后检查所有权限
        val allPermissionStatus = AppConstants.Permissions.ALL_PERMISSIONS.map { permission ->
            val granted = ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
            if (!AppConstants.Permissions.CORE_PERMISSIONS.contains(permission)) {
                Log.i(TAG, "  ${if (granted) "✅" else "❌"} [可选] $permission: ${if (granted) "已授予" else "需要请求"}")
            }
            permission to granted
        }.toMap()

        val coreGrantedCount = corePermissionStatus.values.count { it }
        val allGrantedCount = allPermissionStatus.values.count { it }
        Log.i(TAG, "📊 核心权限状态: $coreGrantedCount/${AppConstants.Permissions.CORE_PERMISSIONS.size} 已授予")
        Log.i(TAG, "📊 总权限状态: $allGrantedCount/${AppConstants.Permissions.ALL_PERMISSIONS.size} 已授予")

        // 如果核心权限都已授予，直接启动GPS功能
        if (corePermissionStatus.all { it.value }) {
            Log.i(TAG, "✅ 核心权限检查通过，直接启动位置更新")
            locationSensorManager.startLocationUpdates()
            startGpsStatusMonitoring()

            // 如果还有其他权限未授予，可以在后台请求
            if (!allPermissionStatus.all { it.value }) {
                Log.i(TAG, "📝 后台请求剩余权限以获得完整功能")
                val missingPermissions = allPermissionStatus.filter { !it.value }.keys.toTypedArray()
                fullPermissionLauncher?.launch(missingPermissions)
            }
        } else {
            Log.i(TAG, "⚠️ 需要请求核心权限")
            val missingCorePermissions = corePermissionStatus.filter { !it.value }.keys.toTypedArray()
            Log.i(TAG, "📝 需要请求的核心权限: ${missingCorePermissions.joinToString(", ")}")
            fullPermissionLauncher?.launch(AppConstants.Permissions.ALL_PERMISSIONS)
        }
    }

    /**
     * 处理GPS权限请求结果
     */
    private fun handleGpsPermissionResult(permissions: Map<String, Boolean>) {
        Log.i(TAG, "🔍 GPS权限请求结果:")
        permissions.forEach { (permission, granted) ->
            Log.i(TAG, "  ${if (granted) "✅" else "❌"} $permission: ${if (granted) "已授予" else "被拒绝"}")
        }

        val hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                                  permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (hasLocationPermission) {
            Log.i(TAG, "✅ GPS权限已获取，启动位置更新")
            locationSensorManager.startLocationUpdates()
            startGpsStatusMonitoring()
        } else {
            Log.e(TAG, "❌ GPS权限被拒绝，无法启动GPS功能")
            Log.e(TAG, "💡 请在设置中手动授予位置权限")
        }
    }

    /**
     * 处理完整权限请求结果
     */
    private fun handleFullPermissionResult(permissions: Map<String, Boolean>) {
        Log.i(TAG, "🔍 权限请求结果:")
        permissions.forEach { (permission, granted) ->
            Log.i(TAG, "  ${if (granted) "✅" else "❌"} $permission: ${if (granted) "已授予" else "被拒绝"}")
        }

        val grantedPermissions = permissions.filter { it.value }
        val deniedPermissions = permissions.filter { !it.value }

        Log.i(TAG, "📊 权限统计: ${grantedPermissions.size}/${permissions.size} 已授予")

        if (permissions.all { it.value }) {
            Log.i(TAG, "✅ 所有权限已获取，启动位置更新")
            locationSensorManager.startLocationUpdates()
            startGpsStatusMonitoring()
        } else {
            Log.w(TAG, "⚠️ 部分权限未获取，功能可能受限")
            Log.w(TAG, "❌ 被拒绝的权限: ${deniedPermissions.keys.joinToString(", ")}")

            // 检查核心权限是否都被授予
            val corePermissionsGranted = AppConstants.Permissions.CORE_PERMISSIONS.all { corePermission ->
                permissions[corePermission] == true
            }

            if (corePermissionsGranted) {
                Log.i(TAG, "✅ 核心权限已获取，启动位置更新")
                locationSensorManager.startLocationUpdates()
                startGpsStatusMonitoring()
            } else {
                Log.e(TAG, "❌ 核心权限被拒绝，无法启动GPS功能")
                val deniedCorePermissions = AppConstants.Permissions.CORE_PERMISSIONS.filter { 
                    permissions[it] != true 
                }
                Log.e(TAG, "❌ 被拒绝的核心权限: ${deniedCorePermissions.joinToString(", ")}")
            }
        }
    }

    /**
     * 检查特定权限是否已授予
     */
    fun isPermissionGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 检查GPS权限是否已授予
     */
    fun isLocationPermissionGranted(): Boolean {
        return isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION) ||
               isPermissionGranted(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    /**
     * 检查所有核心权限是否已授予
     */
    fun areCorePermissionsGranted(): Boolean {
        return AppConstants.Permissions.CORE_PERMISSIONS.all { permission ->
            isPermissionGranted(permission)
        }
    }

    /**
     * 启动GPS状态监控
     */
    private fun startGpsStatusMonitoring() {
        Log.i(TAG, "📡 启动GPS状态监控...")
        // 这里可以添加GPS状态监控逻辑
        // 目前由LocationSensorManager处理
    }

    /**
     * 清理权限管理器资源
     */
    fun cleanup() {
        Log.i(TAG, "🧹 清理权限管理器资源...")
        gpsPermissionLauncher = null
        fullPermissionLauncher = null
    }
}
