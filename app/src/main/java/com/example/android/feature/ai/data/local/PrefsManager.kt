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
    
    private const val KEY_COMBO_RESET_DELAY = "combo_reset_delay"
    private const val DEFAULT_COMBO_RESET_DELAY = 3000L
    
    fun getComboResetDelay(context: Context): Long = getPrefs(context).getLong(KEY_COMBO_RESET_DELAY, DEFAULT_COMBO_RESET_DELAY)
    fun setComboResetDelay(context: Context, delayMs: Long) = getPrefs(context).edit().putLong(KEY_COMBO_RESET_DELAY, delayMs).apply()
    
    private const val KEY_HAND_DETECTION_CONFIDENCE = "hand_detection_confidence"
    private const val KEY_HAND_PRESENCE_CONFIDENCE = "hand_presence_confidence"
    private const val KEY_TRACKING_CONFIDENCE = "tracking_confidence"
    private const val KEY_MIN_HANDEDNESS_SCORE = "min_handedness_score"
    
    private const val DEFAULT_HAND_DETECTION_CONFIDENCE = 0.7f
    private const val DEFAULT_HAND_PRESENCE_CONFIDENCE = 0.7f
    private const val DEFAULT_TRACKING_CONFIDENCE = 0.5f
    private const val DEFAULT_MIN_HANDEDNESS_SCORE = 0.6f
    
    fun getHandDetectionConfidence(context: Context): Float = getPrefs(context).getFloat(KEY_HAND_DETECTION_CONFIDENCE, DEFAULT_HAND_DETECTION_CONFIDENCE)
    fun setHandDetectionConfidence(context: Context, value: Float) = getPrefs(context).edit().putFloat(KEY_HAND_DETECTION_CONFIDENCE, value).apply()
    
    fun getHandPresenceConfidence(context: Context): Float = getPrefs(context).getFloat(KEY_HAND_PRESENCE_CONFIDENCE, DEFAULT_HAND_PRESENCE_CONFIDENCE)
    fun setHandPresenceConfidence(context: Context, value: Float) = getPrefs(context).edit().putFloat(KEY_HAND_PRESENCE_CONFIDENCE, value).apply()
    
    fun getTrackingConfidence(context: Context): Float = getPrefs(context).getFloat(KEY_TRACKING_CONFIDENCE, DEFAULT_TRACKING_CONFIDENCE)
    fun setTrackingConfidence(context: Context, value: Float) = getPrefs(context).edit().putFloat(KEY_TRACKING_CONFIDENCE, value).apply()
    
    fun getMinHandednessScore(context: Context): Float = getPrefs(context).getFloat(KEY_MIN_HANDEDNESS_SCORE, DEFAULT_MIN_HANDEDNESS_SCORE)
    fun setMinHandednessScore(context: Context, value: Float) = getPrefs(context).edit().putFloat(KEY_MIN_HANDEDNESS_SCORE, value).apply()
}
