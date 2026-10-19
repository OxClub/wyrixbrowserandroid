package com.wyrix.browser

import com.wyrix.browser.core.LoadDecision.*
import com.wyrix.browser.core.LoadPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class LoadPolicyTest {
    private fun d(u: String) = LoadPolicy.decide(u)
    @Test fun webAllowed() { assertEquals(ALLOW, d("https://a.b")); assertEquals(ALLOW, d("http://a.b")) }
    @Test fun aboutBlankOnly() { assertEquals(ALLOW, d("about:blank")); assertEquals(DENY, d("about:config")); assertEquals(DENY, d("about:neterror")) }
    @Test fun localFilesDenied() { assertEquals(DENY, d("file:///sdcard/x")); assertEquals(DENY, d("content://x/y")); assertEquals(DENY, d("resource://gre/x")); assertEquals(DENY, d("chrome://x")) }
    @Test fun externalNeedsConfirmation() { listOf("intent://x#Intent;end", "mailto:a@b.c", "tel:1", "sms:1", "market://details?id=x", "geo:0,0").forEach { assertEquals(it, CONFIRM_EXTERNAL, d(it)) } }
    @Test fun unknownSchemeDenied() { assertEquals(DENY, d("foo://bar")); assertEquals(DENY, d("view-source:https://a.b")); assertEquals(DENY, d("garbage")) }
}
