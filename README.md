# MzFit

A personal training application scaffold using Kotlin Multiplatform and Compose Multiplatform. Its module layout, serif headings, Material 3 controls, and responsive navigation are guided by the local **MZBotanicalsV1** project (`C:/Users/zakfa/StudioProjects/WhiteLabel`). The training app uses a charcoal, off-white and electric-blue palette, with cyan camera landmarks. This is an independent app with its own package ID (`com.mlpt.app`).

## Included

- User-supplied MzFit Android launcher icon and native startup splash. Original artwork and a 512px store-icon derivative are kept in `branding/`; see [branding details](branding/README.md).
- Android training studio: saved custom routines, exercise/equipment search, custom exercises, ordering, per-exercise targets and optional weekday schedules.
- Durable live workout logger with actual reps/loads or timed sets, previous-session reuse, automatic rest timing, always-visible rest controls, notes and atomic/idempotent workout completion.
- Android Today/Train/Progress bottom navigation, MzFit navy/green styling, real calendar-based weekly goals, 28-day activity and recorded lift/load-volume trends.

- Today dashboard with a suggested workout and activity summary.
- Hamburger navigation with Today, Workouts by body part, ML pose detection, and Profile & progress.
- Workout library with full-body, chest, back, legs, shoulders, arms and core filters.
- Interactive front/back muscle map with 12 selectable groups, accessible selection chips, primary-target recommendations and separate supporting-muscle matches.
- 22 exercise guides and eight original animated main-lift demonstrations, with play/pause, half speed, reset and a position slider. Animations start paused and run without network access.
- Active-session checklist, replacement confirmation, and completion logging.
- Profile progress graphs and completed-workout history with expandable exercise snapshots.
- Android live camera pose detection with experimental squat, push-up and biceps-curl feedback, rep counting, color-coded landmarks and persistent pose-session logs. An iOS native camera implementation and Xcode host are included; Apple-platform compilation and device verification remain outstanding.
- Editable name, training goal and weekly session target.
- Android Training tools: rest timer, default rest duration, kilograms/pounds preference, optional screen-awake mode, actual set/rep/load journals and notes for completed workouts, and JSON history export through Android's file picker.
- Shared responsive UI and accessible hamburger drawer.
- Nine sample workout plans. Android and iOS have local storage adapters; desktop and web use an in-memory repository. No account or cloud setup required.

Android profile, routines, active training-studio drafts and completed-workout history survive restarts in an app-private SQLite database. The iOS host connects the original repository to a native SQLite document store; see [iOS build instructions and verification limits](iosApp/README.md). The new training studio is Android-specific; other platforms retain the earlier checklist workflow. Desktop and web data last only for the current process. Lift graphs use recorded session order; Android weekly reporting uses completion timestamps and local calendar weeks. Starter workouts are demonstration content for product development.

## Local workout database

Android uses `training.db`, schema version 4, through `WorkoutDatabase`, `AndroidTrainingStorage` and `StudioDatabase`. Completed workouts store the plan ID, title, completion timestamp, body part, planned duration and an ordered exercise snapshot. Prescribed targets are preserved separately from actual reps/loads/seconds. History remains accurate if a routine changes later. The profile is stored in the same database.

- `completed_workouts`: one row per completed session, with database-generated IDs.
- `completed_exercises`: ordered exercise snapshots linked to a workout by a foreign key.
- `profile`: locally saved profile and weekly target.
- `app_metadata`: the one-time legacy-import marker.
- `workout_journal` and `workout_sets`: session notes and manually entered actual sets, linked to completed workout snapshots. Schema 1/2 upgrades preserve existing workout/profile/pose records.
- `saved_routines`: versioned routine documents with prescribed exercise order/targets and weekday assignments.
- `active_workout`: a single versioned live-workout draft with raw inputs, selected units, completion flags and notes.
- `workout_details`: elapsed wall-clock span, planned/completed set counts, and a unique draft-to-workout marker preventing duplicate completion.
- `timed_sets`: actual hold/interval seconds, kept separate from repetition-based sets.

Workout and exercise rows commit in one transaction. Database operations run on the IO dispatcher; the repository only publishes completion after commit. A failed save keeps the active checklist available for retry. Loading failures block edits until history loads successfully, and simultaneous completion requests cannot log the same active session twice. The application owns one repository instance across activity recreation.

On first database load, the previous `training` preference's JSON is imported transactionally with its original session IDs, titles and exercise counts. The import marker is committed with the rows, preventing duplicate imports. Old records retain unknown completion times and missing exercise details; the app labels them as imported history. Malformed legacy data is preserved and produces a retryable loading error. The original preference is retained as a migration backup and is no longer updated or used after the import marker is present.

**Profile & progress → Completed workouts** shows saved history, completion dates and expandable exercise details. It initially shows ten recent records; **Show more workouts** reveals the rest. Progress graphs use the same loaded database records. Clearing app storage or uninstalling removes this local database. Android offers JSON export; cloud backup and restore/import are not implemented. Future schema versions must provide explicit migrations; there is no destructive upgrade fallback.

## Android training tools

### Training studio (0.7.0)

**Today** shows the current user's next scheduled routine or a starter plan, a Resume action when a draft exists, the real weekly goal, 28 days of activity and recorded lift highlights. **Train** supports starter searches and custom routine creation. Build routines from the guided exercise library or add a custom name, reorder exercises, set rep or second targets, choose sets and optionally assign weekdays. The editor confirms discarding unsaved edits and keeps Save visible. Deleting or editing a routine preserves completed snapshots and any already-started draft.

During a workout, enter actual reps and an optional total external load, or actual seconds for timed targets. Check **Log set** to record the set and automatically start rest. An unchecked/invalid set is never counted as completed. Previously recorded values can be copied explicitly into unchecked compatible sets; doing so does not mark them complete or prescribe a load increase. Completed values are locked until unchecked. Add/remove sets and enter notes as needed. Rest controls stay above the primary bottom navigation while a workout is active.

The app-owned `TrainingStudio` publishes edits immediately and coalesces SQLite checkpoints off the main thread. A saved-draft indicator shows when a checkpoint is durable; write errors preserve the current input in memory and offer retry. After abrupt process death, the most recently committed draft is restored. Finishing a session saves only completed sets and exercises, notes, timing and the draft's idempotency marker, then removes the active draft in the same transaction. A SQL failure rolls back the whole completion and keeps the draft for retry. Finish confirmation explicitly reports partial completion. Duration is the wall-clock span since starting, including breaks and time away from the app.

**Progress** adds Monday–Sunday weekly totals in the phone's current timezone, a 28-day activity grid, heaviest recorded external loads and load trends. Future-dated sessions and undated legacy imports do not inflate the calendar. Load-volume totals use recorded external load × actual reps; they do not infer body mass, machine ratios, timed-hold volume or unrecorded weights. Exercise records match normalized exact names, preserving distinct exercise variations. No estimated one-rep max or adaptive programming model is claimed.

The product priorities and competitor evidence are recorded in [the competitive brief](docs/COMPETITIVE_BRIEF.md). This release does not add accounts, cloud synchronization, wearable integration, payments or automated trainer programming.

Open the drawer → **Training tools**. The rest timer is also on **Workouts**. Start, pause/resume or reset a rest; the default is 90 seconds and can be changed in 15-second increments up to 10 minutes. It measures elapsed time rather than counting coroutine ticks, so sleep and navigation do not extend the rest. Its deadline and paused duration survive process recreation; a deadline is discarded across a device reboot. This version displays completion in the app and does not deliver background alerts, sound or notifications.

For a completed workout, choose **View / edit sets & notes** to review or correct its journal. Rep-based sets accept integer reps (1–999) and an optional non-negative load; timed sets store actual seconds (1–86400). Kilograms are stored canonically; the pounds preference converts display/input. Blank load means bodyweight or unrecorded load, while zero is an explicit zero load. The original prescribed targets are preserved separately. Imported sessions without exercise snapshots support notes only. The editor validates entries, confirms discarding edits, and retains entries on save failure. Notes and replacement rep/timed rows commit together; retries do not duplicate sets. These manual records do not alter ML pose measurements.

**Export all history** opens Android's system file picker and writes readable JSON containing the profile, completed workouts, prescribed exercises, journal rep/timed sets/notes, routines, active studio draft, completion details, and all persisted pose-session/rep/observation rows. Export uses one consistent database transaction, includes observations beyond the detail screen's 100-row limit, and writes off the main thread. Save into a private destination of your choice. Export does not implement restore/import. No broad storage permission is requested. The app waits for pending pose/studio writes before enabling export; the output excludes rest-timer/preferences state.

Preferences include kilograms/pounds and an optional keep-screen-awake setting, applied while the app's window is visible. These Android additions do not extend the iOS storage format or desktop/web repositories.

## Android pose detection

Open the hamburger menu → **ML · Pose detection**. Choose **Bodyweight squat**, **Push-up** or **Biceps curl**, read its camera-position instructions, confirm a steady side-on view, then tap **Start exercise & log** and allow camera access. Stop with **Stop & save log**. Switching cameras resets tracking; leaving the tab or backgrounding the app closes the session as interrupted and releases the camera.

Uses `com.google.mlkit:pose-detection:18.0.0-beta5` in stream mode and CameraX 1.4.2. A shared camera viewport and CameraX coordinate transforms align the skeleton with the preview's crop, rotation and mirror. Landmarks with at least 0.5 in-frame confidence are drawn; coaching requires every exercise-specific joint to have at least 0.75 confidence and remain inside the frame. Camera images and full landmark-coordinate sequences are not stored or uploaded. Desktop and web show a mobile availability message; iOS supplies its own AVFoundation/ML Kit screen through the shared app's platform callback.

### Experimental movement feedback

The common `PoseCoach` applies original, transparent 2D angle heuristics to ML Kit landmarks. It is not a trained exercise-recognition model, a validated accuracy score or a safety assessment. Users select the exercise; the app does not independently confirm which exercise is being performed. A green overlay means the current visible checks matched; amber means an adjustment was flagged; gray means visibility or camera setup is insufficient. A prior attempt's feedback stays visible separately.

| Exercise | Tracked signals | Prototype movement thresholds |
| --- | --- | --- |
| Bodyweight squat | Hip–knee–ankle angle, projected trunk lean | Start ≥160°, target ≤100°; trunk lean >55° flagged |
| Standard push-up | Shoulder–elbow–wrist angle, shoulder–hip–ankle alignment | Start ≥155°, target ≤95°; body angle <160° flagged |
| Biceps curl | Shoulder–elbow–wrist angle, upper-arm drift, trunk sway | Start ≥155°, target ≤60°; drift >0.35 torso lengths or sway >12° flagged |

These thresholds are product heuristics, not universal technique standards or thresholds validated by Google/ACE. Technique, mobility, body proportions, camera perspective and exercise variations can change the observed angles. The feature cannot assess load selection, grip, pain, three-dimensional spinal alignment, all joint positions or overall exercise safety. No percentage “correctness” score is reported.

Rep counting requires a stable start, movement toward the target and return to a stable start. Start/target bands need at least three analyzed samples spanning 200 ms. A one-frame angle outlier cannot qualify depth. Incomplete range is logged as a partial attempt; full-range reps with alignment or tempo flags are separated from those matching all tracked checks. Attempts shorter than 800 ms receive a tempo cue. Missing joints, camera changes, stale inference results or gaps longer than one second reset the cycle, so unseen movement is not counted. Camera-side tracking stays locked through an observed cycle. Processing is limited to roughly 12 samples/second.

The code supports angle-based pose interpretation described in [Google's pose-classification guidance](https://developers.google.com/ml-kit/vision/pose-detection/classifying-poses). General movement cues were checked against [ACE's bodyweight squat guide](https://www.acefitness.org/resources/everyone/exercise-library/135/bodyweight-squat/), [push-up guide](https://www.acefitness.org/resources/everyone/exercise-library/41/push-up/) and [biceps exercise library](https://www.acefitness.org/resources/everyone/exercise-library/body-part/arms/biceps/). No claim of real-world classification accuracy is made; camera and human-movement acceptance testing remains required.

### Pose-session logs

Schema 2 adds `pose_sessions`, `pose_reps` and `pose_observations` with a non-destructive migration from schema 1. Pose logs are separate from completed workout records and do not inflate workout progress graphs. The ML screen lists saved sessions with expandable per-attempt measurements and feedback. Older history can be loaded; detail views show up to 100 recent observations.

Logs contain the selected exercise, start/end times, status, rule-set version, usable/analyzed sample counts, full-range rep counts, matched-check counts, partial attempts, per-attempt angle ranges/duration/cues, and sampled confidence/angle/phase observations. Observations are checkpointed about once per second and at significant phase/feedback changes. A transaction updates the summary and appends new numbered rep/event rows; retries are idempotent. The application-owned recorder finishes pending writes after navigation. Save errors are visible, preserve the in-memory log for retry, and block starting a replacement session until the pending log is saved.

After process death, previously active persisted sessions are marked interrupted at their last logged sample. There may be up to roughly a checkpoint interval of uncommitted observations after abrupt process termination. Pose guidance is paused when inference stops producing fresh results. This feature has synthetic-landmark regression tests and real-device database tests; it has not been validated against a representative set of human exercise recordings.

Implementation references: [Google ML Kit pose detection](https://developers.google.com/ml-kit/vision/pose-detection/android), [CameraX coordinate transforms](https://developer.android.com/reference/androidx/camera/view/transform/CoordinateTransform).

## Muscle map and movement guides

Open **Muscle map & lifts** from the hamburger menu or the Workouts screen. Tap a region on the front/back map, or choose a muscle chip. Primary-target exercises appear first, followed by exercises where that muscle assists. Each group has at least two primary-target options. Open a card for equipment, target muscles, movement cues and, for the eight main lifts, an animation. **Browse workouts** opens the matching body-part filter.

Animated lifts: barbell back squat, barbell deadlift, barbell bench press, overhead press, bent-over barbell row, pull-up, split squat and Romanian deadlift. These are original simplified 2D illustrations, not videos or individualized technique assessments. Accessory exercises provide written guides. The catalog does not rank a universally best exercise, estimate muscle activation percentages or select an exercise based on injuries or personal ability.

The muscle map uses the same polygon geometry for drawing and hit testing. Movement illustrations interpolate between original poses. They run only after the user presses Play and stop when the guide is closed. All platform hosts use the shared implementation.

Targeting and movement descriptions are curated with reference to the [NASM exercise library](https://www.nasm.org/workout-exercise-guidance), [ACE barbell exercise library](https://www.acefitness.org/resources/everyone/exercise-library/equipment/barbell/), [NASM side plank guide](https://www.nasm.org/resource-center/exercise-library/side-plank), [ACE lateral raise guide](https://www.acefitness.org/resources/everyone/exercise-library/26/lateral-raise/), [NASM core exercise guide](https://www.nasm.org/resource-center/blog/training/best-abs-exercises) and [NASM face pull guide](https://www.nasm.org/resource-center/exercise-library/face-pull). The primary/supporting split is this app's curated grouping, rather than an activation ranking supplied by these sources. No third-party exercise illustrations or videos are bundled.

## Platforms

| Platform | Scaffold |
| --- | --- |
| Android | Activity, manifest and application build configuration (API 26+) |
| Desktop | JVM entry point, resizable window and native packaging configuration |
| Web | Compose/Wasm entry point and HTML host; requires a WasmGC-capable browser |
| iOS | SwiftUI/Xcode host, shared Compose UI, AVFoundation/ML Kit camera and SQLite storage; macOS build, signing and device verification required |

## Run

Use JDK 21 and Android SDK 35. The versions are intentionally pinned to the reference project's Kotlin 2.1.21 / Compose 1.8.0 / AGP 8.8.2 / Gradle 8.11.1 baseline. First builds need internet access for any uncached dependencies.

Set `ANDROID_HOME` to your SDK directory or add an untracked `local.properties` file:

```properties
sdk.dir=C:/Users/YOUR_USER/AppData/Local/Android/Sdk
```

From the repository root on Windows:

```powershell
# Launch desktop
.\gradlew.bat :composeApp:run

# Build Android debug APK
.\gradlew.bat :composeApp:assembleDebug

# Run web development server
.\gradlew.bat :composeApp:wasmJsBrowserDevelopmentRun

# Verify repository behavior and desktop compilation
.\gradlew.bat :data:desktopTest :composeApp:compileKotlinDesktop

# Verify muscle-map targeting, recommendations, animations and offscreen UI interaction
.\gradlew.bat :shared:desktopTest :feature:training:desktopTest

# Build Android database tests (use isolated test databases on a connected device)
.\gradlew.bat :composeApp:assembleDebugAndroidTest
.\gradlew.bat :composeApp:connectedDebugAndroidTest
```

On macOS/Linux, use `./gradlew` (set its executable bit if necessary). On macOS, iOS targets are enabled automatically. The Xcode host lives in `iosApp/MLPT.xcodeproj`; run `pod install` in `iosApp`, open the generated `MLPT.xcworkspace`, choose your signing team and run the MLPT scheme. See [the iOS guide](iosApp/README.md) for prerequisites, simulator build commands and device checks. No Mac was available in this Windows session, so the iOS sources have not been compiled or installed.

If dependency downloads report a Java `PKIX path building failed` error on Windows, this environment successfully used the Windows trusted certificate store without modifying system certificates:

```powershell
.\gradlew.bat '-Djavax.net.ssl.trustStoreType=Windows-ROOT' '-Djavax.net.ssl.trustStore=NONE' :composeApp:wasmJsBrowserDevelopmentRun
```

## Scaffold verification

Training studio 0.7.0, November 17, 2026: 47 shared/JVM tests and 23 Android instrumentation tests passed (70 total). New checks cover checkpoint recovery/retries, invalid-set rejection, previous-value conversion, finish/write ordering, transactional finish rollback, idempotency, no draft resurrection, schema-3-to-4 preservation, timed-set journal corrections, exact volume/record calculations, local Monday boundaries and DST. Android assembly/lint and desktop/Wasm compilation passed. A pre-update private backup was saved under `composeApp/build/backups/`. Version 0.7.0 was installed and cold-launched on the SM-S938U; the updated dashboard displayed the existing Zak profile and one completed workout toward the weekly goal. No fabricated workouts or routines were written to the user's production database during testing. Real training-session and user acceptance testing remains necessary.

MzFit branding update, November 17, 2026: Android 0.6.1 built and installed on the connected SM-S938U. Android lint passed with 0 errors and 38 warnings (including optional monochrome-icon and legacy-square-icon notices). Desktop/Wasm compilation passed for the shared header rename. APK metadata confirms the MzFit label and adaptive launcher resources. Cold-start screenshots were inspected; the splash image scaling was corrected to keep the full logo within Android's circular mask. No database schema or saved workout records were changed by this branding update.

Android 0.6.0 update, November 17, 2026: Android debug/test builds and lint passed (0 errors, 30 warnings); desktop/Wasm compilation and 37 shared/JVM tests passed. Fifteen instrumentation tests passed on the connected SM-S938U, including five journal/migration/export tests and two timer tests in isolated databases/preferences. They cover reopen, replacement/idempotency, SQL rollback, schema-2-to-3 preservation, complete exports, timer pause/recreation and elapsed completion. Version 0.6.0 installed and launched successfully, and the Training tools screen rendered on the phone. A private pre-update database/preferences archive is retained under the ignored `composeApp/build/backups/` directory. No fabricated workouts or journal entries were added to the user's real database. Full journal-entry and file-picker interaction still needs user acceptance.

iOS-source addition checks: 37 shared/JVM tests passed, including five new durable-record tests for Unicode/null round-trips, failed saves, pose checkpoint idempotency, interruption recovery and corrupt/unsupported records. Android debug assembly and desktop/Wasm compilation passed after the shared changes. iOS plist/scheme XML, project object references and source-file paths were checked on Windows. These checks do not compile Swift/Kotlin Native or validate the iPhone camera/SQLite adapter; see the iOS guide above.

Verified locally on November 16, 2026:

- `:data:desktopTest`: 13 tests passed, including workout commit/retry behavior and pose-log retries, interruption handling and stale inference results.
- `:shared:desktopTest`: 13 tests passed for exercise recommendations and pose rules, including clean/flagged reps, partial range, confidence, camera-view gating, outliers and duplicate frames.
- `:feature:training:desktopTest`: 6 tests passed for front/back map taps, animation geometry, actual offscreen tap interaction, play/pause behavior and visual rendering. Previews are written to `feature/training/build/reports/visuals/`.
- `:composeApp:compileKotlinDesktop`: passed.
- `:composeApp:assembleDebug`: passed; APK at `composeApp/build/outputs/apk/debug/composeApp-debug.apk`.
- `:composeApp:compileKotlinWasmJs`: passed using the Windows certificate-store options above.
- `:composeApp:lintDebug`: passed with 0 errors and 30 warnings.
- Eight Android instrumentation tests passed on the connected Samsung SM-S938U, using unique test databases. They cover durable workout/pose reopen, idempotent pose checkpoints, interrupted-session recovery, both workout/pose transaction rollback, legacy import and schema-1-to-2 migration preserving workout/profile records.
- Version 0.5.0 installed and launched successfully. The existing one-workout history was previously backed up, imported, and verified in the installed app's Completed workouts list. Temporary instrumentation helpers were removed after testing.

These are automated build, offscreen UI and launch checks. The rendered map and start/end poses for all eight animated lifts were visually reviewed. Remote phone touch checks were previously blocked by a screen-sharing overlay; live camera detection and the full phone navigation walkthrough still need device acceptance. Full desktop/browser interaction, release installers and iOS builds have not been verified.

## Architecture

```text
composeApp ── navigation
     ├────── feature/training ── shared
     └────── di ── data ──────── shared
```

| Module | Responsibility |
| --- | --- |
| `composeApp` | Platform launchers, drawer shell, state observation, Android camera/ML and local storage adapters |
| `shared` | Domain models, repository contract, theme tokens |
| `data` | Repositories, pose-session recorder, storage contracts, sample data and behavior tests |
| `di` | Explicit composition root; inject a replacement repository here |
| `navigation` | Top-level destination definitions |
| `feature/training` | Dashboard, workouts, progress, profile, muscle map, exercise guides and lift animations |

Dependencies point toward the shared contract. The UI observes `StateFlow`; repository mutations update the same state across screens. The reference uses Koin, but this small scaffold uses constructor injection through `AppContainer` to keep initialization simple. Feature screens can be split into separate modules as they grow.

## Next implementation steps

1. Gather feedback from complete training sessions; extend the Android studio workflow to other platforms.
2. Add authentication and separate trainer/client accounts with server-enforced authorization.
3. Implement trainer plan assignment, exercise editing, scheduling and messaging.
4. Add cloud synchronization behind `TrainingRepository`.
5. Validate the iOS host on macOS/iPhone; add platform UI tests, signing, CI and release configuration.

Authentication, payments, messaging, cloud services and real trainer access are not implemented. No MZBotanicals credentials, Firebase configuration, signing keys or business data are included.
