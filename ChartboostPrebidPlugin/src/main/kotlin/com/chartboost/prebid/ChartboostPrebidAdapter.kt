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
 * loading any ad, so the adapter's name/version/data are attached to the auction request. Registering
 * before or right after PrebidMobile.initializeSdk both work; the only hard requirement is that it
 * happens before the first ad load. The Chartboost Monetization SDK is started separately by the
 * publisher. Re-registering the same name replaces the previous registration (Prebid behavior).
 */
object ChartboostPrebidAdapter {

    /** The adapter name sent in every bid request. Must match the Prebid Server adapter's `rendererName`. */
    val adapterName: String get() = ChartboostPrebidPluginAdapter.NAME

    /** The adapter version sent in every bid request. Must byte-match the Prebid Server adapter's `rendererVersion`. */
    val adapterVersion: String get() = ChartboostPrebidPluginAdapter.ADAPTER_VERSION

    /**
     * Returns whether [adapterVersion] exactly equals [prebidServerAdapterVersion] — the value the
     * Prebid Server adapter stamps as `ext.prebid.meta.rendererVersion`. A mismatch makes Prebid Mobile
     * silently fall back to its own rendering, so a publisher who knows their server stamp can assert
     * on this during integration to fail fast instead of debugging a silent fallback. The comparison is
     * byte-for-byte, matching Prebid's own check.
     */
    fun matchesServerAdapterVersion(prebidServerAdapterVersion: String): Boolean =
        adapterVersion == prebidServerAdapterVersion

    fun register(config: ChartboostPrebidConfig = ChartboostPrebidConfig()) {
        PluginLog.level = config.logLevel
        PrebidMobilePluginRegister.getInstance().registerPlugin(ChartboostPrebidPluginAdapter(config))
    }

    /**
     * Removes the Chartboost plugin adapter from Prebid Mobile's registry, the counterpart to [register].
     * Prebid evicts by plugin name, so this drops whatever plugin is registered under the Chartboost
     * name; it is a no-op when nothing is registered. Call it to tear down a registration whose
     * [ChartboostPrebidConfig.eventListener] captures an Activity, so the listener does not outlive it.
     * The adapter's constructors are internal, so a consumer cannot build one to pass to Prebid directly;
     * this is the only supported way to unregister.
     */
    fun unregister() {
        PrebidMobilePluginRegister.getInstance().unregisterPlugin(ChartboostPrebidPluginAdapter())
    }

    /**
     * Logs the registered adapter name and version at INFO level. Use during integration to confirm
     * the client-side values match the Prebid Server adapter stamp; a mismatch causes silent fallback
     * to Prebid's own rendering.
     */
    fun logIntegrationInfo() {
        PluginLog.i("ChartboostPrebidAdapter: name=$adapterName version=$adapterVersion — version must byte-match the Prebid Server rendererVersion or routing falls back to Prebid's own rendering")
    }
}
