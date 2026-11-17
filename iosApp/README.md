# ML PT for iOS

The SwiftUI host embeds the shared Compose application, including the drawer, workout plans, muscle map, lift animations, profile, graphs and completed-workout history. Requires an Apple Silicon Mac, Xcode, JDK 21, CocoaPods, and iOS 16 or later. The repository's Kotlin 2.1.21 / Compose 1.8.0 baseline should be built with a compatible Xcode 16 toolchain; newer toolchains may require upgrading Kotlin/Compose together.

## Build and install

1. Copy/clone the **whole repository** to the Mac. Set `JAVA_HOME` to JDK 21. Configure the Android SDK if Gradle's Android plugin requests it; do not copy Windows `local.properties`.
2. From `iosApp`, run `pod install`. Open **MLPT.xcworkspace**, which this command generates, rather than the `.xcodeproj`.
3. Choose the **MLPT** scheme. In **Signing & Capabilities**, select your Apple development team and a unique bundle identifier.
4. Choose a connected iPhone, enable Developer Mode if requested, and press Run. The build phase calls `:composeApp:embedAndSignAppleFrameworkForXcode` and links the shared UI automatically. No manually copied framework is needed.

For a simulator compile without device signing, after installing pods:

```sh
cd iosApp
xcodebuild -workspace MLPT.xcworkspace -scheme MLPT -configuration Debug \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
  CODE_SIGNING_ALLOWED=NO build
```

Arm64 iOS devices and Apple Silicon simulators are configured. An Intel Mac simulator target is not included. The simulator can exercise the shared screens but cannot validate live camera coaching. First builds download Gradle/Kotlin Native dependencies and CocoaPods; keep network access available.

## Camera and coaching

Open **ML · Pose detection**. Select squat, push-up or curl, choose the camera, read the setup instructions and confirm a steady side-on view. **Start exercise & log** requests camera permission and starts a session; **Stop & save log** commits it. Leaving the screen, locking the phone, or backgrounding interrupts the session and releases the camera. Resuming requires starting a new session.

The native AVFoundation camera supplies upright portrait buffers to Google's ML Kit stream-mode detector. Preview and analysis use the same mirroring; the skeleton fits the image inside the preview's letterbox. The shared `PoseCoach` and `PoseSessionRecorder` provide the same experimental angle checks, visibility gates, rep counting, retry handling and sampled measurements as Android. See the [pose coaching guide](../docs/POSE_COACHING.md) for thresholds and limitations. No camera images or complete landmark sequences are saved.

**Saved pose sessions** lists summaries, **Load older sessions** expands the list, and **View session measurements** opens a chooser for the loaded history. Details show rep measurements and the latest 100 observations of the selected session. Pose logs remain separate from workout completion graphs.

Implementation follows [Google's iOS pose detector integration](https://developers.google.com/ml-kit/vision/pose-detection/ios) and [Kotlin's direct Xcode integration](https://kotlinlang.org/docs/multiplatform/multiplatform-direct-integration.html). ML Kit is pinned to `GoogleMLKit/PoseDetection` 8.0.0; retain the generated `Podfile.lock` when building on the Mac.

## Local data

`SQLiteDocumentStore` writes `Application Support/training.sqlite`, excludes it from cloud backup, and uses WAL, full synchronous commits and parameterized statements. The `documents` table holds separate versioned training and pose aggregate records. Each replacement is a single atomic SQLite statement; errors propagate into the shared loading/save/retry UI. Disk operations run off the main thread. No account, server or cloud sync is involved, and Android data is not automatically transferred.

`DocumentTrainingStorage` preserves profile and ordered completed-workout snapshots. `DocumentPoseStorage` preserves angle ranges, timing, feedback, sample counts and observations, performs idempotent checkpoints and recovers interrupted sessions at the last persisted event. Corrupt or newer-format data produces an error rather than being overwritten. Unlike Android's normalized schema, these are aggregate document records in SQLite: each checkpoint rewrites the pose collection. This is an initial local implementation; normalize or partition records before supporting very large histories.

Uninstalling removes the local data. Workout checklists are transient. The app's privacy manifest declares uptime use for local elapsed-time calculations; review the complete SDK privacy report before App Store submission.

## Verification and remaining device checks

This iOS implementation was authored on Windows. Xcode compilation, CocoaPods resolution, signing and iPhone execution **have not been performed**. Shared storage round-trip, corruption, idempotency and interruption tests run on the JVM; they do not validate the native Swift SQLite wrapper or Apple camera integration.

On the Mac, run the simulator build above, then verify on an iPhone:

- Drawer navigation, muscle selections, lift animations and profile graphs.
- Complete a workout and edit the profile; force-quit/reopen and confirm both persist.
- Camera permission grant/denial, front/rear alignment, partial/out-of-frame tracking and all three exercises.
- Stop, navigate away, background and force-quit during a pose session; verify logs, interruption status and no duplicate reps after reopening.
- Confirm native UI fits with larger accessibility text sizes and that history remains scrollable.

App Store distribution additionally needs an app icon, your signing/release configuration, a final privacy report and real-person coaching validation. No signed IPA or App Store upload was produced.
