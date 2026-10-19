# Performance notes (design intent — not measured)

No measurements exist yet; targets below are goals, not results.
- Engine start-up is lazy but triggered early from `MainActivity` so it overlaps first composition.
- Max 8 live sessions; LRU suspension; `onTrimMemory` suspends more aggressively.
- Session-state serialisation happens only when persisting, not on every engine callback.
- Home screen is local Compose (no network, no engine session for blank tabs).
- Reduced-motion setting and battery-saver detection (animations are minimal by default).
- Measure with Android Studio profiler / `adb shell dumpsys meminfo` / Macrobenchmark before claiming numbers.
