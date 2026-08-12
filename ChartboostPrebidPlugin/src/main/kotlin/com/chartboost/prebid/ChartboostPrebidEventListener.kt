/*
 * Copyright (c) 2026 Chartboost, Inc.
 *
 * Licensed under the MIT License.
 */

package com.chartboost.prebid

import org.prebid.mobile.api.exceptions.AdException

/**
 * Optional publisher-facing callback for the lifecycle of ads the Chartboost plugin renders. Set it on
 * [ChartboostPrebidConfig] to observe what the plugin specifically did, independently of Prebid's own
 * per-ad-unit listeners (useful for analytics, dashboards, or distinguishing a real adapter failure from
 * a silent routing fallback). All callbacks run on the main thread.
 *
 * [onAdDismissed] and [onUserEarnedReward] are fullscreen-only; banner ads never invoke them.
 *
 * These callbacks mirror Prebid's own ad lifecycle, which has no terminal show-failed signal: a fullscreen
 * ad that loads but then fails at show time (e.g. it expired before `show()`) is logged but does not invoke
 * [onAdFailed], so do not treat a missing display as a guaranteed failure callback.
 */
public interface ChartboostPrebidEventListener {
    public fun onAdLoaded(format: ChartboostAdFormat) {}

    /**
     * The ad became visible. Fired on the Chartboost show event for [ChartboostAdFormat.INTERSTITIAL] and
     * [ChartboostAdFormat.REWARDED], and on the first recorded impression for [ChartboostAdFormat.BANNER]
     * (the banner path has no separate show signal). Account for that difference in display-timing metrics.
     */
    public fun onAdDisplayed(format: ChartboostAdFormat) {}

    public fun onAdClicked(format: ChartboostAdFormat) {}

    public fun onAdFailed(format: ChartboostAdFormat, error: AdException) {}

    public fun onAdDismissed(format: ChartboostAdFormat) {}

    public fun onUserEarnedReward(format: ChartboostAdFormat) {}
}
