/*
 * Copyright 2026 Chartboost, Inc.
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE file.
 */

package com.chartboost.prebid.internal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Marshals a Prebid forward onto the Android main thread. Chartboost callbacks already arrive on main
 * (verified in the SDK's UiPoster), so this is a defensive seam, also the injection point that keeps the
 * bridge mapping unit-testable without Robolectric.
 */
internal fun interface MainThreadExecutor {
    fun execute(block: () -> Unit)
}

internal object DefaultMainThreadExecutor : MainThreadExecutor {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Dispatchers.Main always dispatches (even when already on the main thread, it posts to the queue), so a
    // Prebid forward never re-enters core synchronously inside createBannerAdView/createInterstitialController.
    override fun execute(block: () -> Unit) {
        scope.launch { block() }
    }
}
