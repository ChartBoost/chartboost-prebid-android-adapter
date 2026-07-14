/*
 * Copyright (c) 2026 Chartboost, Inc.
 *
 * Licensed under the MIT License.
 */

package com.chartboost.prebid

import com.chartboost.prebid.internal.PREBID_LOCATION

/** Plugin log verbosity. Ordered least to most verbose. */
enum class LogLevel { NONE, WARN, DEBUG }

/**
 * Publisher-facing configuration for the Chartboost Prebid plugin. Pass to
 * [ChartboostPrebidRenderer.register] once before loading ads. Every field has a default: location
 * [PREBID_LOCATION], log level WARN, and no event listener. From Java, use [Builder] (Kotlin default
 * arguments are not visible to Java callers).
 */
class ChartboostPrebidConfig(
    /**
     * Chartboost ad "location" used for every plugin-rendered ad. Blank coerces to the default.
     * Override to differentiate placements in Chartboost reporting.
     */
    val location: String = PREBID_LOCATION,
    /**
     * Plugin log verbosity. Default WARN: warnings and the integration-info line are visible; debug
     * lines are off.
     */
    val logLevel: LogLevel = LogLevel.WARN,
    /** Optional callback for plugin-rendered ad lifecycle events. Default null (no callback). */
    val eventListener: ChartboostPrebidEventListener? = null,
) {
    /**
     * Java-friendly builder for [ChartboostPrebidConfig]. Each setter returns `this`; unset fields keep
     * the same defaults as the Kotlin constructor.
     */
    class Builder {
        // Seed every field from a default config so the defaults live in exactly one place (the primary
        // constructor); a Builder that re-declared the literals would silently drift if one changed.
        private val defaults = ChartboostPrebidConfig()
        private var location: String = defaults.location
        private var logLevel: LogLevel = defaults.logLevel
        private var eventListener: ChartboostPrebidEventListener? = defaults.eventListener

        fun setLocation(location: String) = apply { this.location = location }

        fun setLogLevel(level: LogLevel) = apply { logLevel = level }

        fun setEventListener(listener: ChartboostPrebidEventListener?) = apply { eventListener = listener }

        fun build(): ChartboostPrebidConfig = ChartboostPrebidConfig(
            location = location,
            logLevel = logLevel,
            eventListener = eventListener,
        )
    }
}
