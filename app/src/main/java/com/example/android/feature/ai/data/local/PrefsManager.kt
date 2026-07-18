package com.example.android.feature.ai.data.local
import com.example.android.feature.ai.data.local.PrefsManager
import com.example.android.R

import android.content.Context
import android.content.SharedPreferences

object PrefsManager {
    private const val PREFS_NAME = "IAGestosPrefs"
    private const val KEY_SCHEDULE_ENABLED = "schedule_enabled"
    private const val KEY_START_HOUR = "start_hour"
    private const val KEY_START_MINUTE = "start_minute"
    private const val KEY_END_HOUR = "end_hour"
    private const val KEY_END_MINUTE = "end_minute"
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    fun isScheduleEnabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_SCHEDULE_ENABLED, false)
    fun setScheduleEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_SCHEDULE_ENABLED, enabled).apply()
    
    fun getStartHour(context: Context): Int = getPrefs(context).getInt(KEY_START_HOUR, 8)
    fun setStartHour(context: Context, hour: Int) = getPrefs(context).edit().putInt(KEY_START_HOUR, hour).apply()
    
    fun getStartMinute(context: Context): Int = getPrefs(context).getInt(KEY_START_MINUTE, 0)
    fun setStartMinute(context: Context, minute: Int) = getPrefs(context).edit().putInt(KEY_START_MINUTE, minute).apply()
    
    fun getEndHour(context: Context): Int = getPrefs(context).getInt(KEY_END_HOUR, 18)
    fun setEndHour(context: Context, hour: Int) = getPrefs(context).edit().putInt(KEY_END_HOUR, hour).apply()
    
    fun getEndMinute(context: Context): Int = getPrefs(context).getInt(KEY_END_MINUTE, 0)
    fun setEndMinute(context: Context, minute: Int) = getPrefs(context).edit().putInt(KEY_END_MINUTE, minute).apply()
    
    fun getScheduleString(context: Context): String {
        if (!isScheduleEnabled(context)) return "24/7"
        val sh = getStartHour(context).toString().padStart(2, '0')
        val sm = getStartMinute(context).toString().padStart(2, '0')
        val eh = getEndHour(context).toString().padStart(2, '0')
        val em = getEndMinute(context).toString().padStart(2, '0')
        return "$sh:$sm - $eh:$em"
    }
    
    private const val KEY_SHOW_LANDMARKS = "show_landmarks"
    private const val KEY_SHOW_ACTION = "show_action"

    fun isShowLandmarks(context: Context): Boolean = getPrefs(context).getBoolean(KEY_SHOW_LANDMARKS, true)
    fun setShowLandmarks(context: Context, show: Boolean) = getPrefs(context).edit().putBoolean(KEY_SHOW_LANDMARKS, show).apply()

    fun isShowAction(context: Context): Boolean = getPrefs(context).getBoolean(KEY_SHOW_ACTION, true)
    fun setShowAction(context: Context, show: Boolean) = getPrefs(context).edit().putBoolean(KEY_SHOW_ACTION, show).apply()
}
