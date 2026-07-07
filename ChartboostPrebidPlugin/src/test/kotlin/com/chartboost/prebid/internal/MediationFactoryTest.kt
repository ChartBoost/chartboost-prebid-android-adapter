package com.chartboost.prebid.internal

import com.chartboost.prebid.ChartboostPBMPluginRenderer
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
        assertEquals(PrebidMobile.SDK_VERSION, MediationFactory.create().libraryVersion)
    }

    @Test
    fun `uses the renderer version as the adapter version`() {
        assertEquals(ChartboostPBMPluginRenderer.RENDERER_VERSION, MediationFactory.create().adapterVersion)
    }
}
