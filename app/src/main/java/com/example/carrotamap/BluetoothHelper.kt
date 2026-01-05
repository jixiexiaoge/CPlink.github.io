package com.example.carrotamap

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

/**
 * 蓝牙连接状态
 */
enum class BluetoothState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    AUTO_CONNECTING
}

/**
 * 扫描到的设备信息
 */
data class ScannedDevice(
    val device: BluetoothDevice,
    val rssi: Int = 0,
    val isPaired: Boolean = false
)

/**
 * 蓝牙设备管理器
 * 负责蓝牙设备的扫描、连接和数据发送
 */
class BluetoothHelper(private val context: Context) {
    companion object {
        private const val TAG = "BluetoothHelper"
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val SCAN_TIMEOUT = 30000L // 30秒超时
    }

    private val bluetoothManager: BluetoothManager? = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    // 连接状态
    private val _connectionState = MutableStateFlow(BluetoothState.DISCONNECTED)
    val connectionState: StateFlow<BluetoothState> = _connectionState.asStateFlow()

    // 连接的设备名称
    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()

    // 扫描到的设备列表 (使用 Map 保证设备唯一并更新 RSSI)
    private val deviceMap = mutableMapOf<String, ScannedDevice>()
    private val _scannedDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val scannedDevices: StateFlow<List<ScannedDevice>> = _scannedDevices.asStateFlow()

    // 是否正在扫描
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var scanJob: Job? = null

    /**
     * 蓝牙搜索广播接收器
     */
    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    val rssi: Int = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                    device?.let {
                        val scannedDevice = ScannedDevice(
                            device = it,
                            rssi = rssi,
                            isPaired = it.bondState == BluetoothDevice.BOND_BONDED
                        )
                        deviceMap[it.address] = scannedDevice
                        _scannedDevices.value = deviceMap.values.toList().sortedByDescending { d -> d.rssi }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Log.i(TAG, "扫描完成")
                    _isScanning.value = false
                }
            }
        }
    }

    init {
        // 注册广播
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        context.registerReceiver(receiver, filter)
    }

    /**
     * 清理资源
     */
    fun cleanup() {
        try {
            context.unregisterReceiver(receiver)
        } catch (e: Exception) {
            Log.e(TAG, "注销广播接收器失败", e)
        }
        stopScan()
        disconnect()
    }

    /**
     * 检查蓝牙权限
     */
    fun hasPermissions(): Boolean {
        val basePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
        }
        
        // 扫描通常需要定位权限
        val locationPermission = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        
        return basePermissions && locationPermission
    }

    /**
     * 开始扫描设备
     */
    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!hasPermissions() || bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.w(TAG, "无法扫描：权限不足或蓝牙未开启")
            return
        }

        if (_isScanning.value) return

        // 清空旧列表，先加入已配对设备
        deviceMap.clear()
        bluetoothAdapter.bondedDevices.forEach {
            deviceMap[it.address] = ScannedDevice(it, 0, true)
        }
        _scannedDevices.value = deviceMap.values.toList()

        // 开始发现新设备
        if (bluetoothAdapter.isDiscovering) {
            bluetoothAdapter.cancelDiscovery()
        }
        
        if (bluetoothAdapter.startDiscovery()) {
            _isScanning.value = true
            Log.i(TAG, "开始扫描附近蓝牙设备...")
            
            // 设置超时
            scanJob?.cancel()
            scanJob = CoroutineScope(Dispatchers.Main).launch {
                delay(SCAN_TIMEOUT)
                if (_isScanning.value) {
                    stopScan()
                    Log.i(TAG, "扫描超时 (30s)")
                }
            }
        } else {
            Log.e(TAG, "启动扫描失败")
        }
    }

    /**
     * 停止扫描
     */
    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (bluetoothAdapter?.isDiscovering == true) {
            bluetoothAdapter.cancelDiscovery()
        }
        _isScanning.value = false
        scanJob?.cancel()
    }

    /**
     * 连接设备
     */
    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice, isAuto: Boolean = false, onResult: (Boolean) -> Unit = {}) {
        if (!hasPermissions()) {
            onResult(false)
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                _connectionState.value = if (isAuto) BluetoothState.AUTO_CONNECTING else BluetoothState.CONNECTING
                
                // 取消扫描
                stopScan()

                val tmpSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                tmpSocket.connect()
                
                socket = tmpSocket
                outputStream = tmpSocket.outputStream
                
                _connectionState.value = BluetoothState.CONNECTED
                _connectedDeviceName.value = getDeviceName(device)
                
                saveLastDeviceAddress(device.address)
                
                withContext(Dispatchers.Main) {
                    onResult(true)
                }
                Log.i(TAG, "已连接到设备: ${device.name}")
            } catch (e: IOException) {
                Log.e(TAG, "连接失败: ${e.message}")
                closeSocket()
                _connectionState.value = BluetoothState.DISCONNECTED
                _connectedDeviceName.value = null
                
                withContext(Dispatchers.Main) {
                    onResult(false)
                }
            }
        }
    }

    /**
     * 断开连接
     */
    fun disconnect() {
        closeSocket()
        _connectionState.value = BluetoothState.DISCONNECTED
        _connectedDeviceName.value = null
    }

    private fun closeSocket() {
        try {
            outputStream?.close()
            socket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "关闭 Socket 失败", e)
        } finally {
            socket = null
            outputStream = null
        }
    }

    /**
     * 发送数据
     */
    fun sendData(message: String) {
        if (_connectionState.value != BluetoothState.CONNECTED || outputStream == null) {
            Log.w(TAG, "未连接，无法发送数据")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                outputStream?.write(message.toByteArray())
                Log.d(TAG, "已发送蓝牙数据: $message")
            } catch (e: IOException) {
                Log.e(TAG, "发送蓝牙数据失败", e)
                disconnect()
            }
        }
    }
    
    /**
     * 尝试自动连接上次的设备
     */
    @SuppressLint("MissingPermission")
    fun tryAutoConnect() {
        val lastAddress = getLastDeviceAddress() ?: return
        if (!hasPermissions() || bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return
        if (_connectionState.value != BluetoothState.DISCONNECTED) return
        
        try {
            val device = bluetoothAdapter.getRemoteDevice(lastAddress)
            Log.i(TAG, "尝试自动连接上次记录的设备: $lastAddress")
            connect(device, isAuto = true)
        } catch (e: Exception) {
            Log.e(TAG, "自动连接异常", e)
        }
    }

    private fun saveLastDeviceAddress(address: String) {
        val prefs = context.getSharedPreferences("CarrotBluetooth", Context.MODE_PRIVATE)
        prefs.edit().putString("last_device_address", address).apply()
    }

    private fun getLastDeviceAddress(): String? {
        val prefs = context.getSharedPreferences("CarrotBluetooth", Context.MODE_PRIVATE)
        return prefs.getString("last_device_address", null)
    }

    @SuppressLint("MissingPermission")
    fun getDeviceName(device: BluetoothDevice): String {
        return try {
            if (hasPermissions()) {
                device.name ?: device.address
            } else {
                device.address
            }
        } catch (e: SecurityException) {
            device.address
        }
    }
}
