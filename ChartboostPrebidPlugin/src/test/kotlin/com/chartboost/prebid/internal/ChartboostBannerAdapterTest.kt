/*
 * Copyright 2026 Chartboost, Inc.
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE file.
 */

package com.chartboost.prebid.internal

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.chartboost.prebid.ChartboostAdFormat
import com.chartboost.prebid.ChartboostPrebidEventListener
import com.chartboost.prebid.fakes.FakeChartboostAdFactory
import com.chartboost.prebid.fakes.fakeBid
import com.chartboost.prebid.fakes.fakeBidResponse
import com.chartboost.sdk.ads.Banner
import com.chartboost.sdk.events.CacheError
import com.chartboost.sdk.events.CacheEvent
import com.chartboost.sdk.events.ClickEvent
import com.chartboost.sdk.events.ImpressionEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.prebid.mobile.rendering.bidding.listeners.DisplayViewListener
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
    private val fired = mutableListOf<String>()

    private fun capturingNotifier() = UrlNotifier(
        eventsEnabled = true,
        nurlEnabled = true,
        burlEnabled = true,
        scope = CoroutineScope(Dispatchers.Unconfined),
        httpGet = { fired += it },
    )

    private fun adapter(
        bid: org.prebid.mobile.rendering.bidding.data.bid.Bid? = fakeBid(),
        notifier: UrlNotifier = UrlNotifier(),
        eventListener: ChartboostPrebidEventListener? = null,
    ) = ChartboostBannerAdapter(
        context, listener, fakeBidResponse(bid), factory, MainThreadExecutor { it() }, notifier,
        eventListener = eventListener,
    )

    private fun adapterWithScheduler(scheduler: TeardownScheduler): ChartboostBannerAdapter =
        ChartboostBannerAdapter(context, listener, fakeBidResponse(fakeBid()), factory, MainThreadExecutor { it() }, UrlNotifier(), teardownScheduler = scheduler)

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
        assert(scheduler.cancelledActions.contains(firstAction)) {
            "expected the first scheduled runnable to be passed to cancel() on the second detach"
        }
        scheduler.runPending() // only the second runnable is still pending
        verify(exactly = 1) { factory.banner.detach() }
    }

    private fun cacheError(code: CacheError.Code) =
        mockk<CacheError>().also { every { it.code } returns code }

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
            context, listener, fakeBidResponse(fakeBid(adm = "<adm>")), throwing, MainThreadExecutor { it() }, UrlNotifier(),
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
    fun `impression reports displayed and fires impression-side urls but not win`() {
        val bid = fakeBid(
            burl = "https://b",
            events = mapOf(EVENT_WIN_KEY to "https://w", EVENT_IMP_KEY to "https://i"),
        )
        val adapter = adapter(bid = bid, notifier = capturingNotifier())
        adapter.onImpressionRecorded(mockk<ImpressionEvent>(relaxed = true))
        verify { listener.onAdDisplayed() }
        assertEquals(listOf("https://b", "https://i"), fired)
    }
}
