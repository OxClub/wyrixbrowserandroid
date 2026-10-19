# Security and privacy design

Threat model: untrusted web content, untrusted address-bar/intent/clipboard text, untrusted imported files, other apps on the device.

| Control | Where |
|---|---|
| Typed text only navigates to http/https; privileged schemes refused | `core/UrlParser` |
| Page-initiated navigations filtered; `file:`, `content:`, `chrome:` etc. denied; other apps only after confirmation | `core/LoadPolicy`, `engine/ExternalLinks` |
| `intent:` URIs sanitised (component/selector/flags stripped, action allow-listed, BROWSABLE added) | `ExternalLinks` |
| Incoming VIEW intents accepted only for http/https; shared text goes through the same parser | `BrowserViewModel.handleIntent` |
| No JavaScript bridge / `addJavascriptInterface` equivalent is exposed | by construction |
| Remote debugging and `about:config` disabled | `EngineHolder` |
| TLS failures show a native error page; no certificate-exception UI | `TabSession.onLoadError`, `ErrorOverlay` |
| HTTP fallback is explicit, user-initiated, labelled insecure | `ErrorOverlay` |
| Download names sanitised (traversal, control/bidi chars, reserved names, length) | `core/FilenameSanitizer` |
| Imports bounded (5 MB / 20 000 items) and validated | `core/BookmarkHtml` |
| Persisted tab rows validated and size-capped on restore | `data/TabMapper` |
| Backups disabled (`allowBackup=false`, extraction rules exclude all) | manifest, `xml/` |
| Cleartext disabled for the app's own networking | `network_security_config.xml` |
| Private tabs: engine private mode, never persisted, optional FLAG_SECURE | `SessionManager`, `MainActivity` |
| Logs never contain full URLs; private-tab activity never logged | `core/Redactor` |
| Permissions requested at runtime only when a page asks and user approves | `TabSession`, `PromptHost` |
| Notifications from sites denied | `TabSession` |

**Known gaps:** no Safe Browsing configuration (Gecko's provider needs keys); registrable-domain display uses a heuristic, not the
Public Suffix List; per-site tracking exceptions rely on Gecko's own persistence; downloads can't forward cookies; javascript: links
from page content are allowed (they run in the page's own context, same as page script).
