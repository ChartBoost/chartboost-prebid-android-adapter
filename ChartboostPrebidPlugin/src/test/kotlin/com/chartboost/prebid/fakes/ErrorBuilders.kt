/*
 * Copyright (c) 2026 Chartboost, Inc.
 *
 * Licensed under the MIT License.
 */

package com.chartboost.prebid.fakes

import com.chartboost.sdk.events.CacheError
import com.chartboost.sdk.events.ShowError
import io.mockk.every
import io.mockk.mockk

/** Mocked [CacheError] carrying only the given [code], the one property every mapper reads. */
internal fun cacheError(code: CacheError.Code): CacheError =
    mockk<CacheError>().also { every { it.code } returns code }

/** Mocked [ShowError] carrying only the given [code], the one property every mapper reads. */
internal fun showError(code: ShowError.Code): ShowError =
    mockk<ShowError>().also { every { it.code } returns code }
