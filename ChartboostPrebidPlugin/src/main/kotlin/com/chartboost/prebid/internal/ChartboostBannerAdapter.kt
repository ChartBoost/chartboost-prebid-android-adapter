/*
 * Copyright (c) 2026 Chartboost, Inc.
 *
 * Licensed under the MIT License.
 */

package com.chartboost.prebid.internal

import android.annotation.SuppressLint
import android.content.Context
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
import org.prebid.mobile.rendering.bidding.data.bid.Bid
import org.prebid.mobile.rendering.bidding.data.bid.BidResponse
import org.prebid.mobile.rendering.bidding.listeners.DisplayViewListener

/**
 * Banner host view and the Chartboost-callback to Prebid-DisplayViewListener bridge. It is both the View
 * returned by createBannerAdView and the SDK's BannerCallback, holding one [Banner] as its child.
 *
 * The banner path has no separate loadAd, so caching is kicked off at construction. We report "loaded" on
 * cache success (not on show, which re-fires under refresh) and "displayed" on impression.
 *
 * Teardown is driven entirely by window detachment. A view that is created but never attached (the
 * publisher discards it before layout) never reaches onDetachedFromWindow, so Banner.detach() never fires
 * and the underlying ad is only reclaimed at GC; Prebid's plugin contract has no lifecycle-end hook for a
 * created-but-never-displayed view.
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
) : FrameLayout(context), BannerCallback {

    private val loadedLatch = SingleFireLatch()
    private val displayedLatch = SingleFireLatch()
    private val failedLatch = SingleFireLatch()
    private val destroyed = SingleFireLatch()

    private var banner: Banner? = null
    private var teardownAction: Runnable? = null

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
                // No Chartboost size fits inside the negotiated slot, so there is nothing to render there.
                // Rendering something larger than the slot would still count a billable impression.
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
     * Chartboost's own demand omits w/h on banner bids today; without this fallback every such bid would
     * be a hard no-fill, since Prebid picks a plugin once and does not retry through its own renderer on a
     * decline.
     *
     * Re-checks [hasUsableBannerSize] rather than trusting the caller to only pass a fallback when it is
     * needed. The caller (the plugin adapter) already skips resolving one for a usable bid, but this
     * class still must never let a populated [adUnitFallbackSize] override a bid that can stand on its
     * own — deliberate defense-in-depth, verified independently of the caller's own check.
     */
    private fun bannerDimensions(bid: Bid): Pair<Int, Int> {
        val fallback = adUnitFallbackSize
        if (bid.hasUsableBannerSize || fallback == null) return bid.width to bid.height
        // Without this line a no-fill further down reports the substituted dimensions with no hint of
        // where they came from, which reads as the ad unit being at fault when the bid named no size.
        // At WARN so it lands at the default verbosity, and warnOnce because Chartboost demand omits
        // w/h on every banner bid today, so a per-load line would be pure noise. The message carries
        // both size pairs, so the de-dupe is per ad unit slot rather than per process.
        PluginLog.warnOnce(
            "banner bid names no usable size (${bid.width}x${bid.height}); " +
                "falling back to the ad unit's ${fallback.width}x${fallback.height}",
        )
        return fallback.width to fallback.height
    }

    private fun createAndCache(size: Banner.BannerSize, adm: String) {
        // Constructing a Chartboost Banner throws if the Monetization SDK was never started. Never let
        // that escape createBannerAdView; report it through the loading delegate instead.
        try {
            val created = factory.createBanner(context, location, size, this, MediationFactory.create())
            banner = created
            addView(created)
            // No separate loadAd on the banner path, so caching starts here. cache() is async internally
            // (launches on the SDK's main scope), so it does not re-enter the loading delegate synchronously.
            created.cache(adm)
        } catch (e: Exception) {
            mainThread.execute { reportFailed(ChartboostErrorMapper.adCreationFailed(e)) }
        }
    }

    /** Reports a load failure to the Prebid delegate and the optional plugin listener, at most once. */
    private fun reportFailed(error: AdException) {
        // Guard mirrors onAdLoaded's own !failedLatch.hasFired check: a cache error arriving after a
        // successful load (e.g. an internal re-cache) must not fire FAILED-after-LOADED.
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
        // Banner show happens after a successful load (loaded was already reported off cache). Prebid has no
        // terminal show-failed signal, and a failed show fires no impression, which is correct
        // billing. Log only; never re-signal onAdFailed after onAdLoaded.
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
        // The banner caches and shows in one step, so an expiry here is rare and almost always
        // post-display; reporting it as a load failure after onAdLoaded would break Prebid's contract.
        // Log it instead — the fullscreen path, which holds a cached ad before show, surfaces expiry as a
        // reload signal.
        PluginLog.w("banner ad expired [reason ${event.reason}]")
    }

    public override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // A re-attach within the delay window cancels the pending teardown. If the delay has already
        // fired (true teardown), this is a no-op.
        teardownAction?.let { cancelTeardown(it) }
        teardownAction = null
        if (destroyed.hasFired) {
            PluginLog.w("banner re-attached after teardown already ran; the slot stays blank — Prebid's plugin contract exposes no reload hook on this path")
        }
    }

    public override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // Cancel any previously-scheduled teardown first to avoid accumulating runnables under
        // rapid RecyclerView/ViewPager recycling, then schedule a fresh one.
        //
        // On re-attach within TEARDOWN_DELAY_MS the pending action is cancelled in onAttachedToWindow,
        // so no teardown fires — the counter-based approach is gone entirely.
        //
        // Known limitation: if a re-attach happens after the delay has already fired, the banner has
        // already been torn down. Prebid's plugin contract provides no lifecycle-end signal, so any
        // timer-based teardown shares this edge case; it is inherent to the deferred approach.
        teardownAction?.let { cancelTeardown(it) }
        val action = Runnable {
            if (!isAttachedToWindow && destroyed.fire()) {
                banner?.detach()
                banner = null
                // Also drop the publisher-supplied listener so a RecyclerView-cached (recycled-but-not-GC'd)
                // view doesn't pin it; every use is already null-safe.
                eventListener = null
            }
        }
        teardownAction = action
        scheduleTeardown(action)
    }

    private fun scheduleTeardown(action: Runnable) {
        if (teardownScheduler != null) teardownScheduler.schedule(TEARDOWN_DELAY_MS, action)
        else postDelayed(action, TEARDOWN_DELAY_MS)
    }

    private fun cancelTeardown(action: Runnable) {
        if (teardownScheduler != null) teardownScheduler.cancel(action)
        else removeCallbacks(action)
    }

    companion object {
        internal const val TEARDOWN_DELAY_MS = 1_000L
    }
}
