# Releasing WYRIX to Google Play and Amazon Appstore

**What CI produces:** `Release (signed AAB + APK)` builds a signed **AAB** (Google Play) and a signed **APK** (Amazon Appstore / sideloading), both with arm64-v8a + armeabi-v7a. Nothing is uploaded to a store automatically, and the signing key never enters the repo.

**Important: the release (R8-minified) build has never been run.** Install the APK on a real phone and go through `docs/SMOKE_TEST.md` before uploading. Minification can break engine code that debug builds don't.

## 1. One-time: create a signing key for WYRIX
Use a **new** keystore for this app (don't reuse another app's key). In Termux (`pkg install openjdk-17` first if needed):
```bash
keytool -genkeypair -v -keystore wyrix-release.jks -alias wyrix -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 wyrix-release.jks > wyrix-release.jks.b64
```
Back up `wyrix-release.jks` and its two passwords somewhere safe and **offline**. If you lose the key you may not be able to update the app. Never commit or paste these anywhere.

In GitHub: repo → Settings → Secrets and variables → Actions → New repository secret:
| Secret | Value |
|---|---|
| `WYRIX_KEYSTORE_BASE64` | contents of `wyrix-release.jks.b64` |
| `WYRIX_KEYSTORE_PASSWORD` | the keystore password |
| `WYRIX_KEY_ALIAS` | `wyrix` |
| `WYRIX_KEY_PASSWORD` | the key password |

Optional: create an Environment named `release` (Settings → Environments) with required reviewers so only approved runs can use the secrets.

## 2. Build
Actions → **Release (signed AAB + APK)** → Run workflow (optionally set version name/code). Download the artifact `wyrix-release-<code>`: `WYRIX-play-v<code>.aab`, `WYRIX-amazon-v<code>.apk`, `SHA256SUMS.txt`. The log prints the signing certificate fingerprint; keep a copy.
Every store upload needs a **higher versionCode** than the last (the workflow uses 100 + run number unless you override it).

## 3. Google Play
1. Play Console → Create app. Enrol in **Play App Signing**; upload the AAB (it is signed with your *upload* key; Google re-signs for users).
2. Target API: Play raises its minimum target SDK every year. Check the current requirement in Play Console (this project targets 36).
3. Store listing: app icon `store-assets/play/icon-512.png`, feature graphic `store-assets/play/feature-graphic-1024x500.png`, and **your own phone screenshots** (2-8; I can't capture real ones).
4. Policy forms: privacy policy URL (start from `docs/PRIVACY_POLICY.md`, host it on a public page), Data safety form, content rating questionnaire, target audience, ads declaration (no ads).
5. Data safety guidance (verify against your final build): the developer collects no user data and runs no analytics/ads. Passwords are stored encrypted on the device only. Camera is used on-device for QR scanning and when a website asks. Network requests are page loads you initiate plus optional features you switch on (search suggestions, Google Translate, reader mode fetch).
6. Personal developer accounts created recently must run a **closed test** (a minimum number of testers for 14 days) before requesting production access. Check the current numbers in Play Console.

## 4. Amazon Appstore
1. Create a free developer account at developer.amazon.com → Appstore → Add new app (Android).
2. Upload `WYRIX-amazon-v<code>.apk`. Amazon re-signs apps with its own key on distribution. Check in the console whether your account also accepts AAB.
3. Assets: `store-assets/amazon/icon-114.png` (small icon), `store-assets/amazon/icon-512.png` (large icon), `store-assets/amazon/promo-1024x500.png`, plus your own screenshots.
4. Fire devices: older Fire tablets are 32-bit ARM (that's why armeabi-v7a is included). WYRIX has no Google Play Services dependency. Test on a Fire tablet or the Amazon test tools before submitting. Gecko is large, so expect a big download.
5. Content rating questionnaire and privacy policy URL are required.

## 5. Regenerating icons
`python3 tools/make-icons.py` (needs Pillow and numpy) rewrites everything under `store-assets/`. The in-app adaptive icon lives in `app/src/main/res/` (`drawable/ic_launcher_foreground.xml`, `ic_launcher_background.xml`, `ic_launcher_monochrome.xml`).
