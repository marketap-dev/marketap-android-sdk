package com.marketap.sdk.inapp

import com.marketap.sdk.client.inapp.InAppSession
import com.marketap.sdk.client.inapp.InAppSlots
import com.marketap.sdk.client.inapp.pageFloatingToClose
import com.marketap.sdk.domain.repository.InAppView
import com.marketap.sdk.domain.service.inapp.CampaignExposing
import com.marketap.sdk.domain.service.inapp.CampaignFetching
import com.marketap.sdk.domain.service.inapp.InAppPageScope
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class InAppOriginTest {

    private class Origin : InAppOrigin {
        override fun canOpenUrl() = true
        override fun openUrl(url: String) {}
        override fun isShownIn(screen: Any) = false
    }

    private data class Shown(val id: String, val slot: InAppDisplaySlot, val origin: InAppOrigin?, val closeOnPageLeave: Boolean)

    private class View : InAppView {
        val slots = InAppSlots<Any>()
        val shown = mutableListOf<Shown>()

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
            if (!slots.claim(slot, html)) return false
            shown.add(Shown(html, slot, origin, closeOnPageLeave))
            onShow()
            return true
        }
    }

    private class Fetching(val campaigns: List<InAppCampaign>) : CampaignFetching {
        override fun useCampaigns(block: (campaigns: List<InAppCampaign>) -> Unit) = block(campaigns)
        override fun resolveCampaignHtml(campaign: InAppCampaign, event: IngestEventRequest) =
            campaign.copy(html = campaign.id)
    }

    private class Exposing : CampaignExposing {
        val impressions = mutableListOf<String>()
        override fun isCampaignHidden(campaignId: String) = false
        override fun hasReachedImpressionLimit(campaignId: String, windowMinutes: Int, maxCount: Int) = false
        override fun recordImpression(campaignId: String) { impressions.add(campaignId) }
        override fun hideCampaign(campaignId: String, until: Long) {}
    }

    private class AlwaysMatch : ConditionChecker {
        override fun checkCondition(condition: Condition, eventName: String, eventProperties: Map<String, Any>?) = true
    }

    private fun campaign(id: String, displaySlot: String?, floatingScope: String?) = InAppCampaign(
        id = id,
        layout = Layout("MODAL", "IMAGE", emptyList()),
        triggerEventCondition = EventTriggerCondition(Condition(EventFilter("mkt_page_view"))),
        priority = "NORMAL",
        html = null,
        updatedAt = "2026-11-10T00:00:00Z",
        displaySlot = displaySlot,
        floatingScope = floatingScope,
    )

    private fun popup(id: String) = campaign(id, null, null)
    private fun floating(id: String, scope: String? = null) = campaign(id, "FLOATING", scope)

    private val view = View()
    private val exposing = Exposing()
    private val origin = Origin()

    private fun fire(vararg campaigns: InAppCampaign, from: InAppOrigin? = origin) {
        val impressionEvents = mutableListOf<String>()
        InAppService(exposing, AlwaysMatch(), Fetching(campaigns.toList()), view)
            .onEvent(
                IngestEventRequest("e", "mkt_page_view", "u", DeviceReq(deviceId = "d"), emptyMap()),
                from, { impressionEvents.add(it.id) }, { _, _ -> }, { _, _, _ -> }, {},
            )
        assertEquals(exposing.impressions.takeLast(impressionEvents.size), impressionEvents)
    }

    @Test
    fun `REG-24 웹뷰 이벤트의 팝업과 플로팅은 앱이 출처와 함께 그린다`() {
        fire(popup("p"), floating("f", "PAGE"))

        assertEquals(listOf("p", "f"), view.shown.map { it.id })
        assertTrue(view.shown.all { it.origin === origin })
    }

    @Test
    fun `앱 화면 이벤트로 뜬 메시지는 출처가 없다`() {
        fire(popup("p"), from = null)

        assertNull(view.shown.single().origin)
    }

    @Test
    fun `REG-32 웹뷰 이벤트로 뜬 메시지의 노출은 한 번만 기록한다`() {
        fire(popup("p"))

        assertEquals(listOf("p"), exposing.impressions)
    }

    @Test
    fun `출처가 있는 이 페이지에서만 플로팅만 페이지를 벗어나면 닫는 대상이다`() {
        fire(floating("page", "PAGE"))
        assertTrue(view.shown.single().closeOnPageLeave)

        view.slots.release(InAppDisplaySlot.FLOATING, "page")
        fire(floating("until", "UNTIL_CLOSED"), popup("p"))
        assertFalse(view.shown.any { it.id != "page" && it.closeOnPageLeave })

        view.slots.release(InAppDisplaySlot.FLOATING, "until")
        fire(floating("app-page", "PAGE"), from = null)
        assertFalse(view.shown.last().closeOnPageLeave)
    }

    @Test
    fun `closesOnPageLeave 는 출처·자리·화면 이동 값을 모두 본다`() {
        assertTrue(InAppPageScope.closesOnPageLeave(InAppDisplaySlot.FLOATING, "page", hasOrigin = true))
        assertFalse(InAppPageScope.closesOnPageLeave(InAppDisplaySlot.FLOATING, "PAGE", hasOrigin = false))
        assertFalse(InAppPageScope.closesOnPageLeave(InAppDisplaySlot.FLOATING, "UNTIL_CLOSED", hasOrigin = true))
        assertFalse(InAppPageScope.closesOnPageLeave(InAppDisplaySlot.FLOATING, null, hasOrigin = true))
        assertFalse(InAppPageScope.closesOnPageLeave(InAppDisplaySlot.POPUP, "PAGE", hasOrigin = true))
    }

    private fun session(origin: InAppOrigin?, closeOnPageLeave: Boolean) =
        InAppSession({}, { "" }, {}, { _, _ -> }, {}, origin, closeOnPageLeave)

    @Test
    fun `REG-28 주소 변경은 그 출처의 이 페이지에서만 바만 닫는다`() {
        val other = Origin()
        val page = session(origin, closeOnPageLeave = true)

        assertSame(page, pageFloatingToClose(page) { it === origin })
        assertNull(pageFloatingToClose(page) { it === other })
        assertNull(pageFloatingToClose(session(origin, closeOnPageLeave = false)) { it === origin })
        assertNull(pageFloatingToClose(session(null, closeOnPageLeave = false)) { true })
        assertNull(pageFloatingToClose("not-a-session") { true })
        assertNull(pageFloatingToClose(null) { true })
    }

    @Test
    fun `REG-30 addressChanged 를 받은 적 없는 웹만 페이지뷰를 주소 변경으로 본다`() {
        assertTrue(InAppPageScope.pageViewLeavesPage("mkt_page_view", addressChangeReceived = false))
        assertFalse(InAppPageScope.pageViewLeavesPage("mkt_page_view", addressChangeReceived = true))
        assertFalse(InAppPageScope.pageViewLeavesPage("mkt_home_view", addressChangeReceived = false))
    }
}
