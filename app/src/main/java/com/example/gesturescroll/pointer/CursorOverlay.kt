package com.example.gesturescroll.pointer

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import com.example.gesturescroll.util.ScreenUtils

/**
 * ════════════════════════════════════════════════════════════════
 *  CursorOverlay — Animated virtual cursor for Finger Mouse
 * ════════════════════════════════════════════════════════════════
 *
 *  Draws an animated cursor overlay using TYPE_ACCESSIBILITY_OVERLAY
 *  (no SYSTEM_ALERT_WINDOW permission needed).
 *
 *  Visual design:
 *    - Outer ring (pulsing) — shows cursor bounds
 *    - Inner dot (solid) — precise cursor point
 *    - Color changes by state: idle=purple, active=green, longpress=red
 *
 *  The overlay is click-through (FLAG_NOT_TOUCHABLE) so it never
 *  intercepts real touch events.
 */
class CursorOverlay(private val context: Context) {

    companion object {
        private const val OVERLAY_SIZE_PX = 56  // Total overlay view size
        private const val INNER_DOT_RADIUS = 8f
        private const val OUTER_RING_RADIUS = 20f
        private const val OUTER_RING_STROKE = 3f
    }

    // ── Colors ───────────────────────────────────────────────────
    private var dotColor = Color.argb(220, 108, 99, 255)    // Purple (idle)
    private var ringColor = Color.argb(180, 255, 255, 255)  // White ring

    // ── Pulse animation ──────────────────────────────────────────
    private var pulseScale = 1.0f
    private var pulseAnimator: ValueAnimator? = null

    // ── View & Window ────────────────────────────────────────────
    private var overlayView: CursorView? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Move the cursor to the given normalized coordinates.
     * Creates the overlay if it doesn't exist yet.
     */
    fun moveTo(normX: Float, normY: Float) {
        val (sx, sy) = ScreenUtils.normToScreen(context, normX, normY)
        mainHandler.post {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            if (overlayView == null) createOverlay(wm)
            val view = overlayView ?: return@post
            val params = view.layoutParams as WindowManager.LayoutParams
            params.x = sx.toInt() - OVERLAY_SIZE_PX / 2
            params.y = sy.toInt() - OVERLAY_SIZE_PX / 2
            try { wm.updateViewLayout(view, params) } catch (_: Exception) {}
        }
    }

    /**
     * Update cursor color to reflect current gesture state.
     *
     * @param state  "idle", "active", or "longpress"
     */
    fun setState(state: String) {
        val newColor = when (state) {
            "active" -> Color.argb(220, 0, 229, 160)     // Mint green
            "longpress" -> Color.argb(220, 255, 82, 82)  // Red
            else -> Color.argb(220, 108, 99, 255)        // Purple
        }
        if (newColor != dotColor) {
            dotColor = newColor
            mainHandler.post { overlayView?.invalidate() }
        }
    }

    /** Remove the cursor overlay from screen. */
    fun remove() {
        mainHandler.post {
            pulseAnimator?.cancel()
            pulseAnimator = null
            val view = overlayView ?: return@post
            try {
                (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                    .removeView(view)
            } catch (_: Exception) {}
            overlayView = null
        }
    }

    /** Check if cursor is currently showing. */
    val isShowing: Boolean get() = overlayView != null

    // ══════════════════════════════════════════════════════════════
    //  Private
    // ══════════════════════════════════════════════════════════════

    private fun createOverlay(wm: WindowManager) {
        val params = WindowManager.LayoutParams(
            OVERLAY_SIZE_PX, OVERLAY_SIZE_PX,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0; y = 0
        }

        val view = CursorView(context)
        wm.addView(view, params)
        overlayView = view

        // Start pulse animation
        startPulse()
    }

    private fun startPulse() {
        pulseAnimator?.cancel()
        pulseAnimator = ValueAnimator.ofFloat(0.85f, 1.15f).apply {
            duration = 1200
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                pulseScale = animator.animatedValue as Float
                overlayView?.invalidate()
            }
            start()
        }
    }

    /**
     * Custom View that draws the cursor: outer pulsing ring + inner solid dot.
     */
    private inner class CursorView(context: Context) : View(context) {

        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = OUTER_RING_STROKE
        }

        // Subtle shadow behind the dot for visibility on any background
        private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(80, 0, 0, 0)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f

            // Shadow (slightly offset)
            canvas.drawCircle(cx + 1f, cy + 1f, INNER_DOT_RADIUS + 2f, shadowPaint)

            // Outer pulsing ring
            ringPaint.color = ringColor
            canvas.drawCircle(cx, cy, OUTER_RING_RADIUS * pulseScale, ringPaint)

            // Inner solid dot
            dotPaint.color = dotColor
            canvas.drawCircle(cx, cy, INNER_DOT_RADIUS, dotPaint)
        }
    }
}
