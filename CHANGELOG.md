# Changelog

## 0.3.0 (unbuilt)
- Password manager: encrypted vault, generator, health warnings, CSV import/export, save/fill prompts via GeckoView login storage, optional unlock-before-fill.
- QR/barcode scanner (camera or photo) and "share page as QR code".
- Home screen: generated backgrounds, custom photo, quick actions.
- New launcher background and store graphics; signed AAB (Play) + APK (Amazon) release workflow; versionCode/versionName via Gradle properties.
- Dependencies added: CameraX 1.3.4 (core, camera2, lifecycle, view) and ZXing core 3.5.3.

## 0.2.0 (unbuilt)
- Tabs: in-memory page previews, recently-closed list, swipe to close, animated reordering.
- Downloads: filters, cancel, copy link, timestamps. Bookmarks: move to folder. Home: recently visited and saved articles.
- Address bar: long-press copy/share/paste-and-go, search-engine chips. Toolbar: long-press actions, custom stroke icons.
- Privacy: unique-tracker counts with host list, persistent per-site tracking exceptions, site-permission manager.
- Reader mode with offline saved articles; page translation via Google Translate's web service (opt-in per use).
- Optional biometric lock for the app and for private tabs.
- Fix: invisible text/icons on the dark theme, transparent home background, low-contrast off switches.

## 0.1.0 (unreleased, unbuilt)
- Initial full-source drop: GeckoView engine integration, Compose UI, Room/DataStore persistence, CI workflows, unit and instrumented tests.
- Not yet compiled or run — see `docs/VERIFICATION.md`.
