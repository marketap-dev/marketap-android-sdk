package com.marketap.sdk.inapp.trigger

import com.marketap.sdk.domain.service.inapp.CampaignExposureService
import com.marketap.sdk.domain.service.inapp.ruleTagsIntersect
import com.marketap.sdk.model.internal.inapp.GlobalFrequencyCapRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalFrequencyCapTest {
    private var now = 10_000_000L
    private val exposure = CampaignExposureService(InMemoryStorage(), now = { now })

    @Test
    fun `빈 규칙 태그는 모든 캠페인에 해당`() {
        assertTrue(ruleTagsIntersect(emptyList(), emptyList()))
        assertTrue(ruleTagsIntersect(emptyList(), listOf("a")))
    }

    @Test
    fun `규칙 태그와 캠페인 태그가 하나라도 겹치면 해당`() {
        assertTrue(ruleTagsIntersect(listOf("a", "b"), listOf("b")))
        assertFalse(ruleTagsIntersect(listOf("a"), listOf("b")))
        assertFalse(ruleTagsIntersect(listOf("a"), emptyList()))
    }

    @Test
    fun `기간 안 노출이 한도에 닿으면 막는다`() {
        val rule = GlobalFrequencyCapRule(limit = 2, durationMinutes = 60)
        exposure.recordGlobalImpression(listOf("x"))
        assertFalse(exposure.hasReachedGlobalFrequencyCap(rule))
        exposure.recordGlobalImpression(emptyList())
        assertTrue(exposure.hasReachedGlobalFrequencyCap(rule))
    }

    @Test
    fun `기간이 지난 노출은 세지 않는다`() {
        val rule = GlobalFrequencyCapRule(limit = 1, durationMinutes = 60)
        exposure.recordGlobalImpression(emptyList())
        now += 61 * 60_000L
        assertFalse(exposure.hasReachedGlobalFrequencyCap(rule))
    }

    @Test
    fun `규칙 태그와 겹치는 노출만 센다`() {
        val rule = GlobalFrequencyCapRule(limit = 1, durationMinutes = 60, tagIds = listOf("sale"))
        exposure.recordGlobalImpression(listOf("other"))
        assertFalse(exposure.hasReachedGlobalFrequencyCap(rule))
        exposure.recordGlobalImpression(listOf("sale"))
        assertTrue(exposure.hasReachedGlobalFrequencyCap(rule))
    }

    @Test
    fun `보관 기간이 지난 전체 빈도 기록은 쓰고 읽을 때 지운다`() {
        val storage = InMemoryStorage()
        val day = 24 * 60 * 60_000L
        val pruning = CampaignExposureService(storage, { now }, { day })
        pruning.recordGlobalImpression(listOf("a"))
        now += 2 * day
        pruning.recordGlobalImpression(listOf("b"))
        assertEquals(1, Regex("\"ts\"").findAll(storage.values["global_impressions"]!!).count())

        now += 2 * day
        assertFalse(pruning.hasReachedGlobalFrequencyCap(GlobalFrequencyCapRule(limit = 1, durationMinutes = 10 * 24 * 60)))
        assertEquals("[]", storage.values["global_impressions"])
    }

    @Test
    fun `보관 기간은 1년과 가장 긴 규칙 기간 중 긴 쪽이다`() {
        val year = CampaignExposureService.MIN_GLOBAL_IMPRESSION_RETENTION_MS
        assertEquals(year, CampaignExposureService.retentionFor(emptyList()))
        assertEquals(year, CampaignExposureService.retentionFor(listOf(GlobalFrequencyCapRule(limit = 1, durationMinutes = 60))))
        val twoYears = 2 * 365 * 24 * 60
        assertEquals(twoYears * 60_000L, CampaignExposureService.retentionFor(listOf(GlobalFrequencyCapRule(limit = 1, durationMinutes = twoYears))))
    }
}
