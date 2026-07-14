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
