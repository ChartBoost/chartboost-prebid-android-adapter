/*
 * Copyright (c) 2026 Chartboost, Inc.
 *
 * Licensed under the MIT License.
 */

package com.chartboost.prebid.internal

import android.util.Log
import com.chartboost.prebid.LogLevel
import java.util.concurrent.ConcurrentHashMap

/**
 * Thin logging wrapper. Never logs the bidder token or ad markup. [warnOnce] de-dupes a warning to one
 * line per process, used for the token-absent path.
 */
internal object PluginLog {
    private const val TAG = "ChartboostPrebid"
    private val warned: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Current verbosity. Set once per process via [com.chartboost.prebid.ChartboostPrebidAdapter.register]. Thread-safe. */
    @Volatile var level: LogLevel = LogLevel.WARN

    fun d(message: String) {
        if (level >= LogLevel.DEBUG) Log.d(TAG, message)
    }

    fun i(message: String) {
        if (level >= LogLevel.WARN) Log.i(TAG, message)
    }

    fun w(message: String) {
        if (level >= LogLevel.WARN) Log.w(TAG, message)
    }

    /** Logs [message] at most once per process (keyed by the message itself). */
    fun warnOnce(message: String) {
        if (level >= LogLevel.WARN && warned.add(message)) Log.w(TAG, message)
    }
}
