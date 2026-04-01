package com.example.gesturescroll.detection

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * ════════════════════════════════════════════════════════════════
 *  GestureClassifier — Pure gesture recognition logic
 * ════════════════════════════════════════════════════════════════
 *
 *  This class is a PURE FUNCTION: no Android dependencies, no state
 *  mutations, no side effects. It takes landmarks + config and returns
 *  a GestureType. This makes it trivially unit-testable.
 *
 *  MediaPipe Hand Landmarks reference:
 *    0  = WRIST
 *    4  = THUMB_TIP
 *    8  = INDEX_TIP,    6 = INDEX_PIP
 *    12 = MIDDLE_TIP,  10 = MIDDLE_PIP
 *    16 = RING_TIP,    14 = RING_PIP
 *    20 = PINKY_TIP,   18 = PINKY_PIP
 */
class GestureClassifier {

    // ── Thresholds (can be externalized to GesturePreferences later) ──
    companion object {
        /** Normalized distance: thumb-to-finger must be < this for a "pinch". */
        const val PINCH_THRESHOLD = 0.07f

        /** Y-difference for index above middle to trigger scroll up. */
        const val SCROLL_UP_THRESHOLD = 0.05f

        /**
         * Minimum spread delta (over 3 frames) to detect a zoom gesture.
         * Positive = spreading (zoom in), negative = closing (zoom out).
         */
        const val ZOOM_SPREAD_DELTA = 0.018f

        /** Minimum time (ms) a thumb+middle pinch must be held for long press. */
        const val LONG_PRESS_HOLD_MS = 500L
    }

    // ── Internal state for zoom spread tracking ─────────────────
    // These are detection-level state, not app-level state.
    private val spreadHistory = ArrayDeque<Float>(8)

    // ── Long press timing ───────────────────────────────────────
    private var middlePinchStartTime = 0L
    private var wasMiddlePinching = false

    /**
     * Classify the current hand pose into a GestureType.
     *
     * @param landmarks  21 normalized hand landmarks from MediaPipe
     * @param smoothCursorX  Pre-computed smoothed+mirrored cursor X (0..1)
     * @param smoothCursorY  Pre-computed smoothed cursor Y (0..1)
     * @param now  Current timestamp in ms (System.currentTimeMillis)
     * @return The detected GestureType
     */
    fun classify(
        landmarks: List<NormalizedLandmark>,
        smoothCursorX: Float,
        smoothCursorY: Float,
        now: Long
    ): GestureType {
        val lm = landmarks

        // ── 1. FIST — all four fingertips below their PIP joints ──
        if (isFist(lm)) {
            resetInternalState()
            return GestureType.Fist
        }

        // ── Finger extension flags ────────────────────────────────
        val indexExt  = isFingerExtended(lm, tipIdx = 8,  pipIdx = 6)
        val middleExt = isFingerExtended(lm, tipIdx = 12, pipIdx = 10)
        val ringExt   = isFingerExtended(lm, tipIdx = 16, pipIdx = 14)
        val pinkyExt  = isFingerExtended(lm, tipIdx = 20, pipIdx = 18)
        val indexCurled = !indexExt

        // ── Pinch distances ──────────────────────────────────────
        val thumbMiddleDist = dist(lm[4], lm[12])
        val thumbIndexDist  = dist(lm[4], lm[8])
        val isMiddlePinch   = thumbMiddleDist < PINCH_THRESHOLD && thumbIndexDist >= PINCH_THRESHOLD

        // ── 2. LONG PRESS — thumb + middle pinch held ≥ 500ms ────
        if (isMiddlePinch) {
            if (!wasMiddlePinching) {
                wasMiddlePinching = true
                middlePinchStartTime = now
            } else if (now - middlePinchStartTime >= LONG_PRESS_HOLD_MS) {
                // Fire long press — reset so it doesn't re-fire every frame
                wasMiddlePinching = false
                middlePinchStartTime = 0L
                return GestureType.LongPress(smoothCursorX, smoothCursorY)
            }
            // Still holding but haven't reached threshold yet — show idle
            return GestureType.Idle
        } else {
            wasMiddlePinching = false
            middlePinchStartTime = 0L
        }

        // ── Spread history for zoom detection ────────────────────
        val currentSpread = dist(lm[8], lm[12])
        spreadHistory.addLast(currentSpread)
        if (spreadHistory.size > 6) spreadHistory.removeFirst()
        val spreadDelta = if (spreadHistory.size >= 3)
            spreadHistory.last() - spreadHistory[spreadHistory.size - 3]
        else 0f

        // ── 3. SCROLL DOWN — middle UP + index CURLED ────────────
        //    Distinct pose: only middle finger pointing up.
        if (middleExt && indexCurled && !ringExt && !pinkyExt) {
            return GestureType.ScrollDown
        }

        // ── 4. INDEX + MIDDLE both extended ──────────────────────
        //    Branch into: zoom in, zoom out, or scroll up
        if (indexExt && middleExt && !ringExt && !pinkyExt) {
            return when {
                // Zoom IN — fingers spreading apart
                spreadDelta > ZOOM_SPREAD_DELTA -> {
                    spreadHistory.clear()
                    GestureType.ZoomIn
                }
                // Zoom OUT — fingers closing together
                spreadDelta < -ZOOM_SPREAD_DELTA -> {
                    spreadHistory.clear()
                    GestureType.ZoomOut
                }
                // Scroll UP — index tip above middle tip, spread stable
                lm[8].y() - lm[12].y() < -SCROLL_UP_THRESHOLD -> {
                    GestureType.ScrollUp
                }
                // Just two fingers up, no significant movement
                else -> GestureType.Idle
            }
        }

        // ── 5. FINGER POINTER — only index extended ──────────────
        //    This is the Finger Mouse cursor mode.
        if (indexExt && !middleExt && !ringExt && !pinkyExt) {
            return GestureType.FingerPointer(smoothCursorX, smoothCursorY)
        }

        // ── Default: hand detected but no matching gesture ───────
        return GestureType.Idle
    }

    /**
     * Call this when no hand is detected to reset internal tracking state.
     */
    fun onNoHand() {
        resetInternalState()
    }

    private fun resetInternalState() {
        spreadHistory.clear()
        wasMiddlePinching = false
        middlePinchStartTime = 0L
    }

    // ══════════════════════════════════════════════════════════════
    //  Geometry helpers
    // ══════════════════════════════════════════════════════════════

    /**
     * A finger is "extended" if its tip is higher (smaller Y) than its PIP joint.
     * Works because MediaPipe Y increases downward in the frame.
     */
    private fun isFingerExtended(
        lm: List<NormalizedLandmark>,
        tipIdx: Int,
        pipIdx: Int
    ): Boolean = lm[tipIdx].y() < lm[pipIdx].y()

    /**
     * Fist: all four fingertips are below (larger Y) their PIP joints.
     * Thumb is excluded — it folds sideways, not downward.
     */
    private fun isFist(lm: List<NormalizedLandmark>): Boolean =
        listOf(8 to 6, 12 to 10, 16 to 14, 20 to 18)
            .all { (tip, pip) -> lm[tip].y() > lm[pip].y() }

    /** 2D Euclidean distance between two normalized landmarks. */
    private fun dist(a: NormalizedLandmark, b: NormalizedLandmark): Float {
        val dx = a.x() - b.x()
        val dy = a.y() - b.y()
        return sqrt(dx * dx + dy * dy)
    }
}
