package com.chartboost.prebid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ChartboostPrebidRendererTest {

    @Test
    fun `rendererName exposes the name the client sends to Prebid Server`() {
        assertEquals("Chartboost-Android-SDK", ChartboostPrebidRenderer.rendererName)
    }

    @Test
    fun `rendererVersion exposes the version the client sends to Prebid Server`() {
        assertEquals(BuildConfig.RENDERER_VERSION, ChartboostPrebidRenderer.rendererVersion)
    }

    @Test
    fun `matchesServerRendererVersion is true when the server stamp equals the client version`() {
        assertTrue(ChartboostPrebidRenderer.matchesServerRendererVersion(BuildConfig.RENDERER_VERSION))
    }

    @Test
    fun `matchesServerRendererVersion is false on any mismatch including trailing whitespace`() {
        assertFalse(ChartboostPrebidRenderer.matchesServerRendererVersion("${BuildConfig.RENDERER_VERSION} "))
    }
}
