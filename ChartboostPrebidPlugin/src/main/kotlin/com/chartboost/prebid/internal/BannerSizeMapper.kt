/*
 * Copyright (c) 2026 Chartboost, Inc.
 *
 * Licensed under the MIT License.
 */

package com.chartboost.prebid.internal

import com.chartboost.sdk.ads.Banner

/**
 * Width × height as a [Long] so the product can't overflow [Int] for any realistic banner dimension.
 * Shared by [BannerSizeMapper] (picks the largest fit) and [AdUnitBannerSizeResolver] (picks the
 * smallest declared size), so the two never compute area differently.
 */
internal fun bannerArea(width: Int, height: Int): Long = width.toLong() * height.toLong()

/**
 * Resolves a bid's width/height to one of the Monetization SDK's [Banner.BannerSize] values, or null when
 * none of them fit.
 *
 * The rule is containment, not proximity: pick the largest-area [Banner.BannerSize] that fits entirely
 * inside the requested width and height. This is the same idea as fitting a fixed size into an adaptive
 * banner slot, so a full-width request (device width x 50) renders STANDARD 320x50 inside it, while a slot
 * nothing fits into (320x49) is declined as a no-fill (working group, 2026-08-10).
 *
 * Nearest-match was the previous behaviour and is deliberately gone. It let a size render at dimensions
 * larger than the slot the publisher actually had, and counted a billable impression while doing it.
 *
 * The candidate set is read from [Banner.BannerSize.entries] at runtime, so a size the host's SDK adds in
 * a future release starts being selected with no adapter change. HALFPAGE (300x600, added in Monetization
 * 9.14.0) is the size that proved this: it needed no mapper change, only the pinned-version test below.
 *
 * This function is pure. The rejection is logged once by [ChartboostErrorMapper.unsupportedBannerSize] at
 * the call site, so it does not log here.
 */
internal object BannerSizeMapper {

    fun map(width: Int, height: Int): Banner.BannerSize? {
        if (width <= 0 || height <= 0) return null

        // Equal areas resolve to whichever the SDK declares first, since maxByOrNull keeps the first
        // maximum. No two current sizes tie, so this is latent rather than load-bearing.
        return Banner.BannerSize.entries
            .filter { it.width <= width && it.height <= height }
            .maxByOrNull { bannerArea(it.width, it.height) }
    }

    /** Renderable sizes of the host SDK, for error messages. */
    fun supportedSizes(): String =
        Banner.BannerSize.entries.joinToString { "${it.width}x${it.height}" }
}
