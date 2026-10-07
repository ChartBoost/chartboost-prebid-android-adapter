/*
 * Copyright (c) 2026 Chartboost, Inc.
 *
 * Licensed under the MIT License.
 */

package com.chartboost.prebid.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class SingleFireLatchTest {

    @Test
    fun `fires true on the first call`() {
        assertTrue(SingleFireLatch().fire())
    }

    @Test
    fun `fires false on every call after the first`() {
        val latch = SingleFireLatch()
        latch.fire()
        assertFalse(latch.fire())
        assertFalse(latch.fire())
    }

    @Test
    fun `hasFired reflects whether fire has been claimed without claiming it`() {
        val latch = SingleFireLatch()
        assertFalse(latch.hasFired)
        assertFalse(latch.hasFired) // reading does not claim the latch
        latch.fire()
        assertTrue(latch.hasFired)
    }

    @Test
    fun `returns true exactly once under concurrent fire calls`() {
        val latch = SingleFireLatch()
        val trues = AtomicInteger(0)
        val threads = 64
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        val pool = Executors.newFixedThreadPool(threads)
        repeat(threads) {
            pool.execute {
                start.await()
                if (latch.fire()) trues.incrementAndGet()
                done.countDown()
            }
        }
        start.countDown()
        done.await()
        pool.shutdown()

        assertEquals(1, trues.get())
    }
}
