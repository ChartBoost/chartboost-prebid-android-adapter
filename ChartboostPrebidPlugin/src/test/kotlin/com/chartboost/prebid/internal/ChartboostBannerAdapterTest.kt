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
import com.applovin.mediation.ads.MaxAdView
import com.chartboost.prebid.ChartboostAdFormat
import com.chartboost.prebid.ChartboostPrebidEventListener
import com.chartboost.prebid.ChartboostPrebidPluginAdapter
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
import com.google.android.gms.ads.AdView
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
import org.prebid.mobile.api.data.AdFormat
import org.prebid.mobile.api.rendering.BannerView
import org.prebid.mobile.api.rendering.DisplayView
import org.prebid.mobile.api.rendering.pluginrenderer.PrebidMobilePluginRegister
import org.prebid.mobile.configuration.AdUnitConfiguration
import org.prebid.mobile.rendering.bidding.listeners.DisplayViewListener
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
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

/** Queues what the adapter posts, so a test can run it later, after another call has already happened. */
private class QueuedMainThread : MainThreadExecutor {
    private val pending = ArrayDeque<() -> Unit>()
    override fun execute(block: () -> Unit) { pending.addLast(block) }
    fun runAll() { while (pending.isNotEmpty()) pending.removeFirst()() }
}

/** A banner adapter nested the way a host builds it: content > host > display container > adapter. */
private class BannerScreen(
    val activityController: ActivityController<Activity>,
    val content: ViewGroup,
    val host: ViewGroup,
    val displayContainer: ViewGroup,
)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ChartboostBannerAdapterTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val listener = mockk<DisplayViewListener>(relaxed = true)
    private val factory = FakeChartboostAdFactory()

    private companion object {
        const val CONFIG_ID = "config-id"
        val BANNER_AD_SIZE = AdSize(320, 50)

        // Long enough that any detach-driven timer would have fired, like a list row scrolled off and back later.
        val LONG_DETACH: Duration = Duration.ofSeconds(30)
        val TEARDOWN_DELAY: Duration = Duration.ofMillis(ChartboostBannerAdapter.TEARDOWN_DELAY_MS)
    }

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
        ChartboostBannerAdapter(
            context, listener, fakeBidResponse(fakeBid()), factory, MainThreadExecutor { it() },
            teardownScheduler = scheduler,
        )

    private fun queuedAdapter(
        mainThread: QueuedMainThread,
        bid: org.prebid.mobile.rendering.bidding.data.bid.Bid? = fakeBid(),
    ) = ChartboostBannerAdapter(context, listener, fakeBidResponse(bid), factory, mainThread)

    /**
     * Builds an adapter inside [host] on a real Robolectric window. The returned views are the real parents,
     * so tests drive attach and detach through addView/removeView, the way a list, pager or ad host does.
     * The display container stands in for Prebid's DisplayView, which sits between the host and the adapter.
     */
    private fun screen(host: (Context) -> ViewGroup): BannerScreen {
        // The real window attachment below runs a genuine measure/layout pass, which reads the mocked
        // banner's layoutParams; the class-level stub returns null (only so ViewGroup.addView accepts it),
        // so give it real params here for the one scenario that actually measures the tree.
        every { factory.banner.layoutParams } returns FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        val activityController = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = activityController.get()
        val adapter = ChartboostBannerAdapter(activity, listener, fakeBidResponse(fakeBid()), factory, MainThreadExecutor { it() })
        val displayContainer = FrameLayout(activity).apply { addView(adapter) }
        val hostView = host(activity).apply { addView(displayContainer) }
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        content.addView(hostView)
        return BannerScreen(activityController, content, hostView, displayContainer)
    }

    private fun idleFor(duration: Duration) = shadowOf(Looper.getMainLooper()).idleFor(duration)

    @Test
    fun `a banner parked under a BannerView for longer than the delay keeps its ad when re-attached`() {
        val screen = screen { BannerView(it, CONFIG_ID, BANNER_AD_SIZE) }
        screen.content.removeView(screen.host)
        idleFor(LONG_DETACH)
        screen.content.addView(screen.host)
        verify(exactly = 0) { factory.banner.detach() }
    }

    @Test
    fun `a banner whose display container leaves its BannerView is torn down after the delay`() {
        // The ad server winning after a Prebid win swaps our container out without ever destroying it.
        val screen = screen { BannerView(it, CONFIG_ID, BANNER_AD_SIZE) }
        screen.host.removeView(screen.displayContainer)
        idleFor(TEARDOWN_DELAY)
        verify(exactly = 1) { factory.banner.detach() }
    }

    @Test
    fun `a banner parked under an AdMob ad view keeps its ad`() {
        val screen = screen { AdView(it) }
        screen.content.removeView(screen.host)
        idleFor(LONG_DETACH)
        verify(exactly = 0) { factory.banner.detach() }
    }

    @Test
    fun `a banner removed from its AdMob ad view is torn down after the delay`() {
        val screen = screen { AdView(it) }
        screen.host.removeView(screen.displayContainer)
        idleFor(TEARDOWN_DELAY)
        verify(exactly = 1) { factory.banner.detach() }
    }

    @Test
    fun `a banner parked under a MAX ad view keeps its ad`() {
        val screen = screen { MaxAdView(it) }
        screen.content.removeView(screen.host)
        idleFor(LONG_DETACH)
        verify(exactly = 0) { factory.banner.detach() }
    }

    @Test
    fun `a banner parked under a BannerView is torn down when its Activity is finishing`() {
        val screen = screen { BannerView(it, CONFIG_ID, BANNER_AD_SIZE) }
        screen.activityController.get().finish()
        screen.content.removeView(screen.host)
        idleFor(TEARDOWN_DELAY)
        verify(exactly = 1) { factory.banner.detach() }
    }

    @Test
    fun `a banner parked under a BannerView is torn down when its Activity is destroyed`() {
        val screen = screen { BannerView(it, CONFIG_ID, BANNER_AD_SIZE) }
        screen.content.removeView(screen.host)
        screen.activityController.pause().stop().destroy()
        idleFor(TEARDOWN_DELAY)
        verify(exactly = 1) { factory.banner.detach() }
    }

    @Test
    fun `a detached banner with no known ad host is torn down after the delay`() {
        val screen = screen { FrameLayout(it) }
        screen.content.removeView(screen.host)
        idleFor(TEARDOWN_DELAY)
        verify(exactly = 1) { factory.banner.detach() }
    }

    @Test
    fun `a re-attach before the delay cancels the pending teardown`() {
        val scheduler = FakeTeardownScheduler()
        val adapter = adapterWithScheduler(scheduler)
        adapter.onDetachedFromWindow()
        adapter.onAttachedToWindow()
        scheduler.runPending()
        verify(exactly = 0) { factory.banner.detach() }
    }

    @Test
    fun `a second detach cancels the first scheduled teardown`() {
        val scheduler = FakeTeardownScheduler()
        val adapter = adapterWithScheduler(scheduler)
        adapter.onDetachedFromWindow()
        val firstAction = requireNotNull(scheduler.scheduled)
        adapter.onDetachedFromWindow()
        assertTrue(scheduler.cancelledActions.contains(firstAction))
    }

    @Test
    fun `a detach after destroy schedules no teardown`() {
        val scheduler = FakeTeardownScheduler()
        val adapter = adapterWithScheduler(scheduler)
        adapter.destroy()
        adapter.onDetachedFromWindow()
        assertNull(scheduler.scheduled)
    }

    @Test
    fun `destroy cancels the pending teardown`() {
        val scheduler = FakeTeardownScheduler()
        val adapter = adapterWithScheduler(scheduler)
        adapter.onDetachedFromWindow()
        val pending = requireNotNull(scheduler.scheduled)
        adapter.destroy()
        assertTrue(scheduler.cancelledActions.contains(pending))
    }

    @Test
    fun `destroy tears down the SDK banner`() {
        val adapter = adapter()
        adapter.destroy()
        verify(exactly = 1) { factory.banner.detach() }
    }

    @Test
    fun `destroying Prebid's DisplayView tears the SDK banner down`() {
        // The real Prebid path: BannerView.destroy() calls DisplayView.destroy(), which destroys the banner
        // view the plugin returned.
        val plugin = ChartboostPrebidPluginAdapter(factory)
        val bid = fakeBid().also { every { it.prebid } returns null }
        val bidResponse = fakeBidResponse(bid).also {
            every { it.preferredPluginRendererName } returns plugin.getName()
            every { it.preferredPluginRendererVersion } returns plugin.getVersion()
        }
        val bannerConfig = AdUnitConfiguration().apply { setAdFormat(AdFormat.BANNER) }
        // The registry is process-wide and cannot hand back a plugin it replaced, so only a plugin this test
        // added is removed, and one that was already registered under the name is left alone.
        val registry = PrebidMobilePluginRegister.getInstance()
        val addedByThisTest = !registry.containsPlugin(plugin.getName())
        registry.registerPlugin(plugin)
        try {
            DisplayView(context, listener, bannerConfig, bidResponse).destroy()
            shadowOf(Looper.getMainLooper()).idle() // the plugin's default executor posts the SDK teardown
            verify(exactly = 1) { factory.banner.detach() }
        } finally {
            if (addedByThisTest) registry.unregisterPlugin(plugin)
        }
    }

    @Test
    fun `a second destroy does nothing`() {
        val adapter = adapter()
        adapter.destroy()
        adapter.destroy()
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
    fun `a load callback after destroy is dropped`() {
        val adapter = adapter()
        adapter.destroy()
        adapter.onAdLoaded(mockk<CacheEvent>(relaxed = true), null)
        verify(exactly = 0) { listener.onAdLoaded() }
    }

    @Test
    fun `a click after destroy is dropped`() {
        val adapter = adapter()
        adapter.destroy()
        adapter.onAdClicked(mockk<ClickEvent>(relaxed = true), null)
        verify(exactly = 0) { listener.onAdClicked() }
    }

    @Test
    fun `an impression after destroy is dropped`() {
        val adapter = adapter()
        adapter.destroy()
        adapter.onImpressionRecorded(mockk(relaxed = true))
        verify(exactly = 0) { listener.onAdDisplayed() }
    }

    @Test
    fun `a failure posted before destroy is dropped after it`() {
        val mainThread = QueuedMainThread()
        val adapter = queuedAdapter(mainThread, bid = fakeBid(adm = null)) // posts a failure on construction
        adapter.destroy()
        mainThread.runAll()
        verify(exactly = 0) { listener.onAdFailed(any()) }
    }

    @Test
    fun `a load callback queued before destroy does not reach the Prebid listener`() {
        val mainThread = QueuedMainThread()
        val adapter = queuedAdapter(mainThread)
        adapter.onAdLoaded(mockk<CacheEvent>(relaxed = true), null) // queued, not run yet
        adapter.destroy()
        mainThread.runAll()
        verify(exactly = 0) { listener.onAdLoaded() }
    }

    @Test
    fun `a load callback queued before destroy does not show the banner`() {
        val mainThread = QueuedMainThread()
        val adapter = queuedAdapter(mainThread)
        adapter.onAdLoaded(mockk<CacheEvent>(relaxed = true), null) // queued, not run yet
        adapter.destroy()
        mainThread.runAll()
        verify(exactly = 0) { factory.banner.show() }
    }
}
