# Experimental pose coaching

## Use the Android camera screen

Open **ML · Pose detection**, select an exercise, read the camera-position instructions, and confirm a steady side-on view. **Start exercise & log** requests camera permission and begins recording measurements. **Stop & save log** ends the session. Leaving the screen or backgrounding the app interrupts the session and releases the camera. Switching cameras resets tracking.

The native Android implementation combines CameraX **1.4.2** with ML Kit pose detection **18.0.0-beta5** in stream mode. A shared viewport and coordinate transforms align the skeleton with preview rotation, crop, and mirroring. The iOS host has a separate AVFoundation/ML Kit implementation, with Apple-platform compilation and device validation pending.

## Tracked checks

The shared `PoseCoach` applies original 2D angle rules to landmarks. It is not a trained exercise-recognition model, validated form score, injury-prevention system, or assessment of overall exercise safety. The user selects the exercise; the app does not independently identify the movement.

| Exercise | Signals | Prototype thresholds |
| --- | --- | --- |
| Bodyweight squat | Hip–knee–ankle angle; projected trunk lean | Start ≥160°; target ≤100°; lean >55° flagged |
| Standard push-up | Shoulder–elbow–wrist angle; shoulder–hip–ankle alignment | Start ≥155°; target ≤95°; body angle <160° flagged |
| Biceps curl | Shoulder–elbow–wrist angle; upper-arm drift; trunk sway | Start ≥155°; target ≤60°; drift >0.35 torso lengths or sway >12° flagged |

Thresholds are product heuristics, not universal standards or thresholds validated by Google or ACE. Body proportions, mobility, technique variations, camera perspective and visibility affect the measurements. The app cannot assess pain, load selection, grip, three-dimensional spinal alignment or every relevant joint position.

Green means current visible checks matched; amber indicates a flagged adjustment; gray indicates insufficient visibility/setup. A previous attempt's feedback is shown separately. No percentage correctness score is reported.

## Tracking and rep counting

- Skeleton rendering requires landmark confidence of at least 0.5. Coaching requires all exercise-specific joints at confidence ≥0.75 and inside the image.
- Start and target positions need at least three analyzed samples spanning 200 ms. A one-frame outlier cannot qualify range.
- A rep requires an observed start, movement toward the target, and return to a stable start. Partial range is logged separately.
- Attempts shorter than 800 ms receive a tempo cue.
- Missing joints, camera changes, stale results or gaps over one second reset the cycle. Unseen movement is not counted.
- Coaching analysis is limited to roughly 12 samples per second; stale inference cannot leave old guidance appearing current.

## Saved measurements

Logs include exercise selection, start/end times, status, rule-set version, analyzed/usable sample counts, completed/partial attempts, matched-check counts, angle ranges, durations, feedback, and sampled confidence/phase observations. Observations are checkpointed about once per second and at significant changes.

Camera images and full landmark-coordinate sequences are not stored or uploaded. Save errors preserve the current in-memory log for retry. Process-death recovery marks persisted active sessions interrupted at their last logged sample. See [storage and recovery](STORAGE.md) for transaction and export behavior.

## Validation and references

Synthetic-landmark regression tests cover stable positions, confidence gaps, camera-view rejection, outliers, duplicate frames and flagged/clean attempts. Device tests cover persistence, recovery and rollback. These checks do not establish accuracy across representative human exercise recordings; real-world camera and movement acceptance testing remains necessary.

- [Google ML Kit pose interpretation guidance](https://developers.google.com/ml-kit/vision/pose-detection/classifying-poses)
- [Google ML Kit on Android](https://developers.google.com/ml-kit/vision/pose-detection/android)
- [CameraX coordinate transforms](https://developer.android.com/reference/androidx/camera/view/transform/CoordinateTransform)
- [ACE bodyweight squat guide](https://www.acefitness.org/resources/everyone/exercise-library/135/bodyweight-squat/)
- [ACE push-up guide](https://www.acefitness.org/resources/everyone/exercise-library/41/push-up/)
- [ACE biceps exercise library](https://www.acefitness.org/resources/everyone/exercise-library/body-part/arms/biceps/)

General movement cues were informed by these references. They do not constitute endorsement or validation of MzFit's thresholds or coaching accuracy.
