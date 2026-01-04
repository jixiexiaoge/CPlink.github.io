package com.example.carrotamap

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

/**
 * 蓝牙设备管理器
 * 负责蓝牙设备的扫描、连接和数据发送
 */
class BluetoothHelper(private val context: Context) {
    companion object {
        private const val TAG = "BluetoothHelper"
        // 标准串口 UUID
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val bluetoothManager: BluetoothManager? = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    // 连接状态
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    // 连接的设备名称
    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()

    // 扫描到的设备列表
    private val _scannedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val scannedDevices: StateFlow<List<BluetoothDevice>> = _scannedDevices.asStateFlow()

    // 当前 socket
    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    /**
     * 检查蓝牙权限
     */
    fun hasPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                   ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        }
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 开始扫描设备
     * 注意：调用前需确保已获取权限
     */
    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!hasPermissions() || bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.w(TAG, "无法扫描：权限不足或蓝牙未开启")
            return
        }

        // 获取已配对设备
        val pairedDevices = bluetoothAdapter.bondedDevices
        val deviceList = pairedDevices.toList()
        _scannedDevices.value = deviceList
        
        // 注意：为了简化，这里暂时只列出已配对设备。
        // 如果需要发现新设备，需要注册 BroadcastReceiver 监听 ACTION_FOUND，
        // 并调用 bluetoothAdapter.startDiscovery()。
        // 对于车载应用，通常连接的是已配对设备。
        
        Log.i(TAG, "已加载 ${deviceList.size} 个已配对设备")
    }

    /**
     * 连接设备
     */
    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice, onResult: (Boolean) -> Unit) {
        if (!hasPermissions()) {
            onResult(false)
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 取消扫描以加快连接速度
                bluetoothAdapter?.cancelDiscovery()

                val tmpSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                tmpSocket.connect()
                
                socket = tmpSocket
                outputStream = tmpSocket.outputStream
                
                _isConnected.value = true
                _connectedDeviceName.value = device.name ?: device.address
                
                // 保存最后连接的设备地址，以便自动连接
                saveLastDeviceAddress(device.address)
                
                withContext(Dispatchers.Main) {
                    onResult(true)
                }
                Log.i(TAG, "已连接到设备: ${device.name}")
            } catch (e: IOException) {
                Log.e(TAG, "连接失败", e)
                try {
                    socket?.close()
                } catch (closeException: IOException) {
                    Log.e(TAG, "关闭 socket 失败", closeException)
                }
                socket = null
                outputStream = null
                _isConnected.value = false
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
        try {
            outputStream?.close()
            socket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "断开连接失败", e)
        } finally {
            socket = null
            outputStream = null
            _isConnected.value = false
            _connectedDeviceName.value = null
        }
    }

    /**
     * 发送数据
     */
    fun sendData(message: String) {
        if (socket == null || outputStream == null) {
            Log.w(TAG, "未连接，无法发送数据")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                outputStream?.write(message.toByteArray())
                Log.d(TAG, "已发送数据: $message")
            } catch (e: IOException) {
                Log.e(TAG, "发送数据失败", e)
                // 发送失败可能意味着连接断开
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
        
        val device = bluetoothAdapter.getRemoteDevice(lastAddress)
        if (device != null) {
            Log.i(TAG, "尝试自动连接到上次设备: $lastAddress")
            connect(device) { success ->
                if (success) {
                    Log.i(TAG, "自动连接成功")
                } else {
                    Log.w(TAG, "自动连接失败")
                }
            }
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

    /**
     * 安全获取设备名称
     */
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
