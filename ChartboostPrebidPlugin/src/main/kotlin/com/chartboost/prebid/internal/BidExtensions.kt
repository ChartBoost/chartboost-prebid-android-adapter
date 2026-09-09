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
 * True when [Bid.getWidth] and [Bid.getHeight] are both usable (greater than zero).
 *
 * Prebid's `Bid.fromJSONObject` reads a missing `w`/`h` with org.json's `optInt`, which defaults to 0, so
 * a bid that names no size at all is indistinguishable at this point from one that explicitly won at
 * 0x0. Either dimension missing is enough to fail this check — a 320x0 bid is just as unusable as 0x0 —
 * so a bid that fails it should fall back to the ad unit's own configured size rather than becoming a
 * no-fill outright.
 *
 * This is a deliberate difference from iOS, which currently guards on both dimensions being zero and so
 * still no-fills a 320x0 bid. iOS is expected to align to this behaviour, not the other way around.
 */
internal val Bid.hasUsableBannerSize: Boolean
    get() = width > 0 && height > 0
