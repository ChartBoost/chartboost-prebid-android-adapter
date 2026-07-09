/*
 * Copyright (c) 2026 Chartboost, Inc.
 *
 * Licensed under the MIT License.
 */

package com.chartboost.prebid.internal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.prebid.mobile.rendering.bidding.data.bid.Bid
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fires the OpenRTB notification URLs the plugin path owns. Two primitives: [fireWin] (nurl + events.win)
 * and [fireImpression] (burl + events.imp). The per-path policy (the win/impression firing matrix) lives at the call
 * sites: the banner adapter fires impression-side only because core's WinNotifier already fired the win
 * before handoff, while the fullscreen adapter fires win-side at load and impression-side at impression.
 *
 * Everything is wired but ships disabled. No live network call happens until a publisher enables these flags.
 * `burl` stays off regardless until it is confirmed that the exchange does not already bill off the
 * trackers inside the rendered ADM.
 */
internal class UrlNotifier(
    @JvmField var eventsEnabled: Boolean = false,
    @JvmField var nurlEnabled: Boolean = false,
    @JvmField var burlEnabled: Boolean = false,
    private val scope: CoroutineScope = defaultScope,
    private val httpGet: (String) -> Unit = ::defaultHttpGet,
    // Invoked after each fire with the fired URL (the raw template if the price macro failed to resolve)
    // and whether it completed without throwing. Runs on the IO scope, not the main thread.
    private val onResult: (String, Boolean) -> Unit = { _, _ -> },
) {

    /** Win-side: nurl + events.win. */
    fun fireWin(bid: Bid) {
        fire("nurl", nurlEnabled, bid.nurl, bid.price)
        fire("events.win", eventsEnabled, bid.winEventUrl, bid.price)
    }

    /** Impression-side: burl + events.imp. */
    fun fireImpression(bid: Bid) {
        fire("burl", burlEnabled, bid.burl, bid.price)
        fire("events.imp", eventsEnabled, bid.impEventUrl, bid.price)
    }

    private fun fire(kind: String, enabled: Boolean, url: String?, price: Double) {
        if (!enabled || url.isNullOrBlank()) return
        if (!url.startsWith(HTTP_SCHEME, ignoreCase = true) && !url.startsWith(HTTPS_SCHEME, ignoreCase = true)) {
            PluginLog.w("notification url rejected: scheme is not http or https [$kind] ($url)")
            return
        }
        scope.launch {
            // Resolve the price macro inside the coroutine: BigDecimal.valueOf throws on a NaN/infinite
            // price, and resolving here keeps that off the calling adapter's main thread.
            val result = runCatching {
                val resolved = url.withAuctionPrice(price)
                httpGet(resolved)
                resolved
            }
            result.onFailure { PluginLog.w("notification fire failed [$kind]: ${it.message}") }
            // A publisher-supplied onResult must never escape into this IO scope: the scope has no
            // CoroutineExceptionHandler, so a throw here would otherwise crash the app.
            runCatching { onResult(result.getOrDefault(url), result.isSuccess) }
                .onFailure { PluginLog.w("notificationResultListener threw: ${it.message}") }
        }
    }

    private companion object {
        val defaultScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        fun defaultHttpGet(url: String) {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = true
            }
            try {
                // getResponseCode() only throws on I/O failure, not on an HTTP error status, so a
                // dead endpoint (404/500/etc.) would otherwise report as success.
                val code = connection.responseCode
                if (code !in 200..299) throw IOException("HTTP $code")
            } finally {
                connection.disconnect()
            }
        }

        const val TIMEOUT_MS = 10_000
        const val HTTP_SCHEME = "http://"
        const val HTTPS_SCHEME = "https://"
    }
}
