# Gesture Control Android — Complete Refactor Plan

## Background & Problem Analysis

The current project is a **2-file monolith** (`MainActivity.kt` at 412 lines, `GestureScrollService.kt` at 311 lines) that crams camera setup, MediaPipe initialization, gesture detection state machine, coordinate mapping, and accessibility dispatch all into two classes. It also contains **PC-originated concepts** (left click, right click, drag-and-drop) that don't map well to mobile interaction paradigms.

### Issues Identified in Current Code

| Issue | Location | Impact |
|-------|----------|--------|
| `tap()` = "left click" — Android already has native tap | `MainActivity.kt:346`, `GestureScrollService.kt:104` | Misleading naming, unnecessary gesture |
| `longPressAt()` called "right click" | `MainActivity.kt:328-334`, `GestureScrollService.kt:116` | PC concept; long press IS valid, but naming is wrong |
| `startDrag/updateDrag/endDrag` — drag & drop | Full pipeline in both files | Low real-world utility on mobile; overly complex |
| God-class `MainActivity` — camera + MediaPipe + gesture logic + UI | Single 412-line file | Untestable, hard to modify |
| `GestureScrollService` mixes: scroll, tap, drag, zoom, cursor overlay | Single 311-line file | Violates SRP |
| No settings UI — thresholds are hardcoded constants | `MainActivity.kt:56-64` | Users can't tune sensitivity |
| Deprecated `defaultDisplay.getMetrics()` | `GestureScrollService.kt:293-296` | Will break on newer APIs |
| No lifecycle awareness for camera/MediaPipe | `MainActivity.kt` | Potential resource leaks |
| Cursor overlay is a basic orange dot with no animation | `GestureScrollService.kt:252-274` | Poor visual feedback |

---

## User Review Required

> [!IMPORTANT]
> **Gesture removal**: The plan removes **tap (left click)**, **right click**, and **drag & drop** gestures entirely. The "long press" gesture is **kept** but rebranded as a proper mobile long-press (not "right click"). Confirm this is acceptable.

> [!IMPORTANT]
> **Architecture scope**: This refactors the 2-file monolith into ~11 focused files using a clean architecture approach. This is a near-complete rewrite. The project folder structure will change significantly.

> [!WARNING]
> **SharedPreferences for settings**: The plan uses `SharedPreferences` for the settings panel (sensitivity, smoothing, etc.) rather than a Room database. This is appropriate for the small amount of data. Confirm this is acceptable.

---

## Proposed Changes

### Final Project Structure

```
app/src/main/java/com/example/gesturescroll/
├── MainActivity.kt                    ← Slim: UI + nav + permissions only
├── service/
│   └── GestureAccessibilityService.kt ← Accessibility dispatch (scroll, zoom, long-press, cursor)
├── camera/
│   └── CameraManager.kt              ← CameraX setup + frame analysis
├── detection/
│   ├── HandTracker.kt                 ← MediaPipe initialization + landmark extraction
│   ├── GestureClassifier.kt           ← Pure function: landmarks → GestureType enum
│   └── GestureType.kt                 ← Sealed class for all gesture types
├── pointer/
│   ├── FingerPointerController.kt     ← Finger mouse: smoothing, sensitivity, boundary mapping
│   └── CursorOverlay.kt              ← Visual cursor (animated ring + dot)
├── settings/
│   ├── SettingsActivity.kt            ← Settings UI
│   └── GesturePreferences.kt          ← SharedPreferences wrapper
└── util/
    └── ScreenUtils.kt                 ← Screen metrics helper (non-deprecated)
```

```
app/src/main/res/
├── layout/
│   ├── activity_main.xml              ← Redesigned main UI
│   └── activity_settings.xml          ← Settings panel layout
├── values/
│   ├── strings.xml                    ← Updated strings
│   ├── colors.xml                     ← [NEW] Color palette
│   └── themes.xml                     ← [NEW] App theme
├── drawable/
│   ├── cursor_dot.xml                 ← [NEW] Animated cursor drawable
│   └── rounded_card_bg.xml            ← [NEW] Card background
└── xml/
    └── accessibility_service_config.xml ← Updated config
```

---

### Component 1: Gesture Type System

#### [NEW] [GestureType.kt](file:///c:/Users/dell/Desktop/New%20folder%20(2)/GestureScrollAndroid_Enhanced/GestureScrollAndroid_Enhanced/app/src/main/java/com/example/gesturescroll/detection/GestureType.kt)

Sealed class defining **only** the 5 mobile-appropriate gestures:

```kotlin
sealed class GestureType {
    object None : GestureType()
    object Fist : GestureType()           // Pause/Resume toggle
    object FingerPointer : GestureType()   // Cursor move (index only)
    object ScrollUp : GestureType()        // Index + Middle, index above
    object ScrollDown : GestureType()      // Middle up, index curled
    object ZoomIn : GestureType()          // Index + Middle spreading
    object ZoomOut : GestureType()         // Index + Middle closing
    object LongPress : GestureType()       // Thumb + Middle pinch (hold)
}
```

**Removed**: `Tap` / `LeftClick`, `RightClick`, `DragStart/Update/End`

---

### Component 2: Gesture Classifier (Pure Logic)

#### [NEW] [GestureClassifier.kt](file:///c:/Users/dell/Desktop/New%20folder%20(2)/GestureScrollAndroid_Enhanced/GestureScrollAndroid_Enhanced/app/src/main/java/com/example/gesturescroll/detection/GestureClassifier.kt)

A **pure function** class — no state, no side effects. Takes landmarks + thresholds, returns `GestureType`.

- Extracts the gesture detection logic from `MainActivity.onHandResult()` (lines 171-291)
- Makes it unit-testable without Android framework dependencies
- Handles: finger extension detection, fist detection, pinch distance, spread delta for zoom

---

### Component 3: Hand Tracker (MediaPipe Wrapper)

#### [NEW] [HandTracker.kt](file:///c:/Users/dell/Desktop/New%20folder%20(2)/GestureScrollAndroid_Enhanced/GestureScrollAndroid_Enhanced/app/src/main/java/com/example/gesturescroll/detection/HandTracker.kt)

Encapsulates MediaPipe `HandLandmarker` setup with lifecycle awareness:

- `init(context, onResult, onError)` — creates HandLandmarker
- `detect(bitmap, timestamp)` — feeds frame to detection
- `close()` — releases resources
- Exposes results via callback

---

### Component 4: Camera Manager

#### [NEW] [CameraManager.kt](file:///c:/Users/dell/Desktop/New%20folder%20(2)/GestureScrollAndroid_Enhanced/GestureScrollAndroid_Enhanced/app/src/main/java/com/example/gesturescroll/camera/CameraManager.kt)

Lifecycle-aware CameraX wrapper:

- Manages `ProcessCameraProvider`, `Preview`, and `ImageAnalysis`
- Configurable resolution (default 480×360 for performance)
- Connects to `HandTracker` for frame processing
- Proper cleanup via `LifecycleObserver`

---

### Component 5: Finger Pointer Controller (★ MOST IMPORTANT)

#### [NEW] [FingerPointerController.kt](file:///c:/Users/dell/Desktop/New%20folder%20(2)/GestureScrollAndroid_Enhanced/GestureScrollAndroid_Enhanced/app/src/main/java/com/example/gesturescroll/pointer/FingerPointerController.kt)

The **core Finger Mouse system** adapted for Android:

```
Camera Frame → MediaPipe Landmark → Normalized Coords → Smoothing → Boundary Mapping → Screen Coords → Cursor Position
```

**Features:**
1. **Exponential Moving Average smoothing** — reduces jitter (configurable α from settings)
2. **Adjustable sensitivity** — multiplier on movement delta (1.0x–3.0x from settings)
3. **Dead zone** — ignore micro-movements below threshold to prevent tremor-induced drift
4. **Boundary mapping** — map camera ROI (configurable rectangle) to full screen coordinates
5. **Velocity-based acceleration** — slow movement = precise, fast movement = amplified
6. **Screen edge clamping** — prevent cursor from going off-screen
7. **Front-camera mirroring** — X-axis flip for natural feel

**Algorithm:**
```
1. Raw landmark (0..1) from MediaPipe
2. Mirror X for front camera: rawX = 1 - landmark.x
3. Apply ROI mapping: map [roiLeft..roiRight] to [0..1]  
4. Calculate delta from last position
5. Apply dead zone filter (ignore if delta < threshold)
6. Apply velocity acceleration curve
7. Apply sensitivity multiplier
8. Apply EMA smoothing: pos += alpha * (target - pos)
9. Clamp to screen bounds
10. Output screen pixel coordinates
```

---

### Component 6: Cursor Overlay (Visual Feedback)

#### [NEW] [CursorOverlay.kt](file:///c:/Users/dell/Desktop/New%20folder%20(2)/GestureScrollAndroid_Enhanced/GestureScrollAndroid_Enhanced/app/src/main/java/com/example/gesturescroll/pointer/CursorOverlay.kt)

Improved from the basic orange dot to an animated cursor with:
- Pulsing ring animation (breathe effect)
- Color changes based on gesture state (idle blue, active green, long-press red)
- Smooth position transitions via `ValueAnimator`
- Built using `TYPE_ACCESSIBILITY_OVERLAY` (no SYSTEM_ALERT_WINDOW needed)
- Outer ring (48dp) + inner dot (16dp) for better visibility

---

### Component 7: Accessibility Service (Refactored)

#### [MODIFY] [GestureAccessibilityService.kt](file:///c:/Users/dell/Desktop/New%20folder%20(2)/GestureScrollAndroid_Enhanced/GestureScrollAndroid_Enhanced/app/src/main/java/com/example/gesturescroll/service/GestureAccessibilityService.kt)

Renamed from `GestureScrollService`. **Removed methods:**
- ❌ `tap()` — no left click on mobile
- ❌ `startDrag()`, `updateDrag()`, `endDrag()` — no drag-and-drop
- Removed all drag state tracking (`activeDragStroke`, `dragCurrentX/Y`)

**Kept & improved methods:**
- ✅ `scroll(scrollUp: Boolean)` — unchanged
- ✅ `zoom(zoomIn: Boolean)` — unchanged
- ✅ `longPress(normX, normY)` — renamed from `longPressAt`, rebranded from "right click"
- ✅ `moveCursor()` → delegated to `CursorOverlay`
- ✅ `removeCursor()` → delegated to `CursorOverlay`

**Fixed:** Replaced deprecated `defaultDisplay.getMetrics()` with `WindowMetrics` API (API 30+) with backward-compatible fallback.

---

### Component 8: Settings System

#### [NEW] [GesturePreferences.kt](file:///c:/Users/dell/Desktop/New%20folder%20(2)/GestureScrollAndroid_Enhanced/GestureScrollAndroid_Enhanced/app/src/main/java/com/example/gesturescroll/settings/GesturePreferences.kt)

SharedPreferences wrapper exposing:

| Setting | Key | Default | Range |
|---------|-----|---------|-------|
| Cursor Sensitivity | `cursor_sensitivity` | `1.5f` | 0.5 – 3.0 |
| Cursor Smoothing | `cursor_smoothing` | `0.35f` | 0.1 – 0.8 |
| Scroll Speed | `scroll_speed` | `700` | 300 – 1200 px |
| Gesture Mode Enabled | `gesture_enabled` | `true` | on/off |
| Finger Mouse Mode | `finger_mouse_enabled` | `true` | on/off |
| Dead Zone Threshold | `dead_zone` | `0.005f` | 0.001 – 0.02 |

#### [NEW] [SettingsActivity.kt](file:///c:/Users/dell/Desktop/New%20folder%20(2)/GestureScrollAndroid_Enhanced/GestureScrollAndroid_Enhanced/app/src/main/java/com/example/gesturescroll/settings/SettingsActivity.kt)

Clean settings panel with:
- SeekBars for sensitivity/smoothing/scroll speed
- Toggle switches for gesture mode and finger mouse mode
- Real-time value display
- Material Design card-based layout

#### [NEW] [activity_settings.xml](file:///c:/Users/dell/Desktop/New%20folder%20(2)/GestureScrollAndroid_Enhanced/GestureScrollAndroid_Enhanced/app/src/main/res/layout/activity_settings.xml)

Material Design settings layout with grouped cards.

---

### Component 9: Screen Utilities

#### [NEW] [ScreenUtils.kt](file:///c:/Users/dell/Desktop/New%20folder%20(2)/GestureScrollAndroid_Enhanced/GestureScrollAndroid_Enhanced/app/src/main/java/com/example/gesturescroll/util/ScreenUtils.kt)

Non-deprecated screen metrics helper:
- Uses `WindowMetrics` on API 30+ 
- Falls back to `DisplayMetrics` on older APIs
- Provides `screenWidth`, `screenHeight`, `screenCenter()`, `normToScreen()`

---

### Component 10: MainActivity (Slim Coordinator)

#### [MODIFY] [MainActivity.kt](file:///c:/Users/dell/Desktop/New%20folder%20(2)/GestureScrollAndroid_Enhanced/GestureScrollAndroid_Enhanced/app/src/main/java/com/example/gesturescroll/MainActivity.kt)

Reduced from 412 to ~150 lines. Responsibilities:
1. Permission handling (camera)
2. Initialize `CameraManager` and `HandTracker`
3. Route `HandTracker` results → `GestureClassifier` → `GestureAccessibilityService`
4. Update gesture status UI
5. Navigation to Settings
6. Lifecycle management

**Removed**: All gesture detection logic, cursor smoothing math, pinch state machine, spread history, drag tracking.

---

### Component 11: UI & Resources

#### [MODIFY] [activity_main.xml](file:///c:/Users/dell/Desktop/New%20folder%20(2)/GestureScrollAndroid_Enhanced/GestureScrollAndroid_Enhanced/app/src/main/res/layout/activity_main.xml)

Redesigned layout:
- Camera preview (smaller, in a rounded card — not full screen)
- Status indicator (animated dot: green/red)
- Current gesture label (larger, centered)
- Bottom bar with: Settings button, Accessibility button, Gesture toggle
- Updated cheat sheet (only 5 gestures instead of 9)

#### [MODIFY] [AndroidManifest.xml](file:///c:/Users/dell/Desktop/New%20folder%20(2)/GestureScrollAndroid_Enhanced/GestureScrollAndroid_Enhanced/app/src/main/AndroidManifest.xml)

- Register `SettingsActivity`
- Update service reference to new package path
- Add `FOREGROUND_SERVICE` permission for camera on Android 14+

#### [MODIFY] [strings.xml](file:///c:/Users/dell/Desktop/New%20folder%20(2)/GestureScrollAndroid_Enhanced/GestureScrollAndroid_Enhanced/app/src/main/res/values/strings.xml)

Updated gesture descriptions, removed click/drag references.

#### [NEW] [colors.xml](file:///c:/Users/dell/Desktop/New%20folder%20(2)/GestureScrollAndroid_Enhanced/GestureScrollAndroid_Enhanced/app/src/main/res/values/colors.xml)

Material color palette.

#### [NEW] [themes.xml](file:///c:/Users/dell/Desktop/New%20folder%20(2)/GestureScrollAndroid_Enhanced/GestureScrollAndroid_Enhanced/app/src/main/res/values/themes.xml)

Dark theme with accent colors.

---

## Gesture Map Summary (After Refactor)

| Hand Pose | Action | Gesture Type |
|-----------|--------|-------------|
| ✊ Fist | Pause / Resume | `Fist` |
| ☝️ Index only extended | **Finger Mouse** — cursor follows finger | `FingerPointer` |
| ☝✌ Index + Middle up (spread stable) | Scroll UP | `ScrollUp` |
| 🖕 Middle up, Index curled | Scroll DOWN | `ScrollDown` |
| ✌️ Index + Middle spreading | Zoom IN | `ZoomIn` |
| ✌️ Index + Middle closing | Zoom OUT | `ZoomOut` |
| 🤙 Thumb + Middle pinch (hold) | Long Press | `LongPress` |

**Removed:** ❌ Left Click, ❌ Right Click, ❌ Drag & Drop

---

## Open Questions

> [!IMPORTANT]
> 1. **Long Press trigger**: Currently triggered by thumb+middle pinch. Should it require a hold duration (e.g., 600ms of sustained pinch), or fire immediately on pinch detection? The current code fires immediately — I'll add a 500ms hold requirement for reliability.

> [!IMPORTANT]
> 2. **Camera preview visibility**: Should the camera preview be shown full-screen (current), or in a small card with a toggle to show/hide? I'll default to a compact card with toggle.

> [!IMPORTANT]
> 3. **Minimum Android version**: Current `minSdk 26`. The `WindowMetrics` API needs API 30, but I'll add a backward-compatible fallback. Should we raise `minSdk` to 28 or keep 26?

---

## Verification Plan

### Automated Checks
1. `./gradlew assembleDebug` — build succeeds with no errors
2. Verify all gesture types route correctly through the classifier
3. Verify `GesturePreferences` persists and loads settings accurately

### Manual Verification
1. **Finger Mouse**: Index finger movement → smooth cursor tracking with no jitter
2. **Scroll**: Both directions work in browser/social media apps
3. **Zoom**: Works in Google Maps and Gallery
4. **Long Press**: Opens context menu in text-heavy apps
5. **Settings**: All sliders affect behavior in real-time
6. **Pause/Resume**: Fist reliably toggles system
