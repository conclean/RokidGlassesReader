package com.app.glassesreader.metrics

import android.content.Context
import android.util.Log

/**
 * 读屏会话时长记录（开启到关闭）。
 */
object ReadingSessionTracker {
    private const val LOG_TAG = "ReadingSession"
    private const val PREF_NAME = "gr_reading_session"
    private const val KEY_SESSION_START_AT = "session_start_at_ms"

    const val EVENT_READING_START = "reading_start"
    const val EVENT_READING_END = "reading_end"

    fun onReadingStarted(context: Context) {
        val prefs = prefs(context)
        if (prefs.getLong(KEY_SESSION_START_AT, 0L) > 0L) return
        val now = System.currentTimeMillis()
        prefs.edit().putLong(KEY_SESSION_START_AT, now).apply()
        AppMetrics.trackEvent(context, EVENT_READING_START)
        Log.d(LOG_TAG, "session start")
    }

    fun onReadingStopped(context: Context, reason: String) {
        val prefs = prefs(context)
        val startAt = prefs.getLong(KEY_SESSION_START_AT, 0L)
        if (startAt <= 0L) return
        val durationSec = ((System.currentTimeMillis() - startAt) / 1000L).coerceAtLeast(0L)
        prefs.edit().remove(KEY_SESSION_START_AT).apply()
        AppMetrics.trackEvent(
            context,
            EVENT_READING_END,
            mapOf(
                "duration_sec" to durationSec.toString(),
                "reason" to reason
            )
        )
        Log.d(LOG_TAG, "session end sec=$durationSec reason=$reason")
    }

    fun flushOrphanSessionIfNeeded(context: Context) {
        if (prefs(context).getLong(KEY_SESSION_START_AT, 0L) > 0L) {
            onReadingStopped(context, "orphan")
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
}
