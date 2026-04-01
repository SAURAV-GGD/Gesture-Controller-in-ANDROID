package com.example.gesturescroll.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.gesturescroll.pointer.CursorOverlay
import com.example.gesturescroll.settings.GesturePreferences
import com.example.gesturescroll.util.ScreenUtils

/**
 * ════════════════════════════════════════════════════════════════
 *  GestureAccessibilityService — Android touch dispatch layer
 * ════════════════════════════════════════════════════════════════
 *
 *  Dispatches real touch gestures system-wide via
 *  AccessibilityService.dispatchGesture().
 *
 *  MOBILE-ONLY API — NO PC concepts:
 *  ──────────────────────────────────────────────────────────────
 *  scroll(scrollUp)          — swipe up or down
 *  longPress(normX, normY)   — long press at position
 *  zoom(zoomIn)              — two-finger pinch or spread
 *  moveCursor(normX, normY)  — move overlay cursor dot
 *  removeCursor()            — hide cursor
 *
 *  REMOVED from original:
 *  ❌ tap() — Android has native tap, not needed
 *  ❌ startDrag/updateDrag/endDrag — PC drag-and-drop
 * ════════════════════════════════════════════════════════════════
 */
class GestureAccessibilityService : AccessibilityService() {

    companion object {
        /** Singleton reference for MainActivity to call into. */
        var instance: GestureAccessibilityService? = null
            private set

        private const val TAG = "GestureA11ySvc"

        // ── Gesture timing constants ────────────────────────────
        private const val SWIPE_DURATION_MS  = 180L
        private const val LONG_PRESS_MS      = 650L
        private const val ZOOM_DISTANCE_PX   = 320   // Half-spread for zoom
        private const val ZOOM_DURATION_MS   = 280L
    }

    // ── Cursor overlay ────────────────────────────────────────────
    private var cursorOverlay: CursorOverlay? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // ── Preferences (for scroll speed) ────────────────────────────
    private lateinit var prefs: GesturePreferences

    // ── Rate limiting ─────────────────────────────────────────────
    private var lastScrollTime = 0L
    private var lastZoomTime = 0L
    private var lastLongPressTime = 0L
    private val scrollDelay = 110L   // ms between scroll dispatches
    private val zoomDelay = 300L     // ms between zoom dispatches

    // ══════════════════════════════════════════════════════════════
    //  Lifecycle
    // ══════════════════════════════════════════════════════════════

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        prefs = GesturePreferences(this)
        cursorOverlay = CursorOverlay(this)
        Log.i(TAG, "GestureAccessibilityService connected ✅")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        removeCursor()
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* Not used */ }
    override fun onInterrupt() { /* Not used */ }

    // ══════════════════════════════════════════════════════════════
    //  1. SCROLL — swipe up or down from screen center
    // ══════════════════════════════════════════════════════════════

    /**
     * Dispatch a scroll gesture (vertical swipe).
     * Rate-limited to prevent overwhelming the system.
     *
     * @param scrollUp  true = scroll content up (swipe up), false = down
     */
    fun scroll(scrollUp: Boolean) {
        val now = System.currentTimeMillis()
        if (now - lastScrollTime < scrollDelay) return
        lastScrollTime = now

        val (cx, cy) = ScreenUtils.screenCenter(this)
        val half = prefs.scrollSpeed / 2f
        val (startY, endY) = if (scrollUp)
            (cy + half) to (cy - half)
        else
            (cy - half) to (cy + half)

        dispatch(buildSwipe(cx, startY, cx, endY, SWIPE_DURATION_MS))
        Log.d(TAG, "scroll ${if (scrollUp) "UP" else "DOWN"}")
    }

    // ══════════════════════════════════════════════════════════════
    //  2. LONG PRESS — hold at a position
    // ══════════════════════════════════════════════════════════════

    /**
     * Dispatch a long-press gesture at the given position.
     * This is the mobile equivalent of "right click" — opens context menus,
     * selects text, triggers long-press actions.
     *
     * @param normX  Cursor X position (0..1)
     * @param normY  Cursor Y position (0..1)
     */
    fun longPress(normX: Float, normY: Float) {
        val now = System.currentTimeMillis()
        if (now - lastLongPressTime < 1000L) return  // Prevent rapid re-fire
        lastLongPressTime = now

        val (sx, sy) = ScreenUtils.normToScreen(this, normX, normY)
        val path = Path().apply { moveTo(sx, sy); lineTo(sx, sy) }
        dispatch(
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0L, LONG_PRESS_MS))
                .build()
        )
        Log.d(TAG, "longPress ($sx, $sy)")
    }

    // ══════════════════════════════════════════════════════════════
    //  3. ZOOM IN / OUT — two-finger pinch or spread
    // ══════════════════════════════════════════════════════════════

    /**
     * Dispatch a zoom gesture using two simultaneous stroke paths.
     * Each stroke represents one virtual "finger" moving horizontally.
     *
     * @param zoomIn  true = spread (zoom in), false = pinch (zoom out)
     */
    fun zoom(zoomIn: Boolean) {
        val now = System.currentTimeMillis()
        if (now - lastZoomTime < zoomDelay) return
        lastZoomTime = now

        val (cx, cy) = ScreenUtils.screenCenter(this)

        // Finger 1 starts near center, finger 2 starts near center.
        // For zoom in: they move apart. For zoom out: they move together.
        val startOffset = if (zoomIn) 80f else ZOOM_DISTANCE_PX.toFloat()
        val endOffset   = if (zoomIn) ZOOM_DISTANCE_PX.toFloat() else 80f

        // Left finger
        val pathLeft = Path().apply {
            moveTo(cx - startOffset, cy)
            lineTo(cx - endOffset, cy)
        }
        // Right finger
        val pathRight = Path().apply {
            moveTo(cx + startOffset, cy)
            lineTo(cx + endOffset, cy)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(pathLeft, 0L, ZOOM_DURATION_MS))
            .addStroke(GestureDescription.StrokeDescription(pathRight, 0L, ZOOM_DURATION_MS))
            .build()
        dispatch(gesture)
        Log.d(TAG, "zoom ${if (zoomIn) "IN" else "OUT"}")
    }

    // ══════════════════════════════════════════════════════════════
    //  4. CURSOR OVERLAY — visual pointer feedback
    // ══════════════════════════════════════════════════════════════

    /** Move the cursor overlay to the given normalized position. */
    fun moveCursor(normX: Float, normY: Float) {
        cursorOverlay?.moveTo(normX, normY)
    }

    /** Update cursor color state ("idle", "active", "longpress"). */
    fun setCursorState(state: String) {
        cursorOverlay?.setState(state)
    }

    /** Remove the cursor overlay from screen. */
    fun removeCursor() {
        cursorOverlay?.remove()
    }

    // ══════════════════════════════════════════════════════════════
    //  Helpers
    // ══════════════════════════════════════════════════════════════

    private fun buildSwipe(
        x1: Float, y1: Float,
        x2: Float, y2: Float,
        durationMs: Long
    ): GestureDescription {
        val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        return GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, durationMs))
            .build()
    }

    private fun dispatch(gesture: GestureDescription) {
        val ok = dispatchGesture(gesture, null, null)
        if (!ok) Log.w(TAG, "dispatchGesture returned false — is the service connected?")
    }
}
