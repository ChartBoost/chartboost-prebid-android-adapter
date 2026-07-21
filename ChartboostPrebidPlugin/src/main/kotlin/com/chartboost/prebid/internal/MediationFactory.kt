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
 * attributes the traffic as Prebid. libraryVersion is the host app's Prebid Mobile version resolved at
 * runtime, or null (unknown) when that resolution fails. adapterVersion is this plugin's adapter version.
 */
internal object MediationFactory {

    private const val MEDIATION_NAME = "Prebid"

    fun create(): Mediation =
        Mediation(MEDIATION_NAME, hostPrebidVersion, BuildConfig.ADAPTER_VERSION)

    // PrebidMobile.SDK_VERSION is a Java `static final String` with a compile-time ConstantValue, so
    // referencing it directly inlines the literal from the compileOnly dependency this adapter was built
    // against, not whatever Prebid version the host app actually ships. Reading the field via reflection
    // resolves it against the host's classpath at runtime instead; on failure, null (unknown) beats
    // reporting a compile-time version the host may not be running. Resolved once at object init.
    private val hostPrebidVersion: String? =
        runCatching { PrebidMobile::class.java.getField("SDK_VERSION").get(null) as? String }.getOrNull()
}
