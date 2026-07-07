/*
 * Copyright 2026 Chartboost, Inc.
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE file.
 */

package com.chartboost.prebid

import com.chartboost.prebid.internal.PluginLog
import org.prebid.mobile.api.rendering.pluginrenderer.PrebidMobilePluginRegister

/**
 * One-call integration entry point. Register the Chartboost plugin renderer with Prebid Mobile before
 * loading any ad, so the renderer's name/version/data are attached to the auction request. Registering
 * before or right after PrebidMobile.initializeSdk both work; the only hard requirement is that it
 * happens before the first ad load. The Chartboost Monetization SDK is started separately by the
 * publisher. Re-registering the same name replaces the previous registration (Prebid behavior).
 */
object ChartboostPrebidRenderer {

    /** The renderer name sent in every bid request. Must match the Prebid Server adapter's `rendererName`. */
    val rendererName: String get() = ChartboostPBMPluginRenderer.NAME

    /** The renderer version sent in every bid request. Must byte-match the Prebid Server adapter's `rendererVersion`. */
    val rendererVersion: String get() = ChartboostPBMPluginRenderer.RENDERER_VERSION

    /**
     * Returns whether [rendererVersion] exactly equals [prebidServerRendererVersion] — the value the
     * Prebid Server adapter stamps as `ext.prebid.meta.rendererVersion`. A mismatch makes Prebid Mobile
     * silently fall back to its default renderer, so a publisher who knows their server stamp can assert
     * on this during integration to fail fast instead of debugging a silent fallback. The comparison is
     * byte-for-byte, matching Prebid's own check.
     */
    fun matchesServerRendererVersion(prebidServerRendererVersion: String): Boolean =
        rendererVersion == prebidServerRendererVersion

    fun register(config: ChartboostPrebidConfig = ChartboostPrebidConfig()) {
        PrebidMobilePluginRegister.getInstance().registerPlugin(ChartboostPBMPluginRenderer(config))
    }

    /**
     * Logs the registered renderer name and version at INFO level. Use during integration to confirm
     * the client-side values match the Prebid Server adapter stamp; a mismatch causes silent fallback
     * to Prebid's default renderer.
     */
    fun logIntegrationInfo() {
        PluginLog.i("ChartboostPrebidRenderer: name=$rendererName version=$rendererVersion — version must byte-match the Prebid Server rendererVersion or routing falls back to Prebid's default renderer")
    }
}
