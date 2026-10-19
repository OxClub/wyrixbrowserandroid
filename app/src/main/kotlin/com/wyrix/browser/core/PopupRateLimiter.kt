package com.wyrix.browser.core

/** Sliding-window limiter so a page can't open unbounded windows/tabs. */
class PopupRateLimiter(
    private val max: Int = 5,
    private val windowMs: Long = 60_000,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val stamps = ArrayDeque<Long>()

    fun tryAcquire(): Boolean {
        val now = clock()
        while (stamps.isNotEmpty() && now - stamps.first() >= windowMs) stamps.removeFirst()
        if (stamps.size >= max) return false
        stamps.addLast(now)
        return true
    }
}
