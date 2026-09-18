package com.app.glassesreader.sdk

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.app.glassesreader.GlassesReaderApp
import com.rokid.cxr.link.CXRLink
import com.rokid.cxr.link.callbacks.ICXRLinkCbk
import com.rokid.cxr.link.utils.CxrDefs
import com.rokid.cxr.link.utils.GlassInfo

/**
 * CXR-L 连接与会话管理：token → CXRLink(CUSTOMVIEW) → 链路就绪。
 *
 * 参考：sdk/CXR L SDK/07-连接与会话.md
 *
 * 链路就绪：onCXRLConnected(true) 且 onGlassBtConnected(true)
 */
class CxrConnectionManager private constructor() {

    companion object {
        private const val TAG = "CxrConnectionManager"
        private const val DEFAULT_CONNECTION_TIMEOUT_MS = 20_000L
        /** 自动重连等待链路就绪的上限 */
        private const val AUTO_RECONNECT_TIMEOUT_MS = 5_000L

        @Volatile
        private var INSTANCE: CxrConnectionManager? = null

        fun getInstance(): CxrConnectionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CxrConnectionManager().also { INSTANCE = it }
            }
        }
    }

    interface ConnectionCallback {
        fun onConnected()
        fun onDisconnected()
        fun onFailed(message: String?)
        /** CXR-L 无 UUID/MAC 回调，保留空实现兼容旧调用方 */
        fun onConnectionInfo(
            socketUuid: String?,
            macAddress: String?,
            rokidAccount: String?,
            glassesType: Int
        ) {
        }
    }

    private var appContext: Context? = null
    private var connectionCallback: ConnectionCallback? = null

    @Volatile
    private var cxrlConnected: Boolean = false

    @Volatile
    private var glassBtConnected: Boolean = false

    @Volatile
    private var isConnecting: Boolean = false

    private var timeoutHandler: Handler? = null
    private var timeoutRunnable: Runnable? = null
    private var pendingTimeoutMs: Long = DEFAULT_CONNECTION_TIMEOUT_MS

    private val mainHandler = Handler(Looper.getMainLooper())

    private val linkCallback = object : ICXRLinkCbk {
        override fun onCXRLConnected(connected: Boolean) {
            Log.d(TAG, "onCXRLConnected: $connected")
            cxrlConnected = connected
            evaluateLinkState()
        }

        override fun onGlassBtConnected(connected: Boolean) {
            Log.d(TAG, "onGlassBtConnected: $connected")
            glassBtConnected = connected
            evaluateLinkState()
        }

        override fun onGlassDeviceInfo(info: GlassInfo) {
            Log.d(TAG, "onGlassDeviceInfo: $info")
        }

        override fun onGlassWearingStatus(wearing: Boolean) {
            Log.d(TAG, "onGlassWearingStatus: $wearing")
        }

        override fun onGlassAiAssistStart() {
            Log.d(TAG, "onGlassAiAssistStart")
        }

        override fun onGlassAiAssistStop() {
            Log.d(TAG, "onGlassAiAssistStop")
        }

        override fun onGlassAiInterrupt(interrupt: Boolean) {
            Log.d(TAG, "onGlassAiInterrupt: $interrupt")
        }
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        CxrAuthManager.init(context)
        Log.d(TAG, "Initialized, hasToken=${CxrAuthManager.hasSavedToken()}")
    }

    /** 兼容旧 UI 字段名：现表示是否有已保存的鉴权 token */
    fun hasSavedConnectionInfo(): Boolean = CxrAuthManager.hasSavedToken()

    fun getLink(): CXRLink? = GlassesReaderApp.sharedLink

    fun isLinkReady(): Boolean = cxrlConnected && glassBtConnected

    fun isConnected(): Boolean {
        if (isLinkReady()) return true
        // 若进程内已有 link，以就绪标志为准
        return GlassesReaderApp.sharedLink != null && isLinkReady()
    }

    /**
     * 使用已保存 token 自动建链。无 token 时返回 false 且不回调。
     */
    fun autoReconnect(callback: ConnectionCallback? = null): Boolean {
        val token = CxrAuthManager.getSavedToken()
        if (token.isNullOrBlank()) {
            Log.d(TAG, "autoReconnect skipped: no saved token")
            return false
        }
        val ctx = appContext
        if (ctx == null) {
            Log.e(TAG, "autoReconnect skipped: context null")
            return false
        }
        connectWithToken(ctx, token, callback, AUTO_RECONNECT_TIMEOUT_MS)
        return true
    }

    /**
     * 使用指定 token 建立 CUSTOMVIEW 会话并 connect。
     */
    fun connectWithToken(
        context: Context,
        token: String,
        callback: ConnectionCallback? = null,
        timeoutMs: Long = DEFAULT_CONNECTION_TIMEOUT_MS
    ) {
        if (token.isBlank()) {
            callback?.onFailed("empty token")
            return
        }
        if (isConnecting) {
            Log.w(TAG, "Already connecting, ignore duplicate connectWithToken")
            return
        }
        if (isLinkReady() && GlassesReaderApp.sharedLink != null) {
            Log.d(TAG, "Already link-ready")
            callback?.onConnected()
            return
        }

        appContext = context.applicationContext
        connectionCallback = callback
        isConnecting = true
        cxrlConnected = false
        glassBtConnected = false
        pendingTimeoutMs = timeoutMs.coerceAtLeast(1_000L)
        CxrAuthManager.saveToken(token)

        runCatching {
            // 先断开旧实例，避免多 link 并行
            GlassesReaderApp.sharedLink?.let { old ->
                runCatching { old.disconnect() }
            }
            GlassesReaderApp.sharedLink = null

            val link = CXRLink(context.applicationContext).apply {
                configCXRSession(CxrDefs.CXRSession(CxrDefs.CXRSessionType.CUSTOMVIEW))
                setCXRLinkCbk(linkCallback)
            }
            GlassesReaderApp.sharedLink = link

            val started = link.connect(token)
            Log.d(TAG, "connect(token) started=$started")
            if (!started) {
                finishConnectingAsFailed("connect() returned false")
                return
            }
            startTimeout()
        }.onFailure {
            Log.e(TAG, "connectWithToken error: ${it.message}", it)
            finishConnectingAsFailed(it.message)
        }
    }

    fun disconnect() {
        cancelTimeout()
        isConnecting = false
        cxrlConnected = false
        glassBtConnected = false
        runCatching {
            GlassesReaderApp.sharedLink?.disconnect()
        }.onFailure {
            Log.e(TAG, "disconnect failed: ${it.message}", it)
        }
        GlassesReaderApp.sharedLink = null
        Log.d(TAG, "Disconnected")
        mainHandler.post {
            connectionCallback?.onDisconnected()
        }
    }

    private fun evaluateLinkState() {
        val ready = cxrlConnected && glassBtConnected
        if (ready && isConnecting) {
            cancelTimeout()
            isConnecting = false
            Log.d(TAG, "Link ready (CXRL + GlassBt)")
            mainHandler.post {
                connectionCallback?.onConnected()
            }
        } else if (!cxrlConnected && !isConnecting && GlassesReaderApp.sharedLink != null) {
            Log.d(TAG, "CXRL disconnected while session existed")
            mainHandler.post {
                connectionCallback?.onDisconnected()
            }
        }
    }

    private fun startTimeout() {
        cancelTimeout()
        timeoutHandler = Handler(Looper.getMainLooper())
        timeoutRunnable = Runnable {
            if (isConnecting && !isLinkReady()) {
                Log.e(TAG, "Connection timeout")
                finishConnectingAsFailed("connection timeout")
            }
        }
        timeoutHandler?.postDelayed(timeoutRunnable!!, pendingTimeoutMs)
    }

    private fun cancelTimeout() {
        timeoutRunnable?.let { timeoutHandler?.removeCallbacks(it) }
        timeoutHandler = null
        timeoutRunnable = null
    }

    private fun finishConnectingAsFailed(message: String?) {
        cancelTimeout()
        isConnecting = false
        runCatching { GlassesReaderApp.sharedLink?.disconnect() }
        GlassesReaderApp.sharedLink = null
        cxrlConnected = false
        glassBtConnected = false
        mainHandler.post {
            connectionCallback?.onFailed(message)
        }
    }
}
