package com.example.gesturescroll

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.gesturescroll.camera.CameraManager
import com.example.gesturescroll.detection.GestureClassifier
import com.example.gesturescroll.detection.GestureType
import com.example.gesturescroll.detection.HandTracker
import com.example.gesturescroll.pointer.FingerPointerController
import com.example.gesturescroll.service.GestureAccessibilityService
import com.example.gesturescroll.settings.GesturePreferences
import com.example.gesturescroll.settings.SettingsActivity

/**
 * ════════════════════════════════════════════════════════════════
 *  MainActivity — Slim Coordinator
 * ════════════════════════════════════════════════════════════════
 *
 *  Responsibilities (and ONLY these):
 *    1. Camera permission handling
 *    2. Wire up: Camera → HandTracker → Classifier → Service
 *    3. Update UI with current gesture state
 *    4. Navigation to Settings
 *    5. Lifecycle management
 *
 *  All gesture detection logic lives in GestureClassifier.
 *  All gesture dispatch logic lives in GestureAccessibilityService.
 *  All cursor math lives in FingerPointerController.
 */
class MainActivity : AppCompatActivity() {

    // ── Components ──────────────────────────────────────────────
    private lateinit var handTracker: HandTracker
    private lateinit var gestureClassifier: GestureClassifier
    private lateinit var pointerController: FingerPointerController
    private lateinit var prefs: GesturePreferences

    // ── UI Elements ─────────────────────────────────────────────
    private lateinit var previewView: PreviewView
    private lateinit var statusText: TextView
    private lateinit var statusDot: View
    private lateinit var gestureEmoji: TextView
    private lateinit var gestureText: TextView
    private lateinit var gestureSubtext: TextView
    private lateinit var toggleBtn: Button

    // ── State ───────────────────────────────────────────────────
    private var isPaused = false
    private var lastToggleTime = 0L
    private val toggleDelay = 800L  // Fist debounce

    companion object {
        private const val CAMERA_PERMISSION_CODE = 100
    }

    // ══════════════════════════════════════════════════════════════
    //  Lifecycle
    // ══════════════════════════════════════════════════════════════

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ── Initialize preferences ───────────────────────────────
        prefs = GesturePreferences(this)

        // ── Initialize components ────────────────────────────────
        gestureClassifier = GestureClassifier()
        pointerController = FingerPointerController(prefs)

        // ── Bind UI elements ─────────────────────────────────────
        previewView = findViewById(R.id.previewView)
        statusText = findViewById(R.id.statusText)
        statusDot = findViewById(R.id.statusDot)
        gestureEmoji = findViewById(R.id.gestureEmoji)
        gestureText = findViewById(R.id.gestureText)
        gestureSubtext = findViewById(R.id.gestureSubtext)
        toggleBtn = findViewById(R.id.toggleBtn)

        // ── Setup button actions ─────────────────────────────────
        findViewById<Button>(R.id.accessibilityBtn).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<TextView>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        toggleBtn.setOnClickListener {
            togglePause()
        }

        // ── Initialize hand tracker ──────────────────────────────
        handTracker = HandTracker(this) { landmarks ->
            onHandResult(landmarks)
        }

        // ── Camera permission → start pipeline ──────────────────
        if (hasCameraPermission()) {
            startPipeline()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_CODE
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        GestureAccessibilityService.instance?.removeCursor()
        handTracker.close()
    }

    // ══════════════════════════════════════════════════════════════
    //  Pipeline Setup
    // ══════════════════════════════════════════════════════════════

    private fun startPipeline() {
        handTracker.init()
        CameraManager.start(this, previewView) { bitmap, timestamp ->
            handTracker.detectAsync(bitmap, timestamp)
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  Hand Result Callback — the main routing logic
    // ══════════════════════════════════════════════════════════════

    private fun onHandResult(
        landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>?
    ) {
        // ── No hand detected ─────────────────────────────────────
        if (landmarks == null) {
            gestureClassifier.onNoHand()
            pointerController.reset()
            GestureAccessibilityService.instance?.removeCursor()
            updateUI("🖐", "No hand detected", "Show your hand to the camera")
            return
        }

        // ── Check if gestures are enabled ────────────────────────
        if (!prefs.gestureEnabled) {
            updateUI("⏹", "Gestures disabled", "Enable in Settings")
            return
        }

        val now = System.currentTimeMillis()

        // ── Update pointer position (always, for use by all gestures) ──
        val (cursorX, cursorY) = pointerController.update(
            rawLandmarkX = landmarks[8].x(),
            rawLandmarkY = landmarks[8].y()
        )

        // ── Classify the gesture ─────────────────────────────────
        val gesture = gestureClassifier.classify(landmarks, cursorX, cursorY, now)
        val svc = GestureAccessibilityService.instance

        // ── Route to appropriate handler ─────────────────────────
        when (gesture) {
            is GestureType.Fist -> {
                if (now - lastToggleTime > toggleDelay) {
                    togglePause()
                    lastToggleTime = now
                }
            }

            is GestureType.FingerPointer -> {
                if (!isPaused && prefs.fingerMouseEnabled) {
                    svc?.moveCursor(gesture.normX, gesture.normY)
                    svc?.setCursorState("active")
                    updateUI("☝️", "Cursor Move", "Moving pointer")
                }
            }

            is GestureType.ScrollUp -> {
                if (!isPaused) {
                    svc?.scroll(scrollUp = true)
                    svc?.setCursorState("idle")
                    updateUI("⬆️", "Scroll UP", "Index + Middle up")
                }
            }

            is GestureType.ScrollDown -> {
                if (!isPaused) {
                    svc?.scroll(scrollUp = false)
                    svc?.setCursorState("idle")
                    updateUI("⬇️", "Scroll DOWN", "Middle up, Index curled")
                }
            }

            is GestureType.ZoomIn -> {
                if (!isPaused) {
                    svc?.zoom(zoomIn = true)
                    svc?.setCursorState("idle")
                    updateUI("🔍", "Zoom IN", "Fingers spreading")
                }
            }

            is GestureType.ZoomOut -> {
                if (!isPaused) {
                    svc?.zoom(zoomIn = false)
                    svc?.setCursorState("idle")
                    updateUI("🔎", "Zoom OUT", "Fingers closing")
                }
            }

            is GestureType.LongPress -> {
                if (!isPaused) {
                    svc?.longPress(gesture.normX, gesture.normY)
                    svc?.setCursorState("longpress")
                    updateUI("🤙", "Long Press", "Thumb + Middle held")
                }
            }

            is GestureType.Idle -> {
                if (!isPaused) {
                    updateUI("🖐", "Hand detected", "Make a gesture")
                }
            }

            is GestureType.None -> {
                // Shouldn't reach here (handled by null check above)
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  UI Updates
    // ══════════════════════════════════════════════════════════════

    private fun updateUI(emoji: String, title: String, subtitle: String) {
        runOnUiThread {
            gestureEmoji.text = emoji
            gestureText.text = title
            gestureSubtext.text = subtitle
        }
    }

    private fun togglePause() {
        isPaused = !isPaused
        runOnUiThread {
            if (isPaused) {
                statusText.text = "PAUSED"
                statusText.setTextColor(getColor(R.color.status_paused))
                statusDot.setBackgroundColor(getColor(R.color.status_paused))
                toggleBtn.text = "▶ Resume"
                gestureEmoji.text = "⏸"
                gestureText.text = "Paused"
                gestureSubtext.text = "Tap Resume or show fist"
                GestureAccessibilityService.instance?.removeCursor()
            } else {
                statusText.text = "ACTIVE"
                statusText.setTextColor(getColor(R.color.status_active))
                statusDot.setBackgroundColor(getColor(R.color.status_active))
                toggleBtn.text = "⏸ Pause"
                gestureEmoji.text = "✅"
                gestureText.text = "Resumed"
                gestureSubtext.text = "Gesture control active"
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  Permissions
    // ══════════════════════════════════════════════════════════════

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startPipeline()
        }
    }
}
