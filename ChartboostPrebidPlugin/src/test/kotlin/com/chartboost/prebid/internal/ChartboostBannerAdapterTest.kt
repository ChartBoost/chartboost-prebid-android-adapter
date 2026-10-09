/*
 * Copyright (c) 2026 Chartboost, Inc.
 *
 * Licensed under the MIT License.
 */

package com.chartboost.prebid.internal

import android.app.Activity
import android.content.Context
import android.os.Looper
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.chartboost.prebid.ChartboostAdFormat
import com.chartboost.prebid.ChartboostPrebidEventListener
import com.chartboost.prebid.LogLevel
import com.chartboost.prebid.fakes.FakeChartboostAdFactory
import com.chartboost.prebid.fakes.cacheError
import com.chartboost.prebid.fakes.fakeBid
import com.chartboost.prebid.fakes.fakeBidResponse
import com.chartboost.prebid.fakes.showError
import com.chartboost.sdk.ads.Banner
import com.chartboost.sdk.events.CacheError
import com.chartboost.sdk.events.CacheEvent
import com.chartboost.sdk.events.ClickEvent
import com.chartboost.sdk.events.ExpirationEvent
import com.chartboost.sdk.events.ImpressionEvent
import com.chartboost.sdk.events.ShowError
import com.chartboost.sdk.events.ShowEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.prebid.mobile.AdSize
import org.prebid.mobile.rendering.bidding.listeners.DisplayViewListener
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

private class FakeTeardownScheduler : TeardownScheduler {
    var scheduled: Runnable? = null
    val cancelledActions: MutableList<Runnable> = mutableListOf()
    override fun schedule(delayMs: Long, action: Runnable) { scheduled = action }
    override fun cancel(action: Runnable) {
        cancelledActions += action
        if (scheduled === action) scheduled = null
    }
    fun runPending() { scheduled?.run() }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ChartboostBannerAdapterTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val listener = mockk<DisplayViewListener>(relaxed = true)
    private val factory = FakeChartboostAdFactory()
    private val teardownDelay: Duration = Duration.ofMillis(ChartboostBannerAdapter.TEARDOWN_DELAY_MS)

    private fun adapter(
        bid: org.prebid.mobile.rendering.bidding.data.bid.Bid? = fakeBid(),
        eventListener: ChartboostPrebidEventListener? = null,
        adUnitFallbackSize: AdSize? = null,
    ) = ChartboostBannerAdapter(
        context, listener, fakeBidResponse(bid), factory, MainThreadExecutor { it() },
        eventListener = eventListener,
        adUnitFallbackSize = adUnitFallbackSize,
    )

    private fun adapterWithScheduler(scheduler: TeardownScheduler): ChartboostBannerAdapter =
        ChartboostBannerAdapter(context, listener, fakeBidResponse(fakeBid()), factory, MainThreadExecutor { it() }, teardownScheduler = scheduler)

    /**
     * Builds an adapter with no injected [TeardownScheduler] (the production default) and attaches it to a
     * real Robolectric window, so [android.view.View.postDelayed]/[android.view.View.removeCallbacks] route
     * through a real Handler bound to the main looper instead of the view's pre-attach run queue. The
     * returned container is the real parent, so tests drive attach/detach through addView/removeView —
     * the actual signal the production teardown Runnable's own isAttachedToWindow re-check depends on.
     */
    private fun productionAdapter(): Pair<ViewGroup, ChartboostBannerAdapter> {
        // The real window attachment below runs a genuine measure/layout pass, which reads the mocked
        // banner's layoutParams; the class-level stub returns null (only so ViewGroup.addView accepts it),
        // so give it real params here for the one scenario that actually measures the tree.
        every { factory.banner.layoutParams } returns FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        val adapter = ChartboostBannerAdapter(context, listener, fakeBidResponse(fakeBid()), factory, MainThreadExecutor { it() })
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        content.addView(adapter)
        return content to adapter
    }

    @Test
    fun `detach with no injected scheduler tears the banner down after the real delay`() {
        val (content, adapter) = productionAdapter()
        content.removeView(adapter)
        shadowOf(Looper.getMainLooper()).idleFor(teardownDelay)
        verify { factory.banner.detach() }
    }

    @Test
    fun `a banner re-attached before the delay is never torn down by the stale timer when no scheduler is injected`() {
        val (content, adapter) = productionAdapter()
        content.removeView(adapter)
        content.addView(adapter)
        shadowOf(Looper.getMainLooper()).idleFor(teardownDelay.multipliedBy(2))
        verify(exactly = 0) { factory.banner.detach() }
    }

    @Test
    fun `rapid detach-attach cycles tear down exactly once at the final detach's deadline`() {
        val quarter = teardownDelay.dividedBy(4)
        val (content, adapter) = productionAdapter()
        content.removeView(adapter) // stale timer due at t=4q
        shadowOf(Looper.getMainLooper()).idleFor(quarter.multipliedBy(2)) // t=2q
        content.addView(adapter)
        content.removeView(adapter) // final deadline at t=6q
        shadowOf(Looper.getMainLooper()).idleFor(quarter.multipliedBy(3)) // t=5q: past the stale deadline
        verify(exactly = 0) { factory.banner.detach() }
        shadowOf(Looper.getMainLooper()).idleFor(quarter.multipliedBy(2)) // t=7q: past the final deadline
        verify(exactly = 1) { factory.banner.detach() }
    }

    @Test
    fun `detach with no re-attach tears the banner down after the delay`() {
        val scheduler = FakeTeardownScheduler()
        val adapter = adapterWithScheduler(scheduler)
        adapter.onDetachedFromWindow()
        scheduler.runPending()
        verify { factory.banner.detach() }
    }

    @Test
    fun `re-attach before the delay cancels teardown`() {
        val scheduler = FakeTeardownScheduler()
        val adapter = adapterWithScheduler(scheduler)
        adapter.onDetachedFromWindow()
        adapter.onAttachedToWindow()
        scheduler.runPending()
        verify(exactly = 0) { factory.banner.detach() }
    }

    @Test
    fun `re-attach then detach reschedules teardown`() {
        val scheduler = FakeTeardownScheduler()
        val adapter = adapterWithScheduler(scheduler)
        adapter.onDetachedFromWindow()
        adapter.onAttachedToWindow()
        adapter.onDetachedFromWindow()
        scheduler.runPending()
        verify { factory.banner.detach() }
    }

    @Test
    fun `detach then detach without re-attach cancels the first scheduled teardown`() {
        // Regression: onDetachedFromWindow cancels any existing teardownAction before scheduling a fresh
        // one, so rapid RecyclerView or ViewPager cycles cannot accumulate runnables. Covers the cancel
        // branch in ChartboostBannerAdapter that was untested before this test.
        val scheduler = FakeTeardownScheduler()
        val adapter = adapterWithScheduler(scheduler)
        adapter.onDetachedFromWindow() // schedules first runnable
        val firstAction = requireNotNull(scheduler.scheduled)
        adapter.onDetachedFromWindow() // must cancel the first before scheduling a second
        assertTrue(
            "expected the first scheduled runnable to be passed to cancel() on the second detach",
            scheduler.cancelledActions.contains(firstAction),
        )
        scheduler.runPending() // only the second runnable is still pending
        verify(exactly = 1) { factory.banner.detach() }
    }

    @Test
    fun `reports failure through the listener when the markup is empty`() {
        adapter(bid = fakeBid(adm = null))
        verify { listener.onAdFailed(any()) }
        assertEquals(null, factory.lastBannerSize) // no banner was created
    }

    @Test
    fun `reports failure instead of crashing when banner construction throws`() {
        // Constructing a Chartboost Banner throws when the Monetization SDK was never started; it must
        // surface through the delegate, never escape createBannerAdView.
        val throwing = FakeChartboostAdFactory(failCreation = IllegalStateException("Chartboost SDK is not initialized"))
        ChartboostBannerAdapter(
            context, listener, fakeBidResponse(fakeBid(adm = "<adm>")), throwing, MainThreadExecutor { it() },
        )
        verify { listener.onAdFailed(any()) }
    }

    @Test
    fun `caches the markup with the mapped size on construction`() {
        adapter(bid = fakeBid(adm = "<adm>", width = 300, height = 250))
        verify { factory.banner.cache("<adm>") }
        assertEquals(Banner.BannerSize.MEDIUM, factory.lastBannerSize)
    }

    @Test
    fun `reports failure when no Chartboost size fits the negotiated slot`() {
        // 250x250 is narrower than every Chartboost size. This used to snap to the nearest and render larger
        // than the slot while still counting a billable impression; it must fail the load instead.
        adapter(bid = fakeBid(adm = "<adm>", width = 250, height = 250))
        verify { listener.onAdFailed(any()) }
    }

    @Test
    fun `creates no banner when no Chartboost size fits the negotiated slot`() {
        adapter(bid = fakeBid(adm = "<adm>", width = 250, height = 250))
        assertEquals(null, factory.lastBannerSize)
    }

    @Test
    fun `fills a full-width slot with the largest size that fits`() {
        adapter(bid = fakeBid(adm = "<adm>", width = 412, height = 50))
        assertEquals(Banner.BannerSize.STANDARD, factory.lastBannerSize)
    }

    @Test
    fun `bid with no size and a single 300x250 ad unit renders MEDIUM`() {
        adapter(bid = fakeBid(adm = "<adm>", width = 0, height = 0), adUnitFallbackSize = AdSize(300, 250))
        assertEquals(Banner.BannerSize.MEDIUM, factory.lastBannerSize)
    }

    @Test
    fun `bid with no size and a 320x50 ad unit renders STANDARD`() {
        adapter(bid = fakeBid(adm = "<adm>", width = 0, height = 0), adUnitFallbackSize = AdSize(320, 50))
        assertEquals(Banner.BannerSize.STANDARD, factory.lastBannerSize)
    }

    @Test
    fun `bid with width but no height falls back to the ad unit size`() {
        // The 320x0 case: width alone is not "usable", so this must fall back exactly like 0x0 does.
        adapter(bid = fakeBid(adm = "<adm>", width = 320, height = 0), adUnitFallbackSize = AdSize(320, 50))
        assertEquals(Banner.BannerSize.STANDARD, factory.lastBannerSize)
    }

    @Test
    fun `bid with a usable size ignores the ad unit size`() {
        // The bid already fits STANDARD on its own; a mismatched ad unit fallback (MEDIUM's slot) must
        // never override a size the bid can already satisfy.
        adapter(bid = fakeBid(adm = "<adm>", width = 320, height = 50), adUnitFallbackSize = AdSize(300, 250))
        assertEquals(Banner.BannerSize.STANDARD, factory.lastBannerSize)
    }

    @Test
    fun `the size substitution is logged at the default log level`() {
        // The line exists to explain a confusing no-fill, so it has to land at the shipped default (WARN),
        // not only under DEBUG. 728x90 is used by no other test here, so warnOnce's process-wide de-dupe
        // cannot have already consumed this message.
        PluginLog.level = LogLevel.WARN
        adapter(bid = fakeBid(adm = "<adm>", width = 0, height = 0), adUnitFallbackSize = AdSize(728, 90))
        assertTrue(
            "expected a warning naming both the bid size and the substituted ad unit size",
            ShadowLog.getLogsForTag(PluginLog.TAG)
                .any { it.msg.contains("0x0") && it.msg.contains("728x90") },
        )
    }

    @Test
    fun `ad unit with no sizes at all is still a no-fill through unsupportedBannerSize`() {
        adapter(bid = fakeBid(adm = "<adm>", width = 0, height = 0), adUnitFallbackSize = null)
        verify { listener.onAdFailed(any()) }
        assertNull(factory.lastBannerSize)
    }

    @Test
    fun `banner creation attaches the Prebid mediation object`() {
        adapter(bid = fakeBid(adm = "<adm>"))
        val expected = MediationFactory.create()
        assertEquals(expected.mediationType, factory.lastMediation?.mediationType)
        assertEquals(expected.libraryVersion, factory.lastMediation?.libraryVersion)
        assertEquals(expected.adapterVersion, factory.lastMediation?.adapterVersion)
    }

    @Test
    fun `cache success reports loaded then shows inline`() {
        val adapter = adapter()
        adapter.onAdLoaded(mockk<CacheEvent>(relaxed = true), null)
        verify { listener.onAdLoaded() }
        verify { factory.banner.show() }
    }

    @Test
    fun `reports loaded only once`() {
        val adapter = adapter()
        val event = mockk<CacheEvent>(relaxed = true)
        adapter.onAdLoaded(event, null)
        adapter.onAdLoaded(event, null)
        verify(exactly = 1) { listener.onAdLoaded() }
    }

    @Test
    fun `cache failure reports failure and never shows`() {
        val adapter = adapter()
        adapter.onAdLoaded(mockk(relaxed = true), cacheError(CacheError.Code.NO_AD_FOUND))
        verify { listener.onAdFailed(any()) }
        verify(exactly = 0) { factory.banner.show() }
    }

    @Test
    fun `event listener is notified of load on cache success`() {
        val events = mockk<ChartboostPrebidEventListener>(relaxed = true)
        val adapter = adapter(eventListener = events)
        adapter.onAdLoaded(mockk<CacheEvent>(relaxed = true), null)
        verify { events.onAdLoaded(ChartboostAdFormat.BANNER) }
    }

    @Test
    fun `event listener is notified of failure when the markup is empty`() {
        val events = mockk<ChartboostPrebidEventListener>(relaxed = true)
        adapter(bid = fakeBid(adm = null), eventListener = events)
        verify { events.onAdFailed(eq(ChartboostAdFormat.BANNER), any()) }
    }

    @Test
    fun `event listener is notified of display on the first impression`() {
        val events = mockk<ChartboostPrebidEventListener>(relaxed = true)
        val adapter = adapter(eventListener = events)
        adapter.onImpressionRecorded(mockk<ImpressionEvent>(relaxed = true))
        verify { events.onAdDisplayed(ChartboostAdFormat.BANNER) }
    }

    @Test
    fun `event listener is notified of click`() {
        val events = mockk<ChartboostPrebidEventListener>(relaxed = true)
        val adapter = adapter(eventListener = events)
        adapter.onAdClicked(mockk<ClickEvent>(relaxed = true), null)
        verify { events.onAdClicked(ChartboostAdFormat.BANNER) }
    }

    @Test
    fun `late cache success after a prior failure does not call onAdLoaded`() {
        // Regression: before the guard, a success callback arriving after a failure would still fire
        // onAdLoaded because only loadedLatch gated it. The fix checks !failedLatch.hasFired first,
        // mirroring the fullscreen adapter's identical guard.
        val adapter = adapter()
        val event = mockk<CacheEvent>(relaxed = true)
        adapter.onAdLoaded(event, cacheError(CacheError.Code.NO_AD_FOUND)) // failure fires first
        adapter.onAdLoaded(event, null) // late success — must be suppressed
        verify(exactly = 0) { listener.onAdLoaded() }
        verify(exactly = 1) { listener.onAdFailed(any()) }
    }

    @Test
    fun `repeated cache errors report failure only once`() {
        val adapter = adapter()
        adapter.onAdLoaded(mockk(relaxed = true), cacheError(CacheError.Code.NO_AD_FOUND))
        adapter.onAdLoaded(mockk(relaxed = true), cacheError(CacheError.Code.NO_AD_FOUND))
        verify(exactly = 1) { listener.onAdFailed(any()) }
    }

    @Test
    fun `impression reports displayed`() {
        val adapter = adapter()
        adapter.onImpressionRecorded(mockk<ImpressionEvent>(relaxed = true))
        verify { listener.onAdDisplayed() }
    }

    @Test
    fun `repeated impressions report displayed only once`() {
        val adapter = adapter()
        val event = mockk<ImpressionEvent>(relaxed = true)
        adapter.onImpressionRecorded(event)
        adapter.onImpressionRecorded(event)
        verify(exactly = 1) { listener.onAdDisplayed() }
    }

    @Test
    fun `expiry never reports failure`() {
        // Banner expiry is a pure no-op by design (unlike the fullscreen path, which surfaces expiry as
        // a reload signal). Exercise it before any load so loadedLatch is unset: wiring expiry to
        // reportFailed would then actually fire onAdFailed and fail this test. Testing after load would be
        // masked by reportFailed's !loadedLatch.hasFired guard.
        val adapter = adapter()
        adapter.onAdExpired(mockk<ExpirationEvent>(relaxed = true))
        verify(exactly = 0) { listener.onAdFailed(any()) }
    }

    @Test
    fun `show failure after load logs only and never re-signals onAdFailed`() {
        // Prebid has no terminal show-failed signal for banners, so a ShowError arriving after onAdLoaded
        // already reported success must be logged only, never re-signaled as onAdFailed.
        val adapter = adapter()
        adapter.onAdLoaded(mockk<CacheEvent>(relaxed = true), null)
        adapter.onAdShown(mockk<ShowEvent>(relaxed = true), showError(ShowError.Code.NO_CACHED_AD))
        verify(exactly = 1) { listener.onAdLoaded() }
        verify(exactly = 0) { listener.onAdFailed(any()) }
    }

    @Test
    fun `late cache failure after load does not report failed`() {
        val adapter = adapter()
        val event = mockk<CacheEvent>(relaxed = true)
        adapter.onAdLoaded(event, null) // loaded
        adapter.onAdLoaded(event, cacheError(CacheError.Code.NO_AD_FOUND)) // late cache error
        verify(exactly = 0) { listener.onAdFailed(any()) }
        verify(exactly = 1) { listener.onAdLoaded() }
    }

    @Test
    fun `callbacks after teardown are dropped`() {
        val scheduler = FakeTeardownScheduler()
        val adapter = adapterWithScheduler(scheduler)
        adapter.onDetachedFromWindow()
        scheduler.runPending() // fires teardown, claims the destroyed latch
        adapter.onImpressionRecorded(mockk(relaxed = true))
        verify(exactly = 0) { listener.onAdDisplayed() }
    }
}
