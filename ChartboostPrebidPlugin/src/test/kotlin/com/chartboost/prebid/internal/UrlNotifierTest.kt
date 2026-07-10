/*
 * Copyright (c) 2026 Chartboost, Inc.
 *
 * Licensed under the MIT License.
 */

package com.chartboost.prebid.internal

import com.chartboost.prebid.fakes.fakeBid
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class UrlNotifierTest {

    private val fired = mutableListOf<String>()

    private fun notifier(events: Boolean = false, nurl: Boolean = false, burl: Boolean = false) =
        UrlNotifier(
            eventsEnabled = events,
            nurlEnabled = nurl,
            burlEnabled = burl,
            // Unconfined runs the launched block inline (httpGet does not suspend), so fired is synchronous.
            scope = CoroutineScope(Dispatchers.Unconfined),
            httpGet = { fired += it },
        )

    @Test
    fun `fires nothing when every flag is disabled by default`() {
        notifier().fireWin(
            fakeBid(nurl = "https://n", events = mapOf(EVENT_WIN_KEY to "https://w")),
        )
        assertTrue(fired.isEmpty())
    }

    @Test
    fun `fires nurl and the win event for a fullscreen win when enabled`() {
        notifier(events = true, nurl = true).fireWin(
            fakeBid(nurl = "https://n", events = mapOf(EVENT_WIN_KEY to "https://w")),
        )
        assertEquals(listOf("https://n", "https://w"), fired)
    }

    @Test
    fun `fires only impression-side urls even when a win url is present`() {
        notifier(events = true, nurl = true, burl = true).fireImpression(
            fakeBid(
                burl = "https://b",
                events = mapOf(EVENT_WIN_KEY to "https://w", EVENT_IMP_KEY to "https://i"),
            ),
        )
        assertEquals(listOf("https://b", "https://i"), fired)
    }

    @Test
    fun `does not fire burl when burl stays disabled`() {
        notifier(events = true, burl = false).fireImpression(
            fakeBid(burl = "https://b", events = mapOf(EVENT_IMP_KEY to "https://i")),
        )
        assertEquals(listOf("https://i"), fired)
    }

    @Test
    fun `substitutes the auction price macro before firing`() {
        notifier(burl = true).fireImpression(
            fakeBid(burl = "https://b?p=\${AUCTION_PRICE}", price = 1.5),
        )
        assertEquals(listOf("https://b?p=1.5"), fired)
    }

    @Test
    fun `reports success to the result listener when the fire completes`() {
        val results = mutableListOf<Pair<String, Boolean>>()
        UrlNotifier(
            burlEnabled = true,
            scope = CoroutineScope(Dispatchers.Unconfined),
            httpGet = { /* completes without throwing */ },
            onResult = { url, success -> results += url to success },
        ).fireImpression(fakeBid(burl = "https://b"))
        assertEquals(listOf("https://b" to true), results)
    }

    @Test
    fun `reports failure to the result listener when the fire throws`() {
        val results = mutableListOf<Pair<String, Boolean>>()
        UrlNotifier(
            burlEnabled = true,
            scope = CoroutineScope(Dispatchers.Unconfined),
            httpGet = { throw java.io.IOException("network down") },
            onResult = { url, success -> results += url to success },
        ).fireImpression(fakeBid(burl = "https://b"))
        assertEquals(1, results.size)
        assertEquals("https://b", results[0].first)
        assertFalse(results[0].second)
    }

    @Test
    fun `a non-http or non-https url is never fired`() {
        // Regression: before scheme validation, a file:// or javascript: URL in a bid's nurl would
        // open a network connection (or execute arbitrary code via URL.openConnection). Now the
        // fire() method rejects any URL whose scheme is not http:// or https://.
        notifier(nurl = true).fireWin(fakeBid(nurl = "file:///etc/passwd"))
        assertTrue(fired.isEmpty())
    }

    @Test
    fun `an https url is still fired after scheme validation`() {
        notifier(nurl = true).fireWin(fakeBid(nurl = "https://tracker.example.com/win"))
        assertEquals(listOf("https://tracker.example.com/win"), fired)
    }

    @Test
    fun `a plain http url is fired`() {
        notifier(nurl = true).fireWin(fakeBid(nurl = "http://tracker.example.com/win"))
        assertEquals(listOf("http://tracker.example.com/win"), fired)
    }

    @Test
    fun `an uppercase scheme url is fired because schemes are case-insensitive`() {
        notifier(nurl = true).fireWin(fakeBid(nurl = "HTTPS://tracker.example.com/win"))
        assertEquals(listOf("HTTPS://tracker.example.com/win"), fired)
    }

    @Test
    fun `a non-finite bid price does not crash and reports failure`() {
        val results = mutableListOf<Pair<String, Boolean>>()
        UrlNotifier(
            burlEnabled = true,
            scope = CoroutineScope(Dispatchers.Unconfined),
            httpGet = { fired += it },
            onResult = { url, success -> results += url to success },
        ).fireImpression(fakeBid(burl = "https://b?p=\${AUCTION_PRICE}", price = Double.NaN))
        assertTrue(fired.isEmpty()) // macro resolution failed, so nothing was actually fired
        assertEquals(1, results.size)
        assertFalse(results[0].second)
        // resolution failed before substitution, so the unresolved template is surfaced, not a value
        assertEquals("https://b?p=\${AUCTION_PRICE}", results[0].first)
    }

    @Test
    fun `a throwing result listener does not escape into the coroutine scope`() {
        // Regression: onResult used to run outside runCatching, so a throwing publisher listener reached the
        // scope's uncaught-exception path (handleCoroutineException), which on Android crashes the process.
        // The fix wraps onResult in runCatching. Assert nothing escapes to the scope's CoroutineExceptionHandler;
        // revert the wrap and the handler records the throw, failing this test.
        val escaped = mutableListOf<Throwable>()
        val handler = CoroutineExceptionHandler { _, e -> escaped += e }
        UrlNotifier(
            burlEnabled = true,
            scope = CoroutineScope(Dispatchers.Unconfined + handler),
            httpGet = { },
            onResult = { _, _ -> throw RuntimeException("listener blew up") },
        ).fireImpression(fakeBid(burl = "https://b"))
        assertTrue("a throwing onResult must not escape into the coroutine scope", escaped.isEmpty())
    }

    @Test
    fun `a non-2xx response status is treated as a failure`() {
        // getResponseCode() returns 4xx/5xx as a normal value, so a dead endpoint would otherwise report
        // success. The status check that turns that into a thrown IOException lives here.
        assertThrows(IOException::class.java) { ensureSuccessfulStatus(404) }
        assertThrows(IOException::class.java) { ensureSuccessfulStatus(500) }
    }

    @Test
    fun `a 2xx response status is accepted`() {
        ensureSuccessfulStatus(200)
        ensureSuccessfulStatus(204)
        ensureSuccessfulStatus(299)
    }
}
