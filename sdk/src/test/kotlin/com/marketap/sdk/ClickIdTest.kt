package com.marketap.sdk

import com.marketap.sdk.domain.repository.DeviceManager
import com.marketap.sdk.domain.repository.InternalStorage
import com.marketap.sdk.domain.repository.MarketapBackend
import com.marketap.sdk.domain.repository.SessionManager
import com.marketap.sdk.domain.service.event.ClickIdStore
import com.marketap.sdk.domain.service.event.EventIngestionService
import com.marketap.sdk.domain.service.event.UserIngestionService
import com.marketap.sdk.domain.service.inapp.InAppService
import com.marketap.sdk.domain.service.state.ClientStateManager
import com.marketap.sdk.model.internal.api.IngestEventRequest
import com.squareup.moshi.JsonAdapter
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClickIdTest {

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

    private val clid = "8f3a1c2e-4b5d-4e6f-8a9b-0c1d2e3f4a5b"
    private val other = "11111111-2222-4333-8444-555555555555"
    private var clock = 1_000_000L

    private fun store(storage: InternalStorage = MapStorage(), enabled: () -> Boolean = { true }) =
        ClickIdStore(storage, now = { clock }, enabled = enabled)

    @Test
    fun `링크의 mkt_clid 를 https 주소와 앱 스킴 주소에서 꺼낸다`() {
        assertEquals(clid, ClickIdStore.clickIdFromUrl("https://shop.com/p/1?utm_source=kakao&mkt_clid=$clid"))
        assertEquals(clid, ClickIdStore.clickIdFromUrl("myapp://product/1?mkt_clid=$clid&id=3"))
    }

    @Test
    fun `mkt_clid 가 없거나 UUID 가 아니면 null 이다`() {
        assertNull(ClickIdStore.clickIdFromUrl("https://shop.com/p/1?utm_source=kakao"))
        assertNull(ClickIdStore.clickIdFromUrl("https://shop.com/p/1?mkt_clid="))
        assertNull(ClickIdStore.clickIdFromUrl("https://shop.com/p/1?mkt_clid=abc"))
        assertNull(ClickIdStore.clickIdFromUrl("not a url"))
    }

    private fun ClickIdStore.next(properties: Map<String, Any>, eventName: String = "mkt_page_view"): Map<String, Any> {
        val (result, clickId) = attach(eventName, properties)
        clickId?.let(::consume)
        return result
    }

    @Test
    fun `보관한 클릭 ID 는 큐에 들어간 다음 이벤트 하나에만 붙는다`() {
        val store = store()
        store.setPending(clid)

        assertEquals(mapOf("a" to 1, "mkt_clid" to clid), store.next(mapOf("a" to 1)))
        assertEquals(mapOf("a" to 2), store.next(mapOf("a" to 2)))
    }

    @Test
    fun `큐에 들어가기 전이면 다음 이벤트에도 붙는다`() {
        val store = store()
        store.setPending(clid)

        assertEquals(clid, store.attach("mkt_page_view", emptyMap()).second)
        assertEquals(mapOf("mkt_clid" to clid), store.attach("mkt_page_view", emptyMap()).first)
    }

    @Test
    fun `소비 전에 새 링크가 들어오면 새 값은 남긴다`() {
        val store = store()
        store.setPending(clid)
        val (_, attached) = store.attach("mkt_page_view", emptyMap())
        store.setPending(other)
        store.consume(attached!!)

        assertEquals(mapOf("mkt_clid" to other), store.next(emptyMap()))
    }

    @Test
    fun `메시지 노출·클릭 이벤트에는 싣지 않고 남긴다`() {
        val store = store()
        store.setPending(clid)

        assertEquals(emptyMap<String, Any>(), store.next(emptyMap(), "mkt_click_message"))
        assertEquals(emptyMap<String, Any>(), store.next(emptyMap(), "mkt_delivery_message"))
        assertEquals(mapOf("mkt_clid" to clid), store.next(emptyMap()))
    }

    @Test
    fun `받은 시각부터 30분이 지나면 붙이지 않는다`() {
        val store = store()
        store.setPending(clid, receivedAt = clock - 30 * 60 * 1000L - 1)

        assertEquals(mapOf("a" to 1), store.next(mapOf("a" to 1)))
    }

    @Test
    fun `수집이 중지되면 붙이지도 지우지도 않는다`() {
        var enabled = false
        val store = store { enabled }
        store.setPending(clid)

        assertEquals(mapOf("a" to 1), store.next(mapOf("a" to 1)))
        enabled = true
        assertEquals(mapOf("a" to 1, "mkt_clid" to clid), store.next(mapOf("a" to 1)))
    }

    @Test
    fun `이벤트에 같은 클릭 ID 가 있으면 그대로 두고 보관한 값을 지운다`() {
        val store = store()
        store.setPending(clid)

        assertEquals(mapOf("mkt_clid" to clid), store.next(mapOf("mkt_clid" to clid)))
        assertEquals(mapOf("a" to 1), store.next(mapOf("a" to 1)))
    }

    @Test
    fun `이벤트에 다른 클릭 ID 가 있으면 이벤트 값을 두고 보관한 값은 다음 이벤트에 붙인다`() {
        val store = store()
        store.setPending(clid)

        assertEquals(mapOf("mkt_clid" to other), store.next(mapOf("mkt_clid" to other)))
        assertEquals(mapOf("mkt_clid" to clid), store.next(emptyMap()))
    }

    @Test
    fun `링크가 연달아 들어오면 마지막 클릭 ID 를 붙인다`() {
        val store = store()
        store.setPending(clid)
        store.setPending(other)

        assertEquals(mapOf("mkt_clid" to other), store.next(emptyMap()))
    }

    @Test
    fun `일반 이벤트에 클릭 ID 를 싣고 세션 시작 이벤트에는 싣지 않는다`() {
        val sent = mutableListOf<IngestEventRequest>()
        val backend = mockk<MarketapBackend>(relaxed = true)
        val req = slot<IngestEventRequest>()
        every { backend.track(any(), capture(req)) } answers { sent.add(req.captured) }
        every { backend.track(any(), capture(req), any()) } answers {
            sent.add(req.captured)
            thirdArg<() -> Unit>().invoke()
        }
        val state = mockk<ClientStateManager>(relaxed = true)
        every { state.getUserId() } returns null
        every { state.getProjectId() } returns "p1"
        var started = false
        val sessionManager = object : SessionManager {
            override fun updateActivity() {}
            override fun getSessionId(onSessionStart: (sessionId: String) -> Unit): String {
                if (!started) { started = true; onSessionStart("s1") }
                return "s1"
            }
        }
        val service = EventIngestionService(
            backend,
            mockk<InAppService>(relaxed = true),
            state,
            sessionManager,
            mockk<DeviceManager>(relaxed = true),
            mockk<UserIngestionService>(relaxed = true),
            clickIdStore = store(),
        )

        service.setPendingClickId(clid, clock)
        service.trackEvent("mkt_page_view", mapOf("a" to 1))
        service.trackEvent("mkt_add_to_cart", emptyMap())

        assertEquals(listOf("mkt_session_start", "mkt_page_view", "mkt_add_to_cart"), sent.map { it.name })
        assertEquals(listOf(null, clid, null), sent.map { it.properties?.get("mkt_clid") })
    }
}
