/*
 * Copyright (c) 2026 Chartboost, Inc.
 *
 * Licensed under the MIT License.
 */

package com.chartboost.prebid.internal

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.prebid.mobile.AdSize
import org.prebid.mobile.BannerParameters
import org.prebid.mobile.configuration.AdUnitConfiguration
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AdUnitBannerSizeResolverTest {

    /** [bannerParameterSizes] is null when [BannerParameters.getAdSizes] itself should return null. */
    private fun adUnitConfiguration(
        bannerParameterSizes: Set<AdSize>? = null,
        deprecatedSizes: Set<AdSize> = emptySet(),
    ): AdUnitConfiguration {
        val bannerParameters = mockk<BannerParameters>(relaxed = true)
        every { bannerParameters.adSizes } returns bannerParameterSizes
        val config = mockk<AdUnitConfiguration>(relaxed = true)
        every { config.bannerParameters } returns bannerParameters
        every { config.sizes } returns HashSet(deprecatedSizes)
        return config
    }

    @Test
    fun `sizes declared through BannerParameters are used`() {
        val config = adUnitConfiguration(bannerParameterSizes = setOf(AdSize(300, 250)))
        assertEquals(AdSize(300, 250), AdUnitBannerSizeResolver.resolveFallbackSize(config))
    }

    @Test
    fun `sizes declared only through the deprecated setter are used`() {
        val config = adUnitConfiguration(bannerParameterSizes = null, deprecatedSizes = setOf(AdSize(320, 50)))
        assertEquals(AdSize(320, 50), AdUnitBannerSizeResolver.resolveFallbackSize(config))
    }

    @Test
    fun `BannerParameters wins when both are populated`() {
        val config = adUnitConfiguration(
            bannerParameterSizes = setOf(AdSize(300, 250)),
            deprecatedSizes = setOf(AdSize(320, 50)),
        )
        assertEquals(AdSize(300, 250), AdUnitBannerSizeResolver.resolveFallbackSize(config))
    }

    @Test
    fun `multi-size ad unit resolves the smallest and warns`() {
        // Areas: 300x250 = 75000, 320x50 = 16000, 728x90 = 65520. 320x50 is smallest.
        val config = adUnitConfiguration(
            bannerParameterSizes = setOf(AdSize(300, 250), AdSize(320, 50), AdSize(728, 90)),
        )
        val resolved = AdUnitBannerSizeResolver.resolveFallbackSize(config)
        assertEquals(AdSize(320, 50), resolved)
        assertTrue(
            "expected a warning naming the candidates",
            ShadowLog.getLogsForTag(PluginLog.TAG)
                .any { it.msg.contains("300x250") && it.msg.contains("320x50") && it.msg.contains("728x90") },
        )
    }

    @Test
    fun `an ad unit declaring no sizes at all resolves nothing`() {
        assertNull(AdUnitBannerSizeResolver.resolveFallbackSize(adUnitConfiguration()))
    }

    @Test
    fun `an empty BannerParameters size set falls through to the deprecated sizes`() {
        // isNullOrEmpty has two reasons to fall through; the null one is covered above, this is the other.
        val config = adUnitConfiguration(
            bannerParameterSizes = emptySet(),
            deprecatedSizes = setOf(AdSize(728, 90)),
        )
        assertEquals(AdSize(728, 90), AdUnitBannerSizeResolver.resolveFallbackSize(config))
    }

    @Test
    fun `an area tie prefers the size the SDK can actually render`() {
        // 320x50 and 50x320 both have area 16000, and Set iteration order decides nothing here: no
        // Chartboost size fits inside 50x320, so picking it would decline a bid 320x50 renders.
        val config = adUnitConfiguration(bannerParameterSizes = setOf(AdSize(50, 320), AdSize(320, 50)))
        assertEquals(AdSize(320, 50), AdUnitBannerSizeResolver.resolveFallbackSize(config))
    }

    @Test
    fun `an area tie between two renderable sizes resolves the same way every time`() {
        // 600x300 (fits MEDIUM) and 300x600 (fits HALFPAGE) both have area 180000, so the width tie-break
        // decides. Without it the answer would follow HashSet iteration order.
        val config = adUnitConfiguration(bannerParameterSizes = setOf(AdSize(600, 300), AdSize(300, 600)))
        assertEquals(AdSize(300, 600), AdUnitBannerSizeResolver.resolveFallbackSize(config))
    }

    @Test
    fun `a zero-dimension size is never resolved`() {
        // Area 0 would always win as "smallest" and then fail to map, turning a usable fallback into a
        // no-fill.
        val config = adUnitConfiguration(bannerParameterSizes = setOf(AdSize(0, 250), AdSize(300, 250)))
        assertEquals(AdSize(300, 250), AdUnitBannerSizeResolver.resolveFallbackSize(config))
    }

    @Test
    fun `an ad unit declaring only unusable sizes resolves nothing`() {
        val config = adUnitConfiguration(bannerParameterSizes = setOf(AdSize(0, 0), AdSize(-320, 50)))
        assertNull(AdUnitBannerSizeResolver.resolveFallbackSize(config))
    }

    @Test
    fun `a declared size too small for any Chartboost size is still resolved`() {
        // Nothing renders in 100x100, but reporting the no-fill against a size the publisher really
        // declared beats inventing one.
        val config = adUnitConfiguration(bannerParameterSizes = setOf(AdSize(100, 100)))
        assertEquals(AdSize(100, 100), AdUnitBannerSizeResolver.resolveFallbackSize(config))
    }
}
