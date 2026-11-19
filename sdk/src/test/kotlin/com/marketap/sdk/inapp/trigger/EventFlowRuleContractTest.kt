package com.marketap.sdk.inapp.trigger

import com.marketap.sdk.domain.service.inapp.condition.EventFlowRule
import com.marketap.sdk.domain.service.inapp.condition.EventFlowState
import com.marketap.sdk.model.internal.inapp.Condition
import com.marketap.sdk.model.internal.inapp.EventFilter
import com.marketap.sdk.model.internal.inapp.EventFlow
import com.marketap.sdk.model.internal.inapp.EventFlowStep
import com.marketap.sdk.utils.moshi
import org.junit.Assert.assertEquals
import org.junit.Test

/** 웹 SDK·서버와 같은 사례 파일(event-flow-cases.json)로 판정 규칙을 검증한다. */
class EventFlowRuleContractTest {

    private data class Expected(val fired: Boolean, val restarted: Boolean, val state: EventFlowState?)

    @Suppress("UNCHECKED_CAST")
    private val cases: List<Map<String, Any?>> = run {
        val json = javaClass.classLoader!!.getResource("event-flow-cases.json")!!.readText()
        val root = moshi.adapter(Any::class.java).fromJson(json) as Map<String, Any?>
        root["cases"] as List<Map<String, Any?>>
    }

    @Test
    fun `사례 수가 웹·서버와 같다`() {
        assertEquals(26, cases.size)
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `모든 사례가 같은 결과를 낸다`() {
        cases.forEach { case ->
            val name = case["name"] as String
            val spec = case["flow"] as Map<String, Any?>
            val flow = EventFlow(
                precedingSteps = (spec["steps"] as List<Map<String, Any?>>).map { step ->
                    EventFlowStep(
                        conditions = (step["events"] as List<String>).map(::condition),
                        withinSeconds = (step["withinSeconds"] as Double).toLong(),
                    )
                },
                reentryBlockSeconds = (spec["reentryBlockSeconds"] as Double?)?.toLong(),
            )
            val arrival = spec["arrival"] as List<String>
            val cancel = (spec["cancel"] as List<String>).map(::condition)
            var state: EventFlowState? = null

            (case["inputs"] as List<Map<String, Any?>>).forEachIndexed { index, input ->
                if (input["impression"] == true) {
                    state = null
                    return@forEachIndexed
                }
                val eventName = input["event"] as String
                val transition = EventFlowRule.next(
                    state,
                    flow,
                    eventName in arrival,
                    cancel,
                    (input["at"] as Double).toLong() * 1000,
                ) { it.eventFilter.eventName == eventName }

                assertEquals(
                    "$name #$index",
                    expected(input["expect"] as Map<String, Any?>),
                    Expected(transition.fired, transition.restarted, transition.state),
                )
                state = transition.state
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun expected(expect: Map<String, Any?>): Expected {
        val state = (expect["state"] as Map<String, Any?>?)?.let {
            EventFlowState(
                nextStep = (it["nextStep"] as Double).toInt(),
                lastStepAt = (it["lastStepAt"] as Double).toLong() * 1000,
                blockedUntil = (it["blockedUntil"] as Double?)?.let { at -> at.toLong() * 1000 },
            )
        }
        return Expected(expect["fired"] as Boolean, expect["restarted"] as Boolean, state)
    }

    private fun condition(eventName: String) = Condition(EventFilter(eventName))
}
