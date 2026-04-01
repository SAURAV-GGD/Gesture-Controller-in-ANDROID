package com.example.gesturescroll.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * ════════════════════════════════════════════════════════════════
 *  GesturePreferences — Typed SharedPreferences wrapper
 * ════════════════════════════════════════════════════════════════
 *
 *  Centralizes all tunable parameters. Every slider/toggle in
 *  SettingsActivity reads and writes through this class.
 *
 *  Default values are chosen for mid-range device comfort.
 */
class GesturePreferences(context: Context) {

    companion object {
        private const val PREFS_NAME = "gesture_control_prefs"

        // Keys
        private const val KEY_CURSOR_SENSITIVITY = "cursor_sensitivity"
        private const val KEY_CURSOR_SMOOTHING = "cursor_smoothing"
        private const val KEY_SCROLL_SPEED = "scroll_speed"
        private const val KEY_DEAD_ZONE = "dead_zone"
        private const val KEY_GESTURE_ENABLED = "gesture_enabled"
        private const val KEY_FINGER_MOUSE_ENABLED = "finger_mouse_enabled"

        // Defaults
        const val DEFAULT_CURSOR_SENSITIVITY = 1.5f
        const val DEFAULT_CURSOR_SMOOTHING = 0.35f
        const val DEFAULT_SCROLL_SPEED = 700        // pixels
        const val DEFAULT_DEAD_ZONE = 0.005f
        const val DEFAULT_GESTURE_ENABLED = true
        const val DEFAULT_FINGER_MOUSE_ENABLED = true

        // Ranges (for UI slider scaling)
        val SENSITIVITY_RANGE = 0.5f..3.0f
        val SMOOTHING_RANGE = 0.1f..0.8f
        val SCROLL_SPEED_RANGE = 300..1200
        val DEAD_ZONE_RANGE = 0.001f..0.02f
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Cursor Sensitivity ──────────────────────────────────────
    // Multiplier for finger movement → cursor movement.
    // Higher = faster cursor, lower = more precise.
    var cursorSensitivity: Float
        get() = prefs.getFloat(KEY_CURSOR_SENSITIVITY, DEFAULT_CURSOR_SENSITIVITY)
        set(value) = prefs.edit().putFloat(KEY_CURSOR_SENSITIVITY,
            value.coerceIn(SENSITIVITY_RANGE)).apply()

    // ── Cursor Smoothing (EMA alpha) ────────────────────────────
    // 0.1 = very smooth (laggy), 0.8 = very responsive (jittery).
    var cursorSmoothing: Float
        get() = prefs.getFloat(KEY_CURSOR_SMOOTHING, DEFAULT_CURSOR_SMOOTHING)
        set(value) = prefs.edit().putFloat(KEY_CURSOR_SMOOTHING,
            value.coerceIn(SMOOTHING_RANGE)).apply()

    // ── Scroll Distance ─────────────────────────────────────────
    // How many pixels each scroll gesture moves.
    var scrollSpeed: Int
        get() = prefs.getInt(KEY_SCROLL_SPEED, DEFAULT_SCROLL_SPEED)
        set(value) = prefs.edit().putInt(KEY_SCROLL_SPEED,
            value.coerceIn(SCROLL_SPEED_RANGE)).apply()

    // ── Dead Zone ───────────────────────────────────────────────
    // Minimum normalized movement to register as intentional.
    // Filters out hand tremor and micro-jitter.
    var deadZone: Float
        get() = prefs.getFloat(KEY_DEAD_ZONE, DEFAULT_DEAD_ZONE)
        set(value) = prefs.edit().putFloat(KEY_DEAD_ZONE,
            value.coerceIn(DEAD_ZONE_RANGE)).apply()

    // ── Master Toggle ───────────────────────────────────────────
    var gestureEnabled: Boolean
        get() = prefs.getBoolean(KEY_GESTURE_ENABLED, DEFAULT_GESTURE_ENABLED)
        set(value) = prefs.edit().putBoolean(KEY_GESTURE_ENABLED, value).apply()

    // ── Finger Mouse Toggle ─────────────────────────────────────
    var fingerMouseEnabled: Boolean
        get() = prefs.getBoolean(KEY_FINGER_MOUSE_ENABLED, DEFAULT_FINGER_MOUSE_ENABLED)
        set(value) = prefs.edit().putBoolean(KEY_FINGER_MOUSE_ENABLED, value).apply()

    /** Reset all settings to defaults. */
    fun resetToDefaults() {
        prefs.edit().clear().apply()
    }
}
