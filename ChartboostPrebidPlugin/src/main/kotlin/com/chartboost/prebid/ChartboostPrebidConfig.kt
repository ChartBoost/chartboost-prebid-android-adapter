/*
 * Copyright 2026 Chartboost, Inc.
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE file.
 */

package com.chartboost.prebid

import com.chartboost.prebid.internal.PREBID_LOCATION

/** Plugin log verbosity. Ordered least to most verbose. */
enum class LogLevel { NONE, WARN, DEBUG }

/**
 * Publisher-facing configuration for the Chartboost Prebid plugin. Pass to
 * [ChartboostPrebidRenderer.register] once before loading ads. All defaults preserve behavior
 * identical to a pre-config `register()` call. From Java, use [Builder] (Kotlin default arguments are
 * not visible to Java callers).
 */
data class ChartboostPrebidConfig(
    /**
     * When true, the plugin fires the OpenRTB win/impression notification URLs it owns
     * (events.win/imp + nurl). `burl` is gated separately by [burlEnabled]. Default false.
     */
    val notificationsEnabled: Boolean = false,
    /**
     * Chartboost ad "location" used for every plugin-rendered ad. Blank coerces to the default.
     * Override to differentiate placements in Chartboost reporting.
     */
    val location: String = PREBID_LOCATION,
    /**
     * Plugin log verbosity. Default WARN preserves prior behavior: warnings and the
     * integration-info line are visible; debug lines are off.
     */
    val logLevel: LogLevel = LogLevel.WARN,
    /**
     * When true, the plugin also fires the impression-side `burl`. Gated separately from
     * [notificationsEnabled] and default false, because the exchange may already bill off trackers
     * embedded in the rendered ADM; enabling this without confirming that risks double-counting
     * impressions. Turn on only for a deliberate billing audit.
     */
    val burlEnabled: Boolean = false,
    /** Optional callback for plugin-rendered ad lifecycle events. Default null (no callback). */
    val eventListener: ChartboostPrebidEventListener? = null,
    /**
     * Optional callback invoked after each notification URL fire (gated by [notificationsEnabled] and,
     * for `burl`, by [burlEnabled]). Default null (no callback).
     */
    val notificationResultListener: NotificationResultListener? = null,
) {
    /**
     * Java-friendly builder for [ChartboostPrebidConfig]. Each setter returns `this`; unset fields keep
     * the same defaults as the Kotlin constructor.
     */
    class Builder {
        // Seed every field from a default config so the defaults live in exactly one place (the primary
        // constructor); a Builder that re-declared the literals would silently drift if one changed.
        private val defaults = ChartboostPrebidConfig()
        private var notificationsEnabled: Boolean = defaults.notificationsEnabled
        private var location: String = defaults.location
        private var logLevel: LogLevel = defaults.logLevel
        private var burlEnabled: Boolean = defaults.burlEnabled
        private var eventListener: ChartboostPrebidEventListener? = defaults.eventListener
        private var notificationResultListener: NotificationResultListener? = defaults.notificationResultListener

        fun setNotificationsEnabled(enabled: Boolean) = apply { notificationsEnabled = enabled }

        fun setLocation(location: String) = apply { this.location = location }

        fun setLogLevel(level: LogLevel) = apply { logLevel = level }

        fun setBurlEnabled(enabled: Boolean) = apply { burlEnabled = enabled }

        fun setEventListener(listener: ChartboostPrebidEventListener?) = apply { eventListener = listener }

        fun setNotificationResultListener(listener: NotificationResultListener?) =
            apply { notificationResultListener = listener }

        fun build(): ChartboostPrebidConfig = ChartboostPrebidConfig(
            notificationsEnabled = notificationsEnabled,
            location = location,
            logLevel = logLevel,
            burlEnabled = burlEnabled,
            eventListener = eventListener,
            notificationResultListener = notificationResultListener,
        )
    }
}
