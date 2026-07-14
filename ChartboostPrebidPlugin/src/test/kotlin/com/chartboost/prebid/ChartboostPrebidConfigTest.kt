/*
 * Copyright (c) 2026 Chartboost, Inc.
 *
 * Licensed under the MIT License.
 */

package com.chartboost.prebid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.prebid.mobile.api.exceptions.AdException

class ChartboostPrebidConfigTest {

    @Test
    fun `default config has location set to Prebid`() {
        assertEquals("Prebid", ChartboostPrebidConfig().location)
    }

    @Test
    fun `default config has log level WARN`() {
        assertEquals(LogLevel.WARN, ChartboostPrebidConfig().logLevel)
    }

    @Test
    fun `default config has no event listener`() {
        assertNull(ChartboostPrebidConfig().eventListener)
    }

    @Test
    fun `builder with no setters matches the default config`() {
        assertEquals(ChartboostPrebidConfig(), ChartboostPrebidConfig.Builder().build())
    }

    @Test
    fun `builder applies each field`() {
        val eventListener = object : ChartboostPrebidEventListener {
            override fun onAdLoaded(format: ChartboostAdFormat) {}
            override fun onAdDisplayed(format: ChartboostAdFormat) {}
            override fun onAdClicked(format: ChartboostAdFormat) {}
            override fun onAdFailed(format: ChartboostAdFormat, error: AdException) {}
            override fun onAdDismissed(format: ChartboostAdFormat) {}
            override fun onUserEarnedReward(format: ChartboostAdFormat) {}
        }

        val config = ChartboostPrebidConfig.Builder()
            .setLocation("MyPlacement")
            .setLogLevel(LogLevel.DEBUG)
            .setEventListener(eventListener)
            .build()

        assertEquals("MyPlacement", config.location)
        assertEquals(LogLevel.DEBUG, config.logLevel)
        assertSame(eventListener, config.eventListener)
    }
}
