package com.chartboost.prebid

/**
 * Optional callback invoked after each OpenRTB notification URL the plugin fires. Firing is gated by
 * [ChartboostPrebidConfig.notificationsEnabled] (win / impression / nurl) and, separately, by
 * [ChartboostPrebidConfig.burlEnabled] (burl) — so this fires for `burl` even when `notificationsEnabled`
 * is off. Lets a publisher confirm their beacons are landing when they first turn notifications on.
 *
 * [url] is the URL actually fired, with the auction-price macro already substituted on success; it can
 * therefore carry the clearing price, so do not write it to a shared or persisted log in production.
 * (On a resolution failure the unsubstituted template is passed instead.)
 *
 * [success] is true when the request completed without throwing (the host was reached); it does not
 * assert a 2xx response. Invoked on a background IO thread, so never touch UI from it, and note that two
 * fires from one call (e.g. nurl + events.win) can invoke this concurrently.
 */
fun interface NotificationResultListener {
    fun onResult(url: String, success: Boolean)
}
