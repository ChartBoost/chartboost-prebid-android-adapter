/*
 * Copyright (c) 2026 Chartboost, Inc.
 *
 * Licensed under the MIT License.
 */

package com.chartboost.prebid.fakes

import android.content.Context
import com.chartboost.prebid.internal.ChartboostAdFactory
import com.chartboost.prebid.internal.LoadResult
import com.chartboost.sdk.ads.AdLoadRequest
import com.chartboost.sdk.ads.AdShowResult
import com.chartboost.sdk.ads.BannerAdLoadRequest
import com.chartboost.sdk.ads.BannerAdView
import com.chartboost.sdk.ads.BannerSize
import com.chartboost.sdk.ads.FullscreenAd
import com.chartboost.sdk.ads.InterstitialAdLoadRequest
import com.chartboost.sdk.ads.RewardedAdLoadRequest
import com.chartboost.sdk.callbacks.AdShowCallback
import com.chartboost.sdk.events.ChartboostError
import io.mockk.every
import io.mockk.mockk

/**
 * The injected ad-factory seam: the only mocked boundary. Records what the adapter asked for and holds each
 * load open until the test completes it with [completeBanner] or [completeFullscreen], so a test controls
 * when and how the SDK answers. The ads it hands back are relaxed mocks.
 */
internal class FakeChartboostAdFactory(
    // A relaxed View mock reports a non-null parent/layoutParams, which makes ViewGroup.addView throw; pin
    // them so the banner can be added as a child.
    val bannerAdView: BannerAdView = mockk<BannerAdView>(relaxed = true).also {
        every { it.parent } returns null
        every { it.layoutParams } returns null
    },
    val fullscreenAd: FullscreenAd = mockk(relaxed = true),
    // When set, every load throws it, simulating a load the SDK rejects synchronously.
    private val failCreation: Throwable? = null,
) : ChartboostAdFactory {

    var lastBannerRequest: BannerAdLoadRequest? = null
    var lastInterstitialRequest: InterstitialAdLoadRequest? = null
    var lastRewardedRequest: RewardedAdLoadRequest? = null

    val lastBannerSize: BannerSize? get() = lastBannerRequest?.size
    val lastRequest: AdLoadRequest? get() = lastBannerRequest ?: lastInterstitialRequest ?: lastRewardedRequest

    private var bannerResult: LoadResult<BannerAdView>? = null
    private var fullscreenResult: LoadResult<FullscreenAd>? = null

    /** Every callback the adapter passed to [FullscreenAd.show], oldest first. */
    val showCallbacks: MutableList<AdShowCallback> = mutableListOf()

    init {
        every { fullscreenAd.show(any<AdShowCallback>()) } answers { firstArg<AdShowCallback?>()?.let { showCallbacks += it } }
    }

    override fun loadBanner(
        context: Context,
        request: BannerAdLoadRequest,
        onResult: LoadResult<BannerAdView>,
    ) {
        failCreation?.let { throw it }
        lastBannerRequest = request
        bannerResult = onResult
    }

    override fun loadInterstitial(
        request: InterstitialAdLoadRequest,
        onResult: LoadResult<FullscreenAd>,
    ) {
        failCreation?.let { throw it }
        lastInterstitialRequest = request
        fullscreenResult = onResult
    }

    override fun loadRewarded(
        request: RewardedAdLoadRequest,
        onResult: LoadResult<FullscreenAd>,
    ) {
        failCreation?.let { throw it }
        lastRewardedRequest = request
        fullscreenResult = onResult
    }

    /** Answers the banner load with [bannerAdView], or with [error] and no ad. */
    fun completeBanner(error: ChartboostError? = null) =
        requireNotNull(bannerResult) { "no banner load was started" }(if (error == null) bannerAdView else null, error)

    /** Answers the fullscreen load with [fullscreenAd], or with [error] and no ad. */
    fun completeFullscreen(error: ChartboostError? = null) =
        requireNotNull(fullscreenResult) { "no fullscreen load was started" }(if (error == null) fullscreenAd else null, error)

    /** Answers the latest [FullscreenAd.show] call with success, or with [error]. */
    fun completeShow(error: ChartboostError? = null) {
        val result = mockk<AdShowResult>().also { every { it.error } returns error }
        showCallbacks.last().onAdShown(result)
    }
}
