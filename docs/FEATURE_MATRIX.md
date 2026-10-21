# Feature matrix

Legend: **Implemented** = code exists and is wired into the UI, *untested on device/emulator*. **Partial** = works with stated limits.
**Not implemented** = deliberately absent; reason given. Nothing here has been executed (see README verification note).

| Area | Status | Notes |
|---|---|---|
| GeckoView rendering, navigation, back/forward, reload, stop | Implemented | One GeckoView, session swapped per tab |
| Address bar: URL vs search, HTTPS-first, local commands (`wyrix:settings` …) | Implemented | Unit-tested logic (`UrlParser`) |
| Dangerous typed schemes blocked (`javascript:`, `file:`, `intent:`, `data:` …) | Implemented | Unit-tested |
| Navigation policy for web-initiated loads; external-app hand-off needs confirmation | Implemented | `LoadPolicy`, `ExternalLinks` |
| Domain emphasis + punycode warning in address bar | Implemented | Heuristic registrable-domain split (no Public Suffix List) |
| Search suggestions (local: tabs, bookmarks, history, commands) | Implemented | |
| Search suggestions (network) | Partial | Off by default; DuckDuckGo only; never in private tabs |
| Search engines + custom HTTPS template | Implemented | |
| Tabs: create/close/select/duplicate/pin/reopen closed/limit 100 | Implemented | Reducer unit-tested |
| Tab reorder | Partial | Move earlier/later actions; no drag-and-drop |
| Tab groups, workspaces ("spaces") | Implemented | Room-backed; default Personal/Work/Study |
| Tab Galaxy | Partial | Lightweight 2D overview; no 3D, grid is the default |
| Tab previews | Implemented (0.2.0) | Snapshot of the page taken when you open the tab switcher; kept in memory only (max 24), never written to disk. Tabs you haven't viewed this session show a letter tile |
| Recently closed tabs list, swipe-to-close, animated reordering | Implemented (0.2.0) | List shows up to 20 normal tabs; private tabs are never remembered |
| Favicons | Not implemented | GeckoView exposes no favicon event; fetching icons ourselves was out of scope. Letter tiles used |
| Tab suspension / memory trimming | Implemented | Max 8 live sessions, LRU suspend with session-state restore; `onTrimMemory` |
| Session restore after restart | Implemented | URL + engine session state, validated on load (`TabMapper`) |
| Private tabs | Implemented | Engine private mode; never persisted; no history/downloads list; optional FLAG_SECURE |
| Tracking protection (off/balanced/strict), per-tab disable, per-site exception, counters | Implemented | Uses Gecko content blocking; per-site exception persistence is handled by Gecko and **needs on-device verification** |
| TLS errors: own error UI, no "accept risk" bypass | Implemented | Optional explicit "try HTTP" only for bare-host entries |
| Site info sheet: connection, protection, permissions, clear site data, desktop toggle | Implemented | |
| Permissions: location/camera/mic via prompt + Android runtime bridge | Implemented | |
| Web notifications | Not implemented | Auto-denied (no notification delegate) |
| JS dialogs (alert/confirm) and file picker | Implemented | |
| HTTP auth prompts, date/colour/select prompts | Not implemented | Engine defaults apply |
| Popups | Implemented | New tab; rate-limited, only from visible tab (setting) |
| Context menu (open in new/private tab, copy, share, image address) | Implemented | |
| Downloads via DownloadManager + list, open/share/retry/remove | Implemented | **Limitation:** cookies/POST not forwarded; blob:/data: streamed via MediaStore |
| Download pause/resume | Not implemented | |
| Bookmarks: folders, edit, delete+undo, move up/down, search, import/export HTML | Implemented | Import limits: 5 MB, 20 000 items; http/https only |
| History: search, grouped by day, delete item/site/range, undo | Implemented | |
| Home dashboard: shortcuts, bookmarks, frequent, privacy stats, layouts | Implemented | |
| Themes: dark/light/system, true black, accents, high contrast, reduced motion (+battery saver) | Implemented | Original palette |
| Onboarding, default-browser role request, about/licenses/help | Implemented | |
| Fullscreen video, hardware keyboard shortcuts | Implemented | Ctrl+T/W/R/N |
| Rotation/config changes | Implemented | Activity handles config changes itself |
| Reader mode | Partial (0.2.0) | Text-only. The app fetches the page itself (HTTPS, no cookies) and extracts the article with a heuristic, so JavaScript-rendered, paywalled or login-only pages fail with a message. Adjustable size/typeface; articles from normal tabs can be saved for offline reading (max 200) |
| Page translation | Partial (0.2.0) | **Not on-device.** Opens Google Translate's website for the page after an explicit warning that the address is sent to Google. Gecko's built-in translations API was not used because it couldn't be verified offline |
| Biometric lock | Implemented (0.2.0) | Whole app and/or private tabs, using the platform prompt (biometrics or device PIN on Android 11+, biometrics only on 9-10). Hides content in recents while enabled |
| Per-site tracking protection | Implemented (0.2.0) | Stored by WYRIX per host and applied to the session on load (GeckoView 140 has no persistent exception API). Takes effect with a reload |
| Tracker counts | Implemented (0.2.0) | Unique tracker hosts per page load (not raw requests), with a host list in site details |
| Site permissions manager | Partial (0.2.0) | Lists permissions seen while browsing (normal tabs only). Reset is immediate for sites seen this session, otherwise applied on your next visit to that site |
| Address bar quick actions | Implemented (0.2.0) | Long-press for copy/share/paste-and-go; search-engine chips while typing; clipboard read only when you tap Paste and go |
| Toolbar long-press actions | Implemented (0.2.0) | Back: History. Home: new tab. Tabs: new/private/close/reopen. Reload: hard reload |
| Bookmark move-to-folder, downloads filters/cancel/copy-link, recently visited & saved-articles home sections | Implemented (0.2.0) | |
| Find in page, extensions, sync, password manager, autofill | Not implemented | Out of scope |
| Split view, picture-in-picture, tablet-specific layouts | Not implemented | |
| Localization | Not implemented | UI strings are English literals in Compose; only `app_name` is a resource |
| Accessibility | Partial | Content descriptions, 48dp targets, high-contrast option. No TalkBack pass was possible |
| Mozilla Android Components | Not used | Direct GeckoView integration avoids unverified version coupling |
