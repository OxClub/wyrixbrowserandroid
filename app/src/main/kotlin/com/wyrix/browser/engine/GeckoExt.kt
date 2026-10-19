package com.wyrix.browser.engine

import kotlinx.coroutines.suspendCancellableCoroutine
import org.mozilla.geckoview.GeckoResult
import kotlin.coroutines.resume

/** Suspends until the GeckoResult completes; returns null on failure/exception. */
suspend fun <T> GeckoResult<T>.awaitOrNull(): T? = suspendCancellableCoroutine { cont ->
    accept(
        { value -> if (cont.isActive) cont.resume(value); null },
        { _ -> if (cont.isActive) cont.resume(null); null },
    )
}
