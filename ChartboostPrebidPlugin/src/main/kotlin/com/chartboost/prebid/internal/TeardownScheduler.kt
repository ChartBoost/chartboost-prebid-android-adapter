/*
 * Copyright 2026 Chartboost, Inc.
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE file.
 */

package com.chartboost.prebid.internal

/** Test seam for the banner teardown timer; production uses the host View's postDelayed/removeCallbacks. */
internal interface TeardownScheduler {
    fun schedule(delayMs: Long, action: Runnable)
    fun cancel(action: Runnable)
}
