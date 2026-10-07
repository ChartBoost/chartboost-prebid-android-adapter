/*
 * Copyright (c) 2026 Chartboost, Inc.
 *
 * Licensed under the MIT License.
 */

package com.chartboost.prebid.internal

import com.chartboost.prebid.fakes.fakeBid
import com.chartboost.sdk.events.ImpressionEvent
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BidExtensionsTest {

    @Test
    fun `admOrNull is null when the markup is blank`() {
        assertNull(fakeBid(adm = "   ").admOrNull)
    }

    @Test
    fun `nonBlankAdId is null when the ad id is blank`() {
        val event = mockk<ImpressionEvent>(relaxed = true)
        every { event.adID } returns "   "
        assertNull(event.nonBlankAdId)
    }

    @Test
    fun `hasUsableBannerSize is true when both dimensions are positive`() {
        assertTrue(fakeBid(width = 320, height = 50).hasUsableBannerSize)
    }

    @Test
    fun `hasUsableBannerSize is false when the bid names no size at all`() {
        // How a bid with no `w`/`h` arrives: Prebid reads both with org.json's optInt, which defaults to 0.
        assertFalse(fakeBid(width = 0, height = 0).hasUsableBannerSize)
    }

    @Test
    fun `hasUsableBannerSize is false when only the height is missing`() {
        assertFalse(fakeBid(width = 320, height = 0).hasUsableBannerSize)
    }

    @Test
    fun `hasUsableBannerSize is false when only the width is missing`() {
        assertFalse(fakeBid(width = 0, height = 50).hasUsableBannerSize)
    }

    @Test
    fun `hasUsableBannerSize is false for a negative dimension`() {
        // optInt does not range-check, so a malformed response can carry a negative w/h.
        assertFalse(fakeBid(width = -320, height = -50).hasUsableBannerSize)
    }
}
