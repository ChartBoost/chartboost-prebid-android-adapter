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
 * attributes the traffic as Prebid. libraryVersion reflects the host app's Prebid Mobile version at
 * runtime, falling back to the compile-time version if reflection fails. adapterVersion is this plugin's
 * adapter version.
 */
internal object MediationFactory {

    private const val MEDIATION_NAME = "Prebid"

    fun create(): Mediation =
        Mediation(MEDIATION_NAME, hostPrebidVersion, BuildConfig.ADAPTER_VERSION)

    // PrebidMobile.SDK_VERSION is a compile-time `const val`, so referencing it directly inlines the literal
    // from the compileOnly dependency this adapter was built against, not whatever Prebid version the host
    // app actually ships. Reading the field via reflection resolves it against the host's classpath at
    // runtime instead; the compile-time constant is only a fallback for when that reflection fails.
    private val hostPrebidVersion: String? by lazy {
        runCatching { PrebidMobile::class.java.getField("SDK_VERSION").get(null) as? String }
            .getOrNull() ?: PrebidMobile.SDK_VERSION
    }
}
