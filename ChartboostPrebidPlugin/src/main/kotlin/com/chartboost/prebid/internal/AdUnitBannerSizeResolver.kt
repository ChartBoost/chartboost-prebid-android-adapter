/*
 * Copyright (c) 2026 Chartboost, Inc.
 *
 * Licensed under the MIT License.
 */

package com.chartboost.prebid.internal

import org.prebid.mobile.AdSize
import org.prebid.mobile.configuration.AdUnitConfiguration

/**
 * Resolves the banner size a publisher configured on an ad unit, for use as a fallback when the winning
 * bid names no usable size (see [hasUsableBannerSize]).
 *
 * Mirrors the precedence Prebid Mobile uses in `BasicParameterBuilder.setBannerImpValues` to build
 * `imp.banner.format`: `BannerParameters.adSizes` whenever it is set and non-empty, and the deprecated
 * `AdUnitConfiguration.getSizes()` only when it is not. Reading just `getSizes()` would miss every
 * publisher who declares sizes through `BannerParameters`, which is the current API.
 */
internal object AdUnitBannerSizeResolver {

    /**
     * The smallest usable size the ad unit declares, preferring one the Monetization SDK can render into,
     * or null when it declares none.
     *
     * An ad unit can declare more than one size and the winning bid does not say which it filled, so
     * smallest-by-area is the only choice that cannot exceed a size the publisher offered. Smallest alone
     * is not enough: declared sizes are arbitrary publisher input, so an ad unit offering both 320x50 and
     * 50x320 ties on area, and picking the portrait one would decline a bid the landscape one renders.
     * Renderable sizes are therefore preferred, falling back to the non-renderable set only when nothing
     * is renderable, which keeps the no-fill message pointing at a size the publisher really declared.
     *
     * Ties break by width then height, since both sources are `HashSet`-backed and [AdSize] hashes on its
     * `"WxH"` string, so iteration order says nothing about what the publisher declared.
     */
    fun resolveFallbackSize(adUnitConfiguration: AdUnitConfiguration): AdSize? {
        // Non-positive dimensions mean a misconfigured ad unit. Dropping them keeps a 0x250 entry, whose
        // area is 0, from always winning as "smallest" and turning a usable fallback into a no-fill.
        val usableSizes = declaredSizes(adUnitConfiguration).filter { it.width > 0 && it.height > 0 }
        if (usableSizes.isEmpty()) return null

        val renderableSizes = usableSizes.filter { BannerSizeMapper.map(it.width, it.height) != null }
        val candidates = renderableSizes.ifEmpty { usableSizes }
        val smallest = candidates.minWithOrNull(SMALLEST_FIRST) ?: return null

        if (usableSizes.size > 1) {
            val offered = usableSizes.joinToString { "${it.width}x${it.height}" }
            // warnOnce, not w: a banner reloads repeatedly within one session, so logging every
            // occurrence would repeat the same line for the life of the ad unit.
            PluginLog.warnOnce(
                "Bid names no size and the ad unit declares ${usableSizes.size} sizes ($offered); " +
                    "using the smallest renderable one, ${smallest.width}x${smallest.height}, since the " +
                    "adapter never renders larger than the slot the publisher offered",
            )
        }
        return smallest
    }

    private val SMALLEST_FIRST: Comparator<AdSize> =
        compareBy({ bannerArea(it.width, it.height) }, { it.width }, { it.height })

    private fun declaredSizes(adUnitConfiguration: AdUnitConfiguration): Set<AdSize> {
        val bannerParameterSizes = adUnitConfiguration.bannerParameters?.adSizes
        if (!bannerParameterSizes.isNullOrEmpty()) return bannerParameterSizes
        return adUnitConfiguration.sizes
    }
}
