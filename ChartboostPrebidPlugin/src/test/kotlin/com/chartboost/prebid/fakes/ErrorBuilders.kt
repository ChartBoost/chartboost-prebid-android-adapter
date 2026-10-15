/*
 * Copyright (c) 2026 Chartboost, Inc.
 *
 * Licensed under the MIT License.
 */

package com.chartboost.prebid.fakes

import com.chartboost.sdk.events.ChartboostError

/** A show failure that uses the ad up, unlike the rejections the SDK makes before a show starts. */
internal fun usedUpShowError(): ChartboostError = ChartboostError.Show.Unknown("render failed")
