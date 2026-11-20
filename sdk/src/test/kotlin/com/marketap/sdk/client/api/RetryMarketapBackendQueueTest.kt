package com.marketap.sdk.client.api

import android.app.Activity
import android.content.Context
import com.marketap.sdk.domain.repository.DeviceManager
import com.marketap.sdk.domain.repository.InternalStorage
import com.marketap.sdk.model.external.MarketapLogLevel
import com.marketap.sdk.model.internal.Device
import com.marketap.sdk.model.internal.MarketapServerConfig
import com.marketap.sdk.model.internal.api.DeviceReq
import com.marketap.sdk.model.internal.api.FetchCampaignReq
import com.marketap.sdk.model.internal.api.FetchCampaignsReq
import com.marketap.sdk.model.internal.api.InAppCampaignRes
import com.marketap.sdk.model.internal.api.InAppCampaignSingleRes
import com.marketap.sdk.model.internal.api.IngestEventRequest
import com.marketap.sdk.model.internal.api.IngestRes
import com.marketap.sdk.model.internal.api.ServerInfoRes
import com.marketap.sdk.model.internal.api.ServerResponse
import com.marketap.sdk.model.internal.api.UpdateProfileRequest
import com.marketap.sdk.presentation.MarketapRegistry
import com.marketap.sdk.utils.PairEntry
import com.marketap.sdk.utils.pairAdapter
import com.squareup.moshi.JsonAdapter
import kotlinx.coroutines.delay
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class RetryMarketapBackendQueueTest {
    private class MemoryStorage : InternalStorage {
        private val items = HashMap<String, Any?>()
        private val queues = HashMap<String, ArrayList<Any?>>()

        @Synchronized
        override fun <T> setItem(key: String, value: T?, adapter: JsonAdapter<T>) {
            items[key] = value
        }

        @Suppress("UNCHECKED_CAST")
        @Synchronized
        override fun <T> getItem(key: String, adapter: JsonAdapter<T>): T? = items[key] as T?

        override fun <T> cacheAndGetItem(
            key: String, value: () -> T, adapter: JsonAdapter<T>, invalidationTime: Long
        ): T? = value()

        @Synchronized
        override fun <T> queueItem(key: String, value: T, adapter: JsonAdapter<T>) {
            queues.getOrPut(key) { ArrayList() }.add(value)
        }

        @Suppress("UNCHECKED_CAST")
        @Synchronized
        override fun <T> getItemList(key: String, adapter: JsonAdapter<T>): List<T> =
            (queues[key] ?: emptyList<Any?>()).toList() as List<T>

        @Suppress("UNCHECKED_CAST")
        @Synchronized
        override fun <T> popItems(key: String, adapter: JsonAdapter<T>, count: Int): List<T> {
            val queue = queues[key] ?: return emptyList()
            val taken = queue.take(count)
            repeat(taken.size) { queue.removeAt(0) }
            return taken as List<T>
        }

        @Synchronized
        override fun removeItem(key: String) {
            items.remove(key)
        }

        @Synchronized
        fun queued(key: String): Int = queues[key]?.size ?: 0
    }

    private class FakeApi(private val offline: AtomicBoolean) : MarketapApi {
        val tracked: MutableSet<String> = Collections.synchronizedSet(HashSet())

        override suspend fun track(projectId: String, request: IngestEventRequest): ServerResponse<IngestRes> {
            delay(2)
            if (offline.get()) throw IOException("offline")
            tracked.add(request.id!!)
            return ServerResponse(200, "ok", IngestRes(true))
        }

        override suspend fun updateDevice(projectId: String, request: DeviceReq) =
            ServerResponse(200, "ok", IngestRes(true))

        override suspend fun updateProfile(projectId: String, request: UpdateProfileRequest) =
            ServerResponse(200, "ok", IngestRes(true))

        override suspend fun fetchCampaigns(request: FetchCampaignsReq): ServerResponse<InAppCampaignRes> {
            delay(5_000)
            throw IOException("slow")
        }

        override suspend fun fetchCampaign(
            campaignId: String, request: FetchCampaignReq
        ): ServerResponse<InAppCampaignSingleRes> = throw IOException("unused")

        override suspend fun getServerInfo(projectId: String) = ServerInfoRes(0, false)
    }

    private val device = DeviceReq(deviceId = "device")
    private val storage = MemoryStorage()
    private val offline = AtomicBoolean(false)
    private val api = FakeApi(offline)
    private lateinit var backend: RetryMarketapBackend
    private var previousLogLevel = MarketapLogLevel.INFO

    private val deviceManager = object : DeviceManager {
        override fun isDeviceReady() = true
        override fun setFirstOpen() = false
        override fun getDevice() = Device(
            appLocalId = "local", os = "os", osVersion = "os", libraryVersion = "1",
            model = "model", manufacturer = "manufacturer", brand = "brand", locale = "ko_KR"
        )
        override fun setToken(token: String) {}
        override fun setDeviceOptIn(optIn: Boolean?) {}
        override fun setAppSetId(appSetId: String) {}
        override fun setGoogleAdvertisingId(gaid: String) {}
        override fun requestAuthorizationForPushNotifications(activity: Activity) {}
        override fun isPushNotificationEnabled(context: Context) = false
    }

    private fun event(id: String) =
        IngestEventRequest(id = id, name = "view", userId = null, device = device, properties = null)

    @Before
    fun setUp() {
        previousLogLevel = MarketapRegistry.logLevel
        MarketapRegistry.logLevel = MarketapLogLevel.NONE
        MarketapServerConfig.lastFetchedAtMs = System.currentTimeMillis()

        backend = RetryMarketapBackend(storage, api, deviceManager)
    }

    @After
    fun tearDown() {
        MarketapRegistry.logLevel = previousLogLevel
    }

    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(20)
        }
        return condition()
    }

    @Test
    fun `쌓인 이벤트가 많아도 재접속 뒤 전부 전송된다`() {
        repeat(200) {
            storage.queueItem("events", PairEntry("project", event("backlog-$it")), pairAdapter())
        }
        repeat(20) { backend.track("project", event("new-$it")) }

        assertTrue(waitUntil(10_000) { api.tracked.size == 220 })
        assertEquals(0, storage.queued("events"))
    }

    @Test
    fun `이벤트가 한꺼번에 몰려도 전부 전송된다`() {
        repeat(300) { backend.track("project", event("burst-$it")) }

        assertTrue(waitUntil(10_000) { api.tracked.size == 300 })
    }

    @Test
    fun `전송에 실패한 이벤트는 큐에 남고 다음 track 에서 다시 전송된다`() {
        offline.set(true)
        repeat(30) { backend.track("project", event("offline-$it")) }
        assertTrue(waitUntil(5_000) { storage.queued("events") == 30 })
        Thread.sleep(200)
        assertEquals(30, storage.queued("events"))

        offline.set(false)
        backend.track("project", event("online"))

        assertTrue(waitUntil(10_000) { api.tracked.size == 31 })
    }

    @Test
    fun `이벤트 전송이 밀려 있어도 캠페인 조회는 제한 시간 안에 돌아온다`() {
        repeat(300) { backend.track("project", event("busy-$it")) }

        val returned = CountDownLatch(1)
        thread {
            backend.fetchCampaigns(FetchCampaignsReq("project", null, device), null) {}
            returned.countDown()
        }

        assertTrue(returned.await(3, TimeUnit.SECONDS))
    }
}
