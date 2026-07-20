/*
 * Copyright (c) 2026 Chartboost, Inc.
 *
 * Licensed under the MIT License.
 */

package com.chartboost.prebid

import com.chartboost.prebid.internal.PluginLog
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.prebid.mobile.api.rendering.pluginrenderer.PrebidMobilePluginRegister
import org.prebid.mobile.api.rendering.pluginrenderer.PrebidMobilePluginRenderer
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ChartboostPrebidAdapterTest {

    @After
    fun unmockRegister() {
        unmockkStatic(PrebidMobilePluginRegister::class)
    }

    @After
    fun resetLogLevel() {
        PluginLog.level = LogLevel.WARN
    }

    @Test
    fun `adapterName exposes the name the client sends to Prebid Server`() {
        assertEquals("Chartboost-Android-SDK", ChartboostPrebidAdapter.adapterName)
    }

    @Test
    fun `adapterVersion exposes the version the client sends to Prebid Server`() {
        assertEquals(BuildConfig.ADAPTER_VERSION, ChartboostPrebidAdapter.adapterVersion)
    }

    @Test
    fun `matchesServerAdapterVersion is true when the server stamp equals the client version`() {
        assertTrue(ChartboostPrebidAdapter.matchesServerAdapterVersion(BuildConfig.ADAPTER_VERSION))
    }

    @Test
    fun `matchesServerAdapterVersion is false on any mismatch including trailing whitespace`() {
        assertFalse(ChartboostPrebidAdapter.matchesServerAdapterVersion("${BuildConfig.ADAPTER_VERSION} "))
    }

    @Test
    fun `register registers the adapter with Prebid Mobile`() {
        mockkStatic(PrebidMobilePluginRegister::class)
        val registry = mockk<PrebidMobilePluginRegister>(relaxed = true)
        every { PrebidMobilePluginRegister.getInstance() } returns registry

        ChartboostPrebidAdapter.register()

        verify { registry.registerPlugin(any()) }
    }

    @Test
    fun `register plumbs the supplied config's location into the registered adapter`() {
        mockkStatic(PrebidMobilePluginRegister::class)
        val registry = mockk<PrebidMobilePluginRegister>(relaxed = true)
        every { PrebidMobilePluginRegister.getInstance() } returns registry
        val registered = slot<PrebidMobilePluginRenderer>()
        every { registry.registerPlugin(capture(registered)) } just Runs

        ChartboostPrebidAdapter.register(ChartboostPrebidConfig(location = "MyPlacement"))

        assertEquals("MyPlacement", (registered.captured as ChartboostPrebidPluginAdapter).resolvedLocation())
    }

    @Test
    fun `unregister removes what register added`() {
        // Exercise Prebid's real registry (a process-global name-keyed map) so the test proves the actual
        // round-trip invariant: unregister evicts the same entry register inserted, not just that some
        // plugin carries the Chartboost name. The mid-assert guards against register silently no-op-ing,
        // which would make the final assert vacuously pass.
        val registry = PrebidMobilePluginRegister.getInstance()
        ChartboostPrebidAdapter.register()
        assertTrue(registry.containsPlugin(ChartboostPrebidPluginAdapter.NAME))

        ChartboostPrebidAdapter.unregister()

        assertFalse(registry.containsPlugin(ChartboostPrebidPluginAdapter.NAME))
    }
}
