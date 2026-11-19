package com.marketap.sdk.inapp.trigger

import com.marketap.sdk.inapp.trigger.TestCampaigns.campaign
import com.marketap.sdk.model.internal.inapp.OccurrenceCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 사용자가 바뀌면 이전 사용자의 이벤트·예약·횟수가 새 사용자에게 이어지지 않는다. */
class InAppIdentityTest {
    private val countKey = "inapp_trigger_occurrence_non_windowed:c"
    private val reservationKey = "delayed_in_app_reservations"

    private fun harness() = InAppTriggerHarness(
        listOf(
            campaign("c", occurrence = OccurrenceCondition(3)),
            campaign("d", delaySeconds = 30, trigger = "delay_trigger"),
            campaign("now"),
        )
    )

    @Test
    fun `실행기가 밀려 있어도 사용자 변경 즉시 저장된 예약과 횟수를 지운다`() {
        val h = harness()
        h.fire()
        h.fire("delay_trigger")
        h.view.clear()
        h.scheduler.paused = true

        h.changeUser("u2")

        assertNull(h.storage.values[countKey])
        assertNull(h.storage.values[reservationKey])
        h.scheduler.runQueued()
        h.scheduler.advanceBy(60_000)
        assertEquals(listOf("now"), h.shownIds())
    }

    @Test
    fun `큐에 남은 이전 사용자 이벤트는 사용자 변경 뒤 처리하지 않는다`() {
        val h = harness()
        h.scheduler.paused = true
        h.fire()
        h.fire("delay_trigger")
        h.changeUser("u2")
        h.scheduler.runQueued()
        h.scheduler.advanceBy(60_000)

        assertEquals(emptyList<String>(), h.shownIds())
        assertNull(h.storage.values[countKey])
        assertNull(h.storage.values[reservationKey])
    }

    @Test
    fun `목록을 받는 사이 사용자가 바뀌면 띄우지도 세지도 예약하지도 않는다`() {
        val h = harness()
        h.fetching.beforeDeliver = { h.changeUser("u2") }
        h.fire()
        h.fetching.beforeDeliver = { h.changeUser("u3") }
        h.fire("delay_trigger")

        assertEquals(emptyList<String>(), h.shownIds())
        assertNull(h.storage.values[countKey])
        assertFalse(h.coordinator.isScheduled("d"))
    }

    @Test
    fun `userId 를 읽기 전 세대로 보낸 이벤트는 버린다`() {
        val h = harness()
        val before = h.service.currentGeneration()
        h.changeUser("u2")
        h.fire(generation = before)
        assertEquals(emptyList<String>(), h.shownIds())
    }

    @Test
    fun `실행 중인 예약은 사용자가 바뀌면 띄우지 않는다`() {
        val h = harness()
        h.fire("delay_trigger")
        h.fetching.beforeDeliver = { h.changeUser("u2") }
        h.scheduler.advanceBy(30_000)
        assertEquals(emptyList<String>(), h.shownIds())
        assertFalse(h.coordinator.isScheduled("d"))
    }

    @Test
    fun `사용자 변경 뒤 새 사용자의 횟수는 0부터 센다`() {
        val h = InAppTriggerHarness(listOf(campaign("c", occurrence = OccurrenceCondition(3))))
        h.fire()
        h.fire()
        h.changeUser("u2")
        h.fire()
        h.fire()
        assertEquals(emptyList<String>(), h.shownIds())
        h.fire()
        assertEquals(listOf("c"), h.shownIds())
    }

    @Test
    fun `익명에서 로그인하면 기다리던 지연 예약을 그대로 띄운다`() {
        val h = InAppTriggerHarness(listOf(campaign("d", delaySeconds = 30, trigger = "delay_trigger")))
        h.userId = null
        h.fire("delay_trigger")
        h.changeUser("u1")
        h.scheduler.advanceBy(30_000)
        assertEquals(listOf("d"), h.shownIds())
    }

    @Test
    fun `익명에서 로그인해도 누적 횟수를 이어서 센다`() {
        val h = InAppTriggerHarness(listOf(campaign("c", occurrence = OccurrenceCondition(3))))
        h.userId = null
        h.fire()
        h.fire()
        h.changeUser("u1")
        h.fire()
        assertEquals(listOf("c"), h.shownIds())
    }

    @Test
    fun `로그아웃하면 지연 예약과 누적 횟수를 지운다`() {
        val h = harness()
        h.fire()
        h.fire("delay_trigger")
        h.changeUser(null)
        assertNull(h.storage.values[countKey])
        assertNull(h.storage.values[reservationKey])
        h.scheduler.advanceBy(60_000)
        assertFalse(h.coordinator.isScheduled("d"))
    }

    @Test
    fun `switchIdentity 는 지금 사용자를 잠금 안에서 읽어 익명이면 유지하고 다른 사용자면 지운다`() {
        val h = harness()
        h.userId = null
        h.fire("delay_trigger")
        h.service.switchIdentity("u1", { h.userId }) { h.userId = "u1" }
        assertTrue(h.coordinator.isScheduled("d"))

        h.service.switchIdentity("u1", { h.userId }) { h.userId = "u1" }
        assertTrue(h.coordinator.isScheduled("d"))

        h.service.switchIdentity(null, { h.userId }) { h.userId = null }
        h.scheduler.runQueued()
        assertNull(h.storage.values[reservationKey])
        assertFalse(h.coordinator.isScheduled("d"))
    }
}
