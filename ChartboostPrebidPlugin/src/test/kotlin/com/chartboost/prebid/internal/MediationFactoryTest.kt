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
        // Read the expected value through the same reflection MediationFactory uses, rather than the
        // compile-time constant directly: both this test and the adapter compile against the same Prebid
        // dependency, so comparing against the inlined constant would pass even if the adapter fell back to
        // reading a stale compile-time literal instead of the host's actual classpath value.
        val expected = requireNotNull(PrebidMobile::class.java.getField("SDK_VERSION").get(null) as? String)
        assertEquals(expected, MediationFactory.create().libraryVersion)
    }

    @Test
    fun `uses the adapter version as the mediation adapter version`() {
        assertEquals(ChartboostPrebidPluginAdapter.ADAPTER_VERSION, MediationFactory.create().adapterVersion)
    }
}
