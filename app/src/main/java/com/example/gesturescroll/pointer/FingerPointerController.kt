package com.example.gesturescroll.pointer

import com.example.gesturescroll.settings.GesturePreferences
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * ════════════════════════════════════════════════════════════════
 *  FingerPointerController — The Finger Mouse Brain
 * ════════════════════════════════════════════════════════════════
 *
 *  Converts raw MediaPipe index-finger landmarks into smooth,
 *  stable, screen-mapped cursor coordinates.
 *
 *  Pipeline:
 *    Raw Landmark → Mirror → Dead Zone Filter → Sensitivity
 *    → Velocity Acceleration → EMA Smoothing → Screen Clamp
 *
 *  Inspired by SAURAV-GGD/Finger-Mouse but adapted for Android:
 *  - No ROI rectangle (not practical on mobile front camera)
 *  - Velocity-based acceleration instead of fixed multiplier
 *  - Configurable via GesturePreferences (user settings panel)
 */
class FingerPointerController(private val prefs: GesturePreferences) {

    // ── Smoothed cursor position (normalized 0..1) ──────────────
    private var smoothX = 0.5f
    private var smoothY = 0.5f
    private var initialized = false

    // ── Previous raw position for delta calculation ─────────────
    private var prevRawX = 0.5f
    private var prevRawY = 0.5f

    companion object {
        /**
         * Velocity acceleration curve parameters.
         * Below lowSpeed: movement is 1:1 (precise mode).
         * Above highSpeed: movement is amplified by maxMultiplier.
         * Between: linear interpolation.
         */
        private const val LOW_SPEED_THRESHOLD = 0.005f
        private const val HIGH_SPEED_THRESHOLD = 0.05f
        private const val MAX_VELOCITY_MULTIPLIER = 2.0f
    }

    /**
     * Process a raw index fingertip position from MediaPipe.
     *
     * @param rawLandmarkX  Raw landmark X (0..1, NOT mirrored)
     * @param rawLandmarkY  Raw landmark Y (0..1)
     * @return Pair(smoothedNormX, smoothedNormY) ready for screen mapping
     */
    fun update(rawLandmarkX: Float, rawLandmarkY: Float): Pair<Float, Float> {
        // ── Step 1: Mirror X for front camera ────────────────────
        // Front camera is mirrored — flip so left hand movement → left cursor movement
        val mirroredX = 1f - rawLandmarkX
        val rawY = rawLandmarkY

        // ── Step 2: First frame initialization ───────────────────
        if (!initialized) {
            smoothX = mirroredX
            smoothY = rawY
            prevRawX = mirroredX
            prevRawY = rawY
            initialized = true
            return smoothX to smoothY
        }

        // ── Step 3: Calculate movement delta ─────────────────────
        val deltaX = mirroredX - prevRawX
        val deltaY = rawY - prevRawY
        prevRawX = mirroredX
        prevRawY = rawY

        // ── Step 4: Dead zone filter ─────────────────────────────
        // Ignore micro-movements caused by hand tremor.
        // This is the single most effective anti-jitter measure.
        val deadZone = prefs.deadZone
        val absDx = abs(deltaX)
        val absDy = abs(deltaY)
        val filteredDx = if (absDx < deadZone) 0f else deltaX
        val filteredDy = if (absDy < deadZone) 0f else deltaY

        // If both axes are in dead zone, skip entirely
        if (filteredDx == 0f && filteredDy == 0f) {
            return smoothX to smoothY
        }

        // ── Step 5: Velocity-based acceleration ──────────────────
        // Slow movements = precise control (1:1 mapping)
        // Fast movements = amplified (covers more screen distance)
        val speed = sqrt(filteredDx * filteredDx + filteredDy * filteredDy)
        val velocityMultiplier = when {
            speed <= LOW_SPEED_THRESHOLD -> 1.0f
            speed >= HIGH_SPEED_THRESHOLD -> MAX_VELOCITY_MULTIPLIER
            else -> {
                // Linear interpolation between 1.0 and MAX
                val t = (speed - LOW_SPEED_THRESHOLD) /
                        (HIGH_SPEED_THRESHOLD - LOW_SPEED_THRESHOLD)
                1.0f + t * (MAX_VELOCITY_MULTIPLIER - 1.0f)
            }
        }

        // ── Step 6: Apply sensitivity and velocity ───────────────
        val sensitivity = prefs.cursorSensitivity
        val targetX = smoothX + filteredDx * sensitivity * velocityMultiplier
        val targetY = smoothY + filteredDy * sensitivity * velocityMultiplier

        // ── Step 7: EMA (Exponential Moving Average) smoothing ───
        // Lower alpha = smoother but laggier
        // Higher alpha = more responsive but jittery
        val alpha = prefs.cursorSmoothing
        smoothX += alpha * (targetX - smoothX)
        smoothY += alpha * (targetY - smoothY)

        // ── Step 8: Clamp to screen bounds ───────────────────────
        smoothX = smoothX.coerceIn(0.01f, 0.99f)
        smoothY = smoothY.coerceIn(0.01f, 0.99f)

        return smoothX to smoothY
    }

    /**
     * Reset the controller state. Call when hand disappears
     * so the next detection starts fresh without a "jump".
     */
    fun reset() {
        initialized = false
        smoothX = 0.5f
        smoothY = 0.5f
        prevRawX = 0.5f
        prevRawY = 0.5f
    }

    /** Current smoothed position (for use by other gestures like LongPress). */
    val currentX: Float get() = smoothX
    val currentY: Float get() = smoothY
}
