package com.app.glassesreader.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.luminance
import androidx.compose.material.icons.filled.ArrowBack
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.app.glassesreader.sdk.CxrAuthManager
import com.app.glassesreader.sdk.CxrConnectionManager
import com.app.glassesreader.sdk.CxrCustomViewManager
import com.app.glassesreader.ui.theme.GlassesReaderTheme
import CustomIconButton

/**
 * CXR-L 连接页：检测官方 App → 鉴权拿 token → connect 建链。
 * （保留原 Activity 名以免改 Manifest；不再以 BLE 扫描为主路径）
 */
class DeviceScanActivity : ComponentActivity() {

    companion object {
        private const val TAG = "DeviceScanActivity"
        private const val SUCCESS_DELAY_MS = 1500L
        private const val PREF_APP_SETTINGS = "gr_app_settings"
        private const val KEY_DARK_THEME = "dark_theme"
    }

    private var statusText by mutableStateOf<String?>(null)
    private var isBusy by mutableStateOf(false)
    private var requiredAppInstalled by mutableStateOf(false)
    private var requiredAppName by mutableStateOf("Rokid AI App")

    private val connectionManager = CxrConnectionManager.getInstance()
    private lateinit var appPrefs: SharedPreferences

    private val mainHandler = Handler(Looper.getMainLooper())
    private val finishRunnable = Runnable {
        setResult(RESULT_OK)
        finish()
    }

    private val authLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            // AuthorizationHelper 走 startActivityForResult；部分机型会回调到此
            handleAuthResult(result.resultCode, result.data)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appPrefs = getSharedPreferences(PREF_APP_SETTINGS, Context.MODE_PRIVATE)
        val systemDarkMode =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        val isDarkTheme = appPrefs.getBoolean(KEY_DARK_THEME, systemDarkMode)

        CxrAuthManager.init(this)
        connectionManager.init(this)
        refreshRequiredAppState()

        lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        refreshRequiredAppState()
                        if (connectionManager.isConnected()) {
                            statusText = "已连接（链路就绪）"
                        }
                    }
                    Lifecycle.Event.ON_DESTROY -> {
                        mainHandler.removeCallbacks(finishRunnable)
                    }
                    else -> Unit
                }
            }
        })

        setContent {
            GlassesReaderTheme(darkTheme = isDarkTheme) {
                Scaffold { innerPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        CxrLConnectScreen(
                            requiredAppName = requiredAppName,
                            requiredAppInstalled = requiredAppInstalled,
                            isBusy = isBusy,
                            statusText = statusText,
                            isConnected = connectionManager.isConnected(),
                            hasToken = CxrAuthManager.hasSavedToken(),
                            onAuthorizeAndConnect = ::authorizeAndConnect,
                            onConnectWithSavedToken = ::connectWithSavedToken,
                            onBack = { finish() }
                        )
                    }
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CxrAuthManager.AUTH_REQUEST_CODE) {
            handleAuthResult(resultCode, data)
        }
    }

    private fun refreshRequiredAppState() {
        requiredAppInstalled = CxrAuthManager.isRequiredAppInstalled(this)
        requiredAppName = CxrAuthManager.requiredAppLabel(this)
    }

    private fun authorizeAndConnect() {
        if (isBusy) return
        refreshRequiredAppState()
        if (!requiredAppInstalled) {
            statusText = "请先安装 $requiredAppName（≥ 1.9.0）"
            return
        }
        if (connectionManager.isConnected()) {
            statusText = "已连接，无需重复连接"
            return
        }

        isBusy = true
        statusText = "正在请求授权…"

        val handledSync = CxrAuthManager.requestAuthorization(this) { outcome ->
            runOnUiThread { applyAuthOutcome(outcome) }
        }
        if (!handledSync) {
            // 等待 onActivityResult；保持 isBusy
            statusText = "请在官方应用中完成授权…"
        }
    }

    private fun handleAuthResult(resultCode: Int, data: Intent?) {
        val outcome = CxrAuthManager.handleActivityResult(resultCode, data)
        applyAuthOutcome(outcome)
    }

    private fun applyAuthOutcome(outcome: CxrAuthManager.AuthOutcome) {
        when (outcome) {
            is CxrAuthManager.AuthOutcome.Success -> {
                statusText = "授权成功，正在连接眼镜…"
                connectWithToken(outcome.token)
            }
            is CxrAuthManager.AuthOutcome.Failed -> {
                isBusy = false
                statusText = "授权失败：${outcome.message}"
            }
            CxrAuthManager.AuthOutcome.Cancelled -> {
                isBusy = false
                statusText = "已取消授权"
            }
            CxrAuthManager.AuthOutcome.Pending -> {
                // ignore
            }
        }
    }

    private fun connectWithSavedToken() {
        val token = CxrAuthManager.getSavedToken()
        if (token.isNullOrBlank()) {
            statusText = "尚无本地 token，请先授权"
            return
        }
        isBusy = true
        statusText = "正在使用已保存凭证连接…"
        connectWithToken(token)
    }

    private fun connectWithToken(token: String) {
        connectionManager.connectWithToken(
            this,
            token,
            object : CxrConnectionManager.ConnectionCallback {
                override fun onConnected() {
                    Log.d(TAG, "Link ready")
                    isBusy = false
                    statusText = "连接成功！正在打开自定义页面…"
                    CxrCustomViewManager.ensureInitialized()
                    mainHandler.postDelayed(finishRunnable, SUCCESS_DELAY_MS)
                }

                override fun onDisconnected() {
                    isBusy = false
                    statusText = "连接已断开"
                }

                override fun onFailed(message: String?) {
                    isBusy = false
                    statusText = "连接失败：${message ?: "unknown"}"
                }
            }
        )
    }
}

@Composable
private fun CxrLConnectScreen(
    requiredAppName: String,
    requiredAppInstalled: Boolean,
    isBusy: Boolean,
    statusText: String?,
    isConnected: Boolean,
    hasToken: Boolean,
    onAuthorizeAndConnect: () -> Unit,
    onConnectWithSavedToken: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CustomIconButton(
                onClick = onBack,
                size = 56.dp,
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ) {
                androidx.compose.material3.Icon(
                    imageVector = androidx.compose.material.icons.Icons.Filled.ArrowBack,
                    contentDescription = "返回"
                )
            }
            Text(
                text = "设备连接",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Text(
            text = "CXR-L 需通过 $requiredAppName 鉴权后与眼镜建链。请先确保官方应用已安装，并完成眼镜在官方应用内的配对。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = if (requiredAppInstalled) {
                "已检测到 $requiredAppName"
            } else {
                "未检测到 $requiredAppName，请先安装"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (requiredAppInstalled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            }
        )

        Text(
            text = when {
                isConnected -> "状态：已连接"
                hasToken -> "状态：已有本地授权，可直接连接"
                else -> "状态：未授权"
            },
            style = MaterialTheme.typography.bodyMedium
        )

        if (isBusy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        statusText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onAuthorizeAndConnect,
            enabled = !isBusy && requiredAppInstalled && !isConnected,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (hasToken) "重新授权并连接" else "授权并连接")
        }

        if (hasToken && !isConnected) {
            Button(
                onClick = onConnectWithSavedToken,
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("使用已保存授权连接")
            }
        }
    }
}
