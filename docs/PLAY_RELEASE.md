# MzFit Google Play release

Prepared November 23, 2026. This is a submission checklist, not a declaration of store approval.

## Implemented

- Android compile/target SDK 36, version 0.8.0 (code 10).
- AGP 8.13.2 / Gradle 8.14.3; release build is non-debuggable.
- Explicit cloud-backup and device-transfer exclusions for app-private records.
- Local upload signing via ignored `signing.properties`. Release tasks fail when signing configuration is absent.
- Privacy & safety screen in the navigation menu, readable offline.
- Camera/SDK disclosures before starting pose coaching and general-fitness limitations.
- Publishable policy source: `docs/PRIVACY.md` and ready-to-host `docs/privacy.html` (hosting still required).
- Native ELF compatibility checker: `python scripts/check_native_alignment.py <bundle.aab>`.

Validation evidence: [PLAY_VALIDATION.md](PLAY_VALIDATION.md).

## Signing and build

The initial upload key is in `release-keys/mzfit-upload.p12`; its passwords are in ignored `signing.properties`. Neither belongs in source control. Back up both securely using a password manager/encrypted backup before the first upload. Do not print passwords into logs. Enroll in Play App Signing and use this key as the upload key. If signing is managed elsewhere, replace the local configuration before the first release.

Build: `./gradlew :composeApp:bundleRelease`.
Bundle: `composeApp/build/outputs/bundle/release/composeApp-release.aab`.
This production-signed app will not update an existing debug-signed installation. Keep the debug app on the development phone until data migration/restore is addressed; uninstalling it would remove local history. App exports exist but automatic restore is not implemented.

## Owner setup still required

- Create/verify a Play Console developer account and confirm developer identity/contact information.
- Confirm permanent package name `com.mlpt.app` before first upload.
- Choose a public support email and a private contact route for sensitive privacy requests. Current in-app link uses public GitHub issues and warns against sharing sensitive information.
- Publish `docs/privacy.html` at a stable public HTTPS page (not a PDF or login-gated URL), and put it in Play Console.
- Back up signing materials securely; do not upload them to the repository.
- Confirm distribution countries, age audience, price, and content rating. No purchases/ads are currently implemented.
- Complete Health apps, Data safety, ads, audience, content rating and app-access declarations in Play Console.
- For personal accounts subject to the rule: run closed testing with at least 12 testers continuously opted in for 14 days, then apply for production access.

## Data safety worksheet — review against the actual release SDKs

- Profile, routines, sets, notes and pose measurements: app-private device storage; no developer account/backend upload.
- Camera input: on-device pose processing; no stored/uploaded image/video in MzFit.
- User-directed export: JSON written to the destination chosen through Android's document picker. User controls later sharing/deletion.
- Google ML Kit: includes device/app information, installation identifiers, diagnostics, performance and feature usage. Pose SDK also uses Firebase installation/remote-configuration services. Do NOT declare that the app collects no data simply because workout records stay local.
- Review SDK data types/purposes, whether collection is optional, retention, and deletion handling in the Console using the linked vendor disclosures. Local Android storage removal does not erase Google's independently processed diagnostics.
- No account creation exists; account-deletion flows do not apply to the current accountless app.
- Camera permission only is explicitly requested by MzFit; audit the final merged manifest for SDK-added permissions.

## Store listing draft

Name: MzFit

Short description: Plan workouts, log your lifts, and follow your strength over time.

Full description:
MzFit is your personal training space. Build routines, schedule training days and record reps, timed sets and external loads. Keep a workout draft, use a rest timer and review your per-exercise load trends and training history.
Explore muscle groups and animated exercise illustrations. Optional experimental camera coaching uses on-device pose detection for selected movements and saves derived session measurements locally. It cannot guarantee safe or correct technique and is not a medical device.
Your workout history is saved on your phone. Export a JSON copy through Training tools. No MzFit account is required. Google ML Kit can collect SDK diagnostic and usage data; see the privacy policy for details.

Assets: existing 512px icon at `branding/mzfit-store-icon-512.png`. Capture real current-app phone screenshots and prepare a 1024 × 500 feature graphic. Do not use unverified health or accuracy claims.

## Release acceptance gates

- Shared/data/UI regression tests, release lint and signed bundle build pass.
- Inspect release manifest (SDK, permissions, exported components and debug flag), verify bundle signature and native 16 KB LOAD alignment.
- Test generated APK ZIP alignment and run on a 16 KB Android environment; static ELF checks alone are insufficient.
- Internal-test installation from Play, cold launch and complete workout through history/strength graph.
- Draft recovery after process death, interrupted saves, persistence after update, export file correctness, camera deny/revoke, backgrounding and offline operation.
- Large fonts, TalkBack, small/large screens, rotation and Android 16 system-bar/back behavior.
- Representative human pose recordings and real workouts; synthetic tests do not establish coaching accuracy.
- Third-party license notices and SDK terms reviewed before production.
- Inspect Play pre-launch report and resolve crashes, accessibility and policy findings.

## Rollout and rollback

Start with internal testing, then required closed testing. Do not release to production while a gate is unresolved. For production, use a staged rollout where available. Halt a rollout for crashes, lost/duplicated workouts, broken camera lifecycle or misleading guidance. Ship a corrective build with a higher version code; do not rely on downgrading an installed database. No database schema changes are introduced by this preparation.

## Sources

- https://developer.android.com/google/play/requirements/target-sdk
- https://developer.android.com/guide/practices/page-sizes
- https://developers.google.com/ml-kit/android-data-disclosure
- https://support.google.com/googleplay/android-developer/answer/10144311
- https://support.google.com/googleplay/android-developer/answer/14738291
- https://support.google.com/googleplay/android-developer/answer/14151465
