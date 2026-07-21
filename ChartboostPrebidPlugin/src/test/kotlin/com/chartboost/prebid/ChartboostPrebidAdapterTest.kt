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
import org.robolectric.shadows.ShadowLog

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

    /** Replaces Prebid's process-global registry with a relaxed mock; undone by [unmockRegister]. */
    private fun mockRegistry(): PrebidMobilePluginRegister {
        mockkStatic(PrebidMobilePluginRegister::class)
        val registry = mockk<PrebidMobilePluginRegister>(relaxed = true)
        every { PrebidMobilePluginRegister.getInstance() } returns registry
        return registry
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
        val registry = mockRegistry()

        ChartboostPrebidAdapter.register()

        verify { registry.registerPlugin(any()) }
    }

    @Test
    fun `register plumbs the supplied config's location into the registered adapter`() {
        val registry = mockRegistry()
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

    @Test
    fun `register with a DEBUG config sets PluginLog level to DEBUG`() {
        mockRegistry()

        ChartboostPrebidAdapter.register(ChartboostPrebidConfig(logLevel = LogLevel.DEBUG))

        assertEquals(LogLevel.DEBUG, PluginLog.level)
    }

    @Test
    fun `register with the default config leaves PluginLog level at WARN`() {
        mockRegistry()

        ChartboostPrebidAdapter.register()

        assertEquals(LogLevel.WARN, PluginLog.level)
    }

    @Test
    fun `unregister does not reset the log level that register configured`() {
        // Regression: unregister() used to construct a throwaway ChartboostPrebidPluginAdapter whose init
        // block reset PluginLog.level to the default WARN config, clobbering whatever register() configured.
        ChartboostPrebidAdapter.register(ChartboostPrebidConfig(logLevel = LogLevel.NONE))

        ChartboostPrebidAdapter.unregister()

        assertEquals(LogLevel.NONE, PluginLog.level)
    }

    @Test
    fun `logIntegrationInfo emits an INFO line naming the adapter and its version`() {
        ChartboostPrebidAdapter.logIntegrationInfo()

        val logged = ShadowLog.getLogsForTag(PluginLog.TAG)
        assertTrue(logged.any { it.msg.contains(ChartboostPrebidAdapter.adapterName) && it.msg.contains(ChartboostPrebidAdapter.adapterVersion) })
    }
}
