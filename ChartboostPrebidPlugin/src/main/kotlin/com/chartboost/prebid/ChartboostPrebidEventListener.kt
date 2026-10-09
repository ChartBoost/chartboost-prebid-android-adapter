/*
 * Copyright (c) 2026 Chartboost, Inc.
 *
 * Licensed under the MIT License.
 */

package com.chartboost.prebid

import org.prebid.mobile.api.exceptions.AdException

/**
 * Optional publisher-facing callback for the lifecycle of ads the Chartboost plugin renders. Set it on
 * [ChartboostPrebidConfig] to observe what the plugin did, independently of Prebid's own per-ad-unit
 * listeners. All callbacks run on the main thread.
 *
 * [onAdDismissed] and [onUserEarnedReward] are fullscreen-only; banner ads never invoke them.
 *
 * A fullscreen ad that loads and then expires before `show()` invokes [onAdFailed]. Prebid's own ad unit
 * listener also reports the failure, so the publisher can load a new ad. Prebid has no terminal
 * show-failed signal, so any other failure at show time is only logged and does not invoke [onAdFailed].
 */
public interface ChartboostPrebidEventListener {
    public fun onAdLoaded(format: ChartboostAdFormat) {}

    /**
     * The ad became visible. Fired on the Chartboost show event for [ChartboostAdFormat.INTERSTITIAL] and
     * [ChartboostAdFormat.REWARDED], and on the first recorded impression for [ChartboostAdFormat.BANNER],
     * which has no separate show signal. Account for that in display-timing metrics.
     */
    public fun onAdDisplayed(format: ChartboostAdFormat) {}

    public fun onAdClicked(format: ChartboostAdFormat) {}

    public fun onAdFailed(format: ChartboostAdFormat, error: AdException) {}

    public fun onAdDismissed(format: ChartboostAdFormat) {}

    public fun onUserEarnedReward(format: ChartboostAdFormat) {}
}
