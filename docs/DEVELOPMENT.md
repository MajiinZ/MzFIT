# Development guide

## Toolchain

MzFit pins its build dependencies for reproducibility. Check the Gradle files before upgrading components independently.

| Component | Version |
| --- | --- |
| JDK | 21 |
| Kotlin | 2.1.21 |
| Compose Multiplatform | 1.8.0 |
| Gradle wrapper | 8.14.3 |
| Android Gradle Plugin | 8.13.2 |
| Android compile / target SDK | 36 / 36 |
| Android minimum SDK | 26 |
| Coroutines | 1.10.1 |
| CameraX | 1.4.2 |
| Android ML Kit pose detector | 18.0.0-beta5 |

Versions describe the repository configuration, not compliance with future store submission requirements.

## Local configuration

Install JDK 21 and Android SDK Platform 36. Set `JAVA_HOME` to the JDK and configure the SDK through Android Studio, `ANDROID_HOME`, or an untracked root `local.properties` file:

```properties
# Windows example; replace with your SDK location.
sdk.dir=C\:/Users/YOUR_USER/AppData/Local/Android/Sdk
```

First builds require network access to download dependencies. The repository includes the Gradle wrapper; a separate Gradle installation is unnecessary.

## Build and run

Run these commands from the repository root. On Windows, replace `./gradlew` with `.\gradlew.bat`.

```sh
# Android debug APK
./gradlew :composeApp:assembleDebug

# Install on a connected Android device or running emulator
./gradlew :composeApp:installDebug

# Desktop application
./gradlew :composeApp:run

# Web development server
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
```

The debug APK is generated at `composeApp/build/outputs/apk/debug/composeApp-debug.apk`. Web requires a browser with WasmGC support. Desktop and web use in-memory repositories; they do not include Android's persistent training studio or live camera.

For iOS, follow the [native host guide](../iosApp/README.md). Apple Silicon device/simulator targets are enabled on macOS. The host source is included, but Xcode compilation, signing and iPhone execution have not been verified.

## Automated verification

```sh
# Shared rules, repositories, studio behavior and Compose UI checks
./gradlew :shared:desktopTest :data:desktopTest :feature:training:desktopTest

# Android static analysis
./gradlew :composeApp:lintDebug

# Android instrumentation tests: connect a device or start an emulator first
./gradlew :composeApp:connectedDebugAndroidTest

# Platform compilation
./gradlew :composeApp:compileKotlinDesktop :composeApp:compileKotlinWasmJs
```

The November 17, 2026 validation of Android 0.7.0 passed:

| Test suite | Tests |
| --- | ---: |
| Shared domain and pose rules | 13 |
| Data, repositories, studio and insights | 28 |
| Shared training UI and geometry | 6 |
| Android database, timer and calendar instrumentation | 23 |
| **Total** | **70** |

Coverage includes invalid-set rejection, raw draft recovery, failed-save retries, finish/checkpoint ordering, atomic rollback, idempotent completion, schema migrations, timed-set corrections, unit conversion, calendar/DST boundaries and recorded-load calculations. Instrumentation tests use isolated databases and preferences. Android debug assembly/lint and desktop/Wasm compilation also passed. No continuous-integration workflow is configured; these counts report a completed local/device validation, not a live CI status.

Offscreen UI previews are generated under `feature/training/build/reports/visuals/`. The checked-in README previews come from those Compose renders and contain no personal workout data.

Automated tests do not establish live pose-coaching accuracy. Real exercise recordings, accessibility/font scaling, full end-to-end training sessions, file-picker interactions and release distribution still require acceptance testing.

## Windows certificate troubleshooting

If Java dependency downloads fail with `PKIX path building failed`, the Windows trusted certificate store can be used for an individual invocation:

```powershell
.\gradlew.bat '-Djavax.net.ssl.trustStoreType=Windows-ROOT' '-Djavax.net.ssl.trustStore=NONE' :composeApp:assembleDebug
```

This does not disable certificate validation or modify system certificates.

## Repository conventions

- Keep domain and state-management logic in shared modules; isolate camera, file-picker and database adapters at platform boundaries.
- Preserve committed history through explicit migrations. Do not add a destructive database fallback.
- Add focused regression coverage when changing persistence, calculations or session lifecycle behavior.
- Keep credentials, signing keys, `local.properties`, database files, exports and build artifacts out of commits.
- Update platform status when adding a feature that is not available on every target.
