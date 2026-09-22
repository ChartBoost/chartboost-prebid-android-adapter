/*
 * Copyright (c) 2026 Chartboost, Inc.
 *
 * Licensed under the MIT License.
 */

package com.chartboost.prebid.internal

import com.chartboost.sdk.ads.Banner

/**
 * Width × height as a [Long] so it cannot overflow [Int]. Shared by [BannerSizeMapper] and
 * [AdUnitBannerSizeResolver] so the two never compute area differently.
 */
internal fun bannerArea(width: Int, height: Int): Long = width.toLong() * height.toLong()

/**
 * Resolves a bid's width/height to one of the Monetization SDK's [Banner.BannerSize] values, or null when
 * none of them fit.
 *
 * The rule is containment, not proximity: the largest-area [Banner.BannerSize] that fits entirely inside
 * the requested width and height. A full-width request (device width x 50) renders STANDARD 320x50 inside
 * it, while a slot nothing fits into (320x49) is declined as a no-fill, since rendering larger than the
 * slot would still count a billable impression.
 *
 * Candidates come from [Banner.BannerSize.entries] at runtime, so a size the host SDK adds later is
 * selected with no adapter change.
 *
 * Pure: the rejection is logged by [ChartboostErrorMapper.unsupportedBannerSize] at the call site.
 */
internal object BannerSizeMapper {

    fun map(width: Int, height: Int): Banner.BannerSize? {
        if (width <= 0 || height <= 0) return null

        // Equal areas resolve to whichever the SDK declares first, since maxByOrNull keeps the first
        // maximum. Latent: no two current sizes tie.
        return Banner.BannerSize.entries
            .filter { it.width <= width && it.height <= height }
            .maxByOrNull { bannerArea(it.width, it.height) }
    }

    /** Renderable sizes of the host SDK, for error messages. */
    fun supportedSizes(): String =
        Banner.BannerSize.entries.joinToString { "${it.width}x${it.height}" }
}
