package com.example.gesturescroll.detection

/**
 * ════════════════════════════════════════════════════════════════
 *  GestureType — Sealed class representing all recognized gestures
 * ════════════════════════════════════════════════════════════════
 *
 *  Mobile-first design: Only gestures that make sense on Android.
 *  No left-click, right-click, or drag-and-drop (those are PC concepts).
 *
 *  Each subclass carries any data the gesture needs (e.g. cursor coords).
 */
sealed class GestureType {

    /** No hand detected or hand is in an unrecognized pose. */
    object None : GestureType()

    /** ✊ Fist — all fingers curled. Toggles pause/resume. */
    object Fist : GestureType()

    /**
     * ☝️ Finger Pointer — only index finger extended.
     * The cursor should track the index fingertip.
     *
     * @param normX  Mirrored, smoothed X coordinate in [0..1]
     * @param normY  Smoothed Y coordinate in [0..1]
     */
    data class FingerPointer(
        val normX: Float,
        val normY: Float
    ) : GestureType()

    /** ☝✌ Scroll Up — index + middle extended, index tip above middle tip. */
    object ScrollUp : GestureType()

    /** 🖕 Scroll Down — middle extended, index curled, ring+pinky curled. */
    object ScrollDown : GestureType()

    /** ✌️↔ Zoom In — index + middle spreading apart. */
    object ZoomIn : GestureType()

    /** ✌️→← Zoom Out — index + middle closing together. */
    object ZoomOut : GestureType()

    /**
     * 🤙 Long Press — thumb + middle finger pinch held for duration.
     * Dispatches a long-press touch at the current cursor position.
     *
     * @param normX  Cursor X in [0..1]
     * @param normY  Cursor Y in [0..1]
     */
    data class LongPress(
        val normX: Float,
        val normY: Float
    ) : GestureType()

    /** 🖐 Hand detected but in a neutral/open pose — no action. */
    object Idle : GestureType()
}
