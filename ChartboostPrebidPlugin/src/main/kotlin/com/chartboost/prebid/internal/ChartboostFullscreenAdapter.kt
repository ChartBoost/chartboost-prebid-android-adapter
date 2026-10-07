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
import org.prebid.mobile.rendering.bidding.data.bid.BidResponse
import org.prebid.mobile.rendering.bidding.interfaces.InterstitialControllerListener

/**
 * Interstitial and rewarded controller, plus the bridge from Chartboost callbacks to
 * InterstitialControllerListener. Rewarded rides the same path, branched on
 * [AdUnitConfiguration.isRewarded] at load. InterstitialCallback and RewardedCallback are siblings, so
 * the adapter implements both and serves as the callback for whichever ad it builds.
 */
internal class ChartboostFullscreenAdapter(
    context: Context,
    private val listener: InterstitialControllerListener,
    private val factory: ChartboostAdFactory,
    private val mainThread: MainThreadExecutor = DefaultMainThreadExecutor,
    private val location: String = PREBID_LOCATION,
    private val eventListener: ChartboostPrebidEventListener? = null,
) : PrebidMobileInterstitialControllerInterface, InterstitialCallback, RewardedCallback {

    private val readyLatch = SingleFireLatch()
    private val displayedLatch = SingleFireLatch()
    private val failedLatch = SingleFireLatch()
    private val destroyed = SingleFireLatch()

    // Released on destroy(): the factory needs the context only to construct the ad, and Prebid can retain
    // a discarded controller, so holding a publisher Activity past destroy() would pin it.
    private var context: Context? = context

    private var ad: Ad? = null
    private var adFormat: ChartboostAdFormat = ChartboostAdFormat.INTERSTITIAL

    override fun loadAd(adUnitConfiguration: AdUnitConfiguration, bidResponse: BidResponse) {
        val context = this.context
        if (context == null) {
            // destroy() already ran, so a late loadAd must not resurrect the controller or re-pin the
            // released context.
            PluginLog.w("fullscreen loadAd after destroy(); ignoring")
            return
        }
        adFormat = if (adUnitConfiguration.isRewarded) ChartboostAdFormat.REWARDED else ChartboostAdFormat.INTERSTITIAL
        val adm = bidResponse.winningBid?.admOrNull
        if (adm == null) {
            mainThread.execute { reportLoadFailed(ChartboostErrorMapper.admInvalid()) }
            return
        }

        val mediation = MediationFactory.create()
        // Interstitial and Rewarded share the SDK's Ad interface.
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
    }

    override fun show() {
        val ad = this.ad
        if (ad == null) {
            // Warn rather than silent-drop: a publisher calling show() expects an ad.
            PluginLog.w("fullscreen show() called with no loaded ad (destroyed, expired, or never loaded); ignoring")
            return
        }
        ad.show()
    }

    override fun destroy() {
        // The SDK exposes no destroy() for fullscreen ads, so clearCache() is the available teardown. It
        // only clears loaded-ad state and does NOT tear down an ad that is already showing, so post-show
        // signals (impression/click/dismiss/reward) keep arriving and must still be forwarded, which is why
        // the listeners are retained here. Only the load paths are gated on `destroyed` (see onAdLoaded
        // and onAdExpired). The context has no post-destroy use.
        ad?.clearCache()
        ad = null
        context = null
        destroyed.fire()
    }

    override fun onAdLoaded(event: CacheEvent, error: CacheError?) = mainThread.execute {
        // A late cache result after destroy() must not report READY to a controller Prebid already
        // discarded, which leaves the ad unit stuck ready and a later show() a no-op. Engagement callbacks
        // below are deliberately NOT gated: clearCache() does not tear down a showing ad.
        if (destroyed.hasFired) return@execute
        if (error != null) {
            reportLoadFailed(ChartboostErrorMapper.map(error))
        } else if (!failedLatch.hasFired && readyLatch.fire()) {
            // Suppress a late success after a reported failure, so the publisher never sees READY
            // following FAILED for one load.
            listener.onInterstitialReadyForDisplay()
            eventListener?.onAdLoaded(adFormat)
        }
    }

    override fun onAdRequestedToShow(event: ShowEvent) {
        PluginLog.d("fullscreen onAdRequestedToShow")
    }

    override fun onAdShown(event: ShowEvent, error: ShowError?) = mainThread.execute {
        if (error != null) {
            // No impression fires on a failed show, which is correct billing.
            PluginLog.w("fullscreen show failed: ${ChartboostErrorMapper.mapShow(error)}")
        } else if (displayedLatch.fire()) {
            listener.onInterstitialDisplayed()
            eventListener?.onAdDisplayed(adFormat)
        }
    }

    override fun onImpressionRecorded(event: ImpressionEvent) = mainThread.execute {
        event.nonBlankAdId?.let { PluginLog.d("fullscreen impression recorded, Chartboost adID=$it") }
    }

    override fun onAdClicked(event: ClickEvent, error: ClickError?) = mainThread.execute {
        if (error != null) PluginLog.d("fullscreen click error ${error.code}")
        listener.onInterstitialClicked()
        eventListener?.onAdClicked(adFormat)
    }

    override fun onAdDismiss(event: DismissEvent) = mainThread.execute {
        listener.onInterstitialClosed()
        eventListener?.onAdDismissed(adFormat)
    }

    override fun onRewardEarned(event: RewardEvent) = mainThread.execute {
        listener.onUserEarnedReward()
        eventListener?.onUserEarnedReward(adFormat)
    }

    override fun onAdExpired(event: ExpirationEvent) = mainThread.execute {
        if (destroyed.hasFired) return@execute
        // Surface expiry as a load failure only before the ad was reported ready: afterwards Prebid has
        // already notified AD_LOADED, so failing here would violate its post-load contract and a later
        // show() would be dropped. Post-ready expiry surfaces at show() through ShowError.AD_EXPIRED,
        // which onAdShown logs. Clear the dead cache now, since destroy() may never run for an ad that
        // never showed.
        if (!readyLatch.hasFired) {
            ad?.clearCache()
            ad = null
            reportLoadFailed(ChartboostErrorMapper.adExpired(event))
        }
    }

    /** Reports a load failure to the Prebid listener and the optional plugin listener, at most once. */
    private fun reportLoadFailed(error: AdException) {
        // Mirrors onAdLoaded's own !failedLatch.hasFired check: a cache error arriving after
        // ready-for-display must not fire FAILED-after-LOADED.
        if (!readyLatch.hasFired && failedLatch.fire()) {
            listener.onInterstitialFailedToLoad(error)
            eventListener?.onAdFailed(adFormat, error)
        }
    }
}
