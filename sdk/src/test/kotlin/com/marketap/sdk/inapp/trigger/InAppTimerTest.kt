package com.marketap.sdk.inapp.trigger

import com.marketap.sdk.inapp.trigger.TestCampaigns.campaign
import com.marketap.sdk.model.internal.inapp.InAppCampaignTimer
import com.marketap.sdk.utils.longAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InAppTimerTest {
    private val timer = InAppCampaignTimer(minutes = 10, revision = "r1")

    @Test
    fun `다시 뜰 때도 최초 노출 시각을 타이머 시작 시각으로 넘긴다`() {
        val h = InAppTriggerHarness(listOf(campaign("a", timer = timer)))
        val first = h.scheduler.now
        h.fire()
        h.view.clear()
        h.scheduler.advanceBy(3 * 60_000)
        h.fire()
        assertEquals(listOf("a", "a"), h.shownIds())
        assertEquals(listOf(first, first), h.view.timerStarts)
    }

    @Test
    fun `타이머가 끝나면 후보에서 빠진다`() {
        val h = InAppTriggerHarness(listOf(campaign("a", timer = timer)))
        h.fire()
        h.view.clear()
        h.scheduler.advanceBy(10 * 60_000)
        h.fire()
        assertEquals(listOf("a"), h.shownIds())
    }

    @Test
    fun `지연 예약이 실행 전에 타이머가 끝나면 띄우지 않는다`() {
        val h = InAppTriggerHarness(listOf(campaign("d", delaySeconds = 60, timer = timer)))
        h.storage.setItem("timer_d_r1", h.scheduler.now - 9 * 60_000 - 30_000, longAdapter)
        h.fire()
        assertTrue(h.coordinator.isScheduled("d"))
        assertEquals(listOf<String>(), h.shownIds())
        h.scheduler.advanceBy(60_000)
        assertEquals(listOf<String>(), h.shownIds())
        assertFalse(h.coordinator.isScheduled("d"))
    }

    @Test
    fun `revision 이 바뀌면 시작 시각을 새로 기록한다`() {
        val h = InAppTriggerHarness(listOf(campaign("a", timer = timer)))
        h.fire()
        h.scheduler.advanceBy(10 * 60_000)
        h.view.clear()
        h.fetching.campaigns = listOf(campaign("a", timer = timer.copy(revision = "r2")))
        h.fire()
        assertEquals(listOf("a", "a"), h.shownIds())
        assertNotEquals(h.view.timerStarts[0], h.view.timerStarts[1])
        assertEquals(h.scheduler.now, h.view.timerStarts[1])
    }

    @Test
    fun `테스트 응답은 시작 시각을 기록하지 않고 만료도 보지 않는다`() {
        val h = InAppTriggerHarness(listOf(campaign("a", timer = timer, isTest = true)))
        h.fire()
        h.scheduler.advanceBy(15 * 60_000)
        h.view.clear()
        h.fire()
        assertEquals(listOf("a", "a"), h.shownIds())
        assertEquals(h.scheduler.now, h.view.timerStarts[1])
        assertEquals(null, h.storage.getItem("timer_a_r1", longAdapter))
    }

    @Test
    fun `상세 응답에 타이머가 없으면 목록의 타이머로 판정한다`() {
        val listed = campaign("a", html = null, timer = timer)
        val h = InAppTriggerHarness(listOf(listed))
        h.fetching.resolved = mapOf("a" to campaign("a"))
        h.fire()
        h.view.clear()
        h.scheduler.advanceBy(10 * 60_000)
        h.fire()
        assertEquals(listOf("a"), h.shownIds())
    }

    @Test
    fun `붙이기 직전 판정은 그 사이 타이머가 끝났으면 거절한다`() {
        val h = InAppTriggerHarness(listOf(campaign("a", timer = timer)))
        h.fire()
        val canMount = h.view.mountChecks.single()
        assertTrue(canMount())
        h.scheduler.advanceBy(10 * 60_000)
        assertFalse(canMount())
    }

    @Test
    fun `테스트 응답은 붙이기 직전에도 만료를 보지 않는다`() {
        val h = InAppTriggerHarness(listOf(campaign("a", timer = timer, isTest = true)))
        h.fire()
        h.scheduler.advanceBy(60 * 60_000)
        assertTrue(h.view.mountChecks.single()())
    }
}
