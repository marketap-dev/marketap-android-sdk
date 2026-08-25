package com.marketap.sdk.inapp

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
import com.marketap.sdk.model.internal.inapp.Layout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 한 이벤트에 후보가 여러 개일 때 1순위가 못 뜨면 다음 후보로 넘어가는지.
 *
 * 실제 사례(web SDK): 1순위 쿠폰 캠페인이 발급 실패로 빈 응답을 받았는데 SDK 가 후보 하나만
 * 시도해서 화면에 아무것도 안 떴다. Android 도 같은 구조였다.
 */
class InAppFallthroughTest {

    private class FakeFetching(
        /** campaignId → resolve 결과. 없으면 null(=서버가 노출을 확정 못 함). */
        val resolved: Map<String, InAppCampaign> = emptyMap(),
        /** resolve 한 번에 걸리는 시간(ms). 실제 구현은 스레드를 최대 1초 블록한다. */
        val resolveCostMs: Long = 0,
        val campaigns: List<InAppCampaign>
    ) : CampaignFetching {
        val resolveCalls = mutableListOf<String>()

        override fun useCampaigns(block: (campaigns: List<InAppCampaign>) -> Unit) = block(campaigns)

        override fun resolveCampaignHtml(
            campaign: InAppCampaign,
            event: IngestEventRequest
        ): InAppCampaign? {
            if (campaign.html != null) return campaign
            resolveCalls.add(campaign.id)
            if (resolveCostMs > 0) Thread.sleep(resolveCostMs)
            return resolved[campaign.id]
        }
    }

    private class FakeExposing(val hidden: Set<String> = emptySet()) : CampaignExposing {
        val impressions = mutableListOf<String>()
        override fun isCampaignHidden(campaignId: String) = campaignId in hidden
        override fun hasReachedImpressionLimit(campaignId: String, windowMinutes: Int, maxCount: Int) = false
        override fun recordImpression(campaignId: String) { impressions.add(campaignId) }
        override fun hideCampaign(campaignId: String, until: Long) {}
    }

    private class FakeView : InAppView {
        val shownHtml = mutableListOf<String>()
        override fun show(
            html: String,
            onShow: () -> Unit,
            onClick: (String) -> String,
            onHide: (HideType) -> Unit,
            onTrack: (eventName: String, properties: Map<String, Any>?) -> Unit,
            onSetUserProperties: (properties: Map<String, Any>) -> Unit,
        ) { shownHtml.add(html) }
    }

    private class AlwaysMatch : ConditionChecker {
        override fun checkCondition(
            condition: Condition,
            eventName: String,
            eventProperties: Map<String, Any>?
        ) = true
    }

    private fun campaign(id: String, html: String? = null) = InAppCampaign(
        id = id,
        layout = Layout("MODAL", "IMAGE", emptyList()),
        triggerEventCondition = EventTriggerCondition(Condition(EventFilter("mkt_home_view"))),
        priority = "NORMAL",
        html = html,
        updatedAt = "2026-03-18T00:00:00Z",
    )

    private val event = IngestEventRequest(
        "e1", "mkt_home_view", "u",
        DeviceReq(deviceId = "d"), emptyMap()
    )

    private fun run(fetching: FakeFetching, exposing: FakeExposing = FakeExposing()): FakeView {
        val view = FakeView()
        InAppService(exposing, AlwaysMatch(), fetching, view)
            .onEvent(event, {}, { _, _ -> }, { _, _, _ -> }, {})
        return view
    }

    @Test
    fun `1순위가 빈 응답이면 다음 후보를 띄운다`() {
        val fetching = FakeFetching(
            resolved = mapOf("b" to campaign("b", "<div>b</div>")),
            campaigns = listOf(campaign("a"), campaign("b"))
        )
        val view = run(fetching)

        assertEquals(listOf("a", "b"), fetching.resolveCalls)
        assertEquals(listOf("<div>b</div>"), view.shownHtml)
    }

    @Test
    fun `1순위가 뜨면 다음 후보는 시도하지 않는다`() {
        val fetching = FakeFetching(
            resolved = mapOf("a" to campaign("a", "<div>a</div>")),
            campaigns = listOf(campaign("a"), campaign("b"))
        )
        val view = run(fetching)

        assertEquals(listOf("a"), fetching.resolveCalls)
        assertEquals(listOf("<div>a</div>"), view.shownHtml)
    }

    @Test
    fun `정적 렌더 캠페인은 요청 예산을 깎지 않는다`() {
        val fetching = FakeFetching(campaigns = listOf(campaign("static", "<div>s</div>")))
        val view = run(fetching)

        assertEquals(emptyList<String>(), fetching.resolveCalls)
        assertEquals(listOf("<div>s</div>"), view.shownHtml)
    }

    @Test
    fun `숨겨진 후보는 요청 없이 건너뛴다`() {
        val fetching = FakeFetching(
            resolved = mapOf("b" to campaign("b", "<div>b</div>")),
            campaigns = listOf(campaign("hidden"), campaign("b"))
        )
        val view = run(fetching, FakeExposing(hidden = setOf("hidden")))

        assertEquals(listOf("b"), fetching.resolveCalls)
        assertEquals(listOf("<div>b</div>"), view.shownHtml)
    }

    @Test
    fun `빈 응답이 이어져도 요청은 5회에서 멈춘다`() {
        val fetching = FakeFetching(campaigns = (1..7).map { campaign("c$it") })
        val view = run(fetching)

        assertEquals(5, fetching.resolveCalls.size)
        assertEquals(emptyList<String>(), view.shownHtml)
    }

    /**
     * resolveCampaignHtml 은 runBlocking 이라 호출 스레드를 fetch 당 최대 1초 붙잡는다.
     * onEvent 는 track() 을 부른 스레드(메인일 수 있음)에서 그대로 돌기 때문에, 시간 예산이
     * 없으면 최대 5초를 블록해 ANR 이 된다. 예산이 실제로 블록 시간을 묶는지 못 박는다.
     */
    @Test
    fun `느린 응답이어도 전체 블록 시간이 예산 안에 묶인다`() {
        val fetching = FakeFetching(
            resolveCostMs = 1000,
            campaigns = (1..7).map { campaign("c$it") }
        )

        val startedAt = System.nanoTime()
        run(fetching)
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000

        // fetch 당 1초 → 2초 예산이면 2회에서 찬다. 5회 상한(=5초)에 도달하면 안 된다.
        assertEquals(2, fetching.resolveCalls.size)
        assertTrue("블록 시간이 ${elapsedMs}ms 로 예산을 넘었다", elapsedMs < 3000)
    }
}
