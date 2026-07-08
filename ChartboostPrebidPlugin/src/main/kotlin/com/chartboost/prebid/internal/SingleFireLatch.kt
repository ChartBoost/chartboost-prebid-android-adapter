/*
 * Copyright (c) 2026 Chartboost, Inc.
 *
 * Licensed under the MIT License.
 */

package com.chartboost.prebid.internal

import java.util.concurrent.atomic.AtomicBoolean

/**
 * One-shot guard. Every Prebid forward (loaded, displayed, impression) goes through one of these so
 * two concurrent Chartboost callbacks can never both fire it. Uses compare-and-set, not read-then-set,
 * so the check and the claim are atomic.
 */
internal class SingleFireLatch {
    private val fired = AtomicBoolean(false)

    /** Returns true exactly once, for the first caller; false on every later call. */
    fun fire(): Boolean = fired.compareAndSet(false, true)

    /** True once [fire] has been claimed. Read-only; does not claim the latch. */
    val hasFired: Boolean get() = fired.get()
}
