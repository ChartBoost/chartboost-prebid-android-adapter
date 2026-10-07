/*
 * Copyright (c) 2026 Chartboost, Inc.
 *
 * Licensed under the MIT License.
 */

package com.chartboost.prebid.fakes

import android.content.Context
import com.chartboost.prebid.internal.ChartboostAdFactory
import com.chartboost.sdk.Mediation
import com.chartboost.sdk.ads.Banner
import com.chartboost.sdk.ads.Interstitial
import com.chartboost.sdk.ads.Rewarded
import com.chartboost.sdk.callbacks.BannerCallback
import com.chartboost.sdk.callbacks.InterstitialCallback
import com.chartboost.sdk.callbacks.RewardedCallback
import io.mockk.every
import io.mockk.mockk

/**
 * The injected ad-factory seam: the only mocked boundary. Returns relaxed mocks so the bridge
 * mapping is verified without the real SDK, and records what the adapter asked for.
 */
internal class FakeChartboostAdFactory(
    // A relaxed View mock reports a non-null parent/layoutParams, which makes ViewGroup.addView throw; pin
    // them so the banner can be added as a child.
    val banner: Banner = mockk<Banner>(relaxed = true).also {
        every { it.parent } returns null
        every { it.layoutParams } returns null
    },
    val interstitial: Interstitial = mockk(relaxed = true),
    val rewarded: Rewarded = mockk(relaxed = true),
    // When set, every create* throws it, simulating the SDK's not-started constructor failure.
    private val failCreation: Throwable? = null,
) : ChartboostAdFactory {

    var lastBannerSize: Banner.BannerSize? = null
    var lastMediation: Mediation? = null
    var lastLocation: String? = null
    var createdInterstitial = false
    var createdRewarded = false

    override fun createBanner(
        context: Context,
        location: String,
        size: Banner.BannerSize,
        callback: BannerCallback,
        mediation: Mediation,
    ): Banner {
        failCreation?.let { throw it }
        lastBannerSize = size
        lastMediation = mediation
        lastLocation = location
        return banner
    }

    override fun createInterstitial(
        context: Context,
        location: String,
        callback: InterstitialCallback,
        mediation: Mediation,
    ): Interstitial {
        failCreation?.let { throw it }
        createdInterstitial = true
        lastMediation = mediation
        lastLocation = location
        return interstitial
    }

    override fun createRewarded(
        context: Context,
        location: String,
        callback: RewardedCallback,
        mediation: Mediation,
    ): Rewarded {
        failCreation?.let { throw it }
        createdRewarded = true
        lastMediation = mediation
        lastLocation = location
        return rewarded
    }
}
