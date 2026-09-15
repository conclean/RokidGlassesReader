package com.app.glassesreader.metrics

import android.app.Application
import android.content.Context
import android.util.Log
import com.umeng.analytics.MobclickAgent
import com.umeng.commonsdk.UMConfigure

/**
 * 应用启动时的基础诊断初始化（匿名、无业务侵入）。
 */
object AppMetrics {
    private const val LOG_TAG = "AppMetrics"
    private const val APP_KEY = "6aa8fdb1d5481f0b42f471a6"
    private const val CHANNEL = "github"
    private const val BOOT_EVENT = "umeng_onboarding_test"

    @Volatile
    private var initialized = false

    fun init(app: Application) {
        if (initialized) return
        try {
            UMConfigure.setLogEnabled(false)
            UMConfigure.preInit(app, APP_KEY, CHANNEL)
            UMConfigure.init(
                app,
                APP_KEY,
                CHANNEL,
                UMConfigure.DEVICE_TYPE_PHONE,
                null
            )
            MobclickAgent.setPageCollectionMode(MobclickAgent.PageMode.AUTO)
            initialized = true
            Log.d(LOG_TAG, "Metrics bootstrap ok")
            trackBootEvent(app)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Metrics bootstrap failed", e)
        }
    }

    private fun trackBootEvent(context: Context) {
        try {
            MobclickAgent.onEvent(context, BOOT_EVENT)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Boot event failed", e)
        }
    }

    fun trackEvent(context: Context, eventId: String, params: Map<String, String>? = null) {
        if (!initialized) return
        try {
            if (params.isNullOrEmpty()) {
                MobclickAgent.onEvent(context, eventId)
            } else {
                MobclickAgent.onEvent(context, eventId, params)
            }
        } catch (e: Exception) {
            Log.e(LOG_TAG, "trackEvent failed: $eventId", e)
        }
    }
}
