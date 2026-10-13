/*
 * Copyright (c) 2026 Chartboost, Inc.
 *
 * Licensed under the MIT License.
 */

package com.chartboost.prebid.fakes

import io.mockk.every
import io.mockk.mockk
import org.prebid.mobile.rendering.bidding.data.bid.Bid
import org.prebid.mobile.rendering.bidding.data.bid.BidResponse
import org.prebid.mobile.rendering.interstitial.rewarded.Reward
import org.prebid.mobile.rendering.interstitial.rewarded.RewardedClosingRules
import org.prebid.mobile.rendering.interstitial.rewarded.RewardedCompletionRules
import org.prebid.mobile.rendering.interstitial.rewarded.RewardedExt

internal const val FAKE_REWARD_TYPE = "coins"
internal const val FAKE_REWARD_COUNT = 5
internal const val LEFTOVER_REWARD_TYPE = "leftover"

/** Builder for a winning [Bid]; Bid has no public constructor, so it is mocked. */
internal fun fakeBid(
    adm: String? = "<chartboost-adm>",
    width: Int = 320,
    height: Int = 50,
    rewardedExt: RewardedExt = RewardedExt.defaultExt(),
): Bid {
    val bid = mockk<Bid>(relaxed = true)
    every { bid.adm } returns adm
    every { bid.width } returns width
    every { bid.height } returns height
    every { bid.rewardedExt } returns rewardedExt
    return bid
}

internal fun fakeBidResponse(bid: Bid? = fakeBid()): BidResponse {
    val response = mockk<BidResponse>(relaxed = true)
    every { response.winningBid } returns bid
    return response
}

/** Builder for the reward Prebid Server sets on a bid (`rwdd.reward`). */
internal fun fakeReward(
    type: String = FAKE_REWARD_TYPE,
    count: Int = FAKE_REWARD_COUNT,
): Reward = Reward(type, count, null)

/** Builder for a bid's rewarded ext; a null [reward] is a bid whose `rwdd` carries no reward. */
internal fun fakeRewardedExt(reward: Reward?): RewardedExt =
    RewardedExt(reward, RewardedCompletionRules(), RewardedClosingRules())
