package com.marketap.sdk.inapp.trigger

import com.marketap.sdk.domain.repository.SessionManager
import com.marketap.sdk.domain.service.event.EventIngestionService
import com.marketap.sdk.domain.service.event.SessionUtmStore
import com.marketap.sdk.domain.service.event.UserIngestionService
import com.marketap.sdk.domain.service.inapp.InAppCallbackFactory
import com.marketap.sdk.domain.service.inapp.InAppService
import com.marketap.sdk.domain.service.state.ClientStateManager
import com.marketap.sdk.model.MarketapConfig
import com.marketap.sdk.model.internal.api.DeviceReq.Companion.toReq
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** 즉시 노출의 노출·클릭 이벤트가 이벤트 수신 시점과 같은 캠페인·메시지·세션 값을 싣는지. */
class InAppImpressionEventTest {
    private val storage = InMemoryStorage()
    private val backend = RecordingBackend()
    private val clientState = ClientStateManager(MarketapConfig("p1", "android", "test"), storage)
    private val deviceManager = FakeDeviceManager()
    private val sessionManager = object : SessionManager {
        var sessionId = "session-1"
        override fun updateActivity() {}
        override fun getSessionId(onSessionStart: (sessionId: String) -> Unit) = sessionId
    }
    private val view = FakeInAppView(autoShow = true)

    private fun service(sessionUtm: SessionUtmStore? = null): EventIngestionService {
        lateinit var ingestion: EventIngestionService
        val factory = InAppCallbackFactory(
            backend = backend,
            projectId = clientState::getProjectId,
            userId = clientState::getUserId,
            device = { deviceManager.getDevice().toReq() },
            sessionId = { ingestion.currentSessionId() },
            trackEvent = { name, props, identity -> ingestion.trackInAppEvent(name, props, identity) },
            setUserProperties = {},
            withSessionUtm = { properties, sessionId -> ingestion.withSessionUtm(properties, sessionId) },
        )
        val inApp = InAppService(
            FakeExposing(),
            MatchByEventName(),
            FakeFetching(listOf(TestCampaigns.campaign("c1", html = "<div/>", layoutSubType = "BOTTOM_SHEET"))),
            view,
            factory::create,
        )
        ingestion = EventIngestionService(
            backend, inApp, clientState, sessionManager, deviceManager, UserIngestionService(clientState, deviceManager, backend, storage),
            sessionUtm,
        )
        return ingestion
    }

    @Test
    fun `노출·클릭 이벤트가 캠페인·메시지·자리 유형·세션 속성을 싣는다`() {
        clientState.setUserId("u1")
        val ingestion = service()

        ingestion.trackEvent(TestCampaigns.TRIGGER, emptyMap())
        view.sessions.single().onClick("loc-1")

        val impression = backend.events.single { it.name == "mkt_delivery_message" }
        val click = backend.events.single { it.name == "mkt_click_message" }
        val props = impression.properties!!
        assertEquals("c1", props["mkt_campaign_id"])
        assertEquals("BOTTOM_SHEET", props["mkt_sub_channel_type"])
        assertEquals("session-1", props["mkt_session_id"])
        assertNotNull(props["mkt_message_id"])
        assertNotNull(props["sdk_integration_state"])
        assertEquals("u1", impression.userId)

        val clickProps = click.properties!!
        assertEquals("c1", clickProps["mkt_campaign_id"])
        assertEquals(props["mkt_message_id"], clickProps["mkt_message_id"])
        assertEquals("loc-1", clickProps["mkt_location_id"])
        assertEquals("BOTTOM_SHEET", clickProps["mkt_sub_channel_type"])
        assertEquals("session-1", clickProps["mkt_session_id"])
        assertEquals("u1", click.userId)
    }

    @Test
    fun `노출·클릭 이벤트에 세션 UTM 을 붙인다`() {
        val ingestion = service(SessionUtmStore(storage))
        ingestion.trackEvent(TestCampaigns.TRIGGER, mapOf("mkt_utm_source" to "naver"))
        view.sessions.single().onClick("loc-1")

        val impression = backend.events.single { it.name == "mkt_delivery_message" }.properties!!
        val click = backend.events.single { it.name == "mkt_click_message" }.properties!!
        assertEquals("naver", impression["mkt_session_utm_source"])
        assertEquals("naver", click["mkt_session_utm_source"])
    }

    @Test
    fun `메시지마다 새 messageId 를 쓴다`() {
        val ingestion = service()
        ingestion.trackEvent(TestCampaigns.TRIGGER, emptyMap())
        view.clear()
        ingestion.trackEvent(TestCampaigns.TRIGGER, emptyMap())

        val ids = backend.events.filter { it.name == "mkt_delivery_message" }.map { it.properties!!["mkt_message_id"] }
        assertEquals(2, ids.toSet().size)
    }

    @Test
    fun `인앱 안 트래킹은 캠페인 속성을 붙여 일반 이벤트로 보낸다`() {
        val ingestion = service()
        ingestion.trackEvent(TestCampaigns.TRIGGER, emptyMap())
        view.sessions.single().onTrack("custom_event", """{"k":"v"}""")

        val tracked = backend.events.single { it.name == "custom_event" }.properties!!
        assertEquals("v", tracked["k"])
        assertEquals("c1", tracked["mkt_campaign_id"])
        assertEquals("session-1", tracked["mkt_session_id"])
    }

    @Test
    fun `인앱 안 트래킹은 사용자·세션이 바뀌어도 띄운 시점의 사용자·세션으로 보낸다`() {
        clientState.setUserId("u1")
        val ingestion = service()
        ingestion.trackEvent(TestCampaigns.TRIGGER, emptyMap())

        clientState.setUserId("u2")
        sessionManager.sessionId = "session-2"
        view.sessions.single().onTrack("custom_event", "{}")

        val tracked = backend.events.single { it.name == "custom_event" }
        assertEquals("u1", tracked.userId)
        assertEquals("session-1", tracked.properties!!["mkt_session_id"])
    }
}
