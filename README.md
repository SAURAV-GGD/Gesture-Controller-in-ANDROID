# GestureScrollAndroid v2.0 — Clean Mobile Gesture Control

<p align="center">
  <img src="https://camo.githubusercontent.com/87cd1af0b51151cc8928461c1287bc71b3d50e3b827988865a497714deeb82ee/68747470733a2f2f696d616765732d7769786d702d6564333061383662386334636138383737373335393463322e7769786d702e636f6d2f662f31326362653861342d663535632d346234302d383562622d6438653134303565376238342f64616f70726c6b2d36336235303664352d306530312d343064342d393738322d3861626333393535346165312e6769663f746f6b656e3d65794a30655841694f694a4b563151694c434a68624763694f694a49557a49314e694a392e65794a7a645749694f694a31636d3436595842774f6a646c4d4751784f4467354f4449794e6a517a4e7a4e684e5759775a4451784e5756684d4751794e6d55774969776961584e7a496a6f6964584a754f6d467763446f335a54426b4d5467344f5467794d6a59304d7a637a5954566d4d4751304d54566c5954426b4d6a5a6c4d434973496d39696169493657317437496e4268644767694f694a634c325a634c7a457959324a6c4f4745304c5759314e574d744e4749304d4330344e574a694c5751345a5445304d44566c4e3249344e4677765a47467663484a73617930324d3249314d445a6b4e5330775a5441784c5451775a4451744f5463344d69303459574a6a4d7a6b314e5452685a5445755a326c6d496e3164585377695958566b496a7062496e5679626a707a5a584a3261574e6c4f6d5a70624755755a473933626d7876595751695858302e61676a317a6654756b50565539582d386a513578435541324e392d61354c6e674e58506d32366b73476e67
" width="700">
</p>
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
