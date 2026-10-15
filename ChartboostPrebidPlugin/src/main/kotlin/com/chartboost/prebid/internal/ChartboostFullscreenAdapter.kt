/*
 * Copyright (c) 2026 Chartboost, Inc.
 *
 * Licensed under the MIT License.
 */

package com.chartboost.prebid.internal

import com.chartboost.prebid.ChartboostAdFormat
import com.chartboost.prebid.ChartboostPrebidEventListener
import com.chartboost.sdk.ads.FullscreenAd
import com.chartboost.sdk.ads.InterstitialAdLoadRequest
import com.chartboost.sdk.ads.RewardedAdLoadRequest
import com.chartboost.sdk.callbacks.FullscreenAdCallback
import com.chartboost.sdk.events.ChartboostError
import org.prebid.mobile.api.exceptions.AdException
import org.prebid.mobile.api.rendering.PrebidMobileInterstitialControllerInterface
import org.prebid.mobile.configuration.AdUnitConfiguration
import org.prebid.mobile.rendering.bidding.data.bid.BidResponse
import org.prebid.mobile.rendering.bidding.interfaces.InterstitialControllerListener
import org.prebid.mobile.rendering.interstitial.rewarded.RewardManager
import org.prebid.mobile.rendering.interstitial.rewarded.RewardedExt

/**
 * Interstitial and rewarded controller, plus the bridge from Chartboost callbacks to
 * InterstitialControllerListener. Rewarded rides the same path, branched on
 * [AdUnitConfiguration.isRewarded] at load: the request type picks the format, and only a rewarded ad
 * calls [onRewardEarned].
 */
internal class ChartboostFullscreenAdapter(
    private val listener: InterstitialControllerListener,
    private val factory: ChartboostAdFactory,
    private val mainThread: MainThreadExecutor = DefaultMainThreadExecutor,
    private val location: String = PREBID_LOCATION,
    private val eventListener: ChartboostPrebidEventListener? = null,
) : PrebidMobileInterstitialControllerInterface, FullscreenAdCallback {

    private val readyLatch = SingleFireLatch()
    private val displayedLatch = SingleFireLatch()
    private val failedLatch = SingleFireLatch()
    private val destroyed = SingleFireLatch()

    private var ad: FullscreenAd? = null
    private var adFormat: ChartboostAdFormat = ChartboostAdFormat.INTERSTITIAL

    // From show() until the ad closes or the show fails. FullscreenAd.destroy() tears down an ad on screen,
    // so a destroy() in this window waits for the close and the post-show signals still reach Prebid.
    private var showInProgress = false

    // Rewarded only: the ad unit's RewardManager and the reward of this ad's bid, until a grant uses it. See
    // withBidReward().
    private var rewardManager: RewardManager? = null
    private var bidReward: RewardedExt? = null

    override fun loadAd(adUnitConfiguration: AdUnitConfiguration, bidResponse: BidResponse) {
        if (destroyed.hasFired) {
            // A late loadAd must not resurrect a controller Prebid already discarded.
            PluginLog.w("fullscreen loadAd after destroy(); ignoring")
            return
        }
        adFormat = if (adUnitConfiguration.isRewarded) ChartboostAdFormat.REWARDED else ChartboostAdFormat.INTERSTITIAL
        val winningBid = bidResponse.winningBid
        val adm = winningBid?.admOrNull
        if (winningBid == null || adm == null) {
            mainThread.execute { reportLoadFailed(ChartboostErrorMapper.admInvalid()) }
            return
        }
        if (adUnitConfiguration.isRewarded) {
            // Like Prebid's own renderer, give the ad unit the reward Prebid Server set on the bid. Prebid
            // reads it whenever it grants the reward.
            bidReward = winningBid.rewardedExt
            rewardManager = adUnitConfiguration.rewardManager.also { it.rewardedExt = winningBid.rewardedExt }
        }

        val mediation = MediationFactory.create()
        val onResult: LoadResult<FullscreenAd> = { ad, error -> mainThread.execute { onLoadResult(ad, error) } }
        try {
            if (adUnitConfiguration.isRewarded) {
                factory.loadRewarded(RewardedAdLoadRequest(location, adm, mediation), onResult)
            } else {
                factory.loadInterstitial(InterstitialAdLoadRequest(location, adm, mediation), onResult)
            }
        } catch (e: Exception) {
            mainThread.execute { reportLoadFailed(ChartboostErrorMapper.adCreationFailed(e)) }
        }
    }

    private fun onLoadResult(loaded: FullscreenAd?, error: ChartboostError?) {
        if (loaded == null) {
            // A late failure after destroy() must not reach a controller Prebid already discarded.
            if (!destroyed.hasFired) reportLoadFailed(ChartboostErrorMapper.map(error))
            return
        }
        // Nothing else owns a late ad, so release it rather than leak its SDK core.
        if (destroyed.hasFired || failedLatch.hasFired) {
            loaded.destroy()
            return
        }
        loaded.callback = this
        ad = loaded
        if (readyLatch.fire()) {
            listener.onInterstitialReadyForDisplay()
            eventListener?.onAdLoaded(adFormat)
        }
    }

    override fun show() {
        val ad = this.ad
        if (ad == null) {
            // Warn rather than silent-drop: a publisher calling show() expects an ad.
            PluginLog.w("fullscreen show() called with no loaded ad (destroyed, expired, or never loaded); ignoring")
            return
        }
        if (showInProgress) {
            // One show at a time, so every show result belongs to the show that is opening or on screen.
            PluginLog.w("fullscreen show() called while the ad is already opening or on screen; ignoring")
            return
        }
        showInProgress = true
        ad.show { result -> mainThread.execute { onShowResult(ad, result.error) } }
    }

    private fun onShowResult(shown: FullscreenAd, error: ChartboostError?) {
        if (shown !== ad) return
        if (error == null) reportDisplayed() else reportShowFailed(shown, error)
    }

    private fun reportDisplayed() {
        if (displayedLatch.fire()) {
            listener.onInterstitialDisplayed()
            eventListener?.onAdDisplayed(adFormat)
        }
    }

    override fun destroy() {
        if (!destroyed.fire()) return
        // An ad on screen keeps forwarding its click, reward and close until onAdClosed releases it, which
        // is why the listeners are retained. Only the load paths are gated on `destroyed`.
        if (showInProgress) return
        ad?.destroy()
        ad = null
    }

    override fun onImpressionRecorded(ad: FullscreenAd) = mainThread.execute {
        ad.auctionId.takeIf { it.isNotBlank() }?.let { PluginLog.d("fullscreen impression recorded, Chartboost auctionId=$it") }
    }

    override fun onAdClicked(ad: FullscreenAd) = mainThread.execute {
        listener.onInterstitialClicked()
        eventListener?.onAdClicked(adFormat)
    }

    override fun onAdClosed(ad: FullscreenAd, error: ChartboostError?) = mainThread.execute {
        error?.let { PluginLog.w("fullscreen closed with an error: ${ChartboostErrorMapper.mapShow(it)}") }
        // The SDK closes only an ad that displayed, and its show result can still be queued behind this close.
        // Prebid leaves the ad unit ready until it hears displayed, so report it first.
        reportDisplayed()
        // Prebid grants the reward on close as well.
        withBidReward { listener.onInterstitialClosed() }
        eventListener?.onAdDismissed(adFormat)
        // A closed ad is used up, and nothing else will release it.
        showInProgress = false
        ad.destroy()
        if (this.ad === ad) this.ad = null
    }

    override fun onRewardEarned(ad: FullscreenAd) = mainThread.execute {
        withBidReward { listener.onUserEarnedReward() }
        eventListener?.onUserEarnedReward(adFormat)
    }

    /**
     * Prebid reads the reward from the ad unit's RewardManager when it grants it, and any loadAd() on the ad
     * unit clears that manager, even one the app makes while this ad is still on screen. Put this bid's reward
     * back for the call, then give the manager back to the newer load, unless a load during the call replaced
     * it. Only the first call gets the bid's reward, since Prebid can grant on both the reward and the close.
     */
    private fun withBidReward(notifyPrebid: () -> Unit) {
        val manager = rewardManager
        val reward = bidReward
        if (manager == null || reward == null) {
            notifyPrebid()
            return
        }
        bidReward = null
        val newer = manager.rewardedExt
        manager.rewardedExt = reward
        notifyPrebid()
        if (manager.rewardedExt === reward) manager.rewardedExt = newer
    }

    override fun onAdExpired(ad: FullscreenAd) = mainThread.execute {
        // The SDK expires only a loaded ad that has not started showing, so this can follow ready.
        if (destroyed.hasFired || failedLatch.hasFired || this.ad !== ad) return@execute
        reportAdGone(ChartboostErrorMapper.adExpired())
    }

    /**
     * No impression fires on a failed show, which is correct billing. The ad survives a show the SDK rejects
     * before it starts, so show() can be retried: no usable Activity, the ad already on screen, or the SDK
     * not started. Any other failure uses it up, Chartboost turning the app off included, since the ad could
     * not show until it expired. After destroy() Prebid has discarded this controller, so a failure is only
     * logged and the ad released.
     */
    private fun reportShowFailed(shown: FullscreenAd, error: ChartboostError) {
        showInProgress = false
        if (destroyed.hasFired || displayedLatch.hasFired || error.leavesAdLoaded) {
            PluginLog.w("fullscreen show failed: ${ChartboostErrorMapper.mapShow(error)}")
            if (destroyed.hasFired) releaseAd(shown)
            return
        }
        reportAdGone(ChartboostErrorMapper.showFailed(error))
    }

    private val ChartboostError.leavesAdLoaded: Boolean
        get() = this is ChartboostError.Show.NoContext ||
            this is ChartboostError.Show.FullscreenAlreadyShowing ||
            this is ChartboostError.Show.NotInitialized

    /**
     * The SDK no longer has the ad, after an expiry or a failed show. Prebid has no hook for either, so report
     * a load failure even after ready: it resets the ad unit so the publisher can reload, instead of leaving a
     * dead unit that still reports loaded. Release the ad now, since destroy() may never run for an ad that
     * never showed.
     */
    private fun reportAdGone(error: AdException) {
        ad?.let { releaseAd(it) }
        reportLoadFailed(error)
    }

    private fun releaseAd(gone: FullscreenAd) {
        gone.destroy()
        if (ad === gone) ad = null
    }

    /** Reports a load failure to the Prebid listener and the optional plugin listener, at most once. */
    private fun reportLoadFailed(error: AdException) {
        if (!failedLatch.fire()) return
        listener.onInterstitialFailedToLoad(error)
        eventListener?.onAdFailed(adFormat, error)
    }
}
