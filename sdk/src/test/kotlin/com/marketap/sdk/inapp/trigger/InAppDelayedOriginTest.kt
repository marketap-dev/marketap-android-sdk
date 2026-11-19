package com.marketap.sdk.inapp.trigger

import com.marketap.sdk.inapp.trigger.TestCampaigns.campaign
import com.marketap.sdk.model.internal.inapp.InAppDisplaySlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class InAppDelayedOriginTest {

    @Test
    fun `즉시 노출은 이벤트의 출처를 붙여 띄운다`() {
        val h = InAppTriggerHarness(listOf(campaign("a")))
        val origin = FakeOrigin()
        h.fire(origin = origin)
        assertEquals(listOf("a"), h.shownIds())
        assertSame(origin, h.view.origins.single())
    }

    @Test
    fun `예약을 실행할 때 출처 웹뷰가 살아 있으면 그 출처를 붙여 띄운다`() {
        val h = InAppTriggerHarness(listOf(campaign("a", delaySeconds = 30)))
        val origin = FakeOrigin()
        h.fire(origin = origin)
        h.scheduler.advanceBy(30_000)
        assertEquals(listOf("a"), h.shownIds())
        assertSame(origin, h.view.origins.single())
    }

    @Test
    fun `예약을 기다리는 사이 출처 웹뷰가 사라지면 예약을 버리지 않고 출처 없이 띄운다`() {
        val h = InAppTriggerHarness(listOf(campaign("a", delaySeconds = 30)))
        val origin = FakeOrigin()
        h.fire(origin = origin)
        origin.gone = true
        h.scheduler.advanceBy(30_000)
        assertEquals(listOf("a"), h.shownIds())
        assertNull(h.view.origins.single())
    }

    @Test
    fun `앱 화면 이벤트로 만든 예약은 출처 없이 띄운다`() {
        val h = InAppTriggerHarness(listOf(campaign("a", delaySeconds = 30)))
        h.fire()
        h.scheduler.advanceBy(30_000)
        assertNull(h.view.origins.single())
    }

    @Test
    fun `저장소에서 복원한 예약은 출처를 잃고 출처 없이 띄운다`() {
        val h = InAppTriggerHarness(listOf(campaign("a", delaySeconds = 30)))
        h.fire(origin = FakeOrigin())
        h.restart()
        h.scheduler.advanceBy(30_000)
        assertEquals(listOf("a"), h.shownIds())
        assertNull(h.view.origins.single())
    }

    @Test
    fun `화면 전환 신호는 아직 실행 전인 예약을 버리지 않는다`() {
        val h = InAppTriggerHarness(listOf(campaign("a", delaySeconds = 30, slot = InAppDisplaySlot.FLOATING)))
        val origin = FakeOrigin()
        h.fire(origin = origin)
        h.service.screenChanged()
        assertTrue(h.coordinator.isScheduled("a"))
        h.scheduler.advanceBy(30_000)
        assertEquals(listOf("a"), h.shownIds())
        assertSame(origin, h.view.origins.single())
    }

    @Test
    fun `취소 이벤트로 버린 예약의 출처는 다음 예약에 남지 않는다`() {
        val h = InAppTriggerHarness(listOf(campaign("a", delaySeconds = 30, abortEvents = listOf(TestCampaigns.ABORT))))
        h.fire(origin = FakeOrigin())
        h.fire(TestCampaigns.ABORT)
        h.fire()
        h.scheduler.advanceBy(30_000)
        assertEquals(listOf("a"), h.shownIds())
        assertNull(h.view.origins.single())
    }
}
