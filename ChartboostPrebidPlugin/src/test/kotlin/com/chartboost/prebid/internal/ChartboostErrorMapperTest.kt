/*
 * Copyright (c) 2026 Chartboost, Inc.
 *
 * Licensed under the MIT License.
 */

package com.chartboost.prebid.internal

import com.chartboost.prebid.fakes.cacheError
import com.chartboost.prebid.fakes.showError
import com.chartboost.sdk.events.CacheError
import com.chartboost.sdk.events.ExpirationEvent
import com.chartboost.sdk.events.ShowError
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartboostErrorMapperTest {

    @Test
    fun `maps INVALID_ADM cache code surfacing the raw code in the message`() {
        val message = ChartboostErrorMapper.map(cacheError(CacheError.Code.INVALID_ADM)).message ?: ""
        assertTrue(message, message.contains("INVALID_ADM") && message.contains("46"))
    }

    @Test
    fun `maps NO_AD_FOUND cache code to a no-fill message`() {
        val message = ChartboostErrorMapper.map(cacheError(CacheError.Code.NO_AD_FOUND)).message ?: ""
        assertTrue(message, message.contains("No fill"))
    }

    @Test
    fun `maps an unhandled cache code without losing the raw code`() {
        val message = ChartboostErrorMapper.map(cacheError(CacheError.Code.WEBVIEW_CRASHED)).message ?: ""
        assertTrue(message, message.contains("WEBVIEW_CRASHED") && message.contains("51"))
    }

    @Test
    fun `reports invalid adm with a descriptive message`() {
        val message = ChartboostErrorMapper.admInvalid().message ?: ""
        assertTrue(message, message.contains("ADM"))
    }

    @Test
    fun `maps an expired ad to a descriptive message`() {
        // ExpirationReason lives in the SDK's internal package and cannot be named here; a relaxed mock
        // supplies the reason the message interpolates.
        val message = ChartboostErrorMapper.adExpired(mockk<ExpirationEvent>(relaxed = true)).message ?: ""
        assertTrue(message, message.contains("expired"))
    }

    @Test
    fun `maps SESSION_NOT_STARTED show code to a friendly prefix with raw code preserved`() {
        val message = ChartboostErrorMapper.mapShow(showError(ShowError.Code.SESSION_NOT_STARTED))
        assertTrue(message, message.contains("Chartboost SDK not started") && message.contains("SESSION_NOT_STARTED"))
    }

    @Test
    fun `maps an unhandled show code without losing the raw code`() {
        val message = ChartboostErrorMapper.mapShow(showError(ShowError.Code.DISABLED))
        assertTrue(message, message.contains(ShowError.Code.DISABLED.name) && message.contains(ShowError.Code.DISABLED.errorCode.toString()))
    }
}
