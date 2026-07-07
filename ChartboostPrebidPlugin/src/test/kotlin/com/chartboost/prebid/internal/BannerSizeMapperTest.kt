package com.chartboost.prebid.internal

import com.chartboost.sdk.ads.Banner
import org.junit.Assert.assertEquals
import org.junit.Test

class BannerSizeMapperTest {

    @Test
    fun `maps 320x50 to STANDARD`() {
        assertEquals(Banner.BannerSize.STANDARD, BannerSizeMapper.map(320, 50))
    }

    @Test
    fun `maps 300x250 to MEDIUM`() {
        assertEquals(Banner.BannerSize.MEDIUM, BannerSizeMapper.map(300, 250))
    }

    @Test
    fun `maps 728x90 to LEADERBOARD`() {
        assertEquals(Banner.BannerSize.LEADERBOARD, BannerSizeMapper.map(728, 90))
    }

    @Test
    fun `maps a near-standard size to the nearest STANDARD`() {
        assertEquals(Banner.BannerSize.STANDARD, BannerSizeMapper.map(320, 100))
    }

    @Test
    fun `maps a near-medium size to the nearest MEDIUM`() {
        assertEquals(Banner.BannerSize.MEDIUM, BannerSizeMapper.map(336, 280))
    }

    @Test
    fun `maps a wide tablet size to the nearest LEADERBOARD`() {
        assertEquals(Banner.BannerSize.LEADERBOARD, BannerSizeMapper.map(970, 90))
    }

    @Test
    fun `defaults to STANDARD when dimensions are missing`() {
        assertEquals(Banner.BannerSize.STANDARD, BannerSizeMapper.map(0, 0))
        assertEquals(Banner.BannerSize.STANDARD, BannerSizeMapper.map(-1, -1))
    }
}
