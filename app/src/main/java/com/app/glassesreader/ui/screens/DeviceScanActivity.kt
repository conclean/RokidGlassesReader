package com.app.glassesreader.ui.screens

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.app.glassesreader.sdk.CxrAuthManager
import com.app.glassesreader.sdk.CxrConnectionManager
import com.app.glassesreader.sdk.CxrCustomViewManager
import com.app.glassesreader.ui.components.SimplePermissionItem
import com.app.glassesreader.ui.theme.DarkButtonBackground
import com.app.glassesreader.ui.theme.GlassesReaderTheme
import com.app.glassesreader.ui.theme.LightButtonBackground
import CustomIconButton

/**
 * CXR-L 设备连接页：检测官方 App → 鉴权拿 token → connect 建链。
 * （保留原 Activity 类名以免改 Manifest 引用）
 */
class DeviceScanActivity : ComponentActivity() {

    companion object {
        private const val TAG = "DeviceScanActivity"
        private const val SUCCESS_DELAY_MS = 1500L
        private const val PREF_APP_SETTINGS = "gr_app_settings"
        private const val KEY_DARK_THEME = "dark_theme"
    }

    /** 过程/错误文案；busy 时展示，空闲时仅展示失败类提示 */
    private var statusText by mutableStateOf<String?>(null)
    private var isBusy by mutableStateOf(false)
    private var requiredAppInstalled by mutableStateOf(false)
    private var requiredAppName by mutableStateOf("Rokid AI App")
    private var linkConnected by mutableStateOf(false)
    private var hasSavedToken by mutableStateOf(false)

    private val connectionManager = CxrConnectionManager.getInstance()
    private lateinit var appPrefs: SharedPreferences

    private val mainHandler = Handler(Looper.getMainLooper())
    private val finishRunnable = Runnable {
        setResult(RESULT_OK)
        finish()
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
        refreshUiState()

        lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        refreshUiState()
                        if (linkConnected) {
                            // 总状态行已表达「已连接」，不再堆叠过程文案
                            statusText = null
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
                            isDarkTheme = isDarkTheme,
                            requiredAppName = requiredAppName,
                            requiredAppInstalled = requiredAppInstalled,
                            isBusy = isBusy,
                            statusText = statusText,
                            isConnected = linkConnected,
                            hasToken = hasSavedToken,
                            onAuthorizeAndConnect = ::authorizeAndConnect,
                            onConnectWithSavedToken = ::connectWithSavedToken,
                            onDone = { finish() },
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

    private fun refreshUiState() {
        requiredAppInstalled = CxrAuthManager.isRequiredAppInstalled(this)
        requiredAppName = CxrAuthManager.requiredAppLabel(this)
        linkConnected = connectionManager.isConnected()
        hasSavedToken = CxrAuthManager.hasSavedToken()
    }

    private fun authorizeAndConnect() {
        if (isBusy) return
        refreshUiState()
        if (!requiredAppInstalled) {
            statusText = "请先安装 $requiredAppName（≥ 1.9.0）"
            return
        }
        if (linkConnected) {
            statusText = null
            return
        }

        isBusy = true
        statusText = "正在请求授权…"

        val handledSync = CxrAuthManager.requestAuthorization(this) { outcome ->
            runOnUiThread { applyAuthOutcome(outcome) }
        }
        if (!handledSync) {
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
                hasSavedToken = true
                statusText = "授权成功，正在连接…"
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
            CxrAuthManager.AuthOutcome.Pending -> Unit
        }
    }

    private fun connectWithSavedToken() {
        val token = CxrAuthManager.getSavedToken()
        if (token.isNullOrBlank()) {
            statusText = "尚无本地授权，请先授权"
            hasSavedToken = false
            return
        }
        isBusy = true
        statusText = "正在连接…"
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
                    linkConnected = true
                    hasSavedToken = true
                    statusText = "连接成功"
                    CxrCustomViewManager.ensureInitialized()
                    mainHandler.postDelayed(finishRunnable, SUCCESS_DELAY_MS)
                }

                override fun onDisconnected() {
                    isBusy = false
                    linkConnected = false
                    statusText = "连接已断开"
                }

                override fun onFailed(message: String?) {
                    isBusy = false
                    linkConnected = false
                    statusText = "连接失败：${message ?: "unknown"}"
                }
            }
        )
    }
}

@Composable
private fun CxrLConnectScreen(
    isDarkTheme: Boolean,
    requiredAppName: String,
    requiredAppInstalled: Boolean,
    isBusy: Boolean,
    statusText: String?,
    isConnected: Boolean,
    hasToken: Boolean,
    onAuthorizeAndConnect: () -> Unit,
    onConnectWithSavedToken: () -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    val summaryTitle = when {
        isConnected -> "眼镜已连接"
        !requiredAppInstalled -> "未安装 $requiredAppName"
        isBusy -> "连接中…"
        hasToken -> "已授权，待连接"
        else -> "待授权"
    }
    val summaryCompleted = isConnected
    val showProcessSection = isBusy || (!statusText.isNullOrBlank() && !isConnected)
    val showSuccessHint = isConnected && !statusText.isNullOrBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CustomIconButton(
                onClick = onBack,
                size = 56.dp,
                containerColor = if (isDarkTheme) DarkButtonBackground else LightButtonBackground,
                contentColor = if (isDarkTheme) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "返回",
                    modifier = Modifier.size(24.dp),
                    tint = if (isDarkTheme) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
            Text(
                text = "设备连接",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "连接状态",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    Text(
                        text = "需通过官方应用（$requiredAppName ≥ 1.9.0 或 Hi Rokid）授权后连接；请先在官方应用内完成眼镜配对。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                    )

                    Divider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
                        thickness = 0.5.dp
                    )

                    SimplePermissionItem(
                        title = summaryTitle,
                        isCompleted = summaryCompleted,
                        onClick = {},
                        clickEnabled = false
                    )

                    if (showProcessSection) {
                        Divider(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
                            thickness = 0.5.dp
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isBusy) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                            statusText?.let { msg ->
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else if (showSuccessHint) {
                        Divider(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
                            thickness = 0.5.dp
                        )
                        Text(
                            text = statusText.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                }
            }
        }

        when {
            isConnected -> {
                Button(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("完成")
                }
            }
            !requiredAppInstalled -> {
                Text(
                    text = "请先安装 $requiredAppName 后再返回本页连接。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            else -> {
                // 有 token：主按钮「连接」，次要「重新授权」
                // 无 token：主按钮「授权并连接」
                if (hasToken) {
                    Button(
                        onClick = onConnectWithSavedToken,
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("连接")
                    }
                    OutlinedButton(
                        onClick = onAuthorizeAndConnect,
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("重新授权")
                    }
                } else {
                    Button(
                        onClick = onAuthorizeAndConnect,
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("授权并连接")
                    }
                }
            }
        }
    }
}
