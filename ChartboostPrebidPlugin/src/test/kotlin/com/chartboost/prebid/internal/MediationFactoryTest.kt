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
        // In this single-classpath JVM the runtime value equals the compile-time constant, so the
        // reflection-failure path (null) is untestable here; this pins the happy-path contract.
        assertEquals(PrebidMobile.SDK_VERSION, MediationFactory.create().libraryVersion)
    }

    @Test
    fun `uses the adapter version as the mediation adapter version`() {
        assertEquals(ChartboostPrebidPluginAdapter.ADAPTER_VERSION, MediationFactory.create().adapterVersion)
    }
}
