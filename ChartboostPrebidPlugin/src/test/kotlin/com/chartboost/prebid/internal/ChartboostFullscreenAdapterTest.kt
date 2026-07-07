package com.chartboost.prebid.internal

import android.content.Context
import com.chartboost.prebid.ChartboostAdFormat
import com.chartboost.prebid.ChartboostPrebidEventListener
import com.chartboost.prebid.fakes.FakeChartboostAdFactory
import com.chartboost.prebid.fakes.fakeBid
import com.chartboost.prebid.fakes.fakeBidResponse
import com.chartboost.sdk.events.CacheError
import com.chartboost.sdk.events.CacheEvent
import com.chartboost.sdk.events.ClickEvent
import com.chartboost.sdk.events.DismissEvent
import com.chartboost.sdk.events.ExpirationEvent
import com.chartboost.sdk.events.ImpressionEvent
import com.chartboost.sdk.events.RewardEvent
import com.chartboost.sdk.events.ShowError
import com.chartboost.sdk.events.ShowEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.prebid.mobile.configuration.AdUnitConfiguration
import org.prebid.mobile.rendering.bidding.data.bid.Bid
import org.prebid.mobile.rendering.bidding.data.bid.BidResponse
import org.prebid.mobile.rendering.bidding.interfaces.InterstitialControllerListener

class ChartboostFullscreenAdapterTest {

    private val context = mockk<Context>(relaxed = true)
    private val listener = mockk<InterstitialControllerListener>(relaxed = true)
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
        notifier: UrlNotifier = UrlNotifier(),
        eventListener: ChartboostPrebidEventListener? = null,
    ) = ChartboostFullscreenAdapter(
        context, listener, factory, MainThreadExecutor { it() }, notifier, eventListener = eventListener,
    )

    // ExpirationReason lives in the SDK's internal package and cannot be named here; a relaxed mock
    // supplies the reason the error message interpolates.
    private fun expirationEvent() = mockk<ExpirationEvent>(relaxed = true)

    private fun config(rewarded: Boolean): AdUnitConfiguration =
        mockk<AdUnitConfiguration>(relaxed = true).also { every { it.isRewarded } returns rewarded }

    private fun cacheError(code: CacheError.Code) =
        mockk<CacheError>().also { every { it.code } returns code }

    private fun showError(code: ShowError.Code) =
        mockk<ShowError>().also { every { it.code } returns code }

    @Test
    fun `loadAd builds an interstitial when the unit is not rewarded`() {
        adapter().loadAd(config(rewarded = false), fakeBidResponse())
        assertTrue(factory.createdInterstitial)
        assertFalse(factory.createdRewarded)
    }

    @Test
    fun `loadAd builds a rewarded ad when the unit is rewarded`() {
        adapter().loadAd(config(rewarded = true), fakeBidResponse())
        assertTrue(factory.createdRewarded)
        assertFalse(factory.createdInterstitial)
    }

    @Test
    fun `loadAd reports failure instead of crashing when ad construction throws`() {
        // Constructing the ad throws when the Monetization SDK was never started; loadAd must report it
        // through the listener, never let it escape.
        val throwing = FakeChartboostAdFactory(failCreation = IllegalStateException("Chartboost SDK is not initialized"))
        ChartboostFullscreenAdapter(context, listener, throwing, MainThreadExecutor { it() }, UrlNotifier())
            .loadAd(config(rewarded = false), fakeBidResponse())
        verify { listener.onInterstitialFailedToLoad(any()) }
    }

    @Test
    fun `loadAd caches the bid markup`() {
        adapter().loadAd(config(rewarded = false), fakeBidResponse(fakeBid(adm = "<adm>")))
        verify { factory.interstitial.cache("<adm>") }
    }

    @Test
    fun `loadAd fails through the listener when the markup is empty`() {
        adapter().loadAd(config(rewarded = false), fakeBidResponse(fakeBid(adm = null)))
        verify { listener.onInterstitialFailedToLoad(any()) }
        assertFalse(factory.createdInterstitial)
    }

    @Test
    fun `cache success reports ready for display exactly once`() {
        val adapter = adapter().also { it.loadAd(config(false), fakeBidResponse()) }
        val event = mockk<CacheEvent>(relaxed = true)
        adapter.onAdLoaded(event, null)
        adapter.onAdLoaded(event, null)
        verify(exactly = 1) { listener.onInterstitialReadyForDisplay() }
    }

    @Test
    fun `cache failure reports failed to load`() {
        adapter().onAdLoaded(mockk(relaxed = true), cacheError(CacheError.Code.NO_AD_FOUND))
        verify { listener.onInterstitialFailedToLoad(any()) }
    }

    @Test
    fun `show success reports displayed`() {
        adapter().onAdShown(mockk<ShowEvent>(relaxed = true), null)
        verify { listener.onInterstitialDisplayed() }
    }

    @Test
    fun `show failure does not report displayed because Prebid has no show-failed signal`() {
        adapter().onAdShown(mockk<ShowEvent>(relaxed = true), showError(ShowError.Code.NO_CACHED_AD))
        verify(exactly = 0) { listener.onInterstitialDisplayed() }
    }

    @Test
    fun `click is forwarded`() {
        adapter().onAdClicked(mockk<ClickEvent>(relaxed = true), null)
        verify { listener.onInterstitialClicked() }
    }

    @Test
    fun `dismiss is forwarded as closed`() {
        adapter().onAdDismiss(mockk<DismissEvent>(relaxed = true))
        verify { listener.onInterstitialClosed() }
    }

    @Test
    fun `reward is forwarded as user earned reward`() {
        adapter().onRewardEarned(mockk<RewardEvent>(relaxed = true))
        verify { listener.onUserEarnedReward() }
    }

    @Test
    fun `show delegates to the underlying ad`() {
        val adapter = adapter().also { it.loadAd(config(false), fakeBidResponse()) }
        adapter.show()
        verify { factory.interstitial.show() }
    }

    @Test
    fun `destroy tears the underlying ad down`() {
        val adapter = adapter().also { it.loadAd(config(false), fakeBidResponse()) }
        adapter.destroy()
        verify { factory.interstitial.clearCache() }
    }

    @Test
    fun `fires win-side at load when notifications are enabled`() {
        val bid = fakeBid(nurl = "https://n", events = mapOf(EVENT_WIN_KEY to "https://w"))
        adapter(capturingNotifier()).loadAd(config(false), fakeBidResponse(bid))
        assertEquals(listOf("https://n", "https://w"), fired)
    }

    @Test
    fun `fires impression-side on impression when notifications are enabled`() {
        val bid = fakeBid(burl = "https://b", events = mapOf(EVENT_IMP_KEY to "https://i"))
        val adapter = adapter(capturingNotifier()).also { it.loadAd(config(false), fakeBidResponse(bid)) }
        fired.clear() // drop the win-side fired at load
        adapter.onImpressionRecorded(mockk<ImpressionEvent>(relaxed = true))
        assertEquals(listOf("https://b", "https://i"), fired)
    }

    @Test
    fun `event listener is notified of load with the interstitial format`() {
        val events = mockk<ChartboostPrebidEventListener>(relaxed = true)
        val adapter = adapter(eventListener = events).also { it.loadAd(config(rewarded = false), fakeBidResponse()) }
        adapter.onAdLoaded(mockk<CacheEvent>(relaxed = true), null)
        verify { events.onAdLoaded(ChartboostAdFormat.INTERSTITIAL) }
    }

    @Test
    fun `event listener reports the rewarded format on reward`() {
        val events = mockk<ChartboostPrebidEventListener>(relaxed = true)
        val adapter = adapter(eventListener = events).also { it.loadAd(config(rewarded = true), fakeBidResponse()) }
        adapter.onRewardEarned(mockk<RewardEvent>(relaxed = true))
        verify { events.onUserEarnedReward(ChartboostAdFormat.REWARDED) }
    }

    @Test
    fun `event listener is notified of display on show success`() {
        val events = mockk<ChartboostPrebidEventListener>(relaxed = true)
        val adapter = adapter(eventListener = events).also { it.loadAd(config(rewarded = false), fakeBidResponse()) }
        adapter.onAdShown(mockk<ShowEvent>(relaxed = true), null)
        verify { events.onAdDisplayed(ChartboostAdFormat.INTERSTITIAL) }
    }

    @Test
    fun `event listener is notified of click`() {
        val events = mockk<ChartboostPrebidEventListener>(relaxed = true)
        val adapter = adapter(eventListener = events).also { it.loadAd(config(rewarded = false), fakeBidResponse()) }
        adapter.onAdClicked(mockk<ClickEvent>(relaxed = true), null)
        verify { events.onAdClicked(ChartboostAdFormat.INTERSTITIAL) }
    }

    @Test
    fun `event listener is notified of dismissal`() {
        val events = mockk<ChartboostPrebidEventListener>(relaxed = true)
        val adapter = adapter(eventListener = events).also { it.loadAd(config(rewarded = false), fakeBidResponse()) }
        adapter.onAdDismiss(mockk<DismissEvent>(relaxed = true))
        verify { events.onAdDismissed(ChartboostAdFormat.INTERSTITIAL) }
    }

    @Test
    fun `expiry before ready reports failed to load as a reload signal`() {
        val adapter = adapter().also { it.loadAd(config(rewarded = false), fakeBidResponse()) }
        adapter.onAdExpired(expirationEvent())
        verify { listener.onInterstitialFailedToLoad(any()) }
    }

    @Test
    fun `expiry after ready does not report failed to load because AD_LOADED was already sent`() {
        val adapter = adapter().also { it.loadAd(config(rewarded = false), fakeBidResponse()) }
        adapter.onAdLoaded(mockk<CacheEvent>(relaxed = true), null) // reports ready for display
        adapter.onAdExpired(expirationEvent())
        verify(exactly = 0) { listener.onInterstitialFailedToLoad(any()) }
    }

    @Test
    fun `a cache error then an expiry reports failed to load only once`() {
        val adapter = adapter().also { it.loadAd(config(rewarded = false), fakeBidResponse()) }
        adapter.onAdLoaded(mockk(relaxed = true), cacheError(CacheError.Code.NO_AD_FOUND))
        adapter.onAdExpired(expirationEvent())
        verify(exactly = 1) { listener.onInterstitialFailedToLoad(any()) }
    }

    @Test
    fun `a late success after expiry never reports ready for display`() {
        val adapter = adapter().also { it.loadAd(config(rewarded = false), fakeBidResponse()) }
        adapter.onAdExpired(expirationEvent()) // reports failed, nulls the ad
        adapter.onAdLoaded(mockk<CacheEvent>(relaxed = true), null) // late success must not resurrect it
        verify(exactly = 0) { listener.onInterstitialReadyForDisplay() }
        verify(exactly = 1) { listener.onInterstitialFailedToLoad(any()) }
    }
}
