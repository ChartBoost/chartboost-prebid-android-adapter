/*
 * Copyright (c) 2026 Chartboost, Inc.
 *
 * Licensed under the MIT License.
 */

package com.chartboost.prebid

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.chartboost.prebid.fakes.FakeChartboostAdFactory
import com.chartboost.prebid.fakes.fakeBid
import com.chartboost.prebid.fakes.fakeBidResponse
import com.chartboost.prebid.internal.PREBID_LOCATION
import com.chartboost.sdk.ads.Banner
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.prebid.mobile.AdSize
import org.prebid.mobile.api.data.AdFormat
import org.prebid.mobile.configuration.AdUnitConfiguration
import org.prebid.mobile.rendering.bidding.listeners.DisplayViewListener
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ChartboostPrebidPluginAdapterTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val adapter = ChartboostPrebidPluginAdapter(FakeChartboostAdFactory())

    private fun adapterWithToken(token: String?) =
        ChartboostPrebidPluginAdapter(FakeChartboostAdFactory(), bidderToken = { token })

    @Test
    fun `reports the platform-specific adapter name`() {
        assertEquals("Chartboost-Android-SDK", adapter.getName())
    }

    @Test
    fun `reports the configured adapter version`() {
        assertEquals(BuildConfig.ADAPTER_VERSION, adapter.getVersion())
    }

    @Test
    fun `data carries the fresh bidder token`() {
        assertEquals("token-123", adapterWithToken("token-123").getData()?.getString("bidderToken"))
    }

    @Test
    fun `data omits the token and does not throw when it is unavailable`() {
        assertFalse(adapterWithToken(null).getData()?.has("bidderToken") ?: true)
    }

    @Test
    fun `supports banner rendering`() {
        val config = mockk<AdUnitConfiguration>(relaxed = true)
        every { config.isAdType(AdFormat.BANNER) } returns true
        assertTrue(adapter.isSupportRenderingFor(config))
    }

    @Test
    fun `supports interstitial rendering`() {
        val config = mockk<AdUnitConfiguration>(relaxed = true)
        every { config.isAdType(AdFormat.INTERSTITIAL) } returns true
        assertTrue(adapter.isSupportRenderingFor(config))
    }

    @Test
    fun `supports VAST rendering`() {
        val config = mockk<AdUnitConfiguration>(relaxed = true)
        every { config.isAdType(AdFormat.VAST) } returns true
        assertTrue(adapter.isSupportRenderingFor(config))
    }

    @Test
    fun `does not support native rendering`() {
        val config = mockk<AdUnitConfiguration>(relaxed = true) // every isAdType defaults to false
        assertFalse(adapter.isSupportRenderingFor(config))
    }

    @Test
    fun `createBannerAdView never returns null even when the markup is empty`() {
        val view = adapter.createBannerAdView(
            context,
            mockk<DisplayViewListener>(relaxed = true),
            null,
            mockk(relaxed = true),
            fakeBidResponse(fakeBid(adm = null)),
        )
        assertNotNull(view)
    }

    @Test
    fun `registering an event listener is a no-op and does not throw`() {
        adapter.registerEventListener(mockk(relaxed = true), "key")
        adapter.unregisterEventListener("key")
    }

    @Test
    fun `banner adapter receives the configured location`() {
        val factory = FakeChartboostAdFactory()
        val r = ChartboostPrebidPluginAdapter(
            factory,
            config = ChartboostPrebidConfig(location = "MyPlacement"),
        )
        r.createBannerAdView(
            context,
            mockk<DisplayViewListener>(relaxed = true),
            null,
            mockk(relaxed = true),
            fakeBidResponse(fakeBid(adm = "<adm>", width = 320, height = 50)),
        )
        assertEquals("MyPlacement", factory.lastLocation)
    }

    @Test
    fun `banner adapter receives the default location when config uses the default`() {
        val factory = FakeChartboostAdFactory()
        val r = ChartboostPrebidPluginAdapter(factory)
        r.createBannerAdView(
            context,
            mockk<DisplayViewListener>(relaxed = true),
            null,
            mockk(relaxed = true),
            fakeBidResponse(fakeBid(adm = "<adm>", width = 320, height = 50)),
        )
        assertEquals(PREBID_LOCATION, factory.lastLocation)
    }

    @Test
    fun `banner adapter receives the default location when config location is blank`() {
        val factory = FakeChartboostAdFactory()
        val r = ChartboostPrebidPluginAdapter(
            factory,
            config = ChartboostPrebidConfig(location = "  "),
        )
        r.createBannerAdView(
            context,
            mockk<DisplayViewListener>(relaxed = true),
            null,
            mockk(relaxed = true),
            fakeBidResponse(fakeBid(adm = "<adm>", width = 320, height = 50)),
        )
        assertEquals(PREBID_LOCATION, factory.lastLocation)
    }

    @Test
    fun `banner adapter falls back to the ad unit's configured size when the bid names none`() {
        val factory = FakeChartboostAdFactory()
        val r = ChartboostPrebidPluginAdapter(factory)
        val adConfig = mockk<AdUnitConfiguration>(relaxed = true)
        every { adConfig.sizes } returns hashSetOf(AdSize(300, 250))
        every { adConfig.bannerParameters } returns null
        r.createBannerAdView(
            context,
            mockk<DisplayViewListener>(relaxed = true),
            null,
            adConfig,
            fakeBidResponse(fakeBid(adm = "<adm>", width = 0, height = 0)),
        )
        assertEquals(Banner.BannerSize.MEDIUM, factory.lastBannerSize)
    }

    @Test
    fun `banner adapter never consults the ad unit size when the bid already has a usable one`() {
        val factory = FakeChartboostAdFactory()
        val r = ChartboostPrebidPluginAdapter(factory)
        val adConfig = mockk<AdUnitConfiguration>(relaxed = true)
        every { adConfig.sizes } returns hashSetOf(AdSize(300, 250))
        r.createBannerAdView(
            context,
            mockk<DisplayViewListener>(relaxed = true),
            null,
            adConfig,
            fakeBidResponse(fakeBid(adm = "<adm>", width = 320, height = 50)),
        )
        assertEquals(Banner.BannerSize.STANDARD, factory.lastBannerSize)
        verify(exactly = 0) { adConfig.sizes }
        verify(exactly = 0) { adConfig.bannerParameters }
    }

    @Test
    fun `fullscreen adapter receives the configured location`() {
        val factory = FakeChartboostAdFactory()
        val r = ChartboostPrebidPluginAdapter(
            factory,
            config = ChartboostPrebidConfig(location = "FullscreenPlacement"),
        )
        val adConfig = mockk<AdUnitConfiguration>(relaxed = true)
        every { adConfig.isRewarded } returns false
        r.createInterstitialController(context, mockk(relaxed = true), adConfig, fakeBidResponse())
            .loadAd(adConfig, fakeBidResponse())
        assertEquals("FullscreenPlacement", factory.lastLocation)
    }

    @Test
    fun `fullscreen adapter receives the default location when config uses the default`() {
        val factory = FakeChartboostAdFactory()
        val r = ChartboostPrebidPluginAdapter(factory)
        val adConfig = mockk<AdUnitConfiguration>(relaxed = true)
        every { adConfig.isRewarded } returns false
        r.createInterstitialController(context, mockk(relaxed = true), adConfig, fakeBidResponse())
            .loadAd(adConfig, fakeBidResponse())
        assertEquals(PREBID_LOCATION, factory.lastLocation)
    }
}
