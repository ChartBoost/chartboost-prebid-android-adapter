/*
 * Copyright 2026 Chartboost, Inc.
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE file.
 */

package com.chartboost.prebid.fakes

import io.mockk.every
import io.mockk.mockk
import org.prebid.mobile.rendering.bidding.data.bid.Bid
import org.prebid.mobile.rendering.bidding.data.bid.BidResponse

/** Builder for a winning [Bid]; Bid has no public constructor, so it is mocked. */
internal fun fakeBid(
    adm: String? = "<chartboost-adm>",
    width: Int = 320,
    height: Int = 50,
    price: Double = 1.50,
    nurl: String? = null,
    burl: String? = null,
    events: Map<String, String> = emptyMap(),
): Bid {
    val bid = mockk<Bid>(relaxed = true)
    every { bid.adm } returns adm
    every { bid.width } returns width
    every { bid.height } returns height
    every { bid.price } returns price
    every { bid.nurl } returns nurl
    every { bid.burl } returns burl
    every { bid.events } returns events
    return bid
}

internal fun fakeBidResponse(bid: Bid? = fakeBid()): BidResponse {
    val response = mockk<BidResponse>(relaxed = true)
    every { response.winningBid } returns bid
    return response
}
