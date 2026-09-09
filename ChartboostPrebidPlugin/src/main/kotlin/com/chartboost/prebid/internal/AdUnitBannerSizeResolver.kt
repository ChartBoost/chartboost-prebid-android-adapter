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
 * bid itself names no usable size (see [hasUsableBannerSize]).
 *
 * Mirrors the precedence Prebid Mobile itself uses to build `imp.banner.format` in
 * `BasicParameterBuilder.setBannerImpValues`: `BannerParameters.adSizes` wins whenever it is set and
 * non-empty, and `AdUnitConfiguration.getSizes()` — the deprecated, pre-`BannerParameters` API — is
 * consulted only when it is not. Reading just `getSizes()` would silently miss every publisher who
 * declares sizes through `BannerParameters`, which is the current API.
 *
 * iOS resolves this differently, and cannot be matched exactly: it substitutes `AdUnitConfig.adSize`, the
 * single primary size fixed when the ad unit is constructed. Prebid Mobile Android has no primary-size
 * concept at all — both size sources here are unordered `Set`s — so there is nothing on this platform that
 * corresponds to it. For the single-size ad unit, which is the shape Chartboost demand actually serves
 * today, the two platforms agree. For a multi-size ad unit they can disagree, and closing that needs a
 * cross-platform decision rather than an adapter change.
 */
internal object AdUnitBannerSizeResolver {

    /**
     * The smallest usable size the ad unit declares, preferring one the Monetization SDK can actually
     * render into, or null when it declares none.
     *
     * An ad unit can declare more than one size, and the bid that won does not say which of them it
     * filled. Smallest-by-area is the only choice that cannot exceed a size the publisher actually
     * offered, since a larger pick could overflow a slot sized for something smaller. This is a documented
     * assumption, not a Prebid contract, and should be revisited if Prebid ever surfaces which offered
     * size a bid won.
     *
     * Smallest alone is not enough, though. Declared sizes are arbitrary publisher input, so the smallest
     * by area can easily be one no Chartboost size fits inside: an ad unit offering both 320x50 and 50x320
     * ties on area, and picking the portrait one would decline a bid that the landscape one renders. So
     * renderable sizes are preferred, and the smallest is taken from those; the non-renderable set is used
     * only when nothing is renderable, which keeps the resulting no-fill message pointing at a size the
     * publisher really declared.
     *
     * Ties are broken by width then height so the result never depends on `Set` iteration order. Both
     * sources are `HashSet`-backed and [AdSize] hashes on its `"WxH"` string, so iteration order is
     * arbitrary with respect to what the publisher declared.
     */
    fun resolveFallbackSize(adUnitConfiguration: AdUnitConfiguration): AdSize? {
        // Non-positive dimensions are a misconfigured ad unit. Dropping them here keeps a 0x250 entry,
        // whose area is 0, from always winning as "smallest" and turning a usable fallback into a no-fill.
        val usableSizes = declaredSizes(adUnitConfiguration).filter { it.width > 0 && it.height > 0 }
        if (usableSizes.isEmpty()) return null

        val renderableSizes = usableSizes.filter { BannerSizeMapper.map(it.width, it.height) != null }
        val candidates = renderableSizes.ifEmpty { usableSizes }
        val smallest = candidates.minWithOrNull(SMALLEST_FIRST) ?: return null

        if (usableSizes.size > 1) {
            val offered = usableSizes.joinToString { "${it.width}x${it.height}" }
            // warnOnce, not w: a bid missing its size is the common case today (Chartboost's own demand
            // omits w/h), and a banner reloads repeatedly within one session, so logging every occurrence
            // would spam the log with the same message for the life of the ad unit.
            PluginLog.warnOnce(
                "Bid names no size and the ad unit declares ${usableSizes.size} sizes ($offered); " +
                    "using the smallest renderable one, ${smallest.width}x${smallest.height}, since the " +
                    "adapter never renders larger than the slot the publisher offered",
            )
        }
        return smallest
    }

    /** Area first, then width and height, so an area tie resolves the same way on every run. */
    private val SMALLEST_FIRST: Comparator<AdSize> =
        compareBy({ bannerArea(it.width, it.height) }, { it.width }, { it.height })

    private fun declaredSizes(adUnitConfiguration: AdUnitConfiguration): Set<AdSize> {
        val bannerParameterSizes = adUnitConfiguration.bannerParameters?.adSizes
        if (!bannerParameterSizes.isNullOrEmpty()) return bannerParameterSizes
        return adUnitConfiguration.sizes
    }
}
