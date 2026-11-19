package com.marketap.sdk.inapp.trigger

import com.marketap.sdk.domain.service.inapp.SerialInAppScheduler
import com.marketap.sdk.inapp.trigger.TestCampaigns.campaign
import com.marketap.sdk.model.internal.inapp.GlobalFrequencyCapRule
import com.marketap.sdk.model.internal.inapp.InAppDisplaySlot
import com.marketap.sdk.model.internal.inapp.OccurrenceCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class InAppDelayTest {
    private val tenMinutes = 10 * 60_000L

    @Test
    fun `예약 시각 전에는 띄우지 않고, 예약 시각에 포그라운드면 띄운다`() {
        val h = InAppTriggerHarness(listOf(campaign("a", delaySeconds = 30)))
        h.fire()
        h.scheduler.advanceBy(29_999)
        assertEquals(emptyList<String>(), h.shownIds())
        h.scheduler.advanceBy(1)
        assertEquals(listOf("a"), h.shownIds())
        assertFalse(h.coordinator.isScheduled("a"))
    }

    @Test
    fun `예약 시각에 백그라운드면 보류했다가 포그라운드 복귀 때 띄운다`() {
        val h = InAppTriggerHarness(listOf(campaign("a", delaySeconds = 30)))
        h.fire()
        h.foreground = false
        h.scheduler.advanceBy(60_000)
        assertEquals(emptyList<String>(), h.shownIds())

        h.foreground = true
        h.service.restoreDelayed()
        assertEquals(listOf("a"), h.shownIds())
    }

    @Test
    fun `만료(예약 시각 + 10분) 뒤에 복귀하면 버린다`() {
        val h = InAppTriggerHarness(listOf(campaign("a", delaySeconds = 30)))
        h.fire()
        h.foreground = false
        h.scheduler.advanceBy(30_000 + tenMinutes)
        h.foreground = true
        h.service.restoreDelayed()
        assertEquals(emptyList<String>(), h.shownIds())
        assertFalse(h.coordinator.isScheduled("a"))
    }

    @Test
    fun `앱을 다시 켜면 저장된 예약을 복원해 예약 시각에 띄운다`() {
        val h = InAppTriggerHarness(listOf(campaign("a", delaySeconds = 30)))
        h.foreground = false
        h.fire()
        h.restart()
        h.foreground = true
        h.scheduler.advanceBy(30_000)
        assertEquals(listOf("a"), h.shownIds())
    }

    @Test
    fun `꺼져 있는 동안 예약 시각이 지나도 만료 전에 다시 켜면 띄운다`() {
        val h = InAppTriggerHarness(listOf(campaign("a", delaySeconds = 30)))
        h.fire()
        h.foreground = false
        h.scheduler.advanceBy(5 * 60_000L)
        h.foreground = true
        h.restart()
        assertEquals(listOf("a"), h.shownIds())
    }

    @Test
    fun `취소 이벤트가 오면 예약을 지운다`() {
        val h = InAppTriggerHarness(listOf(campaign("a", delaySeconds = 30, abortEvents = listOf(TestCampaigns.ABORT))))
        h.fire()
        h.fire(TestCampaigns.ABORT)
        h.scheduler.advanceBy(60_000)
        assertEquals(emptyList<String>(), h.shownIds())
        assertFalse(h.coordinator.isScheduled("a"))
    }

    @Test
    fun `재실행 직후 목록을 받기 전에 온 취소 이벤트도 저장된 취소 조건으로 예약을 지운다`() {
        val h = InAppTriggerHarness(listOf(campaign("a", delaySeconds = 30, abortEvents = listOf(TestCampaigns.ABORT))))
        h.fire()
        h.restart()
        h.fetching.available = false
        h.fire(TestCampaigns.ABORT)
        assertFalse(h.coordinator.isScheduled("a"))
    }

    @Test
    fun `예약은 캠페인당 하나이고, 예약 중 재트리거에는 다음 후보가 바로 뜬다`() {
        val h = InAppTriggerHarness(listOf(campaign("a", delaySeconds = 1), campaign("b")))
        h.fire()
        assertEquals(emptyList<String>(), h.shownIds())

        h.fire()
        assertEquals(listOf("b"), h.shownIds())

        h.view.clear()
        h.scheduler.advanceBy(1_000)
        assertEquals(listOf("b", "a"), h.shownIds())
    }

    @Test
    fun `예약하면 누적 횟수를 0으로 되돌린다`() {
        val h = InAppTriggerHarness(listOf(campaign("a", delaySeconds = 30, occurrence = OccurrenceCondition(2))))
        h.fire()
        h.fire()
        assertTrue(h.coordinator.isScheduled("a"))
        h.scheduler.advanceBy(30_000)
        assertEquals(listOf("a"), h.shownIds())

        h.view.clear()
        h.fire()
        assertFalse(h.coordinator.isScheduled("a"))
    }

    @Test
    fun `같은 자리에 인앱이 떠 있으면 예약을 버린다`() {
        val h = InAppTriggerHarness(listOf(campaign("a", delaySeconds = 30)))
        h.fire()
        h.view.occupy(InAppDisplaySlot.POPUP)
        h.scheduler.advanceBy(30_000)
        h.view.clear()
        h.service.restoreDelayed()
        assertEquals(emptyList<String>(), h.shownIds())
        assertFalse(h.coordinator.isScheduled("a"))
    }

    @Test
    fun `다른 자리에 인앱이 떠 있으면 예약을 띄운다`() {
        val h = InAppTriggerHarness(listOf(campaign("f", slot = InAppDisplaySlot.FLOATING, delaySeconds = 30)))
        h.fire()
        h.view.occupy(InAppDisplaySlot.POPUP)
        h.scheduler.advanceBy(30_000)
        assertEquals(listOf("f"), h.shownIds())
    }

    @Test
    fun `팝업 후보가 예약되어도 같은 이벤트의 플로팅 후보는 바로 뜬다`() {
        val h = InAppTriggerHarness(
            listOf(
                campaign("p", delaySeconds = 30),
                campaign("p2"),
                campaign("f", slot = InAppDisplaySlot.FLOATING),
            )
        )
        h.fire()
        assertEquals(listOf("f"), h.shownIds())
        assertTrue(h.coordinator.isScheduled("p"))
    }

    @Test
    fun `실행 직전 캠페인이 목록에서 사라졌거나 숨김이면 버린다`() {
        val h = InAppTriggerHarness(listOf(campaign("a", delaySeconds = 30), campaign("b", delaySeconds = 30, slot = InAppDisplaySlot.FLOATING)))
        h.fire()
        h.fetching.campaigns = listOf(campaign("b", delaySeconds = 30, slot = InAppDisplaySlot.FLOATING))
        h.exposing.hidden.add("b")
        h.scheduler.advanceBy(30_000)
        assertEquals(emptyList<String>(), h.shownIds())
        assertFalse(h.coordinator.isScheduled("a"))
        assertFalse(h.coordinator.isScheduled("b"))
    }

    @Test
    fun `목록 재확인이 타임아웃이면 마지막 캐시로 판정한다`() {
        val a = campaign("a", delaySeconds = 30)
        val h = InAppTriggerHarness(listOf(a))
        h.fire()
        h.fetching.available = false
        h.fetching.cached = listOf(a)
        h.scheduler.advanceBy(30_000)
        assertEquals(listOf("a"), h.shownIds())
    }

    @Test
    fun `목록도 캐시도 없으면 남겨 두었다가 만료 뒤 버린다`() {
        val a = campaign("a", delaySeconds = 30)
        val h = InAppTriggerHarness(listOf(a))
        h.fire()
        h.fetching.available = false
        h.scheduler.advanceBy(30_000)
        assertTrue(h.coordinator.isScheduled("a"))

        h.fetching.available = true
        h.scheduler.advanceBy(30_000)
        assertEquals(listOf("a"), h.shownIds())

        val h2 = InAppTriggerHarness(listOf(a))
        h2.fire()
        h2.fetching.available = false
        h2.scheduler.advanceBy(30_000 + tenMinutes)
        assertFalse(h2.coordinator.isScheduled("a"))
        assertEquals(emptyList<String>(), h2.shownIds())
    }

    @Test
    fun `html 이 없는 예약은 실행할 때 트리거 이벤트로 상세를 받아 띄운다`() {
        val h = InAppTriggerHarness(listOf(campaign("a", html = null, delaySeconds = 30)))
        h.fetching.resolved = mapOf("a" to campaign("a"))
        h.fire()
        assertEquals(emptyList<String>(), h.fetching.resolveCalls)
        h.scheduler.advanceBy(30_000)
        assertEquals(listOf("a"), h.fetching.resolveCalls)
        assertEquals(listOf("a"), h.shownIds())
    }

    @Test
    fun `복원한 예약의 노출·클릭 이벤트는 띄우는 시점의 사용자·세션을 싣는다`() {
        val h = InAppTriggerHarness(listOf(campaign("a", delaySeconds = 30)))
        h.fire()
        h.restart()
        h.sessionId = "s2"
        h.scheduler.advanceBy(30_000)
        h.view.sessions.single().onClick("loc")

        val impression = h.impressionEvents().single()
        assertEquals("s2", impression.properties!!["mkt_session_id"])
        assertEquals("u1", impression.userId)
        val click = h.backend.events.single { it.name == "mkt_click_message" }
        assertEquals("s2", click.properties!!["mkt_session_id"])
        assertEquals(impression.properties!!["mkt_message_id"], click.properties!!["mkt_message_id"])
    }

    @Test
    fun `사용자가 바뀌면 예약을 모두 지운다`() {
        val h = InAppTriggerHarness(listOf(campaign("a", delaySeconds = 30)))
        h.fire()
        h.changeUser("u2")
        h.scheduler.advanceBy(60_000)
        assertEquals(emptyList<String>(), h.shownIds())
        assertFalse(h.coordinator.isScheduled("a"))
    }

    @Test
    fun `두 스레드에서 같은 이벤트를 동시에 보내도 횟수를 잃지 않고 예약은 하나다`() {
        val serial = SerialInAppScheduler()
        val counted = InAppTriggerHarness(listOf(campaign("c", occurrence = OccurrenceCondition(1_000_000))))
        val delayed = InAppTriggerHarness(listOf(campaign("d", delaySeconds = 30)))
        counted.build(serial)
        delayed.build(serial)

        val pool = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        repeat(2) {
            pool.execute {
                start.await()
                repeat(200) {
                    counted.fire()
                    delayed.fire()
                }
            }
        }
        start.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        val drained = CountDownLatch(1)
        serial.execute { drained.countDown() }
        assertTrue(drained.await(10, TimeUnit.SECONDS))
        serial.shutdown()

        val state = counted.storage.values["inapp_trigger_occurrence_non_windowed:c"]!!
        assertTrue(state, state.contains("\"totalCount\":400"))
        val reservations = delayed.storage.values["delayed_in_app_reservations"]!!
        assertEquals(1, Regex("\"campaignId\":\"d\"").findAll(reservations).count())
    }

    @Test
    fun `상세 fetch 는 호출 스레드가 아니라 인앱 실행기에서 한다`() {
        val serial = SerialInAppScheduler()
        val h = InAppTriggerHarness(listOf(campaign("a", html = null)))
        h.fetching.resolved = mapOf("a" to campaign("a"))
        h.build(serial)
        h.fire()
        val drained = CountDownLatch(1)
        serial.execute { drained.countDown() }
        assertTrue(drained.await(5, TimeUnit.SECONDS))
        serial.shutdown()

        assertEquals(listOf("marketap-inapp"), h.fetching.resolveThreads)
    }

    @Test
    fun `화면이 잠깐 없는 사이 예약 시각이 되면 띄우지 않고, 다시 resume 되면 곧 띄운다`() {
        val h = InAppTriggerHarness(listOf(campaign("a", delaySeconds = 30)))
        h.fire()
        h.displayable = false
        h.scheduler.advanceBy(30_000)
        assertEquals(emptyList<String>(), h.shownIds())
        assertTrue(h.coordinator.isScheduled("a"))

        h.displayable = true
        h.scheduler.advanceBy(500)
        assertEquals(listOf("a"), h.shownIds())
    }

    @Test
    fun `백그라운드로 확정되면 짧은 재시도를 멈추고 복귀를 기다린다`() {
        val h = InAppTriggerHarness(listOf(campaign("a", delaySeconds = 30)))
        h.fire()
        h.foreground = false
        h.scheduler.advanceBy(30_000)
        assertEquals(0, h.scheduler.pendingCount)
        assertTrue(h.coordinator.isScheduled("a"))
    }

    @Test
    fun `띄우기 직전에 화면이 없어지면 예약을 소진하지 않는다`() {
        val h = InAppTriggerHarness(listOf(campaign("a", delaySeconds = 30)))
        h.fire()
        h.fetching.beforeDeliver = { h.displayable = false }
        h.scheduler.advanceBy(30_000)
        assertEquals(emptyList<String>(), h.shownIds())
        assertTrue(h.coordinator.isScheduled("a"))

        h.displayable = true
        h.scheduler.advanceBy(30_000)
        assertEquals(listOf("a"), h.shownIds())
    }

    @Test
    fun `예약 실행 때 전체 빈도 한도에 닿았으면 띄우지 않고 버린다`() {
        val h = InAppTriggerHarness(listOf(campaign("a", delaySeconds = 30, tagIds = listOf("t"))))
        h.fetching.globalCaps = listOf(GlobalFrequencyCapRule(tagIds = listOf("t"), limit = 1, durationMinutes = 60))
        h.fire()
        h.exposing.globalCapReached = { true }
        h.scheduler.advanceBy(30_000)
        assertEquals(emptyList<String>(), h.shownIds())
        assertFalse(h.coordinator.isScheduled("a"))
    }

    @Test
    fun `목록을 받지 못하면 저장된 캐시로 판정해 예약을 띄운다`() {
        val h = InAppTriggerHarness(listOf(campaign("a", delaySeconds = 30)))
        h.fire()
        h.fetching.available = false
        h.fetching.cached = listOf(campaign("a", delaySeconds = 30))
        h.scheduler.advanceBy(30_000)
        assertEquals(listOf("a"), h.shownIds())
    }

    @Test
    fun `지연 예약 중에 다른 또는 조건 이벤트가 와도 다시 예약하지 않는다`() {
        val h = InAppTriggerHarness(listOf(campaign("a", delaySeconds = 30, anyOf = listOf("view_a", "view_b"))))
        h.fire("view_a")
        h.scheduler.advanceBy(10_000)
        h.fire("view_b")
        assertEquals(1, h.scheduler.pendingCount)
        h.scheduler.advanceBy(20_000)
        assertEquals(listOf("a"), h.shownIds())
        h.scheduler.advanceBy(60_000)
        assertEquals(listOf("a"), h.shownIds())
    }
}
