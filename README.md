# WYRIX Browser (Android)

A privacy-focused Android browser built on **GeckoView** (Mozilla's Gecko engine) with a **Jetpack Compose** interface.
No accounts, analytics, ads or crash reporting. Browsing data stays on the device and is excluded from Android backups.

> **Read this first — verification status.** This project was authored in an environment with **no network access and no
> Kotlin/Gradle toolchain**. It has **never been compiled or run**. All code was written against GeckoView, AndroidX and
> Compose APIs from memory, and dependency versions were not resolved against live repositories. Expect a first build
> to need small fixes (see `docs/VERIFICATION.md` for the likely trouble spots). The GitHub Actions workflow is the first
> real compile, test and lint run. Nothing in these docs claims a test passed.

## Build

Requirements: JDK 17, Android SDK (platform 35, build-tools 35.0.0), Gradle ≥ 8.9 (wrapper JAR not included — see below), network access.

```bash
bash tools/resolve-geckoview.sh          # resolves latest GeckoView release from maven.mozilla.org into gradle.properties
gradle :app:testDebugUnitTest            # JVM unit tests
gradle :app:assembleDebug                # debug APK -> app/build/outputs/apk/debug/
gradle :app:connectedDebugAndroidTest    # instrumented tests (device/emulator)
```

The Gradle **wrapper JAR is not included** (it can't be generated or checksum-verified offline). Run `tools/bootstrap-wrapper.sh`
once with a local Gradle, verify the JAR checksum against gradle.org, and commit it. Until then CI uses `gradle/actions/setup-gradle`.

If the newest GeckoView changes an API this code uses, pin an earlier release in `gradle.properties`
(`wyrix.geckoview.version=`) — see `docs/DEPENDENCIES.md`.

## Layout

| Path | Contents |
|---|---|
| `core/` | Pure Kotlin logic: URL parsing/policy, filename sanitising, bookmark HTML, rate limiting (unit-tested) |
| `tabs/` | Immutable tab-list reducer (unit-tested) |
| `data/` | Room database, DataStore settings, repositories, download coordinator |
| `engine/` | GeckoView runtime, per-tab session + delegates, session manager (suspension/popups) |
| `ui/` | Compose screens: browser chrome, home, tab switcher, bookmarks, history, downloads, settings, onboarding |
| `BrowserViewModel.kt` | Reduces engine events into UI state; all user actions |
| `tools/` | GeckoView resolver, wrapper bootstrap, source statistics |
| `docs/` | Architecture, feature matrix, permissions, security, verification, release |

See `docs/FEATURE_MATRIX.md` for exactly what is and isn't implemented.
