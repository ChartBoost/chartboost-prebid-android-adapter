/*
 * Copyright (c) 2026 Chartboost, Inc.
 *
 * Licensed under the MIT License.
 */

package com.chartboost.prebid.internal

import com.chartboost.prebid.LogLevel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PluginLogTest {

    @After
    fun resetLogLevel() {
        PluginLog.level = LogLevel.WARN
    }

    @Test
    fun `NONE silences debug, info, warn, and warnOnce`() {
        PluginLog.level = LogLevel.NONE
        PluginLog.d("plugin-log-test-none-d")
        PluginLog.i("plugin-log-test-none-i")
        PluginLog.w("plugin-log-test-none-w")
        PluginLog.warnOnce("plugin-log-test-none-warnonce")
        assertTrue(ShadowLog.getLogsForTag(PluginLog.TAG).isEmpty())
    }

    @Test
    fun `warnOnce logs a given key exactly once`() {
        PluginLog.level = LogLevel.WARN
        val message = "plugin-log-test-warnonce-dedupe"
        PluginLog.warnOnce(message)
        PluginLog.warnOnce(message)
        assertEquals(1, ShadowLog.getLogsForTag(PluginLog.TAG).count { it.msg == message })
    }

    @Test
    fun `i emits when the level is WARN because it gates at greater than or equal to WARN by design`() {
        PluginLog.level = LogLevel.WARN
        val message = "plugin-log-test-i-at-warn-level"
        PluginLog.i(message)
        assertEquals(1, ShadowLog.getLogsForTag(PluginLog.TAG).count { it.msg == message })
    }
}
