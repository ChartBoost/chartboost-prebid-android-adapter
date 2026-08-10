/*
 * Copyright (c) 2026 Chartboost, Inc.
 *
 * Licensed under the MIT License.
 */

package com.chartboost.prebid.internal

import com.chartboost.sdk.ads.Banner

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
 * a later release starts being selected with no adapter change. HALFPAGE 300x600 arrived that way in
 * Monetization 9.14.0; a host still on an older SDK resolves a 300x600 slot to MEDIUM instead.
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
            .maxByOrNull { it.width.toLong() * it.height.toLong() }
    }

    /** Renderable sizes of the host SDK, for error messages. */
    fun supportedSizes(): String =
        Banner.BannerSize.entries.joinToString { "${it.width}x${it.height}" }
}
