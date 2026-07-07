/*
 * Copyright 2026 Chartboost, Inc.
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE file.
 */

package com.chartboost.prebid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.prebid.mobile.api.exceptions.AdException

class ChartboostPrebidConfigTest {

    @Test
    fun `default config has notifications disabled`() {
        assertFalse(ChartboostPrebidConfig().notificationsEnabled)
    }

    @Test
    fun `default config has location set to Prebid`() {
        assertEquals("Prebid", ChartboostPrebidConfig().location)
    }

    @Test
    fun `default config has log level WARN`() {
        assertEquals(LogLevel.WARN, ChartboostPrebidConfig().logLevel)
    }

    @Test
    fun `default config has burl disabled`() {
        assertFalse(ChartboostPrebidConfig().burlEnabled)
    }

    @Test
    fun `default config has no event listener`() {
        assertNull(ChartboostPrebidConfig().eventListener)
    }

    @Test
    fun `default config has no notification result listener`() {
        assertNull(ChartboostPrebidConfig().notificationResultListener)
    }

    @Test
    fun `builder with no setters matches the default config`() {
        assertEquals(ChartboostPrebidConfig(), ChartboostPrebidConfig.Builder().build())
    }

    @Test
    fun `builder applies each field`() {
        val config = ChartboostPrebidConfig.Builder()
            .setNotificationsEnabled(true)
            .setLocation("MyPlacement")
            .setLogLevel(LogLevel.DEBUG)
            .setBurlEnabled(true)
            .build()

        assertEquals(
            ChartboostPrebidConfig(
                notificationsEnabled = true,
                location = "MyPlacement",
                logLevel = LogLevel.DEBUG,
                burlEnabled = true,
            ),
            config,
        )
    }

    @Test
    fun `builder applies the listener fields`() {
        val eventListener = object : ChartboostPrebidEventListener {
            override fun onAdLoaded(format: ChartboostAdFormat) {}
            override fun onAdDisplayed(format: ChartboostAdFormat) {}
            override fun onAdClicked(format: ChartboostAdFormat) {}
            override fun onAdFailed(format: ChartboostAdFormat, error: AdException) {}
            override fun onAdDismissed(format: ChartboostAdFormat) {}
            override fun onUserEarnedReward(format: ChartboostAdFormat) {}
        }
        val resultListener = NotificationResultListener { _, _ -> }

        val config = ChartboostPrebidConfig.Builder()
            .setEventListener(eventListener)
            .setNotificationResultListener(resultListener)
            .build()

        assertSame(eventListener, config.eventListener)
        assertSame(resultListener, config.notificationResultListener)
    }
}
