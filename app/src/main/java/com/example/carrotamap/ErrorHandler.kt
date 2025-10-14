package com.example.carrotamap

import android.util.Log
import kotlinx.coroutines.delay
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.json.JSONException

/**
 * 异常处理工具类
 * 提供统一的异常处理策略，区分不同类型的异常并采取相应的处理措施
 */
object ErrorHandler {
    
    /**
     * 异常类型枚举
     */
    enum class ErrorType {
        NETWORK_TIMEOUT,      // 网络超时
        NETWORK_IO,           // 网络IO异常
        NETWORK_CONNECTION,   // 网络连接异常
        NETWORK_UNKNOWN_HOST, // 未知主机异常
        JSON_PARSE,           // JSON解析异常
        PERMISSION_DENIED,    // 权限被拒绝
        SYSTEM_ERROR,         // 系统错误
        UNKNOWN              // 未知异常
    }
    
    /**
     * 异常处理结果
     */
    data class ErrorResult(
        val type: ErrorType,
        val message: String,
        val shouldRetry: Boolean,
        val retryDelayMs: Long = 0L,
        val maxRetries: Int = 3
    )
    
    /**
     * 分析异常并返回处理策略
     */
    fun analyzeException(exception: Throwable): ErrorResult {
        return when (exception) {
            is SocketTimeoutException -> ErrorResult(
                type = ErrorType.NETWORK_TIMEOUT,
                message = "网络连接超时",
                shouldRetry = true,
                retryDelayMs = 1000L,
                maxRetries = 5
            )
            is ConnectException -> ErrorResult(
                type = ErrorType.NETWORK_CONNECTION,
                message = "网络连接失败：${exception.message}",
                shouldRetry = true,
                retryDelayMs = 2000L,
                maxRetries = 3
            )
            is UnknownHostException -> ErrorResult(
                type = ErrorType.NETWORK_UNKNOWN_HOST,
                message = "无法解析主机地址：${exception.message}",
                shouldRetry = true,
                retryDelayMs = 5000L,
                maxRetries = 2
            )
            is IOException -> ErrorResult(
                type = ErrorType.NETWORK_IO,
                message = "网络IO异常：${exception.message}",
                shouldRetry = true,
                retryDelayMs = 1500L,
                maxRetries = 3
            )
            is JSONException -> ErrorResult(
                type = ErrorType.JSON_PARSE,
                message = "数据解析失败：${exception.message}",
                shouldRetry = false,
                retryDelayMs = 0L,
                maxRetries = 0
            )
            is SecurityException -> ErrorResult(
                type = ErrorType.PERMISSION_DENIED,
                message = "权限被拒绝：${exception.message}",
                shouldRetry = false,
                retryDelayMs = 0L,
                maxRetries = 0
            )
            else -> ErrorResult(
                type = ErrorType.UNKNOWN,
                message = "未知异常：${exception.message}",
                shouldRetry = false,
                retryDelayMs = 2000L,
                maxRetries = 1
            )
        }
    }
    
    /**
     * 记录异常日志
     */
    fun logError(tag: String, operation: String, exception: Throwable, errorResult: ErrorResult) {
        val logMessage = "[$operation] ${errorResult.message} - 类型: ${errorResult.type}"
        
        when (errorResult.type) {
            ErrorType.NETWORK_TIMEOUT -> Log.w(tag, logMessage)
            ErrorType.NETWORK_IO, ErrorType.NETWORK_CONNECTION -> Log.e(tag, logMessage, exception)
            ErrorType.JSON_PARSE -> Log.e(tag, logMessage, exception)
            ErrorType.PERMISSION_DENIED -> Log.e(tag, logMessage, exception)
            ErrorType.SYSTEM_ERROR -> Log.e(tag, logMessage, exception)
            ErrorType.NETWORK_UNKNOWN_HOST -> Log.w(tag, logMessage)
            ErrorType.UNKNOWN -> Log.e(tag, logMessage, exception)
        }
    }
    
    /**
     * 带重试机制的异步操作执行器
     */
    suspend fun <T> executeWithRetry(
        operation: String,
        tag: String,
        maxRetries: Int = 3,
        block: suspend () -> T
    ): T? {
        var lastException: Throwable? = null
        var retryCount = 0
        
        while (retryCount <= maxRetries) {
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                val errorResult = analyzeException(e)
                logError(tag, operation, e, errorResult)
                
                if (!errorResult.shouldRetry || retryCount >= maxRetries) {
                    Log.e(tag, "操作失败，停止重试：$operation")
                    break
                }
                
                retryCount++
                Log.i(tag, "第 $retryCount 次重试 $operation，延迟 ${errorResult.retryDelayMs}ms")
                delay(errorResult.retryDelayMs)
            }
        }
        
        lastException?.let {
            Log.e(tag, "操作最终失败：$operation", it)
        }
        return null
    }
    
    /**
     * 记录操作成功日志
     */
    fun logSuccess(tag: String, operation: String, details: String = "") {
        val message = if (details.isNotEmpty()) "$operation - $details" else operation
        if (AppConstants.Logging.ENABLE_DEBUG_LOGS) {
            Log.d(tag, "✅ $message")
        }
    }
    
    /**
     * 记录操作警告日志
     */
    fun logWarning(tag: String, operation: String, warning: String) {
        Log.w(tag, "⚠️ [$operation] $warning")
    }
    
    /**
     * 记录详细调试信息
     */
    fun logDebug(tag: String, message: String) {
        if (AppConstants.Logging.ENABLE_DEBUG_LOGS) {
            Log.d(tag, message)
        }
    }
    
    /**
     * 记录详细调试信息（仅在启用详细日志时）
     */
    fun logVerbose(tag: String, message: String) {
        if (AppConstants.Logging.ENABLE_VERBOSE_LOGS) {
            Log.v(tag, message)
        }
    }
} 