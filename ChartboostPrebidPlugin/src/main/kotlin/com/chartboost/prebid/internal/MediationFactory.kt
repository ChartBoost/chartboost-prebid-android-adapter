/*
 * Copyright (c) 2026 Chartboost, Inc.
 *
 * Licensed under the MIT License.
 */

package com.chartboost.prebid.internal

import com.chartboost.prebid.BuildConfig
import com.chartboost.sdk.Mediation
import org.prebid.mobile.PrebidMobile

/**
 * Builds the static Prebid mediation param attached to every Chartboost ad, so Chartboost-side reporting
 * attributes the traffic as Prebid. libraryVersion is the host Prebid SDK version at runtime;
 * adapterVersion is this plugin's renderer version.
 */
internal object MediationFactory {

    private const val MEDIATION_NAME = "Prebid"

    fun create(): Mediation =
        Mediation(MEDIATION_NAME, hostPrebidVersion(), BuildConfig.RENDERER_VERSION)

    private fun hostPrebidVersion(): String? =
        runCatching { PrebidMobile.SDK_VERSION }.getOrNull()
}
