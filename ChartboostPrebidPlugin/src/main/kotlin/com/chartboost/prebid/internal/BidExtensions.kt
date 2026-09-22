/*
 * Copyright (c) 2026 Chartboost, Inc.
 *
 * Licensed under the MIT License.
 */

package com.chartboost.prebid.internal

import com.chartboost.sdk.events.ImpressionEvent
import org.prebid.mobile.rendering.bidding.data.bid.Bid

/** The Chartboost ad id carried by an impression event, or null when absent or blank. */
internal val ImpressionEvent.nonBlankAdId: String?
    get() = adID?.takeIf { it.isNotBlank() }

/** ADM only when present and non-blank; null means there is nothing to render. */
internal val Bid.admOrNull: String?
    get() = adm?.takeIf { it.isNotBlank() }

/**
 * True when [Bid.getWidth] and [Bid.getHeight] are both greater than zero.
 *
 * Prebid's `Bid.fromJSONObject` reads a missing `w`/`h` with org.json's `optInt`, which defaults to 0, so
 * a bid naming no size is indistinguishable from one that explicitly won at 0x0. Either dimension missing
 * fails the check, since a 320x0 bid is as unusable as 0x0; such a bid falls back to the ad unit's own
 * configured size rather than becoming a no-fill outright.
 */
internal val Bid.hasUsableBannerSize: Boolean
    get() = width > 0 && height > 0
