/*
 * Copyright (c) 2026 Chartboost, Inc.
 *
 * Licensed under the MIT License.
 */

package com.chartboost.prebid.internal

import com.chartboost.sdk.events.ChartboostError
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartboostErrorMapperTest {

    @Test
    fun `maps an invalid markup load error surfacing the raw code in the message`() {
        val error = ChartboostError.Load.InvalidAdm("bad markup")
        val message = ChartboostErrorMapper.map(error).message ?: ""
        assertTrue(message, message.contains("Invalid ad markup") && message.contains(error.code) && message.contains(error.constant))
    }

    @Test
    fun `maps a no ad load error to a no-fill message`() {
        val message = ChartboostErrorMapper.map(ChartboostError.Load.NoAd).message ?: ""
        assertTrue(message, message.contains("No fill"))
    }

    @Test
    fun `maps an unhandled load error without losing the raw code`() {
        val error = ChartboostError.Load.NoStorage
        val message = ChartboostErrorMapper.map(error).message ?: ""
        assertTrue(message, message.contains(error.code) && message.contains(error.constant))
    }

    @Test
    fun `maps a load that reported neither an ad nor an error`() {
        val message = ChartboostErrorMapper.map(null).message ?: ""
        assertTrue(message, message.contains("no error reported"))
    }

    @Test
    fun `reports invalid adm with a descriptive message`() {
        val message = ChartboostErrorMapper.admInvalid().message ?: ""
        assertTrue(message, message.contains("ADM"))
    }

    @Test
    fun `reports an unsupported banner size with the requested dimensions`() {
        val message = ChartboostErrorMapper.unsupportedBannerSize(250, 250).message ?: ""
        assertTrue(message, message.contains("250x250"))
    }

    @Test
    fun `maps an expired ad to a descriptive message`() {
        val message = ChartboostErrorMapper.adExpired().message ?: ""
        assertTrue(message, message.contains("expired"))
    }

    @Test
    fun `maps a not started show error to a friendly prefix with the raw code preserved`() {
        val error = ChartboostError.Show.NotInitialized
        val message = ChartboostErrorMapper.mapShow(error)
        assertTrue(message, message.contains("Chartboost SDK not started") && message.contains(error.code))
    }

    @Test
    fun `maps an unhandled show error without losing the raw code`() {
        val error = ChartboostError.Show.Disabled
        val message = ChartboostErrorMapper.mapShow(error)
        assertTrue(message, message.contains(error.code) && message.contains(error.constant))
    }
}
