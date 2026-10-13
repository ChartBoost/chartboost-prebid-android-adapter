/*
 * Copyright (c) 2026 Chartboost, Inc.
 *
 * Licensed under the MIT License.
 */

package com.chartboost.prebid.internal

/** Test seam for the banner teardown timer; production uses the adapter's own main-looper Handler. */
internal interface TeardownScheduler {
    fun schedule(delayMs: Long, action: Runnable)
    fun cancel(action: Runnable)
}
