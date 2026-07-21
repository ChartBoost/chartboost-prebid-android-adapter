/*
 * Copyright (c) 2026 Chartboost, Inc.
 *
 * Licensed under the MIT License.
 */

package com.chartboost.prebid

import android.content.Context
import android.view.View
import com.chartboost.prebid.internal.ChartboostAdFactory
import com.chartboost.prebid.internal.ChartboostBannerAdapter
import com.chartboost.prebid.internal.ChartboostFullscreenAdapter
import com.chartboost.prebid.internal.DefaultChartboostAdFactory
import com.chartboost.prebid.internal.PREBID_LOCATION
import com.chartboost.prebid.internal.PluginLog
import com.chartboost.sdk.Chartboost
import org.json.JSONObject
import org.prebid.mobile.api.data.AdFormat
import org.prebid.mobile.api.rendering.PrebidMobileInterstitialControllerInterface
import org.prebid.mobile.api.rendering.pluginrenderer.PluginEventListener
import org.prebid.mobile.api.rendering.pluginrenderer.PrebidMobilePluginRenderer
import org.prebid.mobile.configuration.AdUnitConfiguration
import org.prebid.mobile.rendering.bidding.data.bid.BidResponse
import org.prebid.mobile.rendering.bidding.interfaces.InterstitialControllerListener
import org.prebid.mobile.rendering.bidding.listeners.DisplayVideoListener
import org.prebid.mobile.rendering.bidding.listeners.DisplayViewListener

/**
 * Prebid Mobile plugin adapter that hands Chartboost-flagged winning bids to the Chartboost Monetization
 * SDK for rendering. A thin, stateless-per-load bridge: each ad unit maps to one freshly built per-load adapter
 * with no shared mutable state. Registered via [ChartboostPrebidAdapter.register]; Prebid drives it
 * purely through the [PrebidMobilePluginRenderer] interface, so the type itself is internal — publishers
 * read the name/version through [ChartboostPrebidAdapter].
 */
internal class ChartboostPrebidPluginAdapter(
    private val factory: ChartboostAdFactory,
    private val bidderToken: () -> String? = { Chartboost.getBidderToken() },
    private val config: ChartboostPrebidConfig = ChartboostPrebidConfig(),
) : PrebidMobilePluginRenderer {

    constructor(config: ChartboostPrebidConfig = ChartboostPrebidConfig()) : this(DefaultChartboostAdFactory(), config = config)

    override fun getName(): String = NAME

    override fun getVersion(): String = ADAPTER_VERSION

    override fun getData(): JSONObject? {
        // Fresh per access, no publisher-facing call. A null token still returns an object and warns once,
        // never gating the bid.
        val token = bidderToken()
        if (token == null) {
            PluginLog.warnOnce("Chartboost bidder token unavailable; is the SDK started?")
        }
        return JSONObject().apply { if (token != null) put("bidderToken", token) }
    }

    override fun registerEventListener(pluginEventListener: PluginEventListener, listenerKey: String) {
        // Core event interface is identity-only; impressions flow via the interaction delegate. No-op.
    }

    override fun unregisterEventListener(listenerKey: String) {
        // No-op
    }

    override fun createBannerAdView(
        context: Context,
        displayViewListener: DisplayViewListener,
        displayVideoListener: DisplayVideoListener?,
        adUnitConfiguration: AdUnitConfiguration,
        bidResponse: BidResponse,
    ): View {
        // Reaching here means Prebid matched this plugin's name+version and routed the win to us; a log
        // here is the only observable proof routing did not silently fall back to Prebid's own rendering.
        PluginLog.i("Chartboost plugin won routing for a banner; rendering via the Monetization SDK")
        return ChartboostBannerAdapter(
            context,
            displayViewListener,
            bidResponse,
            factory,
            location = resolvedLocation(),
            eventListener = config.eventListener,
        )
    }

    override fun createInterstitialController(
        context: Context,
        interstitialControllerListener: InterstitialControllerListener,
        adUnitConfiguration: AdUnitConfiguration,
        bidResponse: BidResponse,
    ): PrebidMobileInterstitialControllerInterface {
        PluginLog.i("Chartboost plugin won routing for a fullscreen ad; rendering via the Monetization SDK")
        return ChartboostFullscreenAdapter(
            context,
            interstitialControllerListener,
            factory,
            location = resolvedLocation(),
            eventListener = config.eventListener,
        )
    }

    override fun isSupportRenderingFor(adUnitConfiguration: AdUnitConfiguration): Boolean =
        adUnitConfiguration.isAdType(AdFormat.BANNER) ||
            adUnitConfiguration.isAdType(AdFormat.INTERSTITIAL) ||
            adUnitConfiguration.isAdType(AdFormat.VAST) // VAST is Prebid's video format; covers video interstitial/rewarded

    internal fun resolvedLocation(): String {
        if (config.location.isBlank()) {
            PluginLog.warnOnce("ChartboostPrebidConfig.location is blank; falling back to default \"$PREBID_LOCATION\"")
            return PREBID_LOCATION
        }
        return config.location
    }

    companion object {
        const val NAME: String = "Chartboost-Android-SDK"
        val ADAPTER_VERSION: String = BuildConfig.ADAPTER_VERSION
    }
}
