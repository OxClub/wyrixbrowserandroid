# Android permissions

| Permission | Why | When used |
|---|---|---|
| `INTERNET` | Load web pages | Always |
| `ACCESS_NETWORK_STATE` | Used by the engine/DownloadManager to detect connectivity | Always |
| `CAMERA`, `RECORD_AUDIO`, `MODIFY_AUDIO_SETTINGS` | Web camera/microphone (WebRTC) | Only after a page asks **and** the user taps Allow, then the Android runtime dialog |
| `USE_BIOMETRIC` | Optional fingerprint/face/PIN lock for the browser and private tabs | Only when you enable app lock; the OS handles all biometric data |
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | Web geolocation | Same two-step consent |

No storage permission is requested: downloads use DownloadManager (public Downloads on API 29+, app-specific folder on API 28),
MediaStore for streamed files, and the Storage Access Framework for import/export/file upload.
