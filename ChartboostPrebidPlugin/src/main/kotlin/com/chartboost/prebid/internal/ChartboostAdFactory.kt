/*
 * Copyright (c) 2026 Chartboost, Inc.
 *
 * Licensed under the MIT License.
 */

package com.chartboost.prebid.internal

import android.content.Context
import com.chartboost.sdk.Mediation
import com.chartboost.sdk.ads.Banner
import com.chartboost.sdk.ads.Interstitial
import com.chartboost.sdk.ads.Rewarded
import com.chartboost.sdk.callbacks.BannerCallback
import com.chartboost.sdk.callbacks.InterstitialCallback
import com.chartboost.sdk.callbacks.RewardedCallback

/**
 * The single mocked boundary for tests. Production news up real SDK ads; tests inject fakes that capture
 * the callback and record cache/show/destroy so the bridge mapping can be table-tested without the SDK.
 */
internal interface ChartboostAdFactory {

    fun createBanner(
        context: Context,
        location: String,
        size: Banner.BannerSize,
        callback: BannerCallback,
        mediation: Mediation,
    ): Banner

    fun createInterstitial(
        context: Context,
        location: String,
        callback: InterstitialCallback,
        mediation: Mediation,
    ): Interstitial

    fun createRewarded(
        context: Context,
        location: String,
        callback: RewardedCallback,
        mediation: Mediation,
    ): Rewarded
}

internal class DefaultChartboostAdFactory : ChartboostAdFactory {

    override fun createBanner(
        context: Context,
        location: String,
        size: Banner.BannerSize,
        callback: BannerCallback,
        mediation: Mediation,
    ): Banner = Banner(context, location, size, callback, mediation)

    // Interstitial/Rewarded read the global ContextProvider.context, so the passed context is unused here;
    // the factory keeps it for a uniform signature.
    override fun createInterstitial(
        context: Context,
        location: String,
        callback: InterstitialCallback,
        mediation: Mediation,
    ): Interstitial = Interstitial(location, callback, mediation)

    override fun createRewarded(
        context: Context,
        location: String,
        callback: RewardedCallback,
        mediation: Mediation,
    ): Rewarded = Rewarded(location, callback, mediation)
}
