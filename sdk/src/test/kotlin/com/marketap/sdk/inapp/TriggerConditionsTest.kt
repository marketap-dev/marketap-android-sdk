package com.marketap.sdk.inapp

import com.marketap.sdk.domain.service.inapp.condition.ConditionChecker
import com.marketap.sdk.domain.service.inapp.condition.matchesTrigger
import com.marketap.sdk.model.internal.api.InAppCampaignRes
import com.marketap.sdk.model.internal.api.IN_APP_MESSAGING_ENDPOINT
import com.marketap.sdk.model.internal.inapp.Condition
import com.marketap.sdk.model.internal.inapp.EventFilter
import com.marketap.sdk.model.internal.inapp.EventTriggerCondition
import com.marketap.sdk.utils.adapter
import com.marketap.sdk.utils.deserialize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TriggerConditionsTest {

    private val checker = object : ConditionChecker {
        override fun checkCondition(
            condition: Condition,
            eventName: String,
            eventProperty: Map<String, Any>?,
        ) = condition.eventFilter.eventName == eventName
    }

    private fun cond(name: String) = Condition(EventFilter(name))

    @Test
    fun `둘째 조건에 맞으면 트리거된다`() {
        val trigger = EventTriggerCondition(cond("a"), conditions = listOf(cond("a"), cond("b")))
        assertTrue(checker.matchesTrigger(trigger, "b", null))
    }

    @Test
    fun `모든 조건에 안 맞으면 트리거되지 않는다`() {
        val trigger = EventTriggerCondition(cond("a"), conditions = listOf(cond("a"), cond("b")))
        assertFalse(checker.matchesTrigger(trigger, "c", null))
    }

    @Test
    fun `conditions 가 없으면 condition 으로 판정한다`() {
        val trigger = EventTriggerCondition(cond("a"))
        assertTrue(checker.matchesTrigger(trigger, "a", null))
        assertFalse(checker.matchesTrigger(trigger, "b", null))
    }

    @Test
    fun `v3 응답을 역직렬화한다`() {
        val json = """{"checksum":"c","campaigns":[{"id":"1","layout":{"layoutType":"MODAL","layoutSubType":"IMAGE","orientations":[]},
            "triggerEventCondition":{"condition":{"eventFilter":{"eventName":"a"}},
            "conditions":[{"eventFilter":{"eventName":"a"}},{"eventFilter":{"eventName":"b"}}]},
            "priority":"NORMAL","html":null,"updatedAt":"2026-11-13T00:00:00Z"}]}"""
        val res = json.deserialize(adapter<InAppCampaignRes>())
        val trigger = res.campaigns!![0].triggerEventCondition
        assertEquals(2, trigger.conditions!!.size)
        assertTrue(checker.matchesTrigger(trigger, "b", null))
    }

    @Test
    fun `conditions 없는 구 응답도 역직렬화된다`() {
        val json = """{"checksum":"c","campaigns":[{"id":"1","layout":{"layoutType":"MODAL","layoutSubType":"IMAGE","orientations":[]},
            "triggerEventCondition":{"condition":{"eventFilter":{"eventName":"a"}}},
            "priority":"NORMAL","html":null,"updatedAt":"2026-11-13T00:00:00Z"}]}"""
        val trigger = json.deserialize(adapter<InAppCampaignRes>()).campaigns!![0].triggerEventCondition
        assertNull(trigger.conditions)
        assertTrue(checker.matchesTrigger(trigger, "a", null))
    }

    @Test
    fun `엔드포인트는 v3 이다`() {
        assertNotNull(IN_APP_MESSAGING_ENDPOINT)
        assertTrue(IN_APP_MESSAGING_ENDPOINT.endsWith("/api/v3/campaigns"))
    }
}
