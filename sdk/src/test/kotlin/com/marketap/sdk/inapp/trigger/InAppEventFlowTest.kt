package com.marketap.sdk.inapp.trigger

import com.marketap.sdk.inapp.trigger.TestCampaigns.campaign
import com.marketap.sdk.model.internal.inapp.Condition
import com.marketap.sdk.model.internal.inapp.EventFilter
import com.marketap.sdk.model.internal.inapp.EventFlow
import com.marketap.sdk.model.internal.inapp.EventFlowStep
import com.marketap.sdk.model.internal.inapp.OccurrenceCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 가입(signup) 뒤 30분 안에 상품 조회(view_product)하면 띄우는 플로우 캠페인. 취소 이벤트는 구매(purchase). */
class InAppEventFlowTest {
    private val stateKey = "inapp_event_flow_states"
    private val minute = 60_000L

    private fun flowCampaign(
        id: String = "f",
        html: String? = "<div>$id</div>",
        reentryBlockSeconds: Long? = null,
        occurrence: OccurrenceCondition? = null,
        delaySeconds: Int? = null,
        version: String = "v1",
    ) = campaign(
        id,
        html = html,
        trigger = ARRIVAL,
        abortEvents = listOf(CANCEL),
        occurrence = occurrence,
        delaySeconds = delaySeconds,
        version = version,
        flow = EventFlow(
            listOf(EventFlowStep(listOf(Condition(EventFilter(ENTRY))), withinSeconds = 1800)),
            reentryBlockSeconds,
        ),
    )

    private fun harness(vararg campaigns: com.marketap.sdk.model.internal.InAppCampaign) =
        InAppTriggerHarness(campaigns.toList())

    private fun InAppTriggerHarness.showNext(name: String) {
        view.clear()
        fire(name)
    }

    @Test
    fun `진입 뒤 도달하면 띄우고 진행을 지운다`() {
        val h = harness(flowCampaign())
        h.fire(ENTRY)
        h.scheduler.advanceBy(10 * minute)
        h.fire(ARRIVAL)

        assertEquals(listOf("f"), h.shownIds())
        assertNull(h.storage.values[stateKey])

        h.showNext(ARRIVAL)
        assertEquals(listOf("f"), h.shownIds())
    }

    @Test
    fun `진입 없이 도달하면 띄우지 않는다`() {
        val h = harness(flowCampaign())
        h.fire(ARRIVAL)
        h.fire(ENTRY)

        assertEquals(emptyList<String>(), h.shownIds())
        assertNotNull(h.storage.values[stateKey])
    }

    @Test
    fun `제한 시간이 지나 도달하면 띄우지 않는다`() {
        val h = harness(flowCampaign())
        h.fire(ENTRY)
        h.scheduler.advanceBy(30 * minute + 1)
        h.fire(ARRIVAL)

        assertEquals(emptyList<String>(), h.shownIds())
    }

    @Test
    fun `취소 이벤트가 오면 진행을 버린다`() {
        val h = harness(flowCampaign())
        h.fire(ENTRY)
        h.fire(CANCEL)
        h.fire(ARRIVAL)

        assertEquals(emptyList<String>(), h.shownIds())
    }

    @Test
    fun `진행 중 취소하면 재진입 제한 동안 진입하지 않고, 제한 중 취소는 제한을 늘리지 않는다`() {
        val h = harness(flowCampaign(reentryBlockSeconds = 3600))
        h.fire(ENTRY)
        h.fire(CANCEL)
        h.scheduler.advanceBy(30 * minute)
        h.fire(CANCEL)
        h.fire(ENTRY)
        h.fire(ARRIVAL)
        assertEquals(emptyList<String>(), h.shownIds())

        h.scheduler.advanceBy(30 * minute)
        h.fire(ENTRY)
        h.fire(ARRIVAL)
        assertEquals(listOf("f"), h.shownIds())
    }

    @Test
    fun `진행이 없을 때의 취소는 재진입을 막지 않는다`() {
        val h = harness(flowCampaign(reentryBlockSeconds = 3600))
        h.fire(CANCEL)
        h.fire(ENTRY)
        h.fire(ARRIVAL)

        assertEquals(listOf("f"), h.shownIds())
    }

    @Test
    fun `다시 진입하면 트리거 횟수를 처음부터 센다`() {
        val h = harness(flowCampaign(occurrence = OccurrenceCondition(2)))
        h.fire(ENTRY)
        h.fire(ARRIVAL)
        h.fire(ENTRY)
        h.fire(ARRIVAL)
        assertEquals(emptyList<String>(), h.shownIds())

        h.fire(ARRIVAL)
        assertEquals(listOf("f"), h.shownIds())
    }

    @Test
    fun `지연 중 취소되면 띄우지 않는다`() {
        val h = harness(flowCampaign(delaySeconds = 30, reentryBlockSeconds = 3600))
        h.fire(ENTRY)
        h.fire(ARRIVAL)
        assertTrue(h.coordinator.isScheduled("f"))
        h.fire(CANCEL)
        h.scheduler.advanceBy(minute)

        assertEquals(emptyList<String>(), h.shownIds())
    }

    @Test
    fun `지연 실행 시 진행이 사라졌으면 띄우지 않는다`() {
        val h = harness(flowCampaign(delaySeconds = 30))
        h.fire(ENTRY)
        h.fire(ARRIVAL)
        h.storage.removeItem(stateKey)
        h.scheduler.advanceBy(minute)

        assertEquals(emptyList<String>(), h.shownIds())
    }

    @Test
    fun `지연 뒤 진행이 남아 있으면 띄우고 진행을 지운다`() {
        val h = harness(flowCampaign(delaySeconds = 30))
        h.fire(ENTRY)
        h.fire(ARRIVAL)
        h.scheduler.advanceBy(minute)

        assertEquals(listOf("f"), h.shownIds())
        assertNull(h.storage.values[stateKey])
    }

    @Test
    fun `상세 조회 중 취소되면 띄우지 않고 재진입 제한은 남는다`() {
        val listed = flowCampaign(html = null, reentryBlockSeconds = 3600)
        val h = harness(listed)
        h.fetching.resolved = mapOf("f" to listed.copy(html = "<div>f</div>"))
        h.fire(ENTRY)
        h.fetching.beforeResolve = { h.fire(CANCEL) }
        h.fire(ARRIVAL)

        assertEquals(listOf("f"), h.fetching.resolveCalls)
        assertEquals(emptyList<String>(), h.shownIds())
        assertTrue(h.storage.values[stateKey]!!.contains("blockedUntil"))
    }

    @Test
    fun `상세 응답에 플로우가 없어도 목록 기준으로 진행을 확인한다`() {
        val listed = flowCampaign(html = null)
        val h = harness(listed)
        h.fetching.resolved = mapOf("f" to campaign("f", trigger = ARRIVAL))
        h.fire(ENTRY)
        h.fetching.beforeResolve = { h.storage.removeItem(stateKey) }
        h.fire(ARRIVAL)

        assertEquals(emptyList<String>(), h.shownIds())
    }

    @Test
    fun `목록 조회가 늦어도 저장된 목록으로 진입을 기록하고 표시는 하지 않는다`() {
        val h = harness(flowCampaign())
        h.fetching.available = false
        h.fetching.cached = listOf(flowCampaign())
        h.fire(ENTRY)
        assertNotNull(h.storage.values[stateKey])

        h.fire(ARRIVAL)
        assertEquals(emptyList<String>(), h.shownIds())

        h.fetching.available = true
        h.fire(ARRIVAL)
        assertEquals(listOf("f"), h.shownIds())
    }

    @Test
    fun `설정 버전이 바뀌면 이전 진행으로 띄우지 않는다`() {
        val h = harness(flowCampaign())
        h.fire(ENTRY)
        h.fetching.campaigns = listOf(flowCampaign(version = "v2"))
        h.fire(ARRIVAL)

        assertEquals(emptyList<String>(), h.shownIds())
        assertTrue(h.storage.values[stateKey]!!.contains("\"version\":\"v1\""))
    }

    @Test
    fun `플로우 없는 캠페인은 그대로 트리거된다`() {
        val h = harness(flowCampaign(), campaign("plain", trigger = ARRIVAL, slot = com.marketap.sdk.model.internal.inapp.InAppDisplaySlot.FLOATING))
        h.fire(ARRIVAL)

        assertEquals(listOf("plain"), h.shownIds())
    }

    @Test
    fun `비회원에서 로그인하면 진행을 이어 간다`() {
        val h = harness(flowCampaign())
        h.userId = null
        h.fire(ENTRY)
        h.changeUser("u1")
        h.fire(ARRIVAL)

        assertEquals(listOf("f"), h.shownIds())
    }

    @Test
    fun `처리 전에 로그인해도 비회원 이벤트로 진입한다`() {
        val h = harness(flowCampaign())
        h.userId = null
        h.scheduler.paused = true
        h.fire(ENTRY)
        h.changeUser("u1")
        h.scheduler.runQueued()
        assertNotNull(h.storage.values[stateKey])

        h.fire(ARRIVAL)
        assertEquals(listOf("f"), h.shownIds())
    }

    @Test
    fun `처리 전에 로그인했다가 다른 회원으로 바뀌면 비회원 이벤트로 진입하지 않는다`() {
        val h = harness(flowCampaign())
        h.userId = null
        h.scheduler.paused = true
        h.fire(ENTRY)
        h.changeUser("u1")
        h.changeUser("u2")
        h.scheduler.runQueued()
        assertNull(h.storage.values[stateKey])

        h.fire(ARRIVAL)
        assertEquals(emptyList<String>(), h.shownIds())
    }

    @Test
    fun `다른 회원으로 바뀌면 진행을 지운다`() {
        val h = harness(flowCampaign())
        h.fire(ENTRY)
        h.changeUser("u2")

        assertNull(h.storage.values[stateKey])
        h.fire(ARRIVAL)
        assertEquals(emptyList<String>(), h.shownIds())
    }

    @Test
    fun `처리 전에 다른 회원으로 바뀌면 이전 회원 이벤트로 진입하지 않는다`() {
        val h = harness(flowCampaign())
        h.scheduler.paused = true
        h.fire(ENTRY)
        h.changeUser("u2")
        h.scheduler.runQueued()

        assertNull(h.storage.values[stateKey])
    }

    @Test
    fun `로그아웃하면 진행을 지운다`() {
        val h = harness(flowCampaign())
        h.fire(ENTRY)
        h.changeUser(null)

        assertNull(h.storage.values[stateKey])
    }

    private companion object {
        const val ENTRY = "signup"
        const val ARRIVAL = "view_product"
        const val CANCEL = "purchase"
    }
}
