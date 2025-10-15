package com.example.carrotamap

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.RelativeLayout
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.net.*
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.MutableState

/**
 * 悬浮窗服务
 * 当app进入后台时，显示9个按钮的悬浮窗
 */
class FloatingWindowService : Service() {
    companion object {
        private const val TAG = "FloatingWindowService"
        const val ACTION_START_FLOATING = "START_FLOATING"
        const val ACTION_STOP_FLOATING = "STOP_FLOATING"
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var isFloatingVisible = false
    private var userTypeTextView: TextView? = null
    private var deviceStatusTextView: TextView? = null
    private var speedDataCheckJob: Job? = null
    private var cruiseSpeedIndicator: SpeedIndicatorView? = null
    private var carSpeedIndicator: SpeedIndicatorView? = null
    
    // 智能控速按钮状态管理
    private var speedControlButton: Button? = null
    private var currentSpeedMode = 0 // 0=智能控速, 1=原车巡航, 2=弯道减速
    private var isSpeedModeLoading = false
    
    // 折叠功能状态管理
    private var isFloatingWindowCollapsed = false
    private var buttonLayout: LinearLayout? = null
    
    // 使用广播方式发送控制指令，避免端口冲突

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_FLOATING -> {
                if (!isFloatingVisible) {
                    initializeNetworkManager()
                    showFloatingWindow()
                    startSpeedDataCheck()
                }
            }
            ACTION_STOP_FLOATING -> {
                stopSpeedDataCheck()
                hideFloatingWindow()
                stopSelf()
            }
        }
        return START_STICKY
    }

    /**
     * 显示悬浮窗
     */
    private fun showFloatingWindow() {
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "❌ 没有悬浮窗权限")
            return
        }

        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            
            // 创建悬浮窗布局
            floatingView = createFloatingLayout()
            
            // 设置悬浮窗参数
            val layoutParams = WindowManager.LayoutParams().apply {
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }
                format = PixelFormat.TRANSLUCENT
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                width = WindowManager.LayoutParams.WRAP_CONTENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                gravity = Gravity.TOP or Gravity.START
                x = 100
                y = 100
            }

            windowManager?.addView(floatingView, layoutParams)
            isFloatingVisible = true
            Log.i(TAG, "✅ 悬浮窗显示成功")

        } catch (e: Exception) {
            Log.e(TAG, "❌ 显示悬浮窗失败: ${e.message}", e)
        }
    }

    /**
     * 隐藏悬浮窗
     */
    private fun hideFloatingWindow() {
        try {
            if (isFloatingVisible && floatingView != null) {
                floatingView?.let { view ->
                    windowManager?.removeView(view)
                    isFloatingVisible = false
                    Log.i(TAG, "✅ 悬浮窗隐藏成功")
                }
            } else {
                Log.d(TAG, "🔍 悬浮窗未显示或已隐藏，跳过隐藏操作")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 隐藏悬浮窗失败: ${e.message}", e)
            // 即使隐藏失败，也要重置状态
            isFloatingVisible = false
        } finally {
            // 清理引用
            floatingView = null
        }
    }

    /**
     * 获取设备ID
     */
    private fun getDeviceIdFromPrefs(): String {
        return try {
            val sharedPreferences = getSharedPreferences("device_prefs", Context.MODE_PRIVATE)
            sharedPreferences.getString("device_id", "") ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "❌ 获取设备ID失败: ${e.message}", e)
            ""
        }
    }

    /**
     * 获取用户类型（从SharedPreferences读取MainActivity已获取的数据）
     */
    private fun getUserTypeFromPrefs(): Int {
        return try {
            val sharedPreferences = getSharedPreferences("device_prefs", Context.MODE_PRIVATE)
            sharedPreferences.getInt("user_type", 0)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 获取用户类型失败: ${e.message}", e)
            0
        }
    }

    /**
     * 初始化网络管理器 - 使用广播方式发送控制指令
     * 避免端口冲突，使用MainActivity已有的NetworkManager
     */
    private fun initializeNetworkManager() {
        try {
            Log.i(TAG, "🔄 使用广播方式发送控制指令，避免端口冲突")
            Log.i(TAG, "✅ 网络管理器初始化完成（广播模式）")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 网络管理器初始化失败: ${e.message}", e)
        }
    }




    /**
     * 开始速度数据检查 - 优化版本，减少延迟
     */
    private fun startSpeedDataCheck() {
        stopSpeedDataCheck() // 先停止之前的检查任务
        
        speedDataCheckJob = CoroutineScope(Dispatchers.IO).launch {
            var lastValidData: Pair<Triple<Float, Float, Boolean>, String?>? = null
            var lastUpdateTime = 0L
            
            while (isActive) {
                try {
                    val currentTime = System.currentTimeMillis()
                    val (speedData, deviceIP) = getSpeedDataFromNetworkManager()
                    
                    // 如果数据有效，立即更新并保存
                    if (speedData.third) { // isDataValid
                        lastValidData = Pair(speedData, deviceIP)
                        lastUpdateTime = currentTime
                        
                        withContext(Dispatchers.Main) {
                            updateSpeedIndicators(speedData, deviceIP)
                        }
                        delay(500) // 数据有效时，每500ms检查一次
                    } else {
                        // 数据无效时，使用缓存的有效数据（最多3秒）
                        if (lastValidData != null && (currentTime - lastUpdateTime) < 3000) {
                            withContext(Dispatchers.Main) {
                                updateSpeedIndicators(lastValidData.first, lastValidData.second)
                            }
                        } else {
                            // 超过3秒无有效数据，显示无设备状态
                            withContext(Dispatchers.Main) {
                                updateSpeedIndicators(Triple(0.0f, 0.0f, false), null)
                            }
                        }
                        delay(200) // 数据无效时，每200ms检查一次（更频繁）
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 速度数据检查异常: ${e.message}", e)
                    delay(1000) // 出错后等待1秒再重试
                }
            }
        }
        Log.i(TAG, "🚗 开始优化速度数据检查（高频更新）")
    }

    /**
     * 停止速度数据检查
     */
    private fun stopSpeedDataCheck() {
        speedDataCheckJob?.cancel()
        speedDataCheckJob = null
        Log.i(TAG, "⏹️ 停止速度数据检查")
    }

    /**
     * 从SharedPreferences读取NetworkManager保存的速度数据和设备连接信息 - 优化版本
     * @return Pair<Triple<Float, Float, Boolean>, String?> - (速度数据, 设备IP)
     */
    private suspend fun getSpeedDataFromNetworkManager(): Pair<Triple<Float, Float, Boolean>, String?> {
        return try {
            // 从SharedPreferences获取速度数据（NetworkManager保存）
            val speedPrefs = getSharedPreferences("openpilot_status", Context.MODE_PRIVATE)
            val vCruiseKph = speedPrefs.getFloat("v_cruise_kph", 0.0f)
            val carcruiseSpeed = speedPrefs.getFloat("carcruise_speed", 0.0f)
            val speedLastUpdate = speedPrefs.getLong("last_update", 0L)
            
            // 从SharedPreferences获取设备连接信息（NetworkManager保存）
            val networkPrefs = getSharedPreferences("network_status", Context.MODE_PRIVATE)
            val isRunning = networkPrefs.getBoolean("is_running", false)
            val currentDevice = networkPrefs.getString("current_device", "") ?: ""
            
            val currentTime = System.currentTimeMillis()
            
            // 优化：放宽数据有效性检查，从3秒改为5秒，减少"无数据"状态
            val isSpeedDataValid = (currentTime - speedLastUpdate) < 5000 && speedLastUpdate > 0
            
            // 检查设备是否连接
            val isDeviceConnected = isRunning && currentDevice.isNotEmpty()
            
            // 提取设备IP地址（格式: "192.168.0.3:7706 (vopenpilot)"）
            val deviceIP = if (isDeviceConnected) {
                currentDevice.substringBefore(":").trim()
            } else {
                null
            }
            
            // 优化：即使设备未连接，如果有有效的速度数据也显示（避免频繁闪烁）
            if (isSpeedDataValid) {
                //Log.v(TAG, "✅ 数据有效: 巡航设定=${vCruiseKph}km/h, 车辆巡航=${carcruiseSpeed}km/h, 设备=$deviceIP")
                Pair(Triple(vCruiseKph, carcruiseSpeed, true), deviceIP)
            } else {
                Log.v(TAG, "⚠️ 无有效数据 - 设备连接:$isDeviceConnected, 速度数据:$isSpeedDataValid")
                Pair(Triple(0.0f, 0.0f, false), null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 读取速度数据失败: ${e.message}", e)
            Pair(Triple(0.0f, 0.0f, false), null)
        }
    }

    /**
     * 更新速度指示器和设备状态 - 优化版本，减少UI闪烁
     * @param speedData 速度数据 (巡航设定, 车辆巡航, 数据有效性)
     * @param deviceIP 设备IP地址（如果已连接）
     */
    private fun updateSpeedIndicators(speedData: Triple<Float, Float, Boolean>, deviceIP: String?) {
        val (vCruiseKph, carcruiseSpeed, isDataValid) = speedData
        
        if (isDataValid) {
            // 有效数据：更新速度指示器
            cruiseSpeedIndicator?.updateValue(vCruiseKph.toInt())
            carSpeedIndicator?.updateValue(carcruiseSpeed.toInt())
            
            // 更新设备状态
            deviceStatusTextView?.let { textView ->
                if (deviceIP != null) {
                    // 设备已连接，显示IP地址
                    textView.text = "✅ $deviceIP"
                    textView.setTextColor(0xFF22C55E.toInt()) // 绿色
                } else {
                    // 有数据但设备未连接，显示数据状态
                    textView.text = "📊 数据中"
                    textView.setTextColor(0xFF3B82F6.toInt()) // 蓝色
                }
            }
            
            //Log.v(TAG, "🔄 速度更新: 巡航设定=${vCruiseKph.toInt()}km/h, 车辆巡航=${carcruiseSpeed.toInt()}km/h, 设备=$deviceIP")
        } else {
            // 无有效数据：显示00并标记为无设备
            cruiseSpeedIndicator?.updateValue(0)
            carSpeedIndicator?.updateValue(0)
            
            // 更新设备状态为无设备
            deviceStatusTextView?.let { textView ->
                textView.text = "❌ 无设备"
                textView.setTextColor(0xFFEF4444.toInt()) // 红色
            }
            
            Log.v(TAG, "⚠️ 无设备或无数据")
        }
    }


    /**
     * 创建悬浮窗布局 - 圆角矩形设计
     */
    private fun createFloatingLayout(): View {
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 16, 16, 16)
            // 设置圆角矩形背景 - 调整透明度
            background = createRoundedBackground(0xD0000000.toInt(), 16f) // 增加透明度
        }

        // 左侧：速度圆环区域（包含用户类型标签）
        val speedIndicatorLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        
        
        // 添加2个速度圆环 - 参考代码的颜色方案，上下布局
        cruiseSpeedIndicator = SpeedIndicatorView(this, "巡航设定", 0, 0xFF2196F3.toInt()) // 蓝色 - 巡航设定速度
        carSpeedIndicator = SpeedIndicatorView(this, "车辆巡航", 0, 0xFF22C55E.toInt()) // 绿色 - 车辆巡航速度
        
        // 为速度圆环添加点击事件
        cruiseSpeedIndicator?.setOnClickListener {
            Log.i(TAG, "👤 悬浮窗：用户点击巡航设定速度圆环，跳转到我的页面")
            openProfilePage()
        }
        // 车辆巡航速度圆环点击事件已移除，功能已迁移到主页面设置按钮
        
        speedIndicatorLayout.addView(cruiseSpeedIndicator)
        
        // 用户类型标签（在2个速度表中间）
        userTypeTextView = TextView(this).apply {
            text = "获取中..."
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 8f
            gravity = Gravity.CENTER
            setPadding(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2))
            // 设置半透明背景
            background = createRoundedBackground(0x80000000.toInt(), 4f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(0, dpToPx(4), 0, dpToPx(4)) // 上下间距
            }
            // 添加点击监听器实现折叠/展开功能
            setOnClickListener {
                Log.i(TAG, "👤 悬浮窗：用户点击用户类型标签，切换折叠状态")
                toggleFloatingWindowCollapse()
            }
        }
        speedIndicatorLayout.addView(userTypeTextView)
        
        speedIndicatorLayout.addView(carSpeedIndicator)
        
        // 初始显示0，等待实时数据更新
        cruiseSpeedIndicator?.updateValue(0) // 蓝色圆环初始显示0
        carSpeedIndicator?.updateValue(0)    // 绿色圆环初始显示0
        
        
        // 添加设备连接状态显示（可点击启动模拟导航）
        deviceStatusTextView = TextView(this).apply {
            text = "🔍 搜索设备中..."
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 6f
            gravity = Gravity.CENTER
            setPadding(dpToPx(2), dpToPx(1), dpToPx(2), dpToPx(1))
            // 设置半透明背景
            background = createRoundedBackground(0x60000000.toInt(), 3f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(0, dpToPx(2), 0, 0)
            }
            // 添加点击监听器启动模拟导航
            setOnClickListener {
                Log.i(TAG, "🔧 悬浮窗：用户点击设备状态文本，启动模拟导航")
                startSimulatedNavigation()
            }
        }
        
        speedIndicatorLayout.addView(deviceStatusTextView)
        mainLayout.addView(speedIndicatorLayout)

        // 右侧：按钮区域
        buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // 第一行：回家 公司
        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        addFloatingIconButton(row1, "🏠", "回家", 0xFFFFD700.toInt()) { // 浅黄色
            Log.i(TAG, "🏠 悬浮窗：用户点击回家按钮")
            sendHomeNavigationToAmap()
        }
        addFloatingIconButton(row1, "🏢", "公司", 0xFFFF8C00.toInt()) { // 橙色
            Log.i(TAG, "🏢 悬浮窗：用户点击公司按钮")
            sendCompanyNavigationToAmap()
        }
        buttonLayout?.addView(row1)

        // 第二行：智能控速 数据
        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        // 智能控速按钮 - 使用统一方法创建，但保存引用以便动态更新
        speedControlButton = addFloatingIconButtonWithReference(
            row2, "🎯", "智能控速", 0xFF22C55E.toInt()
        ) {
            Log.i(TAG, "🎮 悬浮窗：用户点击智能控速按钮")
            toggleSpeedControlMode()
        }
        addFloatingIconButton(row2, "📊", "数据", 0xFF6B7280.toInt()) {
            Log.i(TAG, "📊 悬浮窗：用户点击数据按钮")
            openDataPage()
        }
        buttonLayout?.addView(row2)

        buttonLayout?.let { mainLayout.addView(it) }

        // 添加拖动功能
        mainLayout.setOnTouchListener(FloatingTouchListener())

        // 直接读取MainActivity已获取的用户类型数据
        try {
            updateUserTypeTextWithCollapseState()
            Log.i(TAG, "✅ 悬浮窗用户类型显示更新完成")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 读取用户类型失败: ${e.message}", e)
            userTypeTextView?.text = "未知用户 📂"
        }
        
        // 初始化智能控速按钮状态
        try {
            val prefs = getSharedPreferences("CarrotAmap", Context.MODE_PRIVATE)
            currentSpeedMode = prefs.getInt("speed_from_pcm_mode", 0)
            updateSpeedControlButtonUI()
            Log.i(TAG, "✅ 智能控速按钮状态初始化: 模式=$currentSpeedMode")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 初始化智能控速按钮状态失败: ${e.message}", e)
        }

        return mainLayout
    }

    /**
     * 添加悬浮窗图标按钮到布局（圆角设计）
     */
    private fun addFloatingIconButton(parent: LinearLayout, icon: String, text: String, color: Int, onClick: () -> Unit) {
        val button = Button(this).apply {
            this.text = "$icon\n$text"
            setTextColor(0xFFFFFFFF.toInt())
            // 设置圆角背景
            background = createRoundedBackground(color, 8f)
            textSize = 8f
            setPadding(4, 4, 4, 4)
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(75), // 增加按钮宽度
                dpToPx(55)  // 增加按钮高度
            ).apply {
                setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
            }
        }
        button.setOnClickListener { onClick() }
        parent.addView(button)
    }

    /**
     * 添加悬浮窗图标按钮并返回引用（用于需要动态更新的按钮）
     */
    private fun addFloatingIconButtonWithReference(
        parent: LinearLayout, 
        icon: String, 
        text: String, 
        color: Int, 
        onClick: () -> Unit
    ): Button {
        val button = Button(this).apply {
            this.text = "$icon\n$text"
            setTextColor(0xFFFFFFFF.toInt())
            background = createRoundedBackground(color, 8f)
            textSize = 8f
            setPadding(4, 4, 4, 4)
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(75), // 增加按钮宽度
                dpToPx(55)  // 增加按钮高度
            ).apply {
                setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
            }
        }
        button.setOnClickListener { onClick() }
        parent.addView(button)
        return button
    }

    /**
     * dp转px
     */
    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    /**
     * 发送回家导航指令给高德地图
     */
    private fun sendHomeNavigationToAmap() {
        try {
            Log.i(TAG, "🏠 悬浮窗发送一键回家指令给高德地图")
            val homeIntent = Intent("AUTONAVI_STANDARD_BROADCAST_RECV").apply {
                putExtra("KEY_TYPE", 10040)
                putExtra("SOURCE_APP", "CPlink")
                putExtra("DEST", 0) // 0: 回家；1: 回公司
                putExtra("IS_START_NAVI", 0) // 0: 是直接开始导航；1: 否
                setPackage("com.autonavi.amapauto")
                flags = Intent.FLAG_INCLUDE_STOPPED_PACKAGES
            }
            sendBroadcast(homeIntent)
            Log.i(TAG, "✅ 悬浮窗一键回家导航广播已发送")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 悬浮窗发送一键回家指令失败: ${e.message}", e)
        }
    }

    /**
     * 发送导航到公司指令给高德地图
     */
    private fun sendCompanyNavigationToAmap() {
        try {
            Log.i(TAG, "🏢 悬浮窗发送导航到公司指令给高德地图")
            val companyIntent = Intent("AUTONAVI_STANDARD_BROADCAST_RECV").apply {
                putExtra("KEY_TYPE", 10040)
                putExtra("SOURCE_APP", "CPlink")
                putExtra("DEST", 1) // 0: 回家；1: 回公司
                putExtra("IS_START_NAVI", 0) // 0: 是直接开始导航；1: 否
                setPackage("com.autonavi.amapauto")
                flags = Intent.FLAG_INCLUDE_STOPPED_PACKAGES
            }
            sendBroadcast(companyIntent)
            Log.i(TAG, "✅ 悬浮窗导航到公司广播已发送")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 悬浮窗发送导航到公司指令失败: ${e.message}", e)
        }
    }

    /**
     * 发送Carrot命令到设备 - 优化版本，参考AdvancedOperationDialog
     * 通过广播方式发送给MainActivity处理
     */
    private fun sendCarrotCommand(command: String, arg: String) {
        try {
            Log.i(TAG, "🎮 悬浮窗发送Carrot命令: $command $arg")
            
            // 发送广播给MainActivity处理
            val intent = Intent("com.example.cplink.SEND_CARROT_COMMAND").apply {
                putExtra("command", command)
                putExtra("arg", arg)
                setPackage(packageName)  // 限制在本应用内
            }
            sendBroadcast(intent)
            
            Log.i(TAG, "✅ 指令广播已发送: $command $arg")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 悬浮窗发送Carrot命令失败: ${e.message}", e)
        }
    }
    
    // sendCurrentRoadLimitSpeed函数已移除，功能已迁移到MainActivity的设置按钮
    
    /**
     * 发送Carrot命令并带反馈 - 广播通信版本
     * 使用广播方式发送指令到MainActivity，避免端口冲突
     */
    private fun sendCarrotCommandWithFeedback(command: String, arg: String, description: String) {
        try {
            Log.i(TAG, "🎮 悬浮窗发送$description: $command $arg")
            
            // 使用广播方式发送指令到MainActivity
            val intent = Intent("com.example.cplink.SEND_CARROT_COMMAND").apply {
                putExtra("command", command)
                putExtra("arg", arg)
            }
            
            // 发送广播
            sendBroadcast(intent)
            Log.i(TAG, "✅ ${description}指令已通过广播发送: $command $arg")
            Log.i(TAG, "📡 指令将通过MainActivity的NetworkManager发送到comma3设备")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 悬浮窗发送${description}失败: ${e.message}", e)
        }
    }

    /**
     * 切换速度控制模式 - 优化版本，带状态管理和UI反馈
     * 循环切换：智能控速(0) → 原车巡航(1) → 弯道减速(2) → 智能控速(0)
     */
    private fun toggleSpeedControlMode() {
        if (isSpeedModeLoading) {
            Log.w(TAG, "⚠️ 速度模式切换中，请稍候...")
            return
        }
        
        try {
            isSpeedModeLoading = true
            updateSpeedControlButtonUI()
            
            // 从SharedPreferences读取当前模式
            val prefs = getSharedPreferences("CarrotAmap", Context.MODE_PRIVATE)
            val currentMode = prefs.getInt("speed_from_pcm_mode", 0)
            
            // 计算下一个模式（循环）
            val nextMode = (currentMode + 1) % 3
            currentSpeedMode = nextMode
            
            // 模式名称映射
            val modeNames = arrayOf("智能控速", "原车巡航", "弯道减速")
            val modeColors = intArrayOf(0xFF22C55E.toInt(), 0xFF3B82F6.toInt(), 0xFFF59E0B.toInt()) // 绿色、蓝色、橙色
            
            Log.i(TAG, "🔄 切换速度控制模式: ${modeNames[currentMode]} → ${modeNames[nextMode]}")
            
            // 发送模式切换广播给MainActivity
            val intent = Intent("com.example.cplink.CHANGE_SPEED_MODE").apply {
                putExtra("mode", nextMode)
                setPackage(packageName)
            }
            sendBroadcast(intent)
            
            // 保存新模式到SharedPreferences
            prefs.edit().putInt("speed_from_pcm_mode", nextMode).apply()
            
            // 延迟更新UI，模拟网络请求
            CoroutineScope(Dispatchers.Main).launch {
                delay(500) // 模拟网络延迟
                isSpeedModeLoading = false
                updateSpeedControlButtonUI()
                Log.i(TAG, "✅ 模式切换完成: ${modeNames[nextMode]} (SpeedFromPCM=$nextMode)")
            }
            
        } catch (e: Exception) {
            isSpeedModeLoading = false
            updateSpeedControlButtonUI()
            Log.e(TAG, "❌ 切换速度控制模式失败: ${e.message}", e)
        }
    }
    
    /**
     * 更新智能控速按钮的UI状态
     */
    private fun updateSpeedControlButtonUI() {
        speedControlButton?.let { button ->
            val modeNames = arrayOf("智能控速", "原车巡航", "弯道减速")
            val modeColors = intArrayOf(0xFF22C55E.toInt(), 0xFF3B82F6.toInt(), 0xFFF59E0B.toInt()) // 绿色、蓝色、橙色
            
            if (isSpeedModeLoading) {
                // 加载状态：灰色背景，显示"切换中..."
                button.text = "切换中..."
                button.background = createRoundedBackground(0xFF6B7280.toInt(), 8f)
                button.isEnabled = false
            } else {
                // 正常状态：根据当前模式显示颜色和文字
                val currentModeName = modeNames[currentSpeedMode]
                val currentModeColor = modeColors[currentSpeedMode]
                
                button.text = "🎯 $currentModeName"
                button.background = createRoundedBackground(currentModeColor, 8f)
                button.isEnabled = true
            }
        }
    }

    /**
     * 切换悬浮窗折叠状态
     */
    private fun toggleFloatingWindowCollapse() {
        try {
            isFloatingWindowCollapsed = !isFloatingWindowCollapsed
            
            if (isFloatingWindowCollapsed) {
                // 折叠：隐藏按钮区域
                buttonLayout?.visibility = View.GONE
                Log.i(TAG, "📦 悬浮窗已折叠，隐藏9个功能按钮")
            } else {
                // 展开：显示按钮区域
                buttonLayout?.visibility = View.VISIBLE
                Log.i(TAG, "📂 悬浮窗已展开，显示9个功能按钮")
            }
            
            // 更新用户类型文本显示状态指示
            updateUserTypeTextWithCollapseState()
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 切换悬浮窗折叠状态失败: ${e.message}", e)
        }
    }

    /**
     * 更新用户类型文本，显示折叠状态
     */
    private fun updateUserTypeTextWithCollapseState() {
        userTypeTextView?.let { textView ->
            val userType = getUserTypeFromPrefs()
            val userTypeText = when (userType) {
                0 -> "未知用户"
                1 -> "新用户"
                2 -> "支持者"
                3 -> "赞助者"
                4 -> "铁粉"
                else -> "未知类型($userType)"
            }
            
            // 根据折叠状态添加状态指示
            val collapseIndicator = if (isFloatingWindowCollapsed) " 📦" else " 📂"
            textView.text = "$userTypeText$collapseIndicator"
            
            Log.i(TAG, "🔄 用户类型文本已更新: $userTypeText$collapseIndicator")
        }
    }

    /**
     * 打开帮助页面
     */
    private fun openHelpPage() {
        try {
            Log.i(TAG, "❓ 悬浮窗：打开帮助页面")
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("OPEN_PAGE", 1) // 1: 帮助页面
            }
            startActivity(intent)
            hideFloatingWindow()
        } catch (e: Exception) {
            Log.e(TAG, "❌ 悬浮窗打开帮助页面失败: ${e.message}", e)
        }
    }

    /**
     * 打开数据页面
     */
    private fun openDataPage() {
        try {
            Log.i(TAG, "📊 悬浮窗：打开数据页面")
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("OPEN_PAGE", 4) // 4: 实时数据页面
            }
            startActivity(intent)
            hideFloatingWindow()
        } catch (e: Exception) {
            Log.e(TAG, "❌ 悬浮窗打开数据页面失败: ${e.message}", e)
        }
    }
    
    /**
     * 打开我的页面
     */
    private fun openProfilePage() {
        try {
            Log.i(TAG, "👤 悬浮窗：打开我的页面")
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("OPEN_PAGE", 3) // 3: 我的页面
            }
            startActivity(intent)
            hideFloatingWindow()
        } catch (e: Exception) {
            Log.e(TAG, "❌ 悬浮窗打开我的页面失败: ${e.message}", e)
        }
    }

    /**
     * 创建圆角矩形背景
     */
    private fun createRoundedBackground(color: Int, cornerRadius: Float): android.graphics.drawable.Drawable {
        val drawable = android.graphics.drawable.GradientDrawable()
        drawable.setColor(color)
        drawable.cornerRadius = cornerRadius
        return drawable
    }

    /**
     * 自定义速度圆环View - 参考CircularSpeedIndicator设计，上下布局
     */
    private class SpeedIndicatorView(
        context: Context,
        private val label: String,
        private var value: Int,
        private val color: Int
    ) : View(context) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val maxValue = 120
        private val size = 45 // 调整尺寸以适应2x2按钮布局

        init {
            // 设置View尺寸 - 移除标签后减少高度
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(context, size),
                dpToPx(context, size) // 移除标签，高度与宽度相同
            ).apply {
                setMargins(0, dpToPx(context, 4), 0, dpToPx(context, 4))
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            
            val centerX = width / 2f
            val centerY = height / 2f // 移除标签后，圆环居中
            val radius = dpToPx(context, size) / 2f - dpToPx(context, 6) // 参考代码的半径计算
            
            // 绘制白色背景圆
            backgroundPaint.color = Color.WHITE
            backgroundPaint.style = Paint.Style.FILL
            canvas.drawCircle(centerX, centerY, radius, backgroundPaint)
            
            // 绘制彩色圆环
            paint.color = color
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dpToPx(context, 6).toFloat() // 参考代码的线条宽度
            canvas.drawCircle(centerX, centerY, radius, paint)
            
            // 绘制进度弧（可选）
            if (value > 0) {
                val progress = (value.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f)
                val sweepAngle = progress * 360f
                val rectF = RectF(
                    centerX - radius,
                    centerY - radius,
                    centerX + radius,
                    centerY + radius
                )
                paint.color = Color.argb(
                    (0.3f * 255).toInt(),
                    Color.red(color),
                    Color.green(color),
                    Color.blue(color)
                )
                paint.strokeWidth = dpToPx(context, 4).toFloat()
                paint.strokeCap = Paint.Cap.ROUND
                canvas.drawArc(rectF, -90f, sweepAngle, false, paint)
            }
            
            // 绘制数值文本（参考代码样式）
            textPaint.color = color
            textPaint.textSize = spToPx(18) // 参考代码的字体大小
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD
            val textY = centerY + textPaint.textSize / 3
            canvas.drawText(value.toString(), centerX, textY, textPaint)
        }
        
        fun updateValue(newValue: Int) {
            value = newValue
            invalidate()
        }
        
        private fun dpToPx(context: Context, dp: Int): Int {
            val density = context.resources.displayMetrics.density
            return (dp * density).toInt()
        }
        
        private fun spToPx(sp: Int): Float {
            val density = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                context.resources.displayMetrics.density
            } else {
                @Suppress("DEPRECATION")
                context.resources.displayMetrics.scaledDensity
            }
            return sp * density
        }
    }

    /**
     * 悬浮窗拖动监听器
     */
    private inner class FloatingTouchListener : View.OnTouchListener {
        private var initialX = 0
        private var initialY = 0
        private var initialTouchX = 0f
        private var initialTouchY = 0f

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    val layoutParams = floatingView?.layoutParams as? WindowManager.LayoutParams
                    layoutParams?.let {
                        initialX = it.x
                        initialY = it.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                    }
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val layoutParams = floatingView?.layoutParams as? WindowManager.LayoutParams
                    layoutParams?.let {
                        it.x = initialX + (event.rawX - initialTouchX).toInt()
                        it.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager?.updateViewLayout(floatingView, it)
                    }
                    return true
                }
            }
            return false
        }
    }

    /**
     * 启动模拟导航功能
     * 使用当前位置作为起点，公司位置作为目的地
     */
    private fun startSimulatedNavigation() {
        try {
            Log.i(TAG, "🔧 启动模拟导航功能")
            
            // 获取当前位置信息
            val currentLat = getCurrentLocationLatitude()
            val currentLon = getCurrentLocationLongitude()
            
            // 设置目的地为上海东方明珠
            val companyLat = 31.2397  // 上海东方明珠纬度
            val companyLon = 121.4998  // 上海东方明珠经度
            
            Log.i(TAG, "📍 起点坐标: lat=$currentLat, lon=$currentLon")
            Log.i(TAG, "🏗️ 目的地坐标（上海东方明珠）: lat=$companyLat, lon=$companyLon")
            
            // 检查起点和终点是否相同
            if (currentLat == companyLat && currentLon == companyLon) {
                Log.w(TAG, "⚠️ 起点和终点坐标相同，调整公司位置")
                // 如果坐标相同，使用不同的公司位置（深圳）
                val adjustedCompanyLat = 22.5431
                val adjustedCompanyLon = 114.0579
                Log.i(TAG, "🏢 调整后目的地坐标: lat=$adjustedCompanyLat, lon=$adjustedCompanyLon")
                
                // 发送模拟导航广播给高德地图车机版
                sendSimulatedNavigationIntent(currentLat, currentLon, adjustedCompanyLat, adjustedCompanyLon)
            } else {
                // 发送模拟导航广播给高德地图车机版
                sendSimulatedNavigationIntent(currentLat, currentLon, companyLat, companyLon)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 启动模拟导航失败: ${e.message}", e)
        }
    }
    
    /**
     * 发送模拟导航Intent
     */
    private fun sendSimulatedNavigationIntent(startLat: Double, startLon: Double, destLat: Double, destLon: Double) {
        try {
            val intent = Intent("AUTONAVI_STANDARD_BROADCAST_RECV").apply {
                putExtra("KEY_TYPE", 10076) // 模拟导航类型
                putExtra("SOURCE_APP", "CPlink")
                
                // 起点信息
                putExtra("EXTRA_SLAT", startLat)
                putExtra("EXTRA_SLON", startLon)
                putExtra("EXTRA_SNAME", "家")
                
                // 目的地信息
                putExtra("EXTRA_DLAT", destLat)
                putExtra("EXTRA_DLON", destLon)
                putExtra("EXTRA_DNAME", "上海东方明珠")
                
                // 其他必要参数
                putExtra("EXTRA_DEV", 0) // 0: 加密，不需要偏移
                putExtra("EXTRA_M", 0)  // 0: 默认驾驶模式
                putExtra("KEY_RECYLE_SIMUNAVI", true) // 关键：启动模拟导航
                
                setPackage("com.autonavi.amapauto")
                flags = Intent.FLAG_INCLUDE_STOPPED_PACKAGES
            }
            
            sendBroadcast(intent)
            Log.i(TAG, "✅ 模拟导航广播已发送给高德地图车机版")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 发送模拟导航广播失败: ${e.message}", e)
        }
    }
    
    /**
     * 获取当前位置纬度
     */
    private fun getCurrentLocationLatitude(): Double {
        return try {
            // 尝试从多个SharedPreferences获取当前位置
            val carrotPrefs = getSharedPreferences("CarrotAmap", Context.MODE_PRIVATE)
            val devicePrefs = getSharedPreferences("device_prefs", Context.MODE_PRIVATE)
            
            // 优先从CarrotAmap获取
            var lat = carrotPrefs.getFloat("vpPosPointLat", 0.0f).toDouble()
            if (lat == 0.0) {
                // 尝试从device_prefs获取
                lat = devicePrefs.getFloat("vpPosPointLat", 0.0f).toDouble()
            }
            
            if (lat != 0.0) {
                Log.i(TAG, "✅ 获取到手机当前位置纬度: $lat")
                lat
            } else {
                // 如果无法获取手机位置，使用北京作为默认起点
                Log.w(TAG, "⚠️ 未找到手机当前位置，使用默认起点坐标（北京）")
                39.9042
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 获取当前位置纬度失败: ${e.message}", e)
            39.9042 // 默认坐标（北京）
        }
    }
    
    /**
     * 获取当前位置经度
     */
    private fun getCurrentLocationLongitude(): Double {
        return try {
            // 尝试从多个SharedPreferences获取当前位置
            val carrotPrefs = getSharedPreferences("CarrotAmap", Context.MODE_PRIVATE)
            val devicePrefs = getSharedPreferences("device_prefs", Context.MODE_PRIVATE)
            
            // 优先从CarrotAmap获取
            var lon = carrotPrefs.getFloat("vpPosPointLon", 0.0f).toDouble()
            if (lon == 0.0) {
                // 尝试从device_prefs获取
                lon = devicePrefs.getFloat("vpPosPointLon", 0.0f).toDouble()
            }
            
            if (lon != 0.0) {
                Log.i(TAG, "✅ 获取到手机当前位置经度: $lon")
                lon
            } else {
                // 如果无法获取手机位置，使用北京作为默认起点
                Log.w(TAG, "⚠️ 未找到手机当前位置，使用默认起点坐标（北京）")
                116.4074
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 获取当前位置经度失败: ${e.message}", e)
            116.4074 // 默认坐标（北京）
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            stopSpeedDataCheck()
            hideFloatingWindow()
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ 销毁时隐藏悬浮窗异常: ${e.message}")
        }
        Log.i(TAG, "🔧 悬浮窗服务销毁")
    }
}
