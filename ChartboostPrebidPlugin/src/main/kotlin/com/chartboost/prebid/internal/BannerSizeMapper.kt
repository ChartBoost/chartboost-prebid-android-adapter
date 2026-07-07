/*
 * Copyright 2026 Chartboost, Inc.
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE file.
 */

package com.chartboost.prebid.internal

import com.chartboost.sdk.ads.Banner

/**
 * Maps an arbitrary bid width/height to one of the Monetization SDK's three [Banner.BannerSize] values.
 *
 * The backend enforces no size restriction, so the adapter must accept any
 * size rather than reject it client-side. The SDK only renders at the three enum dimensions, so we pick
 * the nearest one; the host should size its container to [Banner.getBannerWidth]/[Banner.getBannerHeight].
 */
internal object BannerSizeMapper {

    fun map(width: Int, height: Int): Banner.BannerSize {
        if (width <= 0 || height <= 0) {
            PluginLog.w("banner bid carries no usable size (${width}x$height); defaulting to STANDARD 320x50")
            return Banner.BannerSize.STANDARD
        }

        val nearest = Banner.BannerSize.entries.minByOrNull { size ->
            val dw = (width - size.width).toLong()
            val dh = (height - size.height).toLong()
            dw * dw + dh * dh
        } ?: Banner.BannerSize.STANDARD

        if (width != nearest.width || height != nearest.height) {
            PluginLog.w(
                "requested banner size ${width}x$height has no exact Chartboost size; rendering nearest " +
                    "${nearest.width}x${nearest.height} — size the host container accordingly",
            )
        }
        return nearest
    }
}
