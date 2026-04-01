# GestureScrollAndroid v2.0 — Clean Mobile Gesture Control

Real-time hand-gesture controller for Android, built with **MediaPipe HandLandmarker** + **CameraX** + **AccessibilityService**.

Features a **Finger Mouse** virtual pointer system inspired by [SAURAV-GGD/Finger-Mouse](https://github.com/SAURAV-GGD/Finger-Mouse), fully adapted for Android.

---

## 🎯 Mobile-First Gestures

Only gestures that make sense on Android — no PC concepts (click, right-click, drag).

| Hand Pose | Action |
|-----------|--------|
| ✊ **Fist** | Pause / Resume |
| ☝️ **Index only** | **Finger Mouse** — cursor follows your finger |
| ☝✌ **Index + Middle up** (spread stable) | **Scroll UP** |
| 🖕 **Middle up, Index curled** | **Scroll DOWN** |
| ✌️↔ **Index + Middle spreading** | **Zoom IN** |
| ✌️→← **Index + Middle closing** | **Zoom OUT** |
| 🤙 **Thumb + Middle pinch** (hold 500ms) | **Long Press** |

---

## 🖱️ Finger Mouse System

The star feature — a virtual pointer controlled by your index finger:

- **EMA smoothing** — reduces jitter for stable cursor movement
- **Dead zone filter** — ignores micro-tremors
- **Velocity acceleration** — slow = precise, fast = amplified
- **Adjustable sensitivity** — configurable 0.5x to 3.0x
- **Animated cursor overlay** — pulsing ring with color states
- **Front-camera mirroring** — natural left/right mapping

---

## 📁 Project Structure

```
app/src/main/java/com/example/gesturescroll/
├── MainActivity.kt                    ← Slim coordinator (~220 lines)
├── camera/
│   └── CameraManager.kt              ← CameraX lifecycle wrapper
├── detection/
│   ├── GestureClassifier.kt           ← Pure gesture classification logic
│   ├── GestureType.kt                 ← Sealed class for gesture types
│   └── HandTracker.kt                 ← MediaPipe wrapper
├── pointer/
│   ├── CursorOverlay.kt              ← Animated cursor (ring + dot)
│   └── FingerPointerController.kt     ← Smoothing, sensitivity, mapping
├── service/
│   └── GestureAccessibilityService.kt ← Touch dispatch (scroll, zoom, etc.)
├── settings/
│   ├── GesturePreferences.kt          ← SharedPreferences wrapper
│   └── SettingsActivity.kt            ← Settings UI
└── util/
    └── ScreenUtils.kt                 ← Non-deprecated screen metrics
```

---

## ⚙️ Settings Panel

All parameters are user-configurable:

| Setting | Default | Range |
|---------|---------|-------|
| Cursor Sensitivity | 1.5x | 0.5 – 3.0 |
| Cursor Smoothing | 0.35 | 0.1 – 0.8 |
| Dead Zone | 0.005 | 0.001 – 0.02 |
| Scroll Speed | 700px | 300 – 1200 |
| Gesture Mode | ON | toggle |
| Finger Mouse | ON | toggle |

---

## 🚀 Setup

### 1. Add MediaPipe model to assets
Download `hand_landmarker.task` from [MediaPipe Models](https://developers.google.com/mediapipe/solutions/vision/hand_landmarker#models) and place it in:
```
app/src/main/assets/hand_landmarker.task
```

### 2. Build & install
```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 3. Enable Accessibility Service
Launch app → tap **"Enable Service"** → find **Gesture Control** → toggle ON.

### 4. Grant camera permission
Accept the camera permission prompt on first launch.

---

## 🏗️ Architecture

```
Camera Frame → CameraManager → HandTracker (MediaPipe)
    → GestureClassifier (pure logic) → GestureType
        → MainActivity (routes) → GestureAccessibilityService (dispatches)
                                → FingerPointerController (cursor math)
                                → CursorOverlay (visual feedback)
```

**Key design decisions:**
- **GestureClassifier is pure** — no Android deps, unit-testable
- **FingerPointerController is configurable** — reads from GesturePreferences
- **CursorOverlay uses TYPE_ACCESSIBILITY_OVERLAY** — no extra permissions
- **CameraManager is lifecycle-aware** — auto cleanup
- **Rate limiting** built into the service layer — prevents gesture spam

---

## 📱 Requirements

- Android 8.0+ (API 26)
- Front camera
- Accessibility service enabled

---

## 🔮 Future Improvements

- Custom gesture mapping
- Multi-hand support
- Gesture recording and replay
- Floating mini-preview window
- Haptic feedback on gesture recognition
- Per-app gesture profiles
