# Google Play preparation validation

November 23, 2026 · MzFit 0.8.0 (version code 10)

## Passed

- Android SDK 36 release bundle and APK built using AGP 8.13.2 / Gradle 8.14.3.
- Release lint: 0 errors, 37 warnings, 1 hint. Remaining findings concern dependency versions, API freshness, icons and style; no lint baseline or blanket suppression was added.
- 47 shared/data/Compose desktop regression tests passed during this preparation.
- 23 Android instrumentation tests passed directly on the connected Samsung SM-S938U. This run preceded the final backup-rule/resource and build-tool patch changes; these final changes were rebuilt and linted afterward.
- API 36 debug build cold-launched successfully on the phone; replacement install preserved existing workout history and active draft.
- Bundle JAR signature verified; release APK signature verified by Android apksigner.
- Both final AAB and release APK: 8 packaged 64-bit ELF libraries have 16 KB-compatible LOAD segment alignment.
- Release APK ZIP alignment: `zipalign -c -P 16 4` returned 0.
- Signing files confirmed excluded from Git. No passwords were written into build output.
- Final merged release manifest audited: target SDK 36, no debug flag, app backup disabled with explicit extraction rules. ML Kit dependencies add network, wake-lock, boot and foreground-service permissions; these are not runtime camera permissions and must be included in the SDK/privacy review.

## Not yet established

- Full 16 KB runtime compatibility: an existing emulator booted with PAGE_SIZE=16384 but stayed RUNNING_LOCKED. A disposable profile did not finish usable startup and was stopped. No successful MzFit run on 16 KB is claimed.
- The Gradle connected-test runner initially failed downloading a UTP component because Java did not trust the environment TLS certificate. The 23 device tests above used `adb shell am instrument` directly instead. Later release builds used the Windows certificate store with TLS verification enabled.
- Visual acceptance of the Privacy & safety screen on the phone was interrupted by disconnection. The screen compiles and its content is available offline; policy access is independent of workout-storage loading/errors.
- Play App Signing enrollment, Play-generated split APK installation, pre-launch report, store review and account-specific testing requirements remain pending.
- Live coaching accuracy, accessibility/font scaling and real complete-workout acceptance remain release gates.
- Support email, policy hosting, screenshots/feature graphic and third-party license review remain pending.

## Reproduction

`./gradlew :shared:desktopTest :data:desktopTest :feature:training:desktopTest`

`./gradlew :composeApp:lintRelease :composeApp:bundleRelease :composeApp:assembleRelease`

`python scripts/check_native_alignment.py composeApp/build/outputs/bundle/release/composeApp-release.aab`

See `docs/PLAY_RELEASE.md` for signing, owner setup and rollout gates.

## Bundle SHA-256

d611ac2fedc5946322a09c7b1df4c62f2801ccefc75d044e08679ec187fa8a5b
