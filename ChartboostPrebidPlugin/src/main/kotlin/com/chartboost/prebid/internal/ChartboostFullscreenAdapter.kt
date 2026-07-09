/*
 * Copyright (c) 2026 Chartboost, Inc.
 *
 * Licensed under the MIT License.
 */

package com.chartboost.prebid.internal

import android.content.Context
import com.chartboost.prebid.ChartboostAdFormat
import com.chartboost.prebid.ChartboostPrebidEventListener
import com.chartboost.sdk.ads.Ad
import com.chartboost.sdk.callbacks.InterstitialCallback
import com.chartboost.sdk.callbacks.RewardedCallback
import com.chartboost.sdk.events.CacheError
import com.chartboost.sdk.events.CacheEvent
import com.chartboost.sdk.events.ClickError
import com.chartboost.sdk.events.ClickEvent
import com.chartboost.sdk.events.DismissEvent
import com.chartboost.sdk.events.ExpirationEvent
import com.chartboost.sdk.events.ImpressionEvent
import com.chartboost.sdk.events.RewardEvent
import com.chartboost.sdk.events.ShowError
import com.chartboost.sdk.events.ShowEvent
import org.prebid.mobile.api.exceptions.AdException
import org.prebid.mobile.api.rendering.PrebidMobileInterstitialControllerInterface
import org.prebid.mobile.configuration.AdUnitConfiguration
import org.prebid.mobile.rendering.bidding.data.bid.Bid
import org.prebid.mobile.rendering.bidding.data.bid.BidResponse
import org.prebid.mobile.rendering.bidding.interfaces.InterstitialControllerListener

/**
 * Interstitial and rewarded controller, plus the Chartboost-callback to InterstitialControllerListener
 * bridge. Rewarded rides this same path, branched on [AdUnitConfiguration.isRewarded] at load.
 * InterstitialCallback and RewardedCallback are siblings (both extend DismissibleAdCallback), so the
 * adapter implements both and serves as the callback for whichever ad it builds.
 *
 * The controller path skips core's WinNotifier, so the win-side is fired here at load.
 */
internal class ChartboostFullscreenAdapter(
    private val context: Context,
    private val listener: InterstitialControllerListener,
    private val factory: ChartboostAdFactory,
    private val mainThread: MainThreadExecutor = DefaultMainThreadExecutor,
    private val urlNotifier: UrlNotifier = UrlNotifier(),
    private val location: String = PREBID_LOCATION,
    private val eventListener: ChartboostPrebidEventListener? = null,
) : PrebidMobileInterstitialControllerInterface, InterstitialCallback, RewardedCallback {

    private val readyLatch = SingleFireLatch()
    private val displayedLatch = SingleFireLatch()
    private val winLatch = SingleFireLatch()
    private val impressionLatch = SingleFireLatch()
    private val failedLatch = SingleFireLatch()
    private val destroyed = SingleFireLatch()

    private var ad: Ad? = null
    private var bid: Bid? = null
    private var adFormat: ChartboostAdFormat = ChartboostAdFormat.INTERSTITIAL

    override fun loadAd(adUnitConfiguration: AdUnitConfiguration, bidResponse: BidResponse) {
        adFormat = if (adUnitConfiguration.isRewarded) ChartboostAdFormat.REWARDED else ChartboostAdFormat.INTERSTITIAL
        val winningBid = bidResponse.winningBid
        bid = winningBid
        val adm = winningBid?.admOrNull
        if (adm == null) {
            mainThread.execute { reportLoadFailed(ChartboostErrorMapper.admInvalid()) }
            return
        }

        val mediation = MediationFactory.create()
        // Interstitial and Rewarded share the SDK's Ad interface; rewarded only differs by riding a Rewarded
        // instance (and the onRewardEarned callback, which this adapter always implements). Constructing the
        // ad throws if the Monetization SDK was never started, so never let that escape loadAd.
        val created = try {
            val newAd = if (adUnitConfiguration.isRewarded) {
                factory.createRewarded(context, location, this, mediation)
            } else {
                factory.createInterstitial(context, location, this, mediation)
            }
            newAd.cache(adm)
            newAd
        } catch (e: Exception) {
            mainThread.execute { reportLoadFailed(ChartboostErrorMapper.adCreationFailed(e)) }
            return
        }
        ad = created

        // Controller path skips WinNotifier, so the adapter owns win-side, fired at load.
        if (winLatch.fire()) urlNotifier.fireWin(winningBid)
    }

    override fun show() {
        ad?.show()
    }

    override fun destroy() {
        // The released Monetization SDK (9.12.x) exposes no destroy() for fullscreen ads, so clearCache()
        // is the available teardown.
        ad?.clearCache()
        ad = null
        destroyed.fire()
    }

    override fun onAdLoaded(event: CacheEvent, error: CacheError?) = mainThread.execute {
        if (destroyed.hasFired) return@execute
        if (error != null) {
            reportLoadFailed(ChartboostErrorMapper.map(error))
        } else if (!failedLatch.hasFired && readyLatch.fire()) {
            // Suppress a late success after a failure was already reported (e.g. an expiry-before-ready that
            // already nulled the ad), so the publisher never sees READY following FAILED for one load.
            listener.onInterstitialReadyForDisplay()
            eventListener?.onAdLoaded(adFormat)
        }
    }

    override fun onAdRequestedToShow(event: ShowEvent) {
        PluginLog.d("fullscreen onAdRequestedToShow")
    }

    override fun onAdShown(event: ShowEvent, error: ShowError?) = mainThread.execute {
        if (destroyed.hasFired) return@execute
        if (error != null) {
            // No terminal show-failed signal in Prebid; no impression fires, which is correct billing.
            PluginLog.w("fullscreen show failed: ${ChartboostErrorMapper.mapShow(error)}")
        } else if (displayedLatch.fire()) {
            listener.onInterstitialDisplayed()
            eventListener?.onAdDisplayed(adFormat)
        }
    }

    override fun onImpressionRecorded(event: ImpressionEvent) = mainThread.execute {
        if (destroyed.hasFired) return@execute
        event.nonBlankAdId?.let { PluginLog.d("fullscreen impression recorded, Chartboost adID=$it") }
        if (impressionLatch.fire()) bid?.let(urlNotifier::fireImpression)
    }

    override fun onAdClicked(event: ClickEvent, error: ClickError?) = mainThread.execute {
        if (destroyed.hasFired) return@execute
        if (error != null) PluginLog.d("fullscreen click error ${error.code}")
        listener.onInterstitialClicked()
        eventListener?.onAdClicked(adFormat)
    }

    override fun onAdDismiss(event: DismissEvent) = mainThread.execute {
        if (destroyed.hasFired) return@execute
        listener.onInterstitialClosed()
        eventListener?.onAdDismissed(adFormat)
    }

    override fun onRewardEarned(event: RewardEvent) = mainThread.execute {
        if (destroyed.hasFired) return@execute
        listener.onUserEarnedReward()
        eventListener?.onUserEarnedReward(adFormat)
    }

    override fun onAdExpired(event: ExpirationEvent) = mainThread.execute {
        if (destroyed.hasFired) return@execute
        // Surface expiry as a load failure only while the ad has NOT been reported ready: after
        // onInterstitialReadyForDisplay, Prebid has already notified AD_LOADED, so a failedToLoad here would
        // violate its post-load contract (and a later show() would be dropped). Post-ready expiry instead
        // surfaces through the SDK's ShowError.AD_EXPIRED at show(), which onAdShown logs. Clear the dead
        // cache now since destroy() may never run for an ad that never showed.
        if (!readyLatch.hasFired) {
            ad?.clearCache()
            ad = null
            reportLoadFailed(ChartboostErrorMapper.adExpired(event))
        }
    }

    /** Reports a load failure to the Prebid listener and the optional plugin listener, at most once. */
    private fun reportLoadFailed(error: AdException) {
        // Guard mirrors onAdLoaded's own !failedLatch.hasFired check: a cache error arriving after
        // ready-for-display must not fire FAILED-after-LOADED.
        if (!readyLatch.hasFired && failedLatch.fire()) {
            listener.onInterstitialFailedToLoad(error)
            eventListener?.onAdFailed(adFormat, error)
        }
    }
}
