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
import org.junit.Assert.assertNull
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
}
