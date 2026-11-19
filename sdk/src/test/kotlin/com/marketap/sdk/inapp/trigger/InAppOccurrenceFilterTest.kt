package com.marketap.sdk.inapp.trigger

import com.marketap.sdk.inapp.trigger.TestCampaigns.campaign
import com.marketap.sdk.model.internal.inapp.FrequencyCap
import com.marketap.sdk.model.internal.inapp.GlobalFrequencyCapRule
import com.marketap.sdk.model.internal.inapp.OccurrenceCondition
import com.marketap.sdk.model.internal.inapp.SendableTimeWindow
import com.marketap.sdk.model.internal.inapp.TimeRange
import com.marketap.sdk.model.internal.inapp.WeeklyTimeRule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/** 후보 필터(시간대·전체 빈도·숨김·빈도 제한)와 이벤트 횟수 조건의 연결. */
class InAppOccurrenceFilterTest {

    @Test
    fun `횟수 조건을 채운 이벤트에서 뜨고, 뜬 뒤에는 다시 0부터 센다`() {
        val h = InAppTriggerHarness(listOf(campaign("a", occurrence = OccurrenceCondition(2))))
        h.fire()
        assertEquals(emptyList<String>(), h.shownIds())
        h.fire()
        assertEquals(listOf("a"), h.shownIds())

        h.view.clear()
        h.fire()
        assertEquals(listOf("a"), h.shownIds())
        h.fire()
        assertEquals(listOf("a", "a"), h.shownIds())
    }

    @Test
    fun `또는 조건의 서로 다른 이벤트를 함께 세어 횟수를 채운다`() {
        val h = InAppTriggerHarness(
            listOf(campaign("a", occurrence = OccurrenceCondition(2), anyOf = listOf("view_a", "view_b")))
        )
        h.fire("view_a")
        assertEquals(emptyList<String>(), h.shownIds())
        h.fire(TestCampaigns.TRIGGER)
        assertEquals(emptyList<String>(), h.shownIds())
        h.fire("view_b")
        assertEquals(listOf("a"), h.shownIds())
    }

    @Test
    fun `취소 이벤트가 오면 누적 횟수를 0으로 되돌린다`() {
        val h = InAppTriggerHarness(
            listOf(campaign("a", occurrence = OccurrenceCondition(2), abortEvents = listOf(TestCampaigns.ABORT)))
        )
        h.fire()
        h.fire(TestCampaigns.ABORT)
        h.fire()
        assertEquals(emptyList<String>(), h.shownIds())
        h.fire()
        assertEquals(listOf("a"), h.shownIds())
    }

    @Test
    fun `숨김 중에는 횟수를 세지 않는다`() {
        val h = InAppTriggerHarness(listOf(campaign("a", occurrence = OccurrenceCondition(2))))
        h.exposing.hidden.add("a")
        h.fire()
        h.fire()
        h.exposing.hidden.clear()
        h.fire()
        assertEquals(emptyList<String>(), h.shownIds())
        h.fire()
        assertEquals(listOf("a"), h.shownIds())
    }

    @Test
    fun `빈도 제한 중에는 횟수를 세지 않는다`() {
        val h = InAppTriggerHarness(
            listOf(campaign("a", occurrence = OccurrenceCondition(2), frequencyCap = FrequencyCap(1, 60)))
        )
        h.exposing.capped.add("a")
        h.fire()
        h.fire()
        h.exposing.capped.clear()
        h.fire()
        assertEquals(emptyList<String>(), h.shownIds())
    }

    @Test
    fun `노출 가능 시간대 밖이면 띄우지도 세지도 않는다`() {
        val seoul = TimeZone.getTimeZone("Asia/Seoul")
        val mondayNoon = Calendar.getInstance(seoul).apply {
            clear()
            set(2026, Calendar.NOVEMBER, 9, 12, 0)
        }.timeInMillis
        val window = SendableTimeWindow(listOf(WeeklyTimeRule("MONDAY", listOf(TimeRange(9 * 60, 13 * 60)))))
        val h = InAppTriggerHarness(
            listOf(campaign("a", occurrence = OccurrenceCondition(2), window = window)),
            scheduler = FakeScheduler(now = mondayNoon + 2 * 60 * 60_000L),
        )
        h.fire()
        h.fire()
        assertEquals(emptyList<String>(), h.shownIds())

        h.scheduler.now = mondayNoon
        h.fire()
        assertEquals(emptyList<String>(), h.shownIds())
        h.fire()
        assertEquals(listOf("a"), h.shownIds())
    }

    @Test
    fun `전체 빈도 한도에 닿으면 태그가 겹치는 캠페인을 거르고, 전체 빈도 제외 캠페인은 띄운다`() {
        val h = InAppTriggerHarness(
            listOf(
                campaign("sale", tagIds = listOf("sale")),
                campaign("other", tagIds = listOf("other")),
            )
        )
        h.fetching.globalCaps = listOf(GlobalFrequencyCapRule(limit = 1, durationMinutes = 60, tagIds = listOf("sale")))
        h.exposing.globalCapReached = { true }
        h.fire()
        assertEquals(listOf("other"), h.shownIds())

        h.view.clear()
        h.fetching.campaigns = listOf(campaign("sale", tagIds = listOf("sale"), disableGlobalFrequencyCap = true))
        h.fire()
        assertEquals(listOf("other", "sale"), h.shownIds())
    }

    @Test
    fun `노출하면 전체 빈도용 노출을 캠페인 태그로 기록한다`() {
        val h = InAppTriggerHarness(listOf(campaign("a", tagIds = listOf("t1"))))
        h.fire()
        assertEquals(listOf(listOf("t1")), h.exposing.globalImpressions)
        assertEquals(listOf("a"), h.exposing.impressions)
    }

    @Test
    fun `사용자가 바뀌면 누적 횟수를 모두 지운다`() {
        val h = InAppTriggerHarness(listOf(campaign("a", occurrence = OccurrenceCondition(2))))
        h.fire()
        h.changeUser("u2")
        h.fire()
        assertEquals(emptyList<String>(), h.shownIds())
        h.fire()
        assertEquals(listOf("a"), h.shownIds())
    }

    @Test
    fun `목록을 받지 못해도 마지막 캐시로 취소 이벤트의 누적 횟수를 되돌린다`() {
        val a = campaign("a", occurrence = OccurrenceCondition(3), abortEvents = listOf(TestCampaigns.ABORT))
        val h = InAppTriggerHarness(listOf(a))
        h.fire()
        h.fire()
        h.fetching.available = false
        h.fetching.cached = listOf(a)
        h.fire(TestCampaigns.ABORT)
        h.fetching.available = true
        h.fire()
        assertEquals(emptyList<String>(), h.shownIds())
    }
}
