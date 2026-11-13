package com.marketap.sdk

import com.marketap.sdk.domain.repository.DeviceManager
import com.marketap.sdk.domain.repository.InternalStorage
import com.marketap.sdk.domain.repository.MarketapBackend
import com.marketap.sdk.domain.repository.SessionManager
import com.marketap.sdk.domain.service.event.EventIngestionService
import com.marketap.sdk.domain.service.event.SessionUtmStore
import com.marketap.sdk.domain.service.event.UserIngestionService
import com.marketap.sdk.domain.service.inapp.InAppService
import com.marketap.sdk.domain.service.state.ClientStateManager
import com.marketap.sdk.model.internal.api.IngestEventRequest
import com.squareup.moshi.JsonAdapter
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
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

    @Test
    fun `UTM이 있는 이벤트는 그대로 두고 같은 세션의 다음 이벤트에 붙인다`() {
        val store = SessionUtmStore(MapStorage())
        val first = mapOf("mkt_utm_source" to "naver", "mkt_utm_medium" to "cpc", "a" to 1)

        assertEquals(first, store.apply(first, "s1"))
        assertEquals(
            mapOf("b" to 2, "mkt_utm_source" to "naver", "mkt_utm_medium" to "cpc"),
            store.apply(mapOf("b" to 2), "s1"),
        )
    }

    @Test
    fun `새 UTM이 오면 세트를 통째로 교체한다`() {
        val store = SessionUtmStore(MapStorage())
        store.apply(mapOf("mkt_utm_source" to "naver", "mkt_utm_medium" to "cpc", "mkt_utm_campaign" to "fall"), "s1")
        store.apply(mapOf("mkt_utm_source" to "kakao", "mkt_utm_medium" to "message"), "s1")

        assertEquals(
            mapOf("mkt_utm_source" to "kakao", "mkt_utm_medium" to "message"),
            store.apply(emptyMap(), "s1"),
        )
    }

    @Test
    fun `세션이 바뀌면 이전 UTM을 붙이지 않는다`() {
        val store = SessionUtmStore(MapStorage())
        store.apply(mapOf("mkt_utm_source" to "naver"), "s1")

        assertEquals(mapOf("a" to 1), store.apply(mapOf("a" to 1), "s2"))
    }

    @Test
    fun `빈 값 UTM은 없는 것으로 보고 저장값을 붙인다`() {
        val store = SessionUtmStore(MapStorage())
        store.apply(mapOf("mkt_utm_source" to "naver"), "s1")

        assertEquals(mapOf("mkt_utm_source" to "naver"), store.apply(mapOf("mkt_utm_source" to ""), "s1"))
    }

    private fun service(storage: InternalStorage, sessionIds: List<Pair<String, Boolean>>): Pair<EventIngestionService, MutableList<IngestEventRequest>> {
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
            SessionUtmStore(storage),
        )
        return service to sent
    }

    @Test
    fun `세션을 연 이벤트의 UTM이 세션 시작 이벤트와 이후 이벤트에 붙는다`() {
        val (service, sent) = service(MapStorage(), listOf("s1" to true, "s1" to false))

        service.trackEvent("mkt_page_view", mapOf("mkt_utm_source" to "naver"))
        service.trackEvent("mkt_add_to_cart", mapOf("product" to "p"))

        assertEquals(listOf("mkt_session_start", "mkt_page_view", "mkt_add_to_cart"), sent.map { it.name })
        sent.forEach { assertEquals("naver", it.properties?.get("mkt_utm_source")) }
    }

    @Test
    fun `새 세션의 세션 시작 이벤트에는 이전 세션 UTM이 붙지 않는다`() {
        val (service, sent) = service(MapStorage(), listOf("s1" to true, "s2" to true))

        service.trackEvent("mkt_page_view", mapOf("mkt_utm_source" to "naver"))
        service.trackEvent("mkt_page_view", emptyMap())

        val second = sent.drop(2)
        assertEquals(listOf("mkt_session_start", "mkt_page_view"), second.map { it.name })
        second.forEach { assertNull(it.properties?.get("mkt_utm_source")) }
    }
}
