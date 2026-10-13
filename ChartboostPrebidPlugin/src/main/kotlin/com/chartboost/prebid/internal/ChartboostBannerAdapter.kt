/*
 * Copyright (c) 2026 Chartboost, Inc.
 *
 * Licensed under the MIT License.
 */

package com.chartboost.prebid.internal

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.widget.FrameLayout
import com.chartboost.prebid.ChartboostAdFormat
import com.chartboost.prebid.ChartboostPrebidEventListener
import com.chartboost.sdk.ads.Banner
import com.chartboost.sdk.callbacks.BannerCallback
import com.chartboost.sdk.events.CacheError
import com.chartboost.sdk.events.CacheEvent
import com.chartboost.sdk.events.ClickError
import com.chartboost.sdk.events.ClickEvent
import com.chartboost.sdk.events.ExpirationEvent
import com.chartboost.sdk.events.ImpressionEvent
import com.chartboost.sdk.events.ShowError
import com.chartboost.sdk.events.ShowEvent
import org.prebid.mobile.AdSize
import org.prebid.mobile.api.exceptions.AdException
import org.prebid.mobile.api.rendering.BannerView
import org.prebid.mobile.api.rendering.PrebidDestroyable
import org.prebid.mobile.rendering.bidding.data.bid.Bid
import org.prebid.mobile.rendering.bidding.data.bid.BidResponse
import org.prebid.mobile.rendering.bidding.listeners.DisplayViewListener

/**
 * Banner host view and the bridge from Chartboost callbacks to Prebid's DisplayViewListener. It is both
 * the View returned by createBannerAdView and the SDK's BannerCallback, holding one [Banner] as its child.
 *
 * The banner path has no separate loadAd, so caching starts at construction. "loaded" is reported on cache
 * success, not on show, which re-fires under refresh, and "displayed" on impression.
 *
 * Prebid's [destroy] tears the banner down, which it calls from BannerView.destroy(), when a refresh
 * replaces the view, and from MAX. A detached view is torn down after a short delay only if its Activity is
 * gone or no known ad host (BannerView, AdMob AdView, MaxAdView) is above it any more. So a list or pager
 * that parks the view keeps its ad, and a view that Prebid or a mediation SDK dropped without destroy() (the
 * ad server or AdMob replacing the ad) is still released.
 *
 * A bid that names no usable size (see [hasUsableBannerSize]) falls back to [adUnitFallbackSize], the ad
 * unit's own configured slot, resolved by the caller via [AdUnitBannerSizeResolver]. A bid with a usable
 * size never consults it.
 */
@SuppressLint("ViewConstructor")
internal class ChartboostBannerAdapter(
    context: Context,
    private val displayViewListener: DisplayViewListener,
    bidResponse: BidResponse,
    private val factory: ChartboostAdFactory,
    private val mainThread: MainThreadExecutor = DefaultMainThreadExecutor,
    private val location: String = PREBID_LOCATION,
    private val teardownScheduler: TeardownScheduler? = null,
    private var eventListener: ChartboostPrebidEventListener? = null,
    private val adUnitFallbackSize: AdSize? = null,
) : FrameLayout(context), BannerCallback, PrebidDestroyable {

    private val loadedLatch = SingleFireLatch()
    private val displayedLatch = SingleFireLatch()
    private val failedLatch = SingleFireLatch()
    private val destroyed = SingleFireLatch()

    private var banner: Banner? = null
    private var teardownAction: Runnable? = null

    // Not View.postDelayed/removeCallbacks: those only reach the handler of the window the view is attached
    // to right now, so a timer posted under one window would survive a cancel after a re-attach to another.
    private val teardownHandler = Handler(Looper.getMainLooper())

    init {
        val winningBid = bidResponse.winningBid
        val adm = winningBid?.admOrNull
        if (adm == null) {
            // Never return null/throw from createBannerAdView; fail through the loading delegate.
            mainThread.execute { reportFailed(ChartboostErrorMapper.admInvalid()) }
        } else {
            val (width, height) = bannerDimensions(winningBid)
            val size = BannerSizeMapper.map(width, height)
            if (size == null) {
                mainThread.execute {
                    reportFailed(ChartboostErrorMapper.unsupportedBannerSize(width, height))
                }
            } else {
                createAndCache(size, adm)
            }
        }
    }

    /**
     * The bid's own width/height when usable, otherwise [adUnitFallbackSize] when one was resolved.
     *
     * Some Chartboost demand omits w/h on banner bids, and Prebid picks a plugin once without
     * retrying through its own renderer on a decline, so without the fallback every such bid is a hard
     * no-fill.
     *
     * Re-checks [hasUsableBannerSize] rather than trusting the caller, so a populated
     * [adUnitFallbackSize] can never override a bid that stands on its own.
     */
    private fun bannerDimensions(bid: Bid): Pair<Int, Int> {
        val fallback = adUnitFallbackSize
        if (bid.hasUsableBannerSize || fallback == null) return bid.width to bid.height
        // Without this line a later no-fill reports the substituted dimensions with no hint of where they
        // came from, which reads as the ad unit being at fault. warnOnce, since this fires on every
        // sizeless banner bid; the message carries both size pairs, so the de-dupe is per slot.
        PluginLog.warnOnce(
            "banner bid names no usable size (${bid.width}x${bid.height}); " +
                "falling back to the ad unit's ${fallback.width}x${fallback.height}",
        )
        return fallback.width to fallback.height
    }

    private fun createAndCache(size: Banner.BannerSize, adm: String) {
        // Report through the loading delegate rather than letting a construction failure escape
        // createBannerAdView.
        try {
            val created = factory.createBanner(context, location, size, this, MediationFactory.create())
            banner = created
            addView(created)
            // cache() is async internally, so it does not re-enter the loading delegate synchronously.
            created.cache(adm)
        } catch (e: Exception) {
            mainThread.execute { reportFailed(ChartboostErrorMapper.adCreationFailed(e)) }
        }
    }

    /** Reports a load failure to the Prebid delegate and the optional plugin listener, at most once. */
    private fun reportFailed(error: AdException) {
        if (destroyed.hasFired) return
        // Mirrors onAdLoaded's own !failedLatch.hasFired check: a cache error arriving after a successful
        // load must not fire FAILED-after-LOADED.
        if (!loadedLatch.hasFired && failedLatch.fire()) {
            displayViewListener.onAdFailed(error)
            eventListener?.onAdFailed(ChartboostAdFormat.BANNER, error)
        }
    }

    override fun onAdLoaded(event: CacheEvent, error: CacheError?) = mainThread.execute {
        if (destroyed.hasFired) return@execute
        if (error != null) {
            reportFailed(ChartboostErrorMapper.map(error))
        } else if (!failedLatch.hasFired && loadedLatch.fire()) {
            // Suppress a late success after a failure was already reported, so the publisher never
            // sees LOADED following FAILED for one load.
            displayViewListener.onAdLoaded()
            eventListener?.onAdLoaded(ChartboostAdFormat.BANNER)
            banner?.show() // renders the cached creative inline into this host view
        }
    }

    override fun onAdRequestedToShow(event: ShowEvent) {
        PluginLog.d("banner onAdRequestedToShow")
    }

    override fun onAdShown(event: ShowEvent, error: ShowError?) {
        // Loaded was already reported off cache, and a failed show fires no impression, which is correct
        // billing, so log only.
        if (error != null) PluginLog.w("banner show failed: ${ChartboostErrorMapper.mapShow(error)}")
    }

    override fun onImpressionRecorded(event: ImpressionEvent) = mainThread.execute {
        if (destroyed.hasFired) return@execute
        event.nonBlankAdId?.let { PluginLog.d("banner impression recorded, Chartboost adID=$it") }
        if (displayedLatch.fire()) {
            displayViewListener.onAdDisplayed()
            eventListener?.onAdDisplayed(ChartboostAdFormat.BANNER)
        }
    }

    override fun onAdClicked(event: ClickEvent, error: ClickError?) = mainThread.execute {
        if (destroyed.hasFired) return@execute
        if (error != null) PluginLog.d("banner click error ${error.code}")
        displayViewListener.onAdClicked()
        eventListener?.onAdClicked(ChartboostAdFormat.BANNER)
    }

    override fun onAdExpired(event: ExpirationEvent) {
        // The banner caches and shows in one step, so expiry here is almost always post-display, and
        // reporting a load failure after onAdLoaded would break Prebid's contract.
        PluginLog.w("banner ad expired [reason ${event.reason}]")
    }

    override fun destroy() = tearDown()

    public override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // A re-attach inside the delay window cancels the pending teardown; once it has fired, a no-op.
        cancelPendingTeardown()
        if (destroyed.hasFired) {
            PluginLog.w("banner re-attached after teardown already ran; the slot stays blank — Prebid's plugin contract exposes no reload hook on this path")
        }
    }

    public override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (destroyed.hasFired) return
        // Cancel any scheduled teardown before scheduling a fresh one, so rapid RecyclerView/ViewPager
        // recycling cannot accumulate runnables.
        cancelPendingTeardown()
        val action = Runnable {
            if (!isAttachedToWindow && isDroppedForGood()) tearDown()
        }
        teardownAction = action
        scheduleTeardown(action)
    }

    /**
     * True when nothing will bring this detached view back: its Activity is gone, or no ad host that owns
     * a banner's lifecycle is above it any more. An unknown host counts as dropped, so a view parked
     * under one is released, never leaked.
     */
    private fun isDroppedForGood(): Boolean {
        val activity = hostActivity()
        if (activity != null && (activity.isFinishing || activity.isDestroyed)) return true
        return !hasAdHostAbove()
    }

    private fun hostActivity(): Activity? {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }

    private fun hasAdHostAbove(): Boolean =
        generateSequence(parent) { it.parent }.any { ancestor ->
            ancestor is BannerView ||
                generateSequence<Class<*>>(ancestor.javaClass) { it.superclass }
                    .any { it.name in MEDIATION_AD_HOST_CLASSES }
        }

    /**
     * The one teardown path, shared by Prebid's [destroy] and the detach timer. Claims [destroyed] before
     * anything is posted, so a callback already queued on the main thread sees it and is dropped.
     */
    private fun tearDown() {
        if (!destroyed.fire()) return
        cancelPendingTeardown()
        mainThread.execute {
            banner?.detach()
            banner = null
            // Drop the publisher listener too, so a destroyed-but-not-GC'd view does not pin it; every use
            // is already null-safe.
            eventListener = null
        }
    }

    private fun cancelPendingTeardown() {
        teardownAction?.let { cancelTeardown(it) }
        teardownAction = null
    }

    private fun scheduleTeardown(action: Runnable) {
        if (teardownScheduler != null) teardownScheduler.schedule(TEARDOWN_DELAY_MS, action)
        else teardownHandler.postDelayed(action, TEARDOWN_DELAY_MS)
    }

    private fun cancelTeardown(action: Runnable) {
        if (teardownScheduler != null) teardownScheduler.cancel(action)
        else teardownHandler.removeCallbacks(action)
    }

    companion object {
        internal const val TEARDOWN_DELAY_MS = 1_000L

        // AdMob's AdView and AdManagerAdView both extend BaseAdView. Matched by name, so the adapter takes no
        // Google or AppLovin dependency.
        private const val ADMOB_BASE_AD_VIEW_CLASS = "com.google.android.gms.ads.BaseAdView"
        private const val MAX_AD_VIEW_CLASS = "com.applovin.mediation.ads.MaxAdView"
        private val MEDIATION_AD_HOST_CLASSES = setOf(ADMOB_BASE_AD_VIEW_CLASS, MAX_AD_VIEW_CLASS)
    }
}
