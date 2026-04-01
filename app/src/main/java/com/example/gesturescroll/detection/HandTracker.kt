package com.example.gesturescroll.detection

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

/**
 * ════════════════════════════════════════════════════════════════
 *  HandTracker — MediaPipe HandLandmarker wrapper
 * ════════════════════════════════════════════════════════════════
 *
 *  Lifecycle-aware wrapper around MediaPipe's HandLandmarker.
 *  Handles initialization, detection, and teardown.
 *
 *  Usage:
 *    val tracker = HandTracker(context) { landmarks -> ... }
 *    tracker.init()
 *    // On each camera frame:
 *    tracker.detectAsync(bitmap, timestamp)
 *    // When done:
 *    tracker.close()
 */
class HandTracker(
    private val context: Context,
    private val onResult: (List<NormalizedLandmark>?) -> Unit
) {

    companion object {
        private const val TAG = "HandTracker"
        private const val MODEL_ASSET = "hand_landmarker.task"

        // Detection confidence thresholds — tuned for mobile front camera
        private const val MIN_DETECTION_CONFIDENCE = 0.55f
        private const val MIN_PRESENCE_CONFIDENCE  = 0.55f
        private const val MIN_TRACKING_CONFIDENCE  = 0.55f
    }

    private var handLandmarker: HandLandmarker? = null
    private var isInitialized = false

    /**
     * Initialize the MediaPipe HandLandmarker.
     * Must be called before detectAsync(). Safe to call multiple times.
     */
    fun init() {
        if (isInitialized) return

        try {
            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder()
                        .setModelAssetPath(MODEL_ASSET)
                        .build()
                )
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumHands(1) // Single hand for performance
                .setMinHandDetectionConfidence(MIN_DETECTION_CONFIDENCE)
                .setMinHandPresenceConfidence(MIN_PRESENCE_CONFIDENCE)
                .setMinTrackingConfidence(MIN_TRACKING_CONFIDENCE)
                .setResultListener { result, _ -> handleResult(result) }
                .setErrorListener { e -> Log.e(TAG, "MediaPipe error: $e") }
                .build()

            handLandmarker = HandLandmarker.createFromOptions(context, options)
            isInitialized = true
            Log.i(TAG, "HandLandmarker initialized ✅")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize HandLandmarker: ${e.message}")
        }
    }

    /**
     * Feed a camera frame to MediaPipe for async detection.
     *
     * @param bitmap    The camera frame as a Bitmap
     * @param timestamp Frame timestamp in microseconds (from ImageProxy)
     */
    fun detectAsync(bitmap: Bitmap, timestamp: Long) {
        if (!isInitialized) return
        try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            handLandmarker?.detectAsync(mpImage, timestamp)
        } catch (e: Exception) {
            // Can happen if frames arrive out of order after close()
            Log.w(TAG, "detectAsync failed: ${e.message}")
        }
    }

    /** Release MediaPipe resources. Safe to call multiple times. */
    fun close() {
        try {
            handLandmarker?.close()
        } catch (_: Exception) { }
        handLandmarker = null
        isInitialized = false
        Log.i(TAG, "HandLandmarker closed")
    }

    private fun handleResult(result: HandLandmarkerResult) {
        if (result.landmarks().isEmpty()) {
            onResult(null) // No hand detected
        } else {
            onResult(result.landmarks()[0]) // First hand's 21 landmarks
        }
    }
}
