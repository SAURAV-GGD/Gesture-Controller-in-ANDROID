package com.example.gesturescroll.util

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.WindowMetrics

/**
 * ════════════════════════════════════════════════════════════════
 *  ScreenUtils — Non-deprecated screen metrics helper
 * ════════════════════════════════════════════════════════════════
 *
 *  Uses WindowMetrics (API 30+) with fallback to DisplayMetrics.
 *  Avoids the deprecated defaultDisplay.getMetrics() path.
 */
object ScreenUtils {

    /**
     * Returns (widthPx, heightPx) for the current default display.
     */
    fun getScreenSize(context: Context): Pair<Int, Int> {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics: WindowMetrics = wm.currentWindowMetrics
            val bounds = metrics.bounds
            bounds.width() to bounds.height()
        } else {
            @Suppress("DEPRECATION")
            val dm = DisplayMetrics().also { wm.defaultDisplay.getMetrics(it) }
            dm.widthPixels to dm.heightPixels
        }
    }

    /** Screen center in pixels. */
    fun screenCenter(context: Context): Pair<Float, Float> {
        val (w, h) = getScreenSize(context)
        return (w / 2f) to (h / 2f)
    }

    /**
     * Convert normalized coordinates (0..1) to screen pixel coordinates.
     * Handles edge clamping so the result is always within screen bounds.
     */
    fun normToScreen(context: Context, normX: Float, normY: Float): Pair<Float, Float> {
        val (w, h) = getScreenSize(context)
        val px = (normX.coerceIn(0f, 1f) * w)
        val py = (normY.coerceIn(0f, 1f) * h)
        return px to py
    }
}
