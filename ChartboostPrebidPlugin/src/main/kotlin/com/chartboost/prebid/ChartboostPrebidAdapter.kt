/*
 * Copyright (c) 2026 Chartboost, Inc.
 *
 * Licensed under the MIT License.
 */

package com.chartboost.prebid

import com.chartboost.prebid.internal.PluginLog
import org.prebid.mobile.api.rendering.pluginrenderer.PrebidMobilePluginRegister

/**
 * One-call integration entry point. Register the Chartboost plugin adapter with Prebid Mobile before
 * loading any ad, so its name, version and data ride the auction request. Before or right after
 * PrebidMobile.initializeSdk both work; the only requirement is that it happens before the first ad load.
 * The Chartboost Monetization SDK is started separately by the publisher. Re-registering the same name
 * replaces the previous registration.
 */
public object ChartboostPrebidAdapter {

    /** The adapter name sent in every bid request. Must match the Prebid Server adapter's `rendererName`. */
    public val adapterName: String get() = ChartboostPrebidPluginAdapter.NAME

    /** The adapter version sent in every bid request. Must byte-match the Prebid Server adapter's `rendererVersion`. */
    public val adapterVersion: String get() = ChartboostPrebidPluginAdapter.ADAPTER_VERSION

    /**
     * Whether [adapterVersion] exactly equals [prebidServerAdapterVersion], the value the Prebid Server
     * adapter stamps as `ext.prebid.meta.rendererVersion`. A mismatch makes Prebid Mobile silently fall
     * back to its own rendering, so asserting on this during integration fails fast instead.
     */
    public fun matchesServerAdapterVersion(prebidServerAdapterVersion: String): Boolean =
        adapterVersion == prebidServerAdapterVersion

    public fun register(config: ChartboostPrebidConfig = ChartboostPrebidConfig()) {
        PluginLog.level = config.logLevel
        PrebidMobilePluginRegister.getInstance().registerPlugin(ChartboostPrebidPluginAdapter(config))
    }

    /**
     * Removes the Chartboost plugin adapter from Prebid Mobile's registry, the counterpart to [register].
     * Prebid evicts by plugin name, so this drops whatever is registered under the Chartboost name, and
     * no-ops when nothing is registered. Call it to tear down a registration whose
     * [ChartboostPrebidConfig.eventListener] captures an Activity, so the listener does not outlive it.
     */
    public fun unregister() {
        PrebidMobilePluginRegister.getInstance().unregisterPlugin(ChartboostPrebidPluginAdapter())
    }

    /** Logs the registered name and version at INFO, to check them against the Prebid Server stamp. */
    public fun logIntegrationInfo() {
        PluginLog.i("ChartboostPrebidAdapter: name=$adapterName version=$adapterVersion — version must byte-match the Prebid Server rendererVersion or routing falls back to Prebid's own rendering")
    }
}
