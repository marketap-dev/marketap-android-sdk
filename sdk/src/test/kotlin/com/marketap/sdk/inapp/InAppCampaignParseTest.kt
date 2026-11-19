package com.marketap.sdk.inapp

import com.marketap.sdk.model.internal.api.InAppCampaignRes
import com.marketap.sdk.model.internal.inapp.InAppCampaignTimer
import com.marketap.sdk.model.internal.inapp.OccurrenceCondition
import com.marketap.sdk.utils.adapter
import com.marketap.sdk.utils.deserialize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InAppCampaignParseTest {

    private val legacyJson = """
        {
          "checksum": "c1",
          "campaigns": [{
            "id": "a",
            "layout": {"layoutType": "MODAL", "layoutSubType": "IMAGE", "orientations": []},
            "triggerEventCondition": {
              "condition": {"eventFilter": {"eventName": "mkt_home_view"}},
              "frequencyCap": {"limit": 1, "durationMinutes": 60}
            },
            "priority": "NORMAL",
            "html": "<div/>",
            "updatedAt": "2026-03-18T00:00:00Z"
          }]
        }
    """.trimIndent()

    private val currentJson = """
        {
          "checksum": "c2",
          "campaigns": [{
            "id": "b",
            "layout": {"layoutType": "MODAL", "layoutSubType": "IMAGE", "orientations": []},
            "triggerEventCondition": {
              "condition": {"eventFilter": {"eventName": "mkt_home_view"}},
              "sendableTimeWindow": {
                "weeklyTimeRules": [{"dayOfWeek": "MONDAY", "timeRanges": [{"startMinuteOfDay": 540, "endMinuteOfDay": 1080}]}],
                "sendableTimePolicy": "SKIP",
                "timezone": "Asia/Seoul"
              },
              "delaySeconds": 30,
              "scrollPercent": 50,
              "occurrenceCondition": {"count": 3, "durationMinutes": 10, "sessionScoped": true},
              "abortConditions": [{"eventFilter": {"eventName": "mkt_purchase"}}]
            },
            "triggerConditionVersion": "v-hash",
            "priority": "NORMAL",
            "html": null,
            "updatedAt": "2026-03-18T00:00:00Z",
            "tagIds": ["t1"],
            "disableGlobalFrequencyCap": true,
            "timer": {"minutes": 5, "revision": "r"},
            "isTest": false
          }],
          "globalFrequencyCaps": [{"channelType": "IN_APP_MESSAGE", "limit": 2, "durationMinutes": 1440, "tagIds": []}]
        }
    """.trimIndent()

    @Test
    fun `새 필드가 없는 예전 응답도 읽는다`() {
        val res = legacyJson.deserialize(adapter<InAppCampaignRes>())
        val trigger = res.campaigns!!.single().triggerEventCondition

        assertNull(trigger.delaySeconds)
        assertNull(trigger.occurrenceCondition)
        assertNull(trigger.abortConditions)
        assertNull(trigger.sendableTimeWindow)
        assertNull(res.campaigns!!.single().triggerConditionVersion)
        assertNull(res.globalFrequencyCaps)
        assertNull(res.campaigns!!.single().timer)
    }

    @Test
    fun `지연·횟수·취소·시간대·전체 빈도 필드를 읽는다`() {
        val res = currentJson.deserialize(adapter<InAppCampaignRes>())
        val campaign = res.campaigns!!.single()
        val trigger = campaign.triggerEventCondition

        assertEquals(30, trigger.delaySeconds)
        assertEquals(OccurrenceCondition(3, 10, true), trigger.occurrenceCondition)
        assertEquals("mkt_purchase", trigger.abortConditions!!.single().eventFilter.eventName)
        assertEquals(540, trigger.sendableTimeWindow!!.weeklyTimeRules.single().timeRanges.single().startMinuteOfDay)
        assertEquals("v-hash", campaign.triggerConditionVersion)
        assertEquals(listOf("t1"), campaign.tagIds)
        assertEquals(true, campaign.disableGlobalFrequencyCap)
        assertEquals(2, res.globalFrequencyCaps!!.single().limit)
        assertEquals(InAppCampaignTimer(5, "r"), campaign.timer)
        assertEquals(false, campaign.isTest)
    }

    @Test
    fun `checksum 이 같아 캠페인이 없는 응답에서도 전체 빈도 규칙을 읽는다`() {
        val res = """{"checksum":"c","campaigns":null,"globalFrequencyCaps":[{"limit":1,"durationMinutes":60,"tagIds":["x"]}]}"""
            .deserialize(adapter<InAppCampaignRes>())

        assertNull(res.campaigns)
        assertEquals(listOf("x"), res.globalFrequencyCaps!!.single().tagIds)
    }
}
