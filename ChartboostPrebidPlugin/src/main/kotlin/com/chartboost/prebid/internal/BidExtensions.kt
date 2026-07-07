package com.chartboost.prebid.internal

import com.chartboost.sdk.events.ImpressionEvent
import org.prebid.mobile.rendering.bidding.data.bid.Bid
import java.math.BigDecimal

/** Event-map keys Prebid uses for the win/impression notification URLs (ext.prebid.events). */
internal const val EVENT_WIN_KEY = "ext.prebid.events.win"
internal const val EVENT_IMP_KEY = "ext.prebid.events.imp"

/** The Chartboost ad id carried by an impression event, or null when absent or blank. */
internal val ImpressionEvent.nonBlankAdId: String?
    get() = adID?.takeIf { it.isNotBlank() }

/** ADM only when present and non-blank; null means there is nothing to render. */
internal val Bid.admOrNull: String?
    get() = adm?.takeIf { it.isNotBlank() }

internal val Bid.winEventUrl: String?
    get() = events?.get(EVENT_WIN_KEY)

internal val Bid.impEventUrl: String?
    get() = events?.get(EVENT_IMP_KEY)

/**
 * Substitutes the OpenRTB ${AUCTION_PRICE} macro (raw and percent-encoded forms) with the clearing price.
 * Plain decimal, no scientific notation, so prices like 1.5 stay "1.5".
 */
internal fun String.withAuctionPrice(price: Double): String {
    val encoded = BigDecimal.valueOf(price).toPlainString()
    return replace("\${AUCTION_PRICE}", encoded)
        .replace("%24%7BAUCTION_PRICE%7D", encoded)
}
