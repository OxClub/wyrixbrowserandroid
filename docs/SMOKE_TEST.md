# Manual smoke test (run on a device/emulator after first successful build)

1. Cold start → onboarding → pick options → "Start browsing"; home appears.
2. Type `example.com` → loads over HTTPS; lock icon shown. Type `some words` → search results.
3. Type `javascript:alert(1)` → refused with a toast. Type `wyrix:settings` → Settings opens.
4. Open 3 tabs, pin one, group two, switch workspace, switch back; close one, "Reopen closed tab".
5. Private tab: visit a site, log in to something, close it; History and Downloads show nothing; reopen → logged out.
6. Visit a page with trackers (e.g. a news site) → site sheet shows blocked count > 0. Toggle strict/off.
7. Visit `https://expired.badssl.com/` → native security error, no bypass; "Try again" works.
8. Download a PDF → notification, appears in Downloads, Open/Share work. Try a `blob:` download page.
9. Permission: a geolocation test page → prompt → Allow → Android dialog; reset in site sheet.
10. Bookmark a page, export HTML, clear bookmarks, import the file; folders preserved.
11. Rotate during load; background and return; kill app from recents and relaunch → tabs restored.
12. Open a link from another app (Share/VIEW intent); set as default browser from Settings.
13. Fullscreen a video → exit via button and Back.
14. Low-memory: open 12 tabs; older ones reload on selection.
15. TalkBack pass over chrome, tab switcher and settings (not yet done).
