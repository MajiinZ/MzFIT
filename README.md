# MzFIT — AI Personal Trainer

Jetpack Compose + CameraX + ML Kit Pose Detection personal training app for Android.

## Features
- **AI Coach (pose detection)** — real-time skeleton overlay with glow, joint-angle arcs, confidence fading and red fault highlighting. One-Euro filtered landmarks, rep state machine with arming, ROM %, tempo and per-rep form scoring, debounced form-fault cues, spoken (TTS) and haptic feedback, auto-start countdown when you're in frame, hold timer for planks/wall sits. Fast and Accurate model toggle.
- **Detailed muscle map** — front/back anatomical figure with 21 muscle groups. Tap any muscle for details. "This week" heatmap shows training load from your logged sets; "Explore" mode highlights primary/secondary muscles. Exercise pages and live sessions show the muscles being worked.
- **Workouts by body part** — Chest, Back, Shoulders, Arms, Core, Legs, Glutes, Full Body; ~60 exercises with steps, cues, equipment and level filters.
- **Navigation** — hamburger drawer (Workouts, AI Coach, Muscle Map, History, Settings, About & Privacy) plus a 3-button bottom bar (Workouts, AI Coach, Muscle Map).
- History, settings (voice, haptics, model, units), and all data stored locally on device.

## Architecture
```
data/        Muscle, BodyPart, ExerciseLibrary, WorkoutRepository (SharedPreferences JSON)
ml/pose/     Skeleton geometry, OneEuroFilter, TrackerSpecs (13 motion patterns), ExerciseTracker
camera/      PoseCamera — LifecycleCameraController + MlKitAnalyzer (view-referenced coords)
ui/          theme, components, musclemap (generated paths), screens, session, navigation
tools/musclegen/gen.py   regenerates ui/musclemap/MuscleMapPaths.kt
```

## Build
```
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:bundleRelease   # ship the AAB so Play splits native libs per ABI
```
