# Verification status and likely first-build trouble spots

Nothing has been compiled, linted, unit-tested, instrumented or run on a device. What *was* done: structural bracket balance
check of all Kotlin files, and careful manual walk-through of the unit tests' expectations against the pure-Kotlin logic.

Most likely compile/runtime issues, in rough order of probability:
1. **GeckoView API drift** (`TabSession.kt`, `EngineHolder.kt`): delegate signatures, `ClearFlags` constants, `ContentBlocking.EtpLevel`, `WebResponse.body`, `GeckoResult.accept` overloads.
2. **Compose API details**: experimental opt-ins, `LinearProgressIndicator(progress = {})`, `Icons.Filled.*` names (only material-icons-core icons used), local `@Composable fun` inside `DropdownMenu` in `BrowserScreen`.
3. **Kotlin generics/inference** in `GeckoExt.awaitOrNull` and lambdas passed to Java SAM types.
4. **Room**: `IS :param` queries with nullable arguments; `@Transaction open suspend` in abstract DAOs.
5. **Popup sessions**: returned unopened `GeckoSession`; `GeckoViewHost` opens defensively — verify there's no double-open crash.
6. **Behavioural**: per-site tracking exceptions persistence; `onLoadError` blank-page approach (URL bar may briefly show `data:`; handled by ignoring `data:` locations); history recording timing vs title events.

Unit-test expectations were checked by hand; a failing test is as likely to be a wrong expectation as a wrong implementation.

## 0.2.0 additions — new unverified API usage
Added code that was **not compiled**. Most likely compile problems, in order:
1. `GeckoView.capturePixels()` (tab previews) in `BrowserViewModel.captureThumbnail`.
2. `ContentBlocking.BlockEvent.uri` in `TabSession` (tracker host).
3. `Modifier.animateItem()`, `SwipeToDismissBox` (Compose 1.7 / Material3 1.3 as pinned in the version catalog).
4. Framework `BiometricPrompt` builder/callback signatures in `security/BiometricGate.kt`.
5. `ImageVector.Builder.addPath(...)` named arguments in `ui/common/WyrixIcons.kt`.
Behavioural things to test on a device: per-site tracking exception takes effect after reload; permission reset for a site not visited this session; reader extraction on a few real news sites; the lock prompt after backgrounding; swipe-to-close in grid layout.
