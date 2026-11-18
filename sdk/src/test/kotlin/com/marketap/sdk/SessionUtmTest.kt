package com.marketap.sdk

import com.marketap.sdk.client.AndroidSessionManager
import com.marketap.sdk.client.policy.RemotePolicyStore
import com.marketap.sdk.domain.repository.DeviceManager
import com.marketap.sdk.domain.repository.InternalStorage
import com.marketap.sdk.domain.repository.MarketapBackend
import com.marketap.sdk.domain.repository.SessionManager
import com.marketap.sdk.domain.service.event.EventIngestionService
import com.marketap.sdk.domain.service.event.SessionUtmStore
import com.marketap.sdk.domain.service.event.UserIngestionService
import com.marketap.sdk.domain.service.inapp.InAppService
import com.marketap.sdk.domain.service.state.ClientStateManager
import com.marketap.sdk.model.internal.AppEventProperty
import com.marketap.sdk.model.internal.api.DeviceReq
import com.marketap.sdk.model.internal.api.IngestEventRequest
import com.marketap.sdk.utils.longAdapter
import com.squareup.moshi.JsonAdapter
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionUtmTest {

    private class MapStorage : InternalStorage {
        val items = mutableMapOf<String, String>()
        override fun <T> setItem(key: String, value: T?, adapter: JsonAdapter<T>) {
            if (value == null) items.remove(key) else items[key] = adapter.toJson(value)
        }
        override fun <T> getItem(key: String, adapter: JsonAdapter<T>): T? = items[key]?.let { adapter.fromJson(it) }
        override fun <T> cacheAndGetItem(key: String, value: () -> T, adapter: JsonAdapter<T>, invalidationTime: Long): T? = value()
        override fun <T> queueItem(key: String, value: T, adapter: JsonAdapter<T>) = throw UnsupportedOperationException()
        override fun <T> getItemList(key: String, adapter: JsonAdapter<T>): List<T> = throw UnsupportedOperationException()
        override fun <T> popItems(key: String, adapter: JsonAdapter<T>, count: Int): List<T> = throw UnsupportedOperationException()
        override fun removeItem(key: String) { items.remove(key) }
    }

    private var clock = 1_000_000L

    private fun store(storage: InternalStorage = MapStorage(), enabled: () -> Boolean = { true }) =
        SessionUtmStore(storage, now = { clock }, enabled = enabled)

    @Test
    fun `UTM이 들어온 이벤트는 mkt_utm 을 그대로 두고 같은 값을 세션 UTM으로도 붙인다`() {
        val result = store().apply(mapOf("mkt_utm_source" to "naver", "mkt_utm_medium" to "cpc", "a" to 1), "s1")

        assertEquals(
            mapOf(
                "mkt_utm_source" to "naver", "mkt_utm_medium" to "cpc", "a" to 1,
                "mkt_session_utm_source" to "naver", "mkt_session_utm_medium" to "cpc",
            ),
            result,
        )
    }

    @Test
    fun `같은 세션의 이후 이벤트에는 세션 UTM만 붙이고 mkt_utm 은 붙이지 않는다`() {
        val store = store()
        store.apply(mapOf("mkt_utm_source" to "naver"), "s1")

        assertEquals(mapOf("b" to 2, "mkt_session_utm_source" to "naver"), store.apply(mapOf("b" to 2), "s1"))
    }

    @Test
    fun `새 UTM이 오면 세트를 통째로 교체하고 세션이 바뀌면 붙이지 않는다`() {
        val store = store()
        store.apply(mapOf("mkt_utm_source" to "naver", "mkt_utm_campaign" to "fall"), "s1")
        store.apply(mapOf("mkt_utm_source" to "kakao"), "s1")

        assertEquals(mapOf("mkt_session_utm_source" to "kakao"), store.apply(emptyMap(), "s1"))
        assertEquals(emptyMap<String, Any>(), store.apply(emptyMap(), "s2"))
    }

    @Test
    fun `setPending 값은 다음 이벤트부터 세션 UTM으로 붙고 mkt_utm 은 만들지 않는다`() {
        val store = store()
        store.setPending(mapOf("mkt_utm_source" to "meta", "mkt_utm_campaign" to "winter"))

        val first = store.apply(mapOf("a" to 1), "s1")
        assertEquals(mapOf("a" to 1, "mkt_session_utm_source" to "meta", "mkt_session_utm_campaign" to "winter"), first)
        assertEquals(mapOf("b" to 2, "mkt_session_utm_source" to "meta", "mkt_session_utm_campaign" to "winter"), store.apply(mapOf("b" to 2), "s1"))
    }

    @Test
    fun `setPending 값은 30분이 지나면 버리고 이벤트의 UTM이 있으면 이벤트 쪽이 우선한다`() {
        val store = store()
        store.setPending(mapOf("mkt_utm_source" to "meta"))
        clock += 30 * 60 * 1000L + 1
        assertEquals(mapOf("a" to 1), store.apply(mapOf("a" to 1), "s1"))

        store.setPending(mapOf("mkt_utm_source" to "meta"))
        assertEquals("naver", store.apply(mapOf("mkt_utm_source" to "naver"), "s1")["mkt_session_utm_source"])
        assertEquals("naver", store.apply(emptyMap(), "s1")["mkt_session_utm_source"])
    }

    @Test
    fun `꺼져 있으면 저장도 부착도 보관도 하지 않는다`() {
        var enabled = false
        val store = store(enabled = { enabled })
        val withUtm = mapOf("mkt_utm_source" to "naver", "a" to 1)

        assertEquals(withUtm, store.apply(withUtm, "s1"))
        store.setPending(mapOf("mkt_utm_source" to "meta"))
        assertEquals(mapOf("b" to 2), store.apply(mapOf("b" to 2), "s1"))

        enabled = true
        assertEquals(mapOf("b" to 2), store.apply(mapOf("b" to 2), "s1"))
    }

    @Test
    fun `원격 정책 sessionUtm 중지를 받으면 붙이지 않고 다른 항목 중지에는 영향이 없다`() {
        val storage = MapStorage()
        val policy = RemotePolicyStore(storage, now = { 1_000L })
        val store = store(storage) { policy.currentlyAllowed("p1", "sessionUtm") }
        store.apply(mapOf("mkt_utm_source" to "naver"), "s1")

        policy.accept("p1", mapOf("version" to 1.0, "revision" to 1.0, "inApp" to mapOf("mode" to "stop")))
        assertEquals(mapOf("b" to 2, "mkt_session_utm_source" to "naver"), store.apply(mapOf("b" to 2), "s1"))

        policy.accept("p1", mapOf("version" to 1.0, "revision" to 2.0, "sessionUtm" to mapOf("mode" to "stop")))
        assertEquals(mapOf("b" to 2), store.apply(mapOf("b" to 2), "s1"))
    }

    @Test
    fun `stored 는 같은 세션의 저장 값만 돌려주고 보관 중인 UTM을 소비하지 않는다`() {
        var enabled = true
        val store = store(enabled = { enabled })
        store.setPending(mapOf("mkt_utm_source" to "meta"))
        assertEquals(emptyMap<String, String>(), store.stored("s1"))

        assertEquals("meta", store.apply(emptyMap(), "s1")["mkt_session_utm_source"])
        assertEquals(mapOf("mkt_session_utm_source" to "meta"), store.stored("s1"))
        assertEquals(emptyMap<String, String>(), store.stored("s2"))

        enabled = false
        assertEquals(emptyMap<String, String>(), store.stored("s1"))
    }

    @Test
    fun `푸시 클릭 이벤트는 진행 중인 세션이 있을 때만 세션 UTM을 싣는다`() {
        val storage = MapStorage()
        val sessions = AndroidSessionManager(storage)
        assertEquals(null, sessions.activeSessionId())

        val sessionId = sessions.getSessionId { }
        assertEquals(sessionId, sessions.activeSessionId())
        val store = store(storage)
        store.apply(mapOf("mkt_utm_source" to "naver"), sessionId)

        val click = IngestEventRequest.click(
            null,
            DeviceReq("d1"),
            AppEventProperty("c1", "OFF_SITE", "ANDROID", "PUSH", "m1"),
            "{}",
            store.stored(sessions.activeSessionId()!!),
        )
        assertEquals("naver", click.properties?.get("mkt_session_utm_source"))
        assertEquals("c1", click.properties?.get("mkt_campaign_id"))

        storage.setItem("marketap_last_activity", System.currentTimeMillis() - 31 * 60 * 1000L, longAdapter)
        assertEquals(null, sessions.activeSessionId())
    }

    @Test
    fun `링크에서 utm 파라미터만 꺼내고 인코딩을 풀며 빈 값은 버린다`() {
        assertEquals(
            mapOf("mkt_utm_source" to "naver", "mkt_utm_campaign" to "가을 세일"),
            SessionUtmStore.utmFromUrl("myapp://product/1?utm_source=naver&utm_medium=&utm_campaign=%EA%B0%80%EC%9D%84%20%EC%84%B8%EC%9D%BC&id=3"),
        )
        assertEquals(emptyMap<String, String>(), SessionUtmStore.utmFromUrl("not a url"))
        assertEquals(emptyMap<String, String>(), SessionUtmStore.utmFromUrl("https://a.com/b"))
    }

    private fun service(
        storage: InternalStorage,
        sessionIds: List<Pair<String, Boolean>>,
        enabled: Boolean = true,
    ): Pair<EventIngestionService, MutableList<IngestEventRequest>> {
        val sent = mutableListOf<IngestEventRequest>()
        val backend = mockk<MarketapBackend>(relaxed = true)
        val req = slot<IngestEventRequest>()
        every { backend.track(any(), capture(req)) } answers { sent.add(req.captured) }
        val state = mockk<ClientStateManager>(relaxed = true)
        every { state.getUserId() } returns null
        every { state.getProjectId() } returns "p1"
        val queue = ArrayDeque(sessionIds)
        val sessionManager = object : SessionManager {
            override fun updateActivity() {}
            override fun getSessionId(onSessionStart: (sessionId: String) -> Unit): String {
                val (id, isNew) = queue.removeFirst()
                if (isNew) onSessionStart(id)
                return id
            }
        }
        val deviceManager = mockk<DeviceManager>(relaxed = true)
        val service = EventIngestionService(
            backend,
            mockk<InAppService>(relaxed = true),
            state,
            sessionManager,
            deviceManager,
            mockk<UserIngestionService>(relaxed = true),
            store(storage) { enabled },
        )
        return service to sent
    }

    @Test
    fun `세션을 연 이벤트의 UTM은 세션 시작 이벤트에는 세션 UTM으로만 실린다`() {
        val (service, sent) = service(MapStorage(), listOf("s1" to true, "s1" to false))

        service.trackEvent("mkt_page_view", mapOf("mkt_utm_source" to "naver"))
        service.trackEvent("mkt_add_to_cart", mapOf("product" to "p"))

        assertEquals(listOf("mkt_session_start", "mkt_page_view", "mkt_add_to_cart"), sent.map { it.name })
        assertEquals(listOf(null, "naver", null), sent.map { it.properties?.get("mkt_utm_source") })
        sent.forEach { assertEquals("naver", it.properties?.get("mkt_session_utm_source")) }
    }

    @Test
    fun `setUtm 후 새 세션을 여는 이벤트면 세션 시작 이벤트에도 세션 UTM이 실린다`() {
        val (service, sent) = service(MapStorage(), listOf("s1" to true, "s1" to false))

        service.setPendingUtm(mapOf("mkt_utm_source" to "meta"))
        service.trackEvent("mkt_first_visit", emptyMap())
        service.trackEvent("mkt_page_view", emptyMap())

        assertEquals(listOf("mkt_session_start", "mkt_first_visit", "mkt_page_view"), sent.map { it.name })
        sent.forEach {
            assertEquals("meta", it.properties?.get("mkt_session_utm_source"))
            assertNull(it.properties?.get("mkt_utm_source"))
        }
    }

    @Test
    fun `새 세션의 세션 시작 이벤트에는 이전 세션 UTM이 붙지 않고 꺼져 있으면 실리지 않는다`() {
        val (service, sent) = service(MapStorage(), listOf("s1" to true, "s2" to true))
        service.trackEvent("mkt_page_view", mapOf("mkt_utm_source" to "naver"))
        service.trackEvent("mkt_page_view", emptyMap())
        sent.drop(2).forEach { assertNull(it.properties?.get("mkt_session_utm_source")) }

        val (off, offSent) = service(MapStorage(), listOf("s1" to true), enabled = false)
        off.trackEvent("mkt_page_view", mapOf("mkt_utm_source" to "naver"))
        assertEquals(listOf(null, "naver"), offSent.map { it.properties?.get("mkt_utm_source") })
        offSent.forEach { assertNull(it.properties?.get("mkt_session_utm_source")) }
    }
}
