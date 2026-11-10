package com.marketap.sdk.inapp

import com.marketap.sdk.client.inapp.InAppSession
import com.marketap.sdk.client.inapp.InAppSlots
import com.marketap.sdk.client.inapp.pageFloatingToClose
import com.marketap.sdk.client.inapp.popupToClose
import com.marketap.sdk.client.inapp.shownBefore
import com.marketap.sdk.client.inapp.shownFrom
import com.marketap.sdk.domain.repository.InAppView
import com.marketap.sdk.domain.service.inapp.CampaignExposing
import com.marketap.sdk.domain.service.inapp.CampaignFetching
import com.marketap.sdk.domain.service.inapp.InAppPageScope
import com.marketap.sdk.domain.service.inapp.InAppService
import com.marketap.sdk.domain.service.inapp.SCREEN_CHANGE_GRACE_MS
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
        var gone = false
        override fun canOpenUrl() = true
        override fun openUrl(url: String) {}
        override val isGone: Boolean get() = gone
    }

    private data class Shown(
        val id: String,
        val slot: InAppDisplaySlot,
        val origin: InAppOrigin?,
        val closeOnPageLeave: Boolean,
        var shownAtMs: Long?,
    )

    private class View(val clock: () -> Long) : InAppView {
        var appearsLater = false
        val slots = InAppSlots<Any>()
        val shown = mutableListOf<Shown>()
        val closed = mutableListOf<String>()
        var returnFromPopup = false

        override fun consumeReturnFromPopup(): Boolean = returnFromPopup.also { returnFromPopup = false }

        override fun closeForAppScreenChange(shownBeforeMs: Long) {
            for (slot in InAppDisplaySlot.values()) {
                val entry = slots.current(slot) as? Shown ?: continue
                val closes = slot == InAppDisplaySlot.POPUP || entry.closeOnPageLeave
                val shownAtMs = entry.shownAtMs ?: continue
                if (closes && shownAtMs < shownBeforeMs && slots.release(slot, entry)) closed.add(entry.id)
            }
        }

        fun release(id: String) = InAppDisplaySlot.values().forEach { slot ->
            (slots.current(slot) as? Shown)?.takeIf { it.id == id }?.let { slots.release(slot, it) }
        }

        fun isOpen(id: String) = InAppDisplaySlot.values().any { (slots.current(it) as? Shown)?.id == id }

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
            val entry = Shown(html, slot, origin, closeOnPageLeave, null)
            if (!slots.claim(slot, entry)) return false
            shown.add(entry)
            if (!appearsLater) {
                entry.shownAtMs = clock()
                onShow()
            }
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

    private var now = 100_000L
    private val view = View { now }
    private val exposing = Exposing()
    private val origin = Origin()
    private val campaigns = mutableListOf<InAppCampaign>()
    private val service = InAppService(exposing, AlwaysMatch(), Fetching(campaigns), view) { now }

    private fun fire(
        vararg campaigns: InAppCampaign,
        from: InAppOrigin? = origin,
        event: String = "mkt_page_view",
    ) {
        val impressionEvents = mutableListOf<String>()
        this.campaigns.clear()
        this.campaigns.addAll(campaigns)
        service.onEvent(
                IngestEventRequest("e", event, "u", DeviceReq(deviceId = "d"), emptyMap()),
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
    fun `출처 웹뷰가 떨어졌어도 메시지는 출처 없이 띄운다`() {
        origin.gone = true

        fire(popup("p"), floating("page", "PAGE"))

        assertEquals(listOf("p", "page"), view.shown.map { it.id })
        assertTrue(view.shown.all { it.origin == null })
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
    fun `이 페이지에서만 플로팅은 출처와 상관없이 페이지를 벗어나면 닫는 대상이다`() {
        fire(floating("page", "PAGE"))
        assertTrue(view.shown.single().closeOnPageLeave)

        view.release("page")
        fire(floating("until", "UNTIL_CLOSED"), popup("p"))
        assertFalse(view.shown.any { it.id != "page" && it.closeOnPageLeave })

        view.release("until")
        fire(floating("app-page", "PAGE"), from = null)
        assertTrue(view.shown.last().closeOnPageLeave)
    }

    @Test
    fun `REG-5 screenChanged 는 출처와 상관없이 이 페이지에서만 플로팅과 팝업을 닫는다`() {
        fire(popup("p"), floating("f", "PAGE"))
        now += 5_000
        service.screenChanged()
        assertEquals(listOf("p", "f"), view.closed)

        fire(popup("q"), floating("g", "PAGE"), from = null)
        now += 5_000
        service.screenChanged()
        assertEquals(listOf("p", "f", "q", "g"), view.closed)
    }

    @Test
    fun `팝업이 닫히며 같은 화면으로 돌아온 직후의 screenChanged 는 무시하고 다음 호출은 처리한다`() {
        fire(floating("f", "PAGE"), from = null)
        now += 5_000
        view.returnFromPopup = true

        service.screenChanged()
        assertTrue(view.isOpen("f"))

        service.screenChanged()
        assertEquals(listOf("f"), view.closed)
    }

    @Test
    fun `screenChanged 는 닫을 때까지 유지 플로팅을 닫지 않는다`() {
        fire(floating("until", "UNTIL_CLOSED"), from = null)
        now += 5_000
        service.screenChanged()

        assertTrue(view.isOpen("until"))
    }

    @Test
    fun `REG-6 앱이 보낸 페이지뷰는 아무것도 닫지 않는다`() {
        fire(popup("p"), floating("f", "PAGE"), from = null)
        now += 5_000
        fire(popup("q"), floating("g", "PAGE"), from = null, event = "mkt_page_view")

        assertEquals(emptyList<String>(), view.closed)
        assertTrue(view.isOpen("p") && view.isOpen("f"))
    }

    @Test
    fun `보호 시간 안에 화면에 나타난 메시지는 screenChanged 로 닫지 않는다`() {
        fire(floating("f", "PAGE"), from = null)
        now += SCREEN_CHANGE_GRACE_MS - 1
        service.screenChanged()
        assertTrue(view.isOpen("f"))

        now += 2
        service.screenChanged()
        assertEquals(listOf("f"), view.closed)
    }

    @Test
    fun `screenChanged 뒤에 들어온 이벤트로 뜬 플로팅은 뜬다`() {
        service.screenChanged()
        now += 1
        fire(floating("f", "PAGE"), from = null)

        assertEquals(listOf("f"), view.shown.map { it.id })
        assertTrue(view.isOpen("f"))
    }

    @Test
    fun `REG-7 띄우는 도중 screenChanged 가 와도 이 페이지에서만 바와 팝업은 새 화면에 뜬다`() {
        var signalDuringFetch = true
        val fetching = object : CampaignFetching {
            override fun useCampaigns(block: (campaigns: List<InAppCampaign>) -> Unit) =
                block(listOf(floating("app-page", "PAGE"), popup("p")))
            override fun resolveCampaignHtml(campaign: InAppCampaign, event: IngestEventRequest): InAppCampaign {
                if (signalDuringFetch) {
                    signalDuringFetch = false
                    now += 5_000
                    service2.screenChanged()
                }
                return campaign.copy(html = campaign.id)
            }
        }
        service2 = InAppService(exposing, AlwaysMatch(), fetching, view) { now }
        val trigger = IngestEventRequest("e1", "mkt_home_view", "u", DeviceReq(deviceId = "d"), emptyMap())

        service2.onEvent(trigger, null, {}, { _, _ -> }, { _, _, _ -> }, {})

        assertEquals(listOf("app-page", "p"), view.shown.map { it.id })
        assertEquals(emptyList<String>(), view.closed)
    }

    private lateinit var service2: InAppService

    @Test
    fun `아직 화면에 나타나지 않은 메시지는 screenChanged 로 닫지 않고 자리도 비우지 않는다`() {
        view.appearsLater = true
        fire(popup("p"), floating("f", "PAGE"), from = null)
        now += 5_000

        service.screenChanged()

        assertEquals(emptyList<String>(), view.closed)
        assertTrue(view.isOpen("p") && view.isOpen("f"))
    }

    @Test
    fun `closesOnPageLeave 는 자리와 화면 이동 값을 본다`() {
        assertTrue(InAppPageScope.closesOnPageLeave(InAppDisplaySlot.FLOATING, "page"))
        assertFalse(InAppPageScope.closesOnPageLeave(InAppDisplaySlot.FLOATING, "UNTIL_CLOSED"))
        assertFalse(InAppPageScope.closesOnPageLeave(InAppDisplaySlot.FLOATING, null))
        assertFalse(InAppPageScope.closesOnPageLeave(InAppDisplaySlot.POPUP, "PAGE"))
    }

    private fun session(origin: InAppOrigin?, closeOnPageLeave: Boolean) =
        InAppSession({}, { "" }, {}, { _, _ -> }, {}, origin, closeOnPageLeave)

    @Test
    fun `REG-28 주소 변경은 그 출처의 이 페이지에서만 바만 닫는다`() {
        val other = Origin()
        val page = session(origin, closeOnPageLeave = true)

        assertSame(page, pageFloatingToClose(page) { it.origin === origin })
        assertNull(pageFloatingToClose(page) { it.origin === other })
        assertNull(pageFloatingToClose(page) { it.origin == null })
        assertNull(pageFloatingToClose(session(origin, closeOnPageLeave = false)) { it.origin === origin })
        assertNull(pageFloatingToClose(session(null, closeOnPageLeave = false)) { true })
        val appPage = session(null, closeOnPageLeave = true)
        assertSame(appPage, pageFloatingToClose(appPage) { it.origin == null })
        assertNull(pageFloatingToClose(appPage) { it.origin === origin })
        assertNull(pageFloatingToClose("not-a-session") { true })
        assertNull(pageFloatingToClose(null) { true })
    }

    @Test
    fun `REG-33 주소 변경은 그 출처에서 시작된 팝업만 닫는다`() {
        val other = Origin()
        val webPopup = session(origin, closeOnPageLeave = false)

        webPopup.shown()
        assertSame(webPopup, popupToClose(webPopup, shownFrom(origin)))
        assertNull(popupToClose(webPopup, shownFrom(other)))
        assertNull(popupToClose(session(null, closeOnPageLeave = false).apply { shown() }, shownFrom(origin)))
        assertNull(popupToClose(null, shownFrom(origin)))
    }

    @Test
    fun `screenChanged 는 보호 시간 이전에 화면에 나타난 세션만 닫는다`() {
        val early = session(origin, closeOnPageLeave = true).apply { shown(atMs = 1_000) }
        val recent = session(null, closeOnPageLeave = true).apply { shown(atMs = 2_000) }
        val notShown = session(null, closeOnPageLeave = true)

        assertSame(early, popupToClose(early, shownBefore(2_000)))
        assertNull(popupToClose(recent, shownBefore(2_000)))
        assertNull(popupToClose(notShown, shownBefore(2_000)))
        assertSame(early, pageFloatingToClose(early, shownBefore(2_000)))
        assertNull(pageFloatingToClose(recent, shownBefore(2_000)))
        assertNull(pageFloatingToClose(notShown, shownBefore(2_000)))
    }

    @Test
    fun `웹뷰의 페이지 이동과 웹뷰가 떨어지는 것은 화면에 나타난 메시지만 닫는다`() {
        val other = Origin()
        val notShown = session(origin, closeOnPageLeave = true)

        assertNull(pageFloatingToClose(notShown, shownFrom(origin)))
        assertNull(popupToClose(notShown, shownFrom(origin)))

        notShown.shown()
        assertSame(notShown, pageFloatingToClose(notShown, shownFrom(origin)))
        assertSame(notShown, popupToClose(notShown, shownFrom(origin)))
        assertNull(popupToClose(notShown, shownFrom(other)))
    }

    @Test
    fun `REG-30 웹뷰의 페이지뷰는 주소 변경 수신 여부와 상관없이 페이지 이동이다`() {
        assertTrue(InAppPageScope.pageViewLeavesPage("mkt_page_view"))
        assertFalse(InAppPageScope.pageViewLeavesPage("mkt_home_view"))
    }
}
