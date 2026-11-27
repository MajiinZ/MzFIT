# Storage and recovery

## Android database

Android stores application data in app-private `training.db`, currently at **schema version 4**. `WorkoutDatabase` uses SQLite with write-ahead logging and foreign-key enforcement. Database work runs off the main thread. Upgrades from schemas 1–3 preserve existing records; unsupported versions fail rather than falling back to destructive recreation.

| Tables | Data |
| --- | --- |
| `profile` | Name, goal, and weekly target |
| `completed_workouts`, `completed_exercises` | Completed sessions and ordered snapshots of prescribed exercises |
| `workout_journal`, `workout_sets` | Session notes, actual repetition counts, and optional external loads in kilograms |
| `timed_sets` | Actual seconds for timed sets |
| `saved_routines` | Versioned routine documents and optional weekday schedules |
| `active_workout` | The single active workout draft, including raw inputs and completion flags |
| `workout_details` | Duration, set counts, and a unique draft-to-workout completion marker |
| `pose_sessions`, `pose_reps`, `pose_observations` | Separate pose-session summaries, attempts, and sampled measurements |
| `app_metadata` | One-time legacy import marker |

## Live workout lifecycle

1. Starting a workout copies its plan into a draft. Later routine edits do not change that snapshot.
2. Set input publishes immediately in memory. An application-owned writer coalesces checkpoints into SQLite; the UI distinguishes saved state from pending/failed writes.
3. After process death, reopening restores the most recently committed draft. Very recent uncommitted input can be lost after abrupt termination.
4. Finishing writes the completed workout, exercise snapshots, actual rep/timed rows, notes, duration, and completion marker, then removes the draft in one transaction.
5. SQL failure rolls back the whole completion, retaining the draft for retry. A committed draft ID cannot create a duplicate workout or be revived by a late checkpoint.

Only checked sets and exercises containing completed sets are included in completion records. The finish dialog reports partial completion. Duration is the wall-clock span since starting, including breaks and time away from the app.

Prescribed sets/targets remain separate from actual measurements. Blank load means bodyweight or unrecorded load; zero is an explicit zero. Loads are stored in kilograms and converted for pounds input/display. Timed holds do not become repetition or external-volume records.

## History and progress

Completed snapshots survive routine edits/deletion. Journals allow actual rep/timed measurements and notes to be corrected together in a transaction. Legacy sessions without exercise snapshots support notes only.

Weekly totals use Monday–Sunday in the phone's current timezone. Future-dated and undated sessions are excluded from the calendar. Lift history matches normalized exact exercise names; distinct named variations remain separate. External-load volume sums recorded kilograms × actual reps, without estimating body mass or machine resistance ratios.

## Legacy import

On initial load, the earlier `training` SharedPreferences JSON is imported transactionally once. Session IDs, titles and counts are retained; unavailable timestamps and exercise details remain unknown. The import marker commits with the records, preventing duplicates. Malformed input is preserved and reported as a retryable error. The original preference remains a migration backup and is not updated after import.

## Pose logs

Pose records remain separate from completed workout history and do not inflate workout progress. Checkpoints update summaries and append numbered rep/event rows idempotently. Failed writes retain the in-memory log for retry and block a replacement session until resolved. A previously active persisted session is recovered as interrupted at its last logged sample.

Pose detail views show up to 100 recent observations; export includes every persisted observation. See [pose coaching](POSE_COACHING.md) for recorded signals and interpretation limits.

## Export and deletion

**Training tools → Export all history** uses Android's system file picker and requires no broad storage permission. One consistent database transaction exports profile, routines, active studio draft, completed snapshots, journals, completion details, and pose records to readable JSON.

Export waits for pending studio/pose writes. It excludes rest-timer/preferences state, camera images, and full landmark sequences. Import/restore and automatic cloud sync are not implemented. Uninstalling or clearing app storage removes local data; exports are user-managed copies and can contain identifying profile and training information.

The rest timer stores its elapsed-time deadline and paused duration separately in preferences. Navigation and sleep do not extend the countdown. An active deadline is discarded after a device reboot; completion is shown in the app without background notifications.

## Other platforms

Desktop/web repositories are in-memory. The iOS host uses a separate native SQLite document adapter for the earlier workout/profile/pose workflow, not Android schema 4 or the new studio. Its native build and device behavior remain unverified; see the [iOS guide](../iosApp/README.md).
