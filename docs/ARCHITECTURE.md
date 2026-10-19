# Architecture

```
MainActivity ── BrowserScreen (Compose) ── BrowserViewModel ──┬─ TabsState (pure reducer)
                                                             ├─ SessionManager ── TabSession ── GeckoSession (delegates → TabEvent)
                                                             ├─ Repositories ── Room (history, bookmarks, tabs, workspaces, downloads)
                                                             ├─ SettingsStore (DataStore)
                                                             └─ DownloadCoordinator (DownloadManager / MediaStore)
```

**Data flow.** The engine never mutates UI state. Each `TabSession` translates GeckoView delegate callbacks into
immutable `TabEvent`s on a `SharedFlow`; `BrowserViewModel` reduces them into `TabsState` (a `StateFlow`). User actions
call ViewModel methods, which update state and drive the engine. The selected tab's `GeckoSession` is exposed as a `StateFlow`
and attached to a single `GeckoView` by `GeckoViewHost`.

**Why no DI framework.** `AppContainer` in `WyrixApp.kt` wires everything by hand; fewer annotation processors, easier to audit.

**Tab lifecycle.** A tab with no URL shows the Compose home screen and has no engine session. First navigation creates a
session. `SessionManager` keeps ≤ 8 sessions (LRU); suspended tabs keep serialized session state and are restored on selection.
Closing a session is delayed ~200 ms so Compose can detach the GeckoView first (GeckoView must not display a closed session).

**Persistence.** Normal tabs are saved (debounced and on `onStop`) as URL + session state; private tabs are never written.
Rows read back go through `TabMapper.restore`, which drops malformed entries and enforces size budgets.

**Process model.** One GeckoRuntime per process (created lazily). The activity declares `configChanges` so rotation never
recreates the view hierarchy.

**Threading.** GeckoView callbacks and Compose run on the main thread. Room/DataStore/IO use coroutines (`Dispatchers.IO` where blocking).
