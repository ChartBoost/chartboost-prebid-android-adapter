/*
 * Copyright (c) 2026 Chartboost, Inc.
 *
 * Licensed under the MIT License.
 */

package com.chartboost.prebid.internal

import android.util.Log
import com.chartboost.prebid.ChartboostAdFormat
import com.chartboost.prebid.ChartboostPrebidEventListener
import com.chartboost.prebid.fakes.FakeChartboostAdFactory
import com.chartboost.prebid.fakes.LEFTOVER_REWARD_TYPE
import com.chartboost.prebid.fakes.NEWER_REWARD_TYPE
import com.chartboost.prebid.fakes.fakeBid
import com.chartboost.prebid.fakes.fakeBidResponse
import com.chartboost.prebid.fakes.fakeReward
import com.chartboost.prebid.fakes.fakeRewardedExt
import com.chartboost.prebid.fakes.usedUpShowError
import com.chartboost.sdk.callbacks.AdShowCallback
import com.chartboost.sdk.events.ChartboostError
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.prebid.mobile.configuration.AdUnitConfiguration
import org.prebid.mobile.rendering.bidding.interfaces.InterstitialControllerListener
import org.prebid.mobile.rendering.interstitial.rewarded.Reward

class ChartboostFullscreenAdapterTest {

    @After
    fun unmockLog() {
        unmockkStatic(Log::class)
    }

    private val listener = mockk<InterstitialControllerListener>(relaxed = true)
    private val factory = FakeChartboostAdFactory()
    private val ad get() = factory.fullscreenAd

    private fun adapter(
        eventListener: ChartboostPrebidEventListener? = null,
    ) = ChartboostFullscreenAdapter(listener, factory, MainThreadExecutor { it() }, eventListener = eventListener)

    /** An adapter that has loaded and reported ready for display. */
    private fun readyAdapter(
        eventListener: ChartboostPrebidEventListener? = null,
        rewarded: Boolean = false,
        config: AdUnitConfiguration = config(rewarded),
        bid: org.prebid.mobile.rendering.bidding.data.bid.Bid = fakeBid(),
    ) = adapter(eventListener).also {
        it.loadAd(config, fakeBidResponse(bid))
        factory.completeFullscreen()
    }

    /** An adapter whose ad is on screen: loaded, shown, and the show reported success. */
    private fun displayedAdapter(eventListener: ChartboostPrebidEventListener? = null) =
        readyAdapter(eventListener).also {
            it.show()
            factory.completeShow()
        }

    private fun showCount() = factory.showCallbacks.size

    private fun config(rewarded: Boolean): AdUnitConfiguration =
        mockk<AdUnitConfiguration>(relaxed = true).also { every { it.isRewarded } returns rewarded }

    /** A real rewarded configuration, so the test reads the same RewardManager state Prebid reads. */
    private fun rewardedConfig() = AdUnitConfiguration().also { it.isRewarded = true }

    /** What [config] holds each time [trigger] reaches the listener, which is what Prebid reads for that call. */
    private fun rewardsSeenByPrebid(
        config: AdUnitConfiguration,
        trigger: (InterstitialControllerListener) -> Unit,
    ): List<Reward?> {
        val seen = mutableListOf<Reward?>()
        every { trigger(listener) } answers { seen += config.rewardManager.rewardedExt.reward }
        return seen
    }

    /** A rewarded adapter loaded with a bid carrying [reward], against the shared [config]. */
    private fun rewardedAdapter(config: AdUnitConfiguration, reward: Reward? = fakeReward()) =
        readyAdapter(config = config, bid = fakeBid(rewardedExt = fakeRewardedExt(reward)))

    /**
     * What `RewardedAdUnit.loadAd()` does to the shared [config], even when Prebid then skips the load. An app
     * can call it while the shown ad is still on screen.
     */
    private fun loadOnTheAdUnit(config: AdUnitConfiguration) = config.rewardManager.clear()

    // region load

    @Test
    fun `loadAd loads an interstitial when the unit is not rewarded`() {
        adapter().loadAd(config(rewarded = false), fakeBidResponse())
        assertEquals(PREBID_LOCATION, factory.lastInterstitialRequest?.adUnit)
        assertNull(factory.lastRewardedRequest)
    }

    @Test
    fun `loadAd loads a rewarded ad when the unit is rewarded`() {
        adapter().loadAd(config(rewarded = true), fakeBidResponse())
        assertEquals(PREBID_LOCATION, factory.lastRewardedRequest?.adUnit)
        assertNull(factory.lastInterstitialRequest)
    }

    @Test
    fun `loadAd loads the bid markup`() {
        adapter().loadAd(config(rewarded = false), fakeBidResponse(fakeBid(adm = "<adm>")))
        assertEquals("<adm>", factory.lastInterstitialRequest?.adm)
    }

    @Test
    fun `loadAd attaches the Prebid mediation object to the request`() {
        adapter().loadAd(config(rewarded = false), fakeBidResponse(fakeBid(adm = "<adm>")))
        val expected = MediationFactory.create()
        val mediation = factory.lastRequest?.mediation
        assertEquals(expected.mediationType, mediation?.mediationType)
        assertEquals(expected.libraryVersion, mediation?.libraryVersion)
        assertEquals(expected.adapterVersion, mediation?.adapterVersion)
    }

    @Test
    fun `loadAd reports failure instead of crashing when the load throws`() {
        val throwing = FakeChartboostAdFactory(failCreation = IllegalStateException("Chartboost SDK is not initialized"))
        ChartboostFullscreenAdapter(listener, throwing, MainThreadExecutor { it() })
            .loadAd(config(rewarded = false), fakeBidResponse())
        verify { listener.onInterstitialFailedToLoad(any()) }
    }

    @Test
    fun `loadAd fails through the listener when the markup is empty`() {
        adapter().loadAd(config(rewarded = false), fakeBidResponse(fakeBid(adm = null)))
        verify { listener.onInterstitialFailedToLoad(any()) }
        assertNull(factory.lastInterstitialRequest)
    }

    @Test
    fun `a loaded ad reports ready for display and gets the adapter as its callback`() {
        val adapter = readyAdapter()
        verify(exactly = 1) { listener.onInterstitialReadyForDisplay() }
        verify { ad.callback = adapter }
    }

    @Test
    fun `a failed load reports failed to load`() {
        adapter().loadAd(config(rewarded = false), fakeBidResponse())
        factory.completeFullscreen(ChartboostError.Load.NoAd)
        verify { listener.onInterstitialFailedToLoad(any()) }
        verify(exactly = 0) { listener.onInterstitialReadyForDisplay() }
    }

    @Test
    fun `a load that finishes after destroy releases the ad and reports nothing`() {
        val adapter = adapter().also { it.loadAd(config(false), fakeBidResponse()) }
        adapter.destroy()
        factory.completeFullscreen()
        verify(exactly = 0) { listener.onInterstitialReadyForDisplay() }
        verify(exactly = 1) { ad.destroy() }
    }

    @Test
    fun `a load that fails after destroy reports nothing`() {
        val adapter = adapter().also { it.loadAd(config(false), fakeBidResponse()) }
        adapter.destroy()
        factory.completeFullscreen(ChartboostError.Load.NoAd)
        verify(exactly = 0) { listener.onInterstitialFailedToLoad(any()) }
    }

    @Test
    fun `loadAd after destroy does not load an ad`() {
        // A late loadAd on a controller Prebid already discarded must be a no-op rather than resurrect it.
        val adapter = adapter().also { it.destroy() }
        adapter.loadAd(config(rewarded = false), fakeBidResponse())
        assertNull(factory.lastInterstitialRequest)
        verify(exactly = 0) { listener.onInterstitialFailedToLoad(any()) }
    }

    // endregion

    // region show

    @Test
    fun `show delegates to the loaded ad`() {
        readyAdapter().show()
        verify { ad.show(any<AdShowCallback>()) }
    }

    @Test
    fun `show success reports displayed`() {
        displayedAdapter()
        verify(exactly = 1) { listener.onInterstitialDisplayed() }
    }

    @Test
    fun `a show success delivered twice reports displayed once`() {
        val adapter = readyAdapter()
        adapter.show()
        factory.completeShow()
        factory.completeShow()
        verify(exactly = 1) { listener.onInterstitialDisplayed() }
    }

    @Test
    fun `show failure does not report displayed`() {
        readyAdapter().show()
        factory.completeShow(usedUpShowError())
        verify(exactly = 0) { listener.onInterstitialDisplayed() }
    }

    @Test
    fun `a show failure that uses up the ad reports failed to load so the ad unit can reload`() {
        readyAdapter().show()
        factory.completeShow(usedUpShowError())
        verify(exactly = 1) { listener.onInterstitialFailedToLoad(any()) }
        verify(exactly = 1) { ad.destroy() }
    }

    @Test
    fun `a rewarded show failure that uses up the ad reports failed to load so the ad unit can reload`() {
        readyAdapter(rewarded = true).show()
        factory.completeShow(usedUpShowError())
        verify(exactly = 1) { listener.onInterstitialFailedToLoad(any()) }
    }

    @Test
    fun `a show failure that uses up the ad notifies the event listener of failure`() {
        val events = mockk<ChartboostPrebidEventListener>(relaxed = true)
        readyAdapter(eventListener = events).show()
        factory.completeShow(usedUpShowError())
        verify { events.onAdFailed(ChartboostAdFormat.INTERSTITIAL, any()) }
    }

    @Test
    fun `a show failure that keeps the ad loaded reports nothing so show can be retried`() {
        val adapter = readyAdapter()
        adapter.show()
        factory.completeShow(ChartboostError.Show.NoContext)
        adapter.show()
        verify(exactly = 0) { listener.onInterstitialFailedToLoad(any()) }
        verify(exactly = 0) { ad.destroy() }
        assertEquals(2, showCount())
    }

    @Test
    fun `a second show while the first is opening does not reach the SDK`() {
        val adapter = readyAdapter()
        adapter.show()
        adapter.show()
        assertEquals(1, showCount())
    }

    @Test
    fun `a second show while the ad is on screen does not reach the SDK`() {
        displayedAdapter().show()
        assertEquals(1, showCount())
    }

    @Test
    fun `a show already on screen reported by the SDK keeps the ad`() {
        readyAdapter().show()
        factory.completeShow(ChartboostError.Show.FullscreenAlreadyShowing)
        verify(exactly = 0) { listener.onInterstitialFailedToLoad(any()) }
        verify(exactly = 0) { ad.destroy() }
    }

    @Test
    fun `a show the SDK refuses as not started keeps the ad so show can be retried`() {
        val adapter = readyAdapter()
        adapter.show()
        factory.completeShow(ChartboostError.Show.NotInitialized)
        adapter.show()
        verify(exactly = 0) { listener.onInterstitialFailedToLoad(any()) }
        verify(exactly = 0) { ad.destroy() }
        assertEquals(2, showCount())
    }

    @Test
    fun `a show refused because Chartboost turned the app off uses up the ad`() {
        // The ad could not show until it expired, so the ad unit is reset and the publisher can move on.
        readyAdapter().show()
        factory.completeShow(ChartboostError.Show.Disabled)
        verify(exactly = 1) { listener.onInterstitialFailedToLoad(any()) }
        verify(exactly = 1) { ad.destroy() }
    }

    @Test
    fun `a failure result after display reports nothing`() {
        displayedAdapter()
        factory.completeShow(usedUpShowError())
        verify(exactly = 0) { listener.onInterstitialFailedToLoad(any()) }
    }

    @Test
    fun `a close handled before the show result still reports displayed, before the close`() {
        // Prebid leaves the ad unit ready, unable to show or load, until it hears displayed.
        val adapter = readyAdapter()
        adapter.show()
        adapter.onAdClosed(ad, null)
        factory.completeShow()
        verify(exactly = 1) { listener.onInterstitialDisplayed() }
        verifyOrder {
            listener.onInterstitialDisplayed()
            listener.onInterstitialClosed()
        }
    }

    @Test
    fun `show after a show failure that used up the ad does not reach the SDK`() {
        val adapter = readyAdapter()
        adapter.show()
        factory.completeShow(usedUpShowError())
        adapter.show()
        assertEquals(1, showCount())
    }

    @Test
    fun `show with no loaded ad warns and shows nothing`() {
        mockkStatic(Log::class)
        every { Log.w(PluginLog.TAG, any<String>()) } returns 0

        adapter().show()

        assertEquals(0, showCount())
        verify { Log.w(PluginLog.TAG, match<String> { "show() called with no loaded ad" in it }) }
    }

    @Test
    fun `show after destroy warns and shows nothing`() {
        val adapter = readyAdapter()
        adapter.destroy()
        mockkStatic(Log::class)
        every { Log.w(PluginLog.TAG, any<String>()) } returns 0

        adapter.show()

        assertEquals(0, showCount())
        verify { Log.w(PluginLog.TAG, match<String> { "show() called with no loaded ad" in it }) }
    }

    // endregion

    // region destroy

    @Test
    fun `destroy releases a loaded ad that is not showing`() {
        readyAdapter().destroy()
        verify(exactly = 1) { ad.destroy() }
    }

    @Test
    fun `destroy while the ad is on screen waits for the close to release it`() {
        // FullscreenAd.destroy() tears down an ad on screen, which would cut the user's ad short.
        val adapter = displayedAdapter()
        adapter.destroy()
        verify(exactly = 0) { ad.destroy() }
        adapter.onAdClosed(ad, null)
        verify(exactly = 1) { ad.destroy() }
        verify(exactly = 1) { listener.onInterstitialClosed() }
    }

    @Test
    fun `destroy while a show is opening waits for its result`() {
        val adapter = readyAdapter()
        adapter.show()
        adapter.destroy()
        verify(exactly = 0) { ad.destroy() }
    }

    @Test
    fun `a show failure after destroy reports nothing and releases the ad`() {
        val adapter = readyAdapter()
        adapter.show()
        adapter.destroy()
        factory.completeShow(usedUpShowError())
        verify(exactly = 0) { listener.onInterstitialFailedToLoad(any()) }
        verify(exactly = 1) { ad.destroy() }
    }

    @Test
    fun `a show rejected before it starts releases an ad destroyed while it was opening`() {
        val adapter = readyAdapter()
        adapter.show()
        adapter.destroy()
        factory.completeShow(ChartboostError.Show.NoContext)
        verify(exactly = 1) { ad.destroy() }
    }

    @Test
    fun `a close after a reported show failure does not report displayed`() {
        // The SDK can time a show out in the same turn the user closes a real impression.
        val adapter = readyAdapter()
        adapter.show()
        factory.completeShow(ChartboostError.Show.TimedOut("show timed out"))
        adapter.onAdClosed(ad, null)
        verify(exactly = 1) { listener.onInterstitialFailedToLoad(any()) }
        verify(exactly = 0) { listener.onInterstitialDisplayed() }
    }

    @Test
    fun `a second show then destroy keeps the opening ad until it closes`() {
        val adapter = readyAdapter()
        adapter.show()
        adapter.show()
        adapter.destroy()
        verify(exactly = 0) { ad.destroy() }
        adapter.onAdClosed(ad, null)
        verify(exactly = 1) { ad.destroy() }
    }

    @Test
    fun `destroy after a show the SDK refused releases the ad at once`() {
        val adapter = readyAdapter()
        adapter.show()
        factory.completeShow(ChartboostError.Show.NoContext)
        adapter.destroy()
        verify(exactly = 1) { ad.destroy() }
    }

    @Test
    fun `a second destroy does nothing`() {
        val adapter = readyAdapter()
        adapter.destroy()
        adapter.destroy()
        verify(exactly = 1) { ad.destroy() }
    }

    @Test
    fun `a reward earned after destroy still reaches the listener`() {
        // The ad on screen outlives destroy(), so a reward the user genuinely earns after the host called it
        // (e.g. from its Activity onDestroy) must still be granted.
        val events = mockk<ChartboostPrebidEventListener>(relaxed = true)
        val adapter = readyAdapter(eventListener = events, rewarded = true)
        adapter.show()
        adapter.destroy()
        adapter.onRewardEarned(ad)
        verify(exactly = 1) { listener.onUserEarnedReward() }
        verify(exactly = 1) { events.onUserEarnedReward(ChartboostAdFormat.REWARDED) }
    }

    @Test
    fun `a close after destroy still reaches the listener`() {
        val adapter = displayedAdapter()
        adapter.destroy()
        adapter.onAdClosed(ad, null)
        verify(exactly = 1) { listener.onInterstitialClosed() }
    }

    // endregion

    // region engagement

    @Test
    fun `click is forwarded`() {
        readyAdapter().onAdClicked(ad)
        verify { listener.onInterstitialClicked() }
    }

    @Test
    fun `close is forwarded and releases the ad`() {
        displayedAdapter().onAdClosed(ad, null)
        verify { listener.onInterstitialClosed() }
        verify(exactly = 1) { ad.destroy() }
    }

    @Test
    fun `show after the ad closed does not reach the SDK`() {
        val adapter = displayedAdapter()
        adapter.onAdClosed(ad, null)
        adapter.show()
        assertEquals(1, showCount())
    }

    @Test
    fun `reward is forwarded as user earned reward`() {
        readyAdapter(rewarded = true).onRewardEarned(ad)
        verify { listener.onUserEarnedReward() }
    }

    @Test
    fun `an impression reaches no Prebid listener`() {
        readyAdapter().onImpressionRecorded(ad)
        verify(exactly = 0) { listener.onInterstitialDisplayed() }
    }

    // endregion

    // region reward

    @Test
    fun `a rewarded ad reports the Prebid Server reward of the bid to Prebid`() {
        val reward = fakeReward()
        val config = rewardedConfig()
        val seen = rewardsSeenByPrebid(config) { it.onUserEarnedReward() }
        rewardedAdapter(config, reward).onRewardEarned(ad)
        assertEquals(listOf<Reward?>(reward), seen)
    }

    @Test
    fun `a rewarded bid without a Prebid Server reward reports no reward`() {
        val config = rewardedConfig()
        // A reward left in the manager must not leak into an ad whose bid carries none.
        config.rewardManager.rewardedExt = fakeRewardedExt(fakeReward(type = LEFTOVER_REWARD_TYPE))
        val seen = rewardsSeenByPrebid(config) { it.onUserEarnedReward() }
        rewardedAdapter(config, reward = null).onRewardEarned(ad)
        assertEquals(listOf<Reward?>(null), seen)
    }

    @Test
    fun `a rewarded ad closed before the SDK reward still reports the Prebid Server reward`() {
        // Prebid grants the reward on close as well, and reads the RewardManager at that moment.
        val reward = fakeReward()
        val config = rewardedConfig()
        val seen = rewardsSeenByPrebid(config) { it.onInterstitialClosed() }
        rewardedAdapter(config, reward).onAdClosed(ad, null)
        assertEquals(listOf<Reward?>(reward), seen)
    }

    @Test
    fun `a load on the ad unit while the ad shows still reports the Prebid Server reward of the shown ad`() {
        val reward = fakeReward()
        val config = rewardedConfig()
        val seen = rewardsSeenByPrebid(config) { it.onUserEarnedReward() }
        val adapter = rewardedAdapter(config, reward)
        loadOnTheAdUnit(config)
        adapter.onRewardEarned(ad)
        assertEquals(listOf<Reward?>(reward), seen)
    }

    @Test
    fun `a rewarded ad closed before the SDK reward and after a load on the ad unit still reports the Prebid Server reward`() {
        val reward = fakeReward()
        val config = rewardedConfig()
        val seen = rewardsSeenByPrebid(config) { it.onInterstitialClosed() }
        val adapter = rewardedAdapter(config, reward)
        loadOnTheAdUnit(config)
        adapter.onAdClosed(ad, null)
        assertEquals(listOf<Reward?>(reward), seen)
    }

    @Test
    fun `a rewarded ad closed after the SDK reward and a load on the ad unit does not get the Prebid Server reward back`() {
        // The load lets Prebid grant once more on close. With the bid's reward back in, it would be credited twice.
        val config = rewardedConfig()
        val seen = rewardsSeenByPrebid(config) { it.onInterstitialClosed() }
        val adapter = rewardedAdapter(config)
        adapter.onRewardEarned(ad)
        loadOnTheAdUnit(config)
        adapter.onAdClosed(ad, null)
        assertEquals(listOf<Reward?>(null), seen)
    }

    @Test
    fun `granting the reward keeps the reward a newer load put on the ad unit`() {
        val config = rewardedConfig()
        val adapter = rewardedAdapter(config)
        val newer = fakeRewardedExt(fakeReward(type = NEWER_REWARD_TYPE))
        config.rewardManager.rewardedExt = newer
        adapter.onRewardEarned(ad)
        assertSame(newer, config.rewardManager.rewardedExt)
    }

    @Test
    fun `a load from inside the reward callback is not undone`() {
        val config = rewardedConfig()
        every { listener.onUserEarnedReward() } answers { loadOnTheAdUnit(config) }
        rewardedAdapter(config).onRewardEarned(ad)
        assertNull(config.rewardManager.rewardedExt.reward)
    }

    // endregion

    // region expiry

    @Test
    fun `expiry after ready reports failed to load so the ad unit can reload`() {
        readyAdapter().onAdExpired(ad)
        verify(exactly = 1) { listener.onInterstitialFailedToLoad(any()) }
    }

    @Test
    fun `expiry after ready notifies the event listener with the ad's format`() {
        val events = mockk<ChartboostPrebidEventListener>(relaxed = true)
        readyAdapter(eventListener = events, rewarded = true).onAdExpired(ad)
        verify { events.onAdFailed(ChartboostAdFormat.REWARDED, any()) }
    }

    @Test
    fun `expiry after ready releases the ad`() {
        readyAdapter().onAdExpired(ad)
        verify(exactly = 1) { ad.destroy() }
    }

    @Test
    fun `show after expiry does not reach the SDK`() {
        val adapter = readyAdapter()
        adapter.onAdExpired(ad)
        adapter.show()
        assertEquals(0, showCount())
    }

    @Test
    fun `a second expiry after ready reports failed to load only once`() {
        val adapter = readyAdapter()
        adapter.onAdExpired(ad)
        adapter.onAdExpired(ad)
        verify(exactly = 1) { listener.onInterstitialFailedToLoad(any()) }
    }

    @Test
    fun `expiry after destroy reports nothing`() {
        val adapter = readyAdapter()
        adapter.destroy()
        adapter.onAdExpired(ad)
        verify(exactly = 0) { listener.onInterstitialFailedToLoad(any()) }
    }

    // endregion

    // region event listener

    @Test
    fun `event listener is notified of load with the interstitial format`() {
        val events = mockk<ChartboostPrebidEventListener>(relaxed = true)
        readyAdapter(eventListener = events)
        verify { events.onAdLoaded(ChartboostAdFormat.INTERSTITIAL) }
    }

    @Test
    fun `event listener reports the rewarded format on reward`() {
        val events = mockk<ChartboostPrebidEventListener>(relaxed = true)
        readyAdapter(eventListener = events, rewarded = true).onRewardEarned(ad)
        verify { events.onUserEarnedReward(ChartboostAdFormat.REWARDED) }
    }

    @Test
    fun `event listener is notified of display on show success`() {
        val events = mockk<ChartboostPrebidEventListener>(relaxed = true)
        displayedAdapter(eventListener = events)
        verify { events.onAdDisplayed(ChartboostAdFormat.INTERSTITIAL) }
    }

    @Test
    fun `event listener is notified of click`() {
        val events = mockk<ChartboostPrebidEventListener>(relaxed = true)
        readyAdapter(eventListener = events).onAdClicked(ad)
        verify { events.onAdClicked(ChartboostAdFormat.INTERSTITIAL) }
    }

    @Test
    fun `event listener is notified of dismissal`() {
        val events = mockk<ChartboostPrebidEventListener>(relaxed = true)
        displayedAdapter(eventListener = events).onAdClosed(ad, null)
        verify { events.onAdDismissed(ChartboostAdFormat.INTERSTITIAL) }
    }

    @Test
    fun `event listener is notified of failure when the markup is empty`() {
        val events = mockk<ChartboostPrebidEventListener>(relaxed = true)
        adapter(eventListener = events).loadAd(config(false), fakeBidResponse(fakeBid(adm = null)))
        verify { events.onAdFailed(eq(ChartboostAdFormat.INTERSTITIAL), any()) }
    }

    // endregion
}
