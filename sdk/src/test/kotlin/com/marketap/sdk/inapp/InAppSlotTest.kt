package com.marketap.sdk.inapp

import com.marketap.sdk.client.inapp.InAppSession
import com.marketap.sdk.client.inapp.InAppSlots
import com.marketap.sdk.domain.repository.InAppView
import com.marketap.sdk.domain.service.inapp.CampaignExposing
import com.marketap.sdk.domain.service.inapp.CampaignFetching
import com.marketap.sdk.domain.service.inapp.InAppService
import com.marketap.sdk.domain.service.inapp.condition.ConditionChecker
import com.marketap.sdk.model.internal.InAppCampaign
import com.marketap.sdk.model.internal.api.DeviceReq
import com.marketap.sdk.model.internal.api.IngestEventRequest
import com.marketap.sdk.model.internal.inapp.Condition
import com.marketap.sdk.model.internal.inapp.EventFilter
import com.marketap.sdk.model.internal.inapp.EventTriggerCondition
import com.marketap.sdk.model.internal.inapp.HideType
import com.marketap.sdk.model.internal.inapp.InAppDisplaySlot
import com.marketap.sdk.model.internal.inapp.InAppOrigin
import com.marketap.sdk.model.internal.inapp.Layout
import com.marketap.sdk.model.internal.slot
import com.marketap.sdk.utils.adapter
import com.marketap.sdk.utils.deserialize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InAppSlotTest {

    private class Shown(
        val id: String,
        val slot: InAppDisplaySlot,
        val onShow: () -> Unit,
        val onHide: (HideType) -> Unit,
    )

    private class SlotView : InAppView {
        val slots = InAppSlots<Shown>()
        val shown = mutableListOf<Shown>()
        val calls = mutableListOf<String>()

        override fun isShowing(slot: InAppDisplaySlot) = slots.isOccupied(slot)

        override fun show(
            html: String,
            slot: InAppDisplaySlot,
            origin: InAppOrigin?,
            closeOnPageLeave: Boolean,
            onShow: () -> Unit,
            onClick: (String) -> String,
            onHide: (HideType) -> Unit,
            onTrack: (eventName: String, properties: Map<String, Any>?) -> Unit,
            onSetUserProperties: (properties: Map<String, Any>) -> Unit,
        ): Boolean {
            val entry = Shown(html, slot, onShow, onHide)
            if (!slots.claim(slot, entry)) return false
            shown.add(entry)
            calls.add("show:$html")
            return true
        }

        fun close(entry: Shown) = slots.release(entry.slot, entry)
    }

    private class Fetching(val campaigns: List<InAppCampaign>) : CampaignFetching {
        val resolveCalls = mutableListOf<String>()
        override fun useCampaigns(block: (campaigns: List<InAppCampaign>) -> Unit) = block(campaigns)
        override fun resolveCampaignHtml(campaign: InAppCampaign, event: IngestEventRequest): InAppCampaign {
            resolveCalls.add(campaign.id)
            return campaign.copy(html = campaign.id)
        }
    }

    private class Exposing : CampaignExposing {
        val impressions = mutableListOf<String>()
        val hidden = mutableListOf<String>()
        override fun isCampaignHidden(campaignId: String) = false
        override fun hasReachedImpressionLimit(campaignId: String, windowMinutes: Int, maxCount: Int) = false
        override fun recordImpression(campaignId: String) { impressions.add(campaignId) }
        override fun hideCampaign(campaignId: String, until: Long) { hidden.add(campaignId) }
    }

    private class AlwaysMatch : ConditionChecker {
        override fun checkCondition(condition: Condition, eventName: String, eventProperties: Map<String, Any>?) = true
    }

    private fun popup(id: String) = campaign(id, null, null)

    private fun floating(id: String, scope: String? = null) = campaign(id, "FLOATING", scope)

    private fun campaign(id: String, displaySlot: String?, floatingScope: String?) = InAppCampaign(
        id = id,
        layout = Layout("MODAL", "IMAGE", emptyList()),
        triggerEventCondition = EventTriggerCondition(Condition(EventFilter("mkt_home_view"))),
        priority = "NORMAL",
        html = null,
        updatedAt = "2026-11-04T00:00:00Z",
        displaySlot = displaySlot,
        floatingScope = floatingScope,
    )

    private fun event(name: String = "mkt_home_view") =
        IngestEventRequest("e1", name, "u", DeviceReq(deviceId = "d"), emptyMap())

    private val view = SlotView()
    private val exposing = Exposing()

    private fun fire(campaigns: List<InAppCampaign>, eventName: String = "mkt_home_view"): Fetching {
        val fetching = Fetching(campaigns)
        InAppService(exposing, AlwaysMatch(), fetching, view)
            .onEvent(event(eventName), {}, { _, _ -> }, { _, _, _ -> }, {})
        return fetching
    }

    @Test
    fun `REG-1 플로팅이 없으면 지금처럼 하나만 뜬다`() {
        val fetching = fire(listOf(popup("a"), popup("b")))

        assertEquals(listOf("a"), view.shown.map { it.id })
        assertEquals(listOf("a"), fetching.resolveCalls)
    }

    @Test
    fun `REG-2 팝업이 떠 있으면 두 번째 팝업은 요청 없이 건너뛴다`() {
        fire(listOf(popup("a")))
        val fetching = fire(listOf(popup("b")))

        assertEquals(listOf("a"), view.shown.map { it.id })
        assertEquals(emptyList<String>(), fetching.resolveCalls)
    }

    @Test
    fun `팝업과 플로팅은 자리마다 하나씩 함께 뜬다`() {
        val fetching = fire(listOf(popup("a"), popup("b"), floating("c"), floating("d")))

        assertEquals(listOf("a", "c"), view.shown.map { it.id })
        assertEquals(listOf(InAppDisplaySlot.POPUP, InAppDisplaySlot.FLOATING), view.shown.map { it.slot })
        assertEquals(listOf("a", "c"), fetching.resolveCalls)
    }

    @Test
    fun `플로팅이 떠 있어도 팝업은 뜬다`() {
        fire(listOf(floating("f")))
        fire(listOf(floating("g"), popup("p")))

        assertEquals(listOf("f", "p"), view.shown.map { it.id })
    }

    @Test
    fun `자리를 반납하면 같은 자리에 다음 메시지가 뜬다`() {
        fire(listOf(floating("f")))
        view.close(view.shown.single())
        fire(listOf(floating("g")))

        assertEquals(listOf("f", "g"), view.shown.map { it.id })
    }

    @Test
    fun `REG-5 앱은 이 페이지에서만 옵션이어도 페이지뷰에 플로팅을 닫지 않는다`() {
        fire(listOf(floating("f", scope = "PAGE")))
        fire(listOf(floating("g", scope = "PAGE")), eventName = "mkt_page_view")

        assertEquals(listOf("show:f"), view.calls)
        assertEquals(listOf("f"), view.shown.map { it.id })
    }

    @Test
    fun `플로팅도 팝업과 같은 노출·숨김 기록을 남긴다`() {
        fire(listOf(floating("f")))
        val entry = view.shown.single()
        entry.onShow()
        entry.onHide(HideType.HIDE_FOR_ONE_DAY)

        assertEquals(listOf("f"), exposing.impressions)
        assertEquals(listOf("f"), exposing.hidden)
    }

    @Test
    fun `반납은 차지한 요청만 할 수 있다`() {
        val slots = InAppSlots<Any>()
        val first = Any()
        val second = Any()
        assertTrue(slots.claim(InAppDisplaySlot.FLOATING, first))
        assertTrue(slots.release(InAppDisplaySlot.FLOATING, first))
        assertTrue(slots.claim(InAppDisplaySlot.FLOATING, second))

        assertFalse(slots.release(InAppDisplaySlot.FLOATING, first))
        assertTrue(slots.isOccupied(InAppDisplaySlot.FLOATING))
    }

    @Test
    fun `세션은 숨긴 뒤 노출·클릭·두 번째 숨김을 전달하지 않는다`() {
        val events = mutableListOf<String>()
        val session = InAppSession(
            onShow = { events.add("show") },
            onClick = { events.add("click"); "id" },
            onHide = { events.add("hide:$it") },
            onTrack = { _, _ -> },
            onSetUserProperties = {},
        )
        session.onHide(HideType.CLOSE)
        session.onHide(HideType.CLOSE)
        session.shown()

        assertNull(session.onClick("loc"))
        assertEquals(listOf("hide:CLOSE"), events)
    }

    @Test
    fun `응답의 displaySlot 을 읽고 floatingScope 는 웹 전달용으로 보관한다`() {
        val json = """
            {"id":"c","layout":{"layoutType":"FLOATING","layoutSubType":"BAR","orientations":[]},
             "triggerEventCondition":{"condition":{"eventFilter":{"eventName":"mkt_home_view"}}},
             "priority":"NORMAL","html":null,"updatedAt":"t",
             "displaySlot":"FLOATING","floatingScope":"PAGE"}
        """.trimIndent()
        val parsed = json.deserialize(adapter<InAppCampaign>())

        assertEquals(InAppDisplaySlot.FLOATING, parsed.slot)
        assertEquals("PAGE", parsed.floatingScope)
    }

    @Test
    fun `displaySlot 이 없는 기존 응답은 팝업이다`() {
        val json = """
            {"id":"c","layout":{"layoutType":"MODAL","layoutSubType":"IMAGE","orientations":[]},
             "triggerEventCondition":{"condition":{"eventFilter":{"eventName":"mkt_home_view"}}},
             "priority":"NORMAL","html":null,"updatedAt":"t"}
        """.trimIndent()
        val parsed = json.deserialize(adapter<InAppCampaign>())

        assertEquals(InAppDisplaySlot.POPUP, parsed.slot)
        assertNull(parsed.floatingScope)
        assertEquals(InAppDisplaySlot.POPUP, InAppDisplaySlot.of("SOMETHING_NEW"))
    }
}
