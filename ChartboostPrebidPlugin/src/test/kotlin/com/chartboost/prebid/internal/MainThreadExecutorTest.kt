/*
 * Copyright (c) 2026 Chartboost, Inc.
 *
 * Licensed under the MIT License.
 */

package com.chartboost.prebid.internal

import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MainThreadExecutorTest {

    @Test
    fun `execute does not run the block synchronously before the caller returns`() {
        var ran = false
        DefaultMainThreadExecutor.execute { ran = true }
        assertFalse(ran)
    }

    @Test
    fun `execute runs the block on the main looper thread once idled`() {
        var thread: Thread? = null
        DefaultMainThreadExecutor.execute { thread = Thread.currentThread() }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(Looper.getMainLooper().thread, thread)
    }
}
