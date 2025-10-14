package com.example.carrotamap.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.carrotamap.NetworkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.rememberCoroutineScope

/**
 * WebRTC视频组件
 */
@Composable
fun TopWebRtcBox(networkManager: NetworkManager?) {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp
    val height = screenWidthDp * 10f / 16f
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // 获取设备IP
    val deviceIp = remember { networkManager?.getCurrentDeviceIP() }

    // 是否显示 WebRTC 画面（根据连接结果自动隐藏/显示）
    var showRtc by remember { mutableStateOf(false) }

    // 尝试连接设备：能连通则显示，否则隐藏
    LaunchedEffect(deviceIp) {
        if (deviceIp.isNullOrEmpty()) {
            showRtc = false
        } else {
            showRtc = withContext(Dispatchers.IO) {
                try {
                    // 简易连通性检测：尝试连接 5088 端口（可根据实际信令/服务端口调整）
                    java.net.Socket().use { s ->
                        s.connect(java.net.InetSocketAddress(deviceIp, 5088), 1200)
                        true
                    }
                } catch (_: Exception) {
                    false
                }
            }
        }
    }

    // 监听页面返回（onResume）时自动重试连接
    DisposableEffect(lifecycleOwner, deviceIp) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                if (!deviceIp.isNullOrEmpty()) {
                    // 启动协程重试（使用 rememberCoroutineScope）
                    scope.launch {
                        showRtc = withContext(Dispatchers.IO) {
                            try {
                                java.net.Socket().use { s ->
                                    s.connect(java.net.InetSocketAddress(deviceIp, 5088), 1200)
                                    true
                                }
                            } catch (_: Exception) {
                                false
                            }
                        }
                    }
                } else {
                    showRtc = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showRtc) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
            factory = { ctx ->
                val svr = org.webrtc.SurfaceViewRenderer(ctx)
                val viewer = com.example.carrotamap.webrtc.WebRtcViewer(ctx, svr, deviceIp ?: "")
                svr.tag = viewer
                // 初始化成功则开始播放
                try { if (!deviceIp.isNullOrEmpty()) viewer.start() } catch (_: Exception) {}
                svr
            },
            update = { view ->
                val viewer = view.tag as? com.example.carrotamap.webrtc.WebRtcViewer
                if (deviceIp.isNullOrEmpty()) {
                    try { viewer?.stop() } catch (_: Exception) {}
                }
            }
        )
    } else {
        // 不显示任何内容（自动隐藏实时画面方框）
    }
}

/**
 * WebView组件
 */
@Composable
fun TopWebBox(networkManager: NetworkManager?) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp
    // 移除固定高度限制，让WebView根据内容自动调整高度

    // 计算目标URL：仅当有设备IP时使用 http://设备IP:5088，否则显示空白
    val targetUrl = try {
        val ip = networkManager?.getCurrentDeviceIP()
        if (!ip.isNullOrEmpty()) "http://$ip:5088" else "about:blank"
    } catch (_: Exception) {
        "about:blank"
    }

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(), // 改为根据内容自动调整高度
        factory = { ctx ->
            android.webkit.WebView(ctx).apply {
                // 启用Cookie（含第三方Cookie），保证站点会话/登录/跳转等完整
                val cm = android.webkit.CookieManager.getInstance()
                cm.setAcceptCookie(true)
                try { android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true) } catch (_: Throwable) {}

                // 基本设置：尽量接近完整浏览器能力
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.setSupportMultipleWindows(true)
                settings.javaScriptCanOpenWindowsAutomatically = true
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                settings.mediaPlaybackRequiresUserGesture = false
                settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = false // 改为false，避免强制缩放
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.allowContentAccess = true
                settings.allowFileAccess = true
                // 适当调整UA，避免被判定为简化版
                settings.userAgentString = settings.userAgentString + " CarrotAmapWebView"

                // WebViewClient：放行由WebView处理（返回false），并处理HTTP/HTTPS
                webViewClient = object : android.webkit.WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: android.webkit.WebView?,
                        request: android.webkit.WebResourceRequest?
                    ): Boolean {
                        val u = request?.url ?: return false
                        return !(u.scheme == "http" || u.scheme == "https")
                    }

                    @Suppress("DEPRECATION")
                    override fun shouldOverrideUrlLoading(view: android.webkit.WebView?, url: String?): Boolean {
                        if (url.isNullOrEmpty()) return false
                        val uri = android.net.Uri.parse(url)
                        return !(uri.scheme == "http" || uri.scheme == "https")
                    }
                }

                // WebChromeClient：处理新窗口、JS对话框、地理位置等
                webChromeClient = object : android.webkit.WebChromeClient() {
                    override fun onCreateWindow(
                        view: android.webkit.WebView?,
                        isDialog: Boolean,
                        isUserGesture: Boolean,
                        resultMsg: android.os.Message?
                    ): Boolean {
                        val transport = resultMsg?.obj as? android.webkit.WebView.WebViewTransport
                        transport?.webView = this@apply
                        resultMsg?.sendToTarget()
                        return true
                    }

                    override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: android.webkit.GeolocationPermissions.Callback?) {
                        callback?.invoke(origin, true, false)
                    }
                }

                // 去除回弹
                overScrollMode = android.view.View.OVER_SCROLL_NEVER
                // 加载目标URL
                loadUrl(targetUrl)
            }
        }
    )
}
