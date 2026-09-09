/*
 * Copyright (c) 2026 Chartboost, Inc.
 *
 * Licensed under the MIT License.
 */

package com.chartboost.prebid.internal

import com.chartboost.sdk.ads.Banner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BannerSizeMapperTest {

    @Test
    fun `every size the host SDK supports resolves to itself at its own dimensions`() {
        // A size's own slot admits nothing larger than itself, so identity holds for every entry. Driven off
        // entries rather than hardcoded names so it keeps holding when the pinned SDK gains HALFPAGE.
        Banner.BannerSize.entries.forEach { size ->
            assertEquals(size, BannerSizeMapper.map(size.width, size.height))
        }
    }

    @Test
    fun `fills a full-width slot with the largest size that fits inside it`() {
        // The case that makes exact matching untenable: a device-width request is the most common banner
        // slot there is, and STANDARD 320x50 fits inside it.
        assertEquals(Banner.BannerSize.STANDARD, BannerSizeMapper.map(412, 50))
    }

    @Test
    fun `rejects a slot one pixel too short for the smallest size`() {
        // 320x49: STANDARD needs 50dp of height, so nothing fits and the bid is a no-fill.
        assertNull(BannerSizeMapper.map(320, 49))
    }

    @Test
    fun `prefers the larger size when two both fit`() {
        // 728x250 admits STANDARD (16k), MEDIUM (75k) and LEADERBOARD (65.5k). Largest area wins, so this
        // pins area rather than width or declaration order.
        assertEquals(Banner.BannerSize.MEDIUM, BannerSizeMapper.map(728, 250))
    }

    @Test
    fun `never returns a size wider than the slot`() {
        // 250 is narrower than every Chartboost size, so no amount of height makes one fit.
        assertNull(BannerSizeMapper.map(250, 250))
        assertNull(BannerSizeMapper.map(250, 5000))
    }

    @Test
    fun `rejects a slot one pixel too narrow for the smallest size`() {
        // Width-side counterpart to the 320x49 height cliff. The filter has two structurally identical
        // clauses, so without this an off-by-one slip in the width clause alone would go unnoticed.
        assertNull(BannerSizeMapper.map(319, 50))
    }

    @Test
    fun `resolves a half page slot to the largest size the pinned SDK can fit`() {
        // Asserted concretely rather than recomputed, so this test cannot agree with a bug in the mapper.
        // Against the pinned 9.14.0, HALFPAGE 300x600 exists and its area (180000) beats every other size,
        // so a 300x600 slot now resolves HALFPAGE where it used to resolve MEDIUM under 9.12.0. If the pin
        // ever moves again, this expectation must be re-checked by hand, which is the point: the flip
        // should be a conscious edit, not silent.
        assertEquals(Banner.BannerSize.HALFPAGE, BannerSizeMapper.map(300, 600))
    }

    @Test
    fun `resolves a slot larger than half page to half page`() {
        // 400x700 comfortably admits HALFPAGE (300x600, area 180000), which still beats every other size.
        assertEquals(Banner.BannerSize.HALFPAGE, BannerSizeMapper.map(400, 700))
    }

    @Test
    fun `rejects half page for a slot one pixel too short and falls back to medium`() {
        // 300x599: one pixel short of the 600 HALFPAGE needs, so it falls back to the next-largest size
        // that fits, MEDIUM 300x250.
        assertEquals(Banner.BannerSize.MEDIUM, BannerSizeMapper.map(300, 599))
    }

    @Test
    fun `never returns a size exceeding the slot in either dimension`() {
        listOf(412 to 50, 728 to 250, 300 to 600, 1000 to 1000, 320 to 50).forEach { (w, h) ->
            val resolved = BannerSizeMapper.map(w, h) ?: return@forEach
            assertEquals("width overflow for ${w}x$h", true, resolved.width <= w)
            assertEquals("height overflow for ${w}x$h", true, resolved.height <= h)
        }
    }

    @Test
    fun `rejects a zero size`() {
        assertNull(BannerSizeMapper.map(0, 0))
    }

    @Test
    fun `rejects a negative size`() {
        assertNull(BannerSizeMapper.map(-1, -1))
    }

    @Test
    fun `rejects a size with only the height missing`() {
        assertNull(BannerSizeMapper.map(320, 0))
    }

    @Test
    fun `rejects a size with only the width missing`() {
        assertNull(BannerSizeMapper.map(0, 50))
    }

    @Test
    fun `reports the host SDK's renderable sizes for error messages`() {
        val sizes = BannerSizeMapper.supportedSizes()
        Banner.BannerSize.entries.forEach { size ->
            assertEquals(true, sizes.contains("${size.width}x${size.height}"))
        }
    }
}
