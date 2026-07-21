/*
 * Copyright (c) 2026 Chartboost, Inc.
 *
 * Licensed under the MIT License.
 */

package com.chartboost.prebid.internal

import com.chartboost.prebid.ChartboostPrebidPluginAdapter
import org.junit.Assert.assertEquals
import org.junit.Test
import org.prebid.mobile.PrebidMobile

class MediationFactoryTest {

    @Test
    fun `creates a Prebid mediation param`() {
        assertEquals("Prebid", MediationFactory.create().mediationType)
    }

    @Test
    fun `uses the host Prebid SDK version as the library version`() {
        // Pins the behavioral contract: libraryVersion equals the classpath's SDK_VERSION. In this test
        // JVM the compile-time and runtime Prebid artifacts are the same, so the reflected value equals the
        // inlined constant and a silent fallback inside MediationFactory is not detectable here; the
        // reflection below only keeps the expected value symmetric with how the adapter reads it.
        val expected = requireNotNull(PrebidMobile::class.java.getField("SDK_VERSION").get(null) as? String)
        assertEquals(expected, MediationFactory.create().libraryVersion)
    }

    @Test
    fun `uses the adapter version as the mediation adapter version`() {
        assertEquals(ChartboostPrebidPluginAdapter.ADAPTER_VERSION, MediationFactory.create().adapterVersion)
    }
}
