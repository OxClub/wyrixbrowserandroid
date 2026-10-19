# Dependencies and how they were chosen

**None of the versions below were resolved against a live repository when this project was written.** They are best-effort
knowledge from training data; the first CI run is the real check.

| Dependency | Version (catalog) | Notes |
|---|---|---|
| Android Gradle Plugin | 8.7.3 | Pairs with Gradle 8.9; compileSdk/targetSdk **35** (spec asked for 36, which needs a newer AGP — raise once verified) |
| Kotlin / Compose compiler plugin | 2.0.21 | |
| KSP | 2.0.21-1.0.28 | Must match Kotlin |
| Compose BOM | 2024.10.01 | Material3 1.3.x |
| Room / DataStore / Lifecycle / Activity | 2.6.1 / 1.1.1 / 2.8.7 / 1.9.3 | |
| **GeckoView** | **resolved at build time** | `tools/resolve-geckoview.sh` picks the newest release from `maven.mozilla.org`. Artifact name tried: `geckoview-omni`, then `geckoview`. |

**GeckoView API coupling.** `engine/TabSession.kt` and `engine/EngineHolder.kt` use delegate signatures as of recent releases
(e.g. `onLocationChange(session, url, perms, hasUserGesture)`, `onExternalResponse(session, WebResponse)`, `ContentBlocking.EtpLevel`).
If the resolved release differs, compile errors will point at exactly those files; pin an older `wyrix.geckoview.version` or adapt signatures.
Mozilla Android Components is intentionally not used.

To update: bump versions in `gradle/libs.versions.toml`, run unit + instrumented tests, run the smoke test (`docs/SMOKE_TEST.md`).
Release builds are signed in CI from secrets; no key material is in the repo.
