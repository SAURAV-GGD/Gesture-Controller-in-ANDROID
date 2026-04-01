package com.example.gesturescroll.camera

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner

/**
 * ════════════════════════════════════════════════════════════════
 *  CameraManager — Lifecycle-aware CameraX wrapper
 * ════════════════════════════════════════════════════════════════
 *
 *  Sets up CameraX with front camera at a low resolution (480×360)
 *  optimized for hand tracking — not photography — so we trade
 *  pixel count for frame rate and lower CPU usage.
 *
 *  Usage:
 *    CameraManager.start(lifecycleOwner, previewView) { bitmap, timestamp ->
 *        tracker.detectAsync(bitmap, timestamp)
 *    }
 */
object CameraManager {

    private const val TAG = "CameraManager"

    /**
     * Resolution for hand tracking. 480×360 gives a good balance
     * between landmark accuracy and processing speed on mid-range devices.
     */
    private val TRACKING_RESOLUTION = Size(480, 360)

    /**
     * Start the front camera with preview and per-frame analysis.
     *
     * @param lifecycleOwner  Activity or Fragment lifecycle
     * @param previewView     The PreviewView to show camera feed
     * @param onFrame         Callback with (bitmap, timestampUs) for each frame
     */
    fun start(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onFrame: (Bitmap, Long) -> Unit
    ) {
        val context = previewView.context
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()

            // ── Preview ──────────────────────────────────────────
            val preview = Preview.Builder()
                .setTargetResolution(TRACKING_RESOLUTION)
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            // ── Image Analysis ───────────────────────────────────
            // KEEP_ONLY_LATEST ensures we never queue frames —
            // if processing is slow, we just skip to the newest frame.
            val analyzer = ImageAnalysis.Builder()
                .setTargetResolution(TRACKING_RESOLUTION)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(
                        ContextCompat.getMainExecutor(context)
                    ) { imageProxy ->
                        try {
                            val bitmap = imageProxy.toBitmap()
                            val timestamp = imageProxy.imageInfo.timestamp
                            onFrame(bitmap, timestamp)
                        } catch (e: Exception) {
                            Log.w(TAG, "Frame conversion failed: ${e.message}")
                        } finally {
                            imageProxy.close()
                        }
                    }
                }

            // ── Bind to lifecycle ────────────────────────────────
            try {
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    analyzer
                )
                Log.i(TAG, "Camera started ✅ (${TRACKING_RESOLUTION})")
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }
}
