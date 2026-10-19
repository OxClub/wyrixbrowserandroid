# Releasing

1. Create an upload keystore locally (never commit it): `keytool -genkeypair -v -keystore wyrix-release.jks -alias wyrix -keyalg RSA -keysize 4096 -validity 10000`.
2. Add repository secrets: `WYRIX_KEYSTORE_BASE64` (`base64 -w0 wyrix-release.jks`), `WYRIX_KEYSTORE_PASSWORD`, `WYRIX_KEY_ALIAS`, `WYRIX_KEY_PASSWORD`. Prefer a protected GitHub Environment named `release`.
3. Bump `versionCode`/`versionName` in `app/build.gradle.kts`, tag `vX.Y.Z`; the Release workflow builds a signed AAB + APK and uploads artifacts.
4. Local signed build: export the same variables as `WYRIX_KEYSTORE_PATH`, `WYRIX_KEYSTORE_PASSWORD`, `WYRIX_KEY_ALIAS`, `WYRIX_KEY_PASSWORD`, then `gradle :app:bundleRelease`.
Debug builds use the `.debug` application-id suffix so they install alongside release builds.
