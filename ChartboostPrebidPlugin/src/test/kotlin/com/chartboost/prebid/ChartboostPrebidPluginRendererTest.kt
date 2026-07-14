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
import com.chartboost.prebid.internal.PluginLog
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.prebid.mobile.api.data.AdFormat
import org.prebid.mobile.configuration.AdUnitConfiguration
import org.prebid.mobile.rendering.bidding.listeners.DisplayViewListener
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ChartboostPrebidPluginRendererTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val renderer = ChartboostPrebidPluginRenderer(FakeChartboostAdFactory())

    private fun rendererWithToken(token: String?) =
        ChartboostPrebidPluginRenderer(FakeChartboostAdFactory(), bidderToken = { token })

    @Test
    fun `reports the platform-specific renderer name`() {
        assertEquals("Chartboost-Android-SDK", renderer.getName())
    }

    @Test
    fun `reports the configured renderer version`() {
        assertEquals(BuildConfig.RENDERER_VERSION, renderer.getVersion())
    }

    @Test
    fun `data carries the fresh bidder token`() {
        assertEquals("token-123", rendererWithToken("token-123").getData()?.getString("bidderToken"))
    }

    @Test
    fun `data omits the token and does not throw when it is unavailable`() {
        assertFalse(rendererWithToken(null).getData()?.has("bidderToken") ?: true)
    }

    @Test
    fun `supports banner rendering`() {
        val config = mockk<AdUnitConfiguration>(relaxed = true)
        every { config.isAdType(AdFormat.BANNER) } returns true
        assertTrue(renderer.isSupportRenderingFor(config))
    }

    @Test
    fun `does not support native rendering`() {
        val config = mockk<AdUnitConfiguration>(relaxed = true) // every isAdType defaults to false
        assertFalse(renderer.isSupportRenderingFor(config))
    }

    @Test
    fun `createBannerAdView never returns null even when the markup is empty`() {
        val view = renderer.createBannerAdView(
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
        renderer.registerEventListener(mockk(relaxed = true), "key")
        renderer.unregisterEventListener("key")
    }

    @After
    fun resetLogLevel() {
        PluginLog.level = LogLevel.WARN
    }

    @Test
    fun `DEBUG config sets PluginLog level to DEBUG`() {
        ChartboostPrebidPluginRenderer(ChartboostPrebidConfig(logLevel = LogLevel.DEBUG))
        assertEquals(LogLevel.DEBUG, PluginLog.level)
    }

    @Test
    fun `default config leaves PluginLog level at WARN`() {
        ChartboostPrebidPluginRenderer(ChartboostPrebidConfig())
        assertEquals(LogLevel.WARN, PluginLog.level)
    }

    @Test
    fun `banner adapter receives the configured location`() {
        val factory = FakeChartboostAdFactory()
        val r = ChartboostPrebidPluginRenderer(
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
        val r = ChartboostPrebidPluginRenderer(factory)
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
    fun `fullscreen adapter receives the configured location`() {
        val factory = FakeChartboostAdFactory()
        val r = ChartboostPrebidPluginRenderer(
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
        val r = ChartboostPrebidPluginRenderer(factory)
        val adConfig = mockk<AdUnitConfiguration>(relaxed = true)
        every { adConfig.isRewarded } returns false
        r.createInterstitialController(context, mockk(relaxed = true), adConfig, fakeBidResponse())
            .loadAd(adConfig, fakeBidResponse())
        assertEquals(PREBID_LOCATION, factory.lastLocation)
    }
}
