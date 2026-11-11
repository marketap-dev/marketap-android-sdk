package com.marketap.sdk.policy

import com.marketap.sdk.client.api.MarketapApi
import com.marketap.sdk.client.api.RetryMarketapBackend
import com.marketap.sdk.client.policy.RemotePolicyStore
import com.marketap.sdk.domain.repository.DeviceManager
import com.marketap.sdk.domain.repository.InternalStorage
import com.marketap.sdk.model.internal.Device
import com.marketap.sdk.model.internal.MarketapServerConfig
import com.marketap.sdk.model.internal.api.*
import com.marketap.sdk.utils.*
import com.squareup.moshi.JsonAdapter
import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class RemotePolicyBackendTest {
    private val project = UUID.randomUUID().toString()
    private val storage = PolicyMemoryStorage()
    private val api = mockk<MarketapApi>()
    private val device = mockk<DeviceManager>()
    private val ok = ServerResponse(data = IngestRes(true), code = 0, message = "ok")
    private val sent = java.util.concurrent.CopyOnWriteArrayList<IngestEventRequest>()

    @Before
    fun setup() {
        MarketapServerConfig.lastFetchedAtMs = System.currentTimeMillis()
        every { device.isDeviceReady() } returns true
        every { device.getDevice() } returns mockk<Device>(relaxed = true) { every { token } returns "token" }
        coEvery { api.track(any(), any()) } answers {
            sent.add(secondArg())
            ok
        }
        coEvery { api.updateProfile(any(), any()) } returns ok
        coEvery { api.updateDevice(any(), any()) } returns ok
    }

    private fun payload(revision: Int, block: String, mode: String = "stop", expires: String? = null): Any {
        val value = mutableMapOf<String, Any>("mode" to mode)
        expires?.let { value["expiresAt"] = it }
        return mapOf("version" to 1, "revision" to revision, block to value)
    }

    private fun event() = IngestEventRequest(null, "evt", null, DeviceReq("d"), null)
    private fun pending() = storage.getItemList("events", pairAdapter<String, IngestEventRequest>())
    private fun server(policy: Any?) = ServerInfoRes(0, false).also { it.sdkPolicy = policy }
    private fun await(condition: () -> Boolean) {
        val until = System.currentTimeMillis() + 3000
        while (!condition() && System.currentTimeMillis() < until) Thread.sleep(10)
        assertTrue(condition())
    }

    @Test
    fun `수집 중지 중 이벤트와 사용자 프로필은 버리고 디바이스는 보낸다`() {
        val backend = RetryMarketapBackend(storage, api, device)
        backend.remotePolicy.accept(project, payload(1, "collection"))
        backend.track(project, event())
        backend.updateProfile(project, UpdateProfileRequest("u", mapOf("x" to "y"), DeviceReq("d")))
        backend.updateDevice(project, DeviceReq("d", token = "token"))
        coVerify(timeout = 3000, exactly = 1) { api.updateDevice(any(), any()) }
        Thread.sleep(200)
        coVerify(exactly = 0) { api.track(any(), any()) }
        coVerify(exactly = 0) { api.updateProfile(any(), any()) }
        assertTrue(pending().isEmpty())
        assertNull(storage.getItem("pending_user_profile", pairAdapter<String, UpdateProfileRequest>()))
    }

    @Test
    fun `중지 중 갱신 시간이 지났으면 시간 캐시와 무관하게 먼저 조회해 해제한다`() {
        RemotePolicyStore(storage).accept(project, payload(1, "collection"))
        val backend = RetryMarketapBackend(storage, api, device)
        coEvery { api.getServerInfo(project) } returns server(payload(2, "collection", "allow"))
        backend.prefetchServerInfo(project)
        await { backend.remotePolicy.currentlyAllowed(project, "collection") }
        backend.track(project, event())
        await { runCatching { coVerify { api.track(project, any()) } }.isSuccess }
        coVerify(exactly = 1) { api.getServerInfo(project) }
    }

    @Test
    fun `갱신 실패 시 중지를 유지하고 저장 블록이 만료되면 허용한다`() {
        RemotePolicyStore(storage).accept(project, payload(1, "collection"))
        val backend = RetryMarketapBackend(storage, api, device)
        coEvery { api.getServerInfo(project) } throws java.io.IOException("offline")
        backend.track(project, event())
        await { runCatching { coVerify { api.getServerInfo(project) } }.isSuccess }
        Thread.sleep(100)
        coVerify(exactly = 0) { api.track(any(), any()) }
        backend.remotePolicy.accept(project, payload(2, "collection", expires = "2020-01-01T00:00:00Z"))
        backend.track(project, event())
        await { runCatching { coVerify { api.track(project, any()) } }.isSuccess }
    }

    @Test
    fun `서버 응답에 정책이 없어도 저장된 중지는 유지한다`() {
        RemotePolicyStore(storage).accept(project, payload(1, "collection"))
        val backend = RetryMarketapBackend(storage, api, device)
        coEvery { api.getServerInfo(project) } returns server(null)
        backend.track(project, event())
        await { runCatching { coVerify { api.getServerInfo(project) } }.isSuccess }
        Thread.sleep(100)
        coVerify(exactly = 0) { api.track(any(), any()) }
        assertTrue(pending().isEmpty())
    }

    @Test
    fun `푸시는 저장 정책만 읽고 중지 시 클릭과 도착 추적을 전송하지 않는다`() {
        RemotePolicyStore(storage).accept(project, payload(1, "pushTracking"))
        val backend = RetryMarketapBackend(storage, api, device, pushOnly = true)
        backend.track(project, event().copy(name = "mkt_delivery_message"))
        backend.track(project, event().copy(name = "mkt_click_message"))
        Thread.sleep(200)
        coVerify(exactly = 0) { api.track(any(), any()) }
        coVerify(exactly = 0) { api.getServerInfo(any()) }
        assertTrue(storage.getItemList("push_events", pairAdapter<String, IngestEventRequest>()).isEmpty())
        RemotePolicyStore(storage).accept(project, payload(2, "pushTracking", "allow"))
        backend.track(project, event())
        await { runCatching { coVerify { api.track(project, any()) } }.isSuccess }
        coVerify(exactly = 0) { api.getServerInfo(any()) }
    }

    @Test
    fun `앱 백엔드는 push 큐를 푸시 추적 정책으로 처리하고 푸시 백엔드는 수집 큐를 건드리지 않는다`() {
        val adapter = pairAdapter<String, IngestEventRequest>()
        storage.queueItem("push_events", PairEntry(project, event().copy(name = "mkt_click_message")), adapter)
        RemotePolicyStore(storage).accept(project, payload(1, "pushTracking"))
        RetryMarketapBackend(storage, api, device).track(project, event())
        await { sent.any { it.name == "evt" } }
        Thread.sleep(100)
        assertEquals(listOf("evt"), sent.map { it.name })
        assertTrue(storage.getItemList("push_events", adapter).isEmpty())

        sent.clear()
        storage.queueItem("push_events", PairEntry(project, event().copy(name = "mkt_click_message")), adapter)
        RemotePolicyStore(storage).accept(project, payload(2, "pushTracking", "allow"))
        RetryMarketapBackend(storage, api, device).track(project, event())
        await { sent.size == 2 }
        assertEquals(setOf("evt", "mkt_click_message"), sent.map { it.name }.toSet())

        sent.clear()
        storage.queueItem("events", PairEntry(project, event().copy(name = "kept")), adapter)
        RetryMarketapBackend(storage, api, device, pushOnly = true).track(project, event().copy(name = "mkt_click_message"))
        await { sent.size == 1 }
        Thread.sleep(100)
        assertEquals(listOf("mkt_click_message"), sent.map { it.name })
        assertEquals(listOf("kept"), storage.getItemList("events", adapter).map { it.second.name })
    }

    @Test
    fun `수집 중지여도 푸시 추적이 허용되면 전송한다`() {
        RemotePolicyStore(storage).accept(project, payload(1, "collection"))
        RetryMarketapBackend(storage, api, device, pushOnly = true).track(project, event())
        await { runCatching { coVerify { api.track(project, any()) } }.isSuccess }
        coVerify(exactly = 0) { api.getServerInfo(any()) }
    }

    @Test
    fun `인앱 중지면 두 API 조회를 하지 않고 캠페인 없음으로 처리한다`() {
        val backend = RetryMarketapBackend(storage, api, device)
        backend.remotePolicy.accept(project, payload(1, "inApp"))
        var list: InAppCampaignRes? = null
        var single: InAppCampaignSingleRes? = null
        var stored = false
        backend.fetchCampaigns(FetchCampaignsReq(project, null, DeviceReq("d"), null), { list = it }) { stored = true }
        backend.fetchCampaign("c", FetchCampaignReq(project, null, DeviceReq("d"), "evt", null), { single = it }) { stored = true }
        assertEquals(emptyList<Any>(), list?.campaigns)
        assertNotNull(single)
        assertNull(single?.campaign)
        assertFalse(stored)
        coVerify(exactly = 0) { api.fetchCampaigns(any()) }
        coVerify(exactly = 0) { api.fetchCampaign(any(), any()) }
    }

    @Test
    fun `server-info 실패 후에는 대기 이벤트마다 조회하지 않고 60초 뒤로 미룬다`() {
        MarketapServerConfig.lastFetchedAtMs = 0
        val adapter = pairAdapter<String, IngestEventRequest>()
        repeat(20) { storage.queueItem("events", PairEntry(project, event().copy(name = "old$it")), adapter) }
        coEvery { api.getServerInfo(project) } throws java.io.IOException("offline")
        val backend = RetryMarketapBackend(storage, api, device)
        repeat(3) { backend.track(project, event()) }
        await { sent.size == 23 }
        await { !backend.remotePolicy.refreshDue(project) }
        coVerify(exactly = 1) { api.getServerInfo(project) }
        assertFalse(backend.remotePolicy.refreshDue(project))
    }

    @Test
    fun `server-info가 응답하지 않아도 실패 후 60초 안에는 다시 기다리지 않는다`() {
        MarketapServerConfig.lastFetchedAtMs = 0
        coEvery { api.getServerInfo(project) } coAnswers {
            kotlinx.coroutines.delay(60_000)
            server(null)
        }
        val backend = RetryMarketapBackend(storage, api, device)
        repeat(3) { backend.track(project, event()) }
        val until = System.currentTimeMillis() + 20_000
        while (sent.size < 3 && System.currentTimeMillis() < until) Thread.sleep(20)
        assertEquals(3, sent.size)
        coVerify(exactly = 1) { api.getServerInfo(project) }
    }

    @Test
    fun `이벤트 큐 저장은 정책 잠금 밖에서 하고 인앱 확인은 잠금을 기다리지 않는다`() {
        val lockedDuringQueue = java.util.concurrent.CopyOnWriteArrayList<Boolean>()
        val observed = object : InternalStorage by storage {
            override fun <T> queueItem(key: String, value: T, adapter: JsonAdapter<T>) {
                lockedDuringQueue.add(Thread.holdsLock(RemotePolicyStore.lock))
                storage.queueItem(key, value, adapter)
            }
        }
        coEvery { api.track(project, any()) } throws java.io.IOException("offline")
        val backend = RetryMarketapBackend(observed, api, device)
        backend.remotePolicy.accept(project, payload(1, "inApp"))
        backend.track(project, event())
        await { lockedDuringQueue.size >= 2 }
        assertFalse(lockedDuringQueue.any { it })

        val locked = CountDownLatch(1)
        val release = CountDownLatch(1)
        Thread { synchronized(RemotePolicyStore.lock) { locked.countDown(); release.await() } }.start()
        assertTrue(locked.await(3, TimeUnit.SECONDS))
        val executor = Executors.newSingleThreadExecutor()
        try {
            assertFalse(executor.submit<Boolean> { backend.remotePolicy.currentlyAllowed(project, "inApp") }.get(1, TimeUnit.SECONDS))
        } finally {
            release.countDown()
            executor.shutdown()
        }
    }

    @Test
    fun `앱 업데이트로 SDK 식별 정보가 바뀌면 저장 정책을 지워 푸시 추적도 허용으로 본다`() {
        RemotePolicyStore(storage, sdkType = "flutter").accept(project, mapOf("version" to 1, "revision" to 7, "collection" to mapOf("mode" to "stop"), "pushTracking" to mapOf("mode" to "stop")))
        RetryMarketapBackend(storage, api, device, sdkType = "react_native").track(project, event())
        await { sent.any { it.name == "evt" } }
        RetryMarketapBackend(storage, api, device, pushOnly = true, sdkType = "flutter").track(project, event().copy(name = "mkt_click_message"))
        await { sent.any { it.name == "mkt_click_message" } }
    }

    @Test
    fun `앱 백엔드가 푸시 큐에서 꺼낸 이벤트는 푸시 출처 API로 보낸다`() {
        val pushApi = mockk<MarketapApi>()
        val pushSent = java.util.concurrent.CopyOnWriteArrayList<String>()
        coEvery { pushApi.track(any(), any()) } answers {
            pushSent.add(secondArg<IngestEventRequest>().name)
            ok
        }
        storage.queueItem("push_events", PairEntry(project, event().copy(name = "mkt_click_message")), pairAdapter<String, IngestEventRequest>())
        RetryMarketapBackend(storage, api, device, pushApi = pushApi).track(project, event())
        await { pushSent.size == 1 && sent.size == 1 }
        Thread.sleep(100)
        assertEquals(listOf("mkt_click_message"), pushSent)
        assertEquals(listOf("evt"), sent.map { it.name })
    }

    @Test
    fun `중지 후 해제되어도 전환 전 실패한 이벤트를 큐에 복구하지 않는다`() {
        val entered = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val release = CompletableDeferred<Unit>()
        coEvery { api.track(project, any()) } coAnswers {
            entered.countDown()
            release.await()
            finished.countDown()
            throw java.io.IOException("offline")
        }
        val backend = RetryMarketapBackend(storage, api, device)
        backend.remotePolicy.accept(project, payload(1, "collection", "allow"))
        backend.track(project, event())
        assertTrue(entered.await(3, TimeUnit.SECONDS))
        backend.remotePolicy.accept(project, payload(2, "collection"))
        backend.remotePolicy.accept(project, payload(3, "collection", "allow"))
        release.complete(Unit)
        assertTrue(finished.await(3, TimeUnit.SECONDS))
        Thread.sleep(100)
        assertTrue(pending().isEmpty())
    }
    @Test
    fun `인앱 응답이 중지 후 도착하면 콜백에 전달하지 않는다`() {
        val entered = CountDownLatch(1)
        val release = CompletableDeferred<Unit>()
        val completed = CountDownLatch(1)
        coEvery { api.fetchCampaigns(any()) } coAnswers {
            entered.countDown()
            release.await()
            ServerResponse(0, "ok", InAppCampaignRes("new", emptyList()))
        }
        val backend = RetryMarketapBackend(storage, api, device)
        var cached = false
        Thread {
            runCatching {
                backend.fetchCampaigns(FetchCampaignsReq(project, null, DeviceReq("d"), null), null) { cached = true }
            }
            completed.countDown()
        }.start()
        assertTrue(entered.await(3, TimeUnit.SECONDS))
        backend.remotePolicy.accept(project, payload(1, "inApp"))
        backend.remotePolicy.accept(project, payload(2, "inApp", "allow"))
        release.complete(Unit)
        assertTrue(completed.await(3, TimeUnit.SECONDS))
        assertFalse(cached)
    }

}
