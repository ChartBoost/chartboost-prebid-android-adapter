/*
 * Copyright (c) 2026 Chartboost, Inc.
 *
 * Licensed under the MIT License.
 */

package com.chartboost.prebid.internal

import android.content.Context
import com.chartboost.sdk.ads.BannerAdLoadRequest
import com.chartboost.sdk.ads.BannerAdView
import com.chartboost.sdk.ads.FullscreenAd
import com.chartboost.sdk.ads.InterstitialAdLoadRequest
import com.chartboost.sdk.ads.RewardedAdLoadRequest
import com.chartboost.sdk.events.ChartboostError

/** A load's outcome: the ad, or null with the error that stopped it. */
internal typealias LoadResult<T> = (ad: T?, error: ChartboostError?) -> Unit

/**
 * The single mocked boundary for tests. Production loads real SDK ads; tests inject fakes that record the
 * request and hand back a mocked ad or an error, so the bridge mapping is testable without the SDK.
 */
internal interface ChartboostAdFactory {

    fun loadBanner(
        context: Context,
        request: BannerAdLoadRequest,
        onResult: LoadResult<BannerAdView>,
    )

    fun loadInterstitial(
        request: InterstitialAdLoadRequest,
        onResult: LoadResult<FullscreenAd>,
    )

    fun loadRewarded(
        request: RewardedAdLoadRequest,
        onResult: LoadResult<FullscreenAd>,
    )
}

internal class DefaultChartboostAdFactory : ChartboostAdFactory {

    override fun loadBanner(
        context: Context,
        request: BannerAdLoadRequest,
        onResult: LoadResult<BannerAdView>,
    ) = BannerAdView.load(context, request) { result -> onResult(result.ad, result.error) }

    override fun loadInterstitial(
        request: InterstitialAdLoadRequest,
        onResult: LoadResult<FullscreenAd>,
    ) = FullscreenAd.load(request) { result -> onResult(result.ad, result.error) }

    override fun loadRewarded(
        request: RewardedAdLoadRequest,
        onResult: LoadResult<FullscreenAd>,
    ) = FullscreenAd.load(request) { result -> onResult(result.ad, result.error) }
}
