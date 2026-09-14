package com.app.glassesreader.sdk

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import com.rokid.sprite.aiapp.externalapp.auth.AuthResult
import com.rokid.sprite.aiapp.externalapp.auth.AuthorizationHelper
import com.rokid.sprite.aiapp.externalapp.auth.GlassPermission

/**
 * CXR-L 鉴权管理：检测官方 App、发起授权、持久化 token。
 *
 * 参考：sdk/CXR L SDK/06-鉴权.md
 */
object CxrAuthManager {

    private const val TAG = "CxrAuthManager"
    private const val PREFS_NAME = "cxr_auth_prefs"
    private const val KEY_TOKEN = "auth_token"

    const val AUTH_REQUEST_CODE = 1001

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getSavedToken(): String? =
        prefs?.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }

    fun hasSavedToken(): Boolean = !getSavedToken().isNullOrBlank()

    fun clearToken() {
        prefs?.edit()?.remove(KEY_TOKEN)?.apply()
        Log.d(TAG, "Auth token cleared")
    }

    fun saveToken(token: String) {
        prefs?.edit()?.putString(KEY_TOKEN, token)?.apply()
        Log.d(TAG, "Auth token saved")
    }

    /** 是否已安装可用的必要官方 App（Rokid AI / Hi Rokid）。 */
    fun isRequiredAppInstalled(activity: Activity): Boolean {
        return runCatching {
            AuthorizationHelper.isRequiredRokidAppInstalled(activity) ||
                AuthorizationHelper.isRequiredHiRokidInstalled(activity)
        }.onFailure {
            Log.e(TAG, "Check required app failed: ${it.message}", it)
        }.getOrDefault(false)
    }

    fun requiredAppLabel(activity: Activity): String {
        return when {
            runCatching { AuthorizationHelper.isConnectHiRokid() }.getOrDefault(false) -> "Hi Rokid"
            else -> "Rokid AI App"
        }
    }

    /**
     * 发起授权。若 SDK 同步返回结果（此前已授权），则直接解析并回调，返回 true。
     * 否则等待 [Activity.onActivityResult] / Activity Result API，返回 false。
     */
    fun requestAuthorization(
        activity: Activity,
        onResult: (AuthOutcome) -> Unit
    ): Boolean {
        val pair = runCatching {
            AuthorizationHelper.requestAuthorization(
                activity,
                emptyArray<GlassPermission>(),
                AUTH_REQUEST_CODE
            )
        }.onFailure {
            Log.e(TAG, "requestAuthorization failed: ${it.message}", it)
            onResult(AuthOutcome.Failed(it.message ?: "requestAuthorization exception"))
            return true
        }.getOrNull() ?: run {
            onResult(AuthOutcome.Failed("requestAuthorization returned null"))
            return true
        }

        // Pair first = resultCode, second = data；文档：若已授权可能直接返回
        val resultCode = pair.first
        val data = pair.second
        if (resultCode != Activity.RESULT_CANCELED || data != null) {
            // 同步结果：立刻解析（含成功 / 失败 / 取消）
            val outcome = parseResult(resultCode, data)
            if (outcome !is AuthOutcome.Pending) {
                onResult(outcome)
                return true
            }
        }
        Log.d(TAG, "Authorization UI launched, waiting for activity result")
        return false
    }

    fun handleActivityResult(resultCode: Int, data: Intent?): AuthOutcome {
        return parseResult(resultCode, data)
    }

    private fun parseResult(resultCode: Int, data: Intent?): AuthOutcome {
        val result = runCatching {
            AuthorizationHelper.parseAuthorizationResult(resultCode, data)
        }.onFailure {
            Log.e(TAG, "parseAuthorizationResult failed: ${it.message}", it)
            return AuthOutcome.Failed(it.message ?: "parse failed")
        }.getOrNull() ?: return AuthOutcome.Failed("parse returned null")

        return when (result) {
            is AuthResult.AuthSuccess -> {
                val token = result.token
                if (token.isBlank()) {
                    clearToken()
                    AuthOutcome.Failed("empty token")
                } else {
                    saveToken(token)
                    AuthOutcome.Success(token)
                }
            }
            is AuthResult.AuthFail -> {
                clearToken()
                AuthOutcome.Failed("auth fail")
            }
            is AuthResult.AuthCancel -> {
                clearToken()
                AuthOutcome.Cancelled
            }
            else -> {
                clearToken()
                AuthOutcome.Failed("unknown auth result: $result")
            }
        }
    }

    sealed class AuthOutcome {
        data class Success(val token: String) : AuthOutcome()
        data class Failed(val message: String) : AuthOutcome()
        data object Cancelled : AuthOutcome()
        /** 仅内部使用：表示尚无最终结果 */
        data object Pending : AuthOutcome()
    }
}
