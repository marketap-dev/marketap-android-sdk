package com.marketap.sdk.inapp.trigger

import com.marketap.sdk.domain.service.inapp.condition.OccurrenceConditionEvaluator
import com.marketap.sdk.model.internal.InAppCampaign
import com.marketap.sdk.model.internal.inapp.Condition
import com.marketap.sdk.model.internal.inapp.EventFilter
import com.marketap.sdk.model.internal.inapp.EventTriggerCondition
import com.marketap.sdk.model.internal.inapp.Layout
import com.marketap.sdk.model.internal.inapp.OccurrenceCondition
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OccurrenceConditionEvaluatorTest {
    private var now = 1_000_000L
    private val storage = InMemoryStorage()
    private val evaluator = OccurrenceConditionEvaluator(storage) { now }

    private fun campaign(
        id: String = "a",
        occurrence: OccurrenceCondition?,
        version: String? = "v1",
    ) = InAppCampaign(
        id = id,
        layout = Layout("MODAL", "IMAGE", emptyList()),
        triggerEventCondition = EventTriggerCondition(
            Condition(EventFilter("e")),
            occurrenceCondition = occurrence,
        ),
        priority = "NORMAL",
        html = null,
        updatedAt = "",
        triggerConditionVersion = version,
    )

    @Test
    fun `횟수 조건이 없으면 매번 충족`() {
        assertTrue(evaluator.recordAndEvaluate(campaign(occurrence = null), "s"))
    }

    @Test
    fun `기간 없는 조건은 누적 횟수가 count 에 닿으면 충족`() {
        val c = campaign(occurrence = OccurrenceCondition(3))
        assertFalse(evaluator.recordAndEvaluate(c, "s1"))
        assertFalse(evaluator.recordAndEvaluate(c, "s2"))
        assertTrue(evaluator.recordAndEvaluate(c, "s3"))
    }

    @Test
    fun `세션 단위 조건은 세션이 바뀌면 1부터 센다`() {
        val c = campaign(occurrence = OccurrenceCondition(2, sessionScoped = true))
        assertFalse(evaluator.recordAndEvaluate(c, "s1"))
        assertFalse(evaluator.recordAndEvaluate(c, "s2"))
        assertTrue(evaluator.recordAndEvaluate(c, "s2"))
    }

    @Test
    fun `기간 조건은 기간 안의 횟수만 센다`() {
        val c = campaign(occurrence = OccurrenceCondition(2, durationMinutes = 10))
        assertFalse(evaluator.recordAndEvaluate(c, "s"))
        now += 11 * 60_000L
        assertFalse(evaluator.recordAndEvaluate(c, "s"))
        now += 9 * 60_000L
        assertTrue(evaluator.recordAndEvaluate(c, "s"))
    }

    @Test
    fun `기간·세션 조건은 기간 안에서 같은 세션 횟수만 센다`() {
        val c = campaign(occurrence = OccurrenceCondition(2, durationMinutes = 10, sessionScoped = true))
        assertFalse(evaluator.recordAndEvaluate(c, "s1"))
        assertFalse(evaluator.recordAndEvaluate(c, "s2"))
        assertTrue(evaluator.recordAndEvaluate(c, "s2"))
    }

    @Test
    fun `triggerConditionVersion 이 바뀌면 처음부터 센다`() {
        val v1 = campaign(occurrence = OccurrenceCondition(2), version = "v1")
        val v2 = campaign(occurrence = OccurrenceCondition(2), version = "v2")
        assertFalse(evaluator.recordAndEvaluate(v1, "s"))
        assertFalse(evaluator.recordAndEvaluate(v2, "s"))
        assertTrue(evaluator.recordAndEvaluate(v2, "s"))
    }

    @Test
    fun `reset 하면 처음부터 센다`() {
        val c = campaign(occurrence = OccurrenceCondition(2))
        evaluator.recordAndEvaluate(c, "s")
        evaluator.reset("a")
        assertFalse(evaluator.recordAndEvaluate(c, "s"))
        assertTrue(evaluator.recordAndEvaluate(c, "s"))
    }

    @Test
    fun `resetAll 은 모든 캠페인의 횟수를 지운다`() {
        val a = campaign("a", OccurrenceCondition(2))
        val b = campaign("b", OccurrenceCondition(2, durationMinutes = 5))
        evaluator.recordAndEvaluate(a, "s")
        evaluator.recordAndEvaluate(b, "s")
        evaluator.resetAll()
        assertFalse(evaluator.recordAndEvaluate(a, "s"))
        assertFalse(evaluator.recordAndEvaluate(b, "s"))
    }
}
