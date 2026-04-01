package com.example.gesturescroll.settings

import android.os.Bundle
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.gesturescroll.R

/**
 * ════════════════════════════════════════════════════════════════
 *  SettingsActivity — User-facing settings panel
 * ════════════════════════════════════════════════════════════════
 *
 *  Exposes all tunable parameters via sliders and toggles.
 *  Changes are saved immediately to SharedPreferences.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: GesturePreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = GesturePreferences(this)

        setupBackButton()
        setupResetButton()
        setupToggles()
        setupSensitivitySlider()
        setupSmoothingSlider()
        setupDeadZoneSlider()
        setupScrollSpeedSlider()
    }

    // ── Navigation ───────────────────────────────────────────────

    private fun setupBackButton() {
        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }
    }

    private fun setupResetButton() {
        findViewById<TextView>(R.id.btnReset).setOnClickListener {
            prefs.resetToDefaults()
            // Reload all UI to reflect defaults
            recreate()
        }
    }

    // ── Toggles ──────────────────────────────────────────────────

    private fun setupToggles() {
        val switchGesture = findViewById<Switch>(R.id.switchGesture)
        val switchFingerMouse = findViewById<Switch>(R.id.switchFingerMouse)

        switchGesture.isChecked = prefs.gestureEnabled
        switchFingerMouse.isChecked = prefs.fingerMouseEnabled

        switchGesture.setOnCheckedChangeListener { _, isChecked ->
            prefs.gestureEnabled = isChecked
        }
        switchFingerMouse.setOnCheckedChangeListener { _, isChecked ->
            prefs.fingerMouseEnabled = isChecked
        }
    }

    // ── Sliders ──────────────────────────────────────────────────

    /**
     * Cursor Sensitivity slider.
     * Range: 0.5 – 3.0, mapped to SeekBar 0–100.
     */
    private fun setupSensitivitySlider() {
        val seekBar = findViewById<SeekBar>(R.id.seekSensitivity)
        val label = findViewById<TextView>(R.id.tvSensitivity)
        val range = GesturePreferences.SENSITIVITY_RANGE

        // Initialize position from saved pref
        val current = prefs.cursorSensitivity
        seekBar.progress = valueToProgress(current, range.start, range.endInclusive)
        label.text = String.format("%.1fx", current)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                val value = progressToValue(progress, range.start, range.endInclusive)
                label.text = String.format("%.1fx", value)
                prefs.cursorSensitivity = value
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
    }

    /**
     * Cursor Smoothing slider.
     * Range: 0.1 – 0.8 (low = smooth/laggy, high = responsive/jittery).
     */
    private fun setupSmoothingSlider() {
        val seekBar = findViewById<SeekBar>(R.id.seekSmoothing)
        val label = findViewById<TextView>(R.id.tvSmoothing)
        val range = GesturePreferences.SMOOTHING_RANGE

        val current = prefs.cursorSmoothing
        seekBar.progress = valueToProgress(current, range.start, range.endInclusive)
        label.text = String.format("%.2f", current)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                val value = progressToValue(progress, range.start, range.endInclusive)
                label.text = String.format("%.2f", value)
                prefs.cursorSmoothing = value
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
    }

    /**
     * Dead Zone slider.
     * Range: 0.001 – 0.02. Filters out hand tremor below this threshold.
     */
    private fun setupDeadZoneSlider() {
        val seekBar = findViewById<SeekBar>(R.id.seekDeadZone)
        val label = findViewById<TextView>(R.id.tvDeadZone)
        val range = GesturePreferences.DEAD_ZONE_RANGE

        val current = prefs.deadZone
        seekBar.progress = valueToProgress(current, range.start, range.endInclusive)
        label.text = String.format("%.3f", current)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                val value = progressToValue(progress, range.start, range.endInclusive)
                label.text = String.format("%.3f", value)
                prefs.deadZone = value
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
    }

    /**
     * Scroll Speed slider.
     * Range: 300 – 1200 pixels per scroll gesture.
     */
    private fun setupScrollSpeedSlider() {
        val seekBar = findViewById<SeekBar>(R.id.seekScrollSpeed)
        val label = findViewById<TextView>(R.id.tvScrollSpeed)
        val range = GesturePreferences.SCROLL_SPEED_RANGE

        val current = prefs.scrollSpeed
        seekBar.progress = valueToProgress(
            current.toFloat(), range.first.toFloat(), range.last.toFloat()
        )
        label.text = "${current}px"

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                val value = progressToValue(
                    progress, range.first.toFloat(), range.last.toFloat()
                ).toInt()
                label.text = "${value}px"
                prefs.scrollSpeed = value
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
    }

    // ── Utility: map between SeekBar(0–100) and actual value ranges ──

    private fun progressToValue(progress: Int, min: Float, max: Float): Float =
        min + (progress / 100f) * (max - min)

    private fun valueToProgress(value: Float, min: Float, max: Float): Int =
        ((value - min) / (max - min) * 100).toInt().coerceIn(0, 100)
}
