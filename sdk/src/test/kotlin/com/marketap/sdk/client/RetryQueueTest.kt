package com.marketap.sdk.client

import com.marketap.sdk.client.api.MarketapApi
import com.marketap.sdk.client.api.RetryMarketapBackend
import com.marketap.sdk.domain.repository.DeviceManager
import com.marketap.sdk.domain.repository.InternalStorage
import com.marketap.sdk.model.internal.Device
import com.marketap.sdk.model.internal.MarketapServerConfig
import com.marketap.sdk.model.internal.api.DeviceReq
import com.marketap.sdk.model.internal.api.IngestEventRequest
import com.marketap.sdk.model.internal.api.IngestRes
import com.marketap.sdk.model.internal.api.ServerResponse
import com.marketap.sdk.utils.PairEntry
import com.marketap.sdk.utils.pairAdapter
import com.squareup.moshi.JsonAdapter
import io.mockk.every
import io.mockk.mockk
import io.mockk.coEvery
import io.mockk.coVerify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class RetryQueueTest {

    private class MemoryStorage : InternalStorage {
        val items = mutableMapOf<String, String>()
        val queues = mutableMapOf<String, MutableList<String>>()

        override fun <T> setItem(key: String, value: T?, adapter: JsonAdapter<T>) {
            if (value == null) items.remove(key) else items[key] = adapter.toJson(value)
        }

        override fun <T> getItem(key: String, adapter: JsonAdapter<T>): T? =
            items[key]?.let { adapter.fromJson(it) }

        override fun <T> cacheAndGetItem(
            key: String,
            value: () -> T,
            adapter: JsonAdapter<T>,
            invalidationTime: Long
        ): T? = value()

        @Synchronized
        override fun <T> queueItem(key: String, value: T, adapter: JsonAdapter<T>) {
            queues.getOrPut(key) { mutableListOf() }.add(adapter.toJson(value))
        }

        @Synchronized
        override fun <T> getItemList(key: String, adapter: JsonAdapter<T>): List<T> =
            queues[key].orEmpty().map { adapter.fromJson(it)!! }

        @Synchronized
        override fun <T> popItems(key: String, adapter: JsonAdapter<T>, count: Int): List<T> {
            val q = queues.getOrPut(key) { mutableListOf() }
            val popped = q.take(count)
            repeat(popped.size) { q.removeAt(0) }
            return popped.map { adapter.fromJson(it)!! }
        }

        override fun removeItem(key: String) {
            items.remove(key)
            queues.remove(key)
        }
    }

    private val storage = MemoryStorage()
    private val api = mockk<MarketapApi>()
    private val deviceManager = mockk<DeviceManager>()
    private val ok = ServerResponse(data = IngestRes(true), code = 0, message = "ok")

    @Before
    fun setUp() {
        MarketapServerConfig.lastFetchedAtMs = System.currentTimeMillis()
        every { deviceManager.isDeviceReady() } returns true
        every { deviceManager.getDevice() } returns mockk<Device>(relaxed = true)
    }

    private fun request() = IngestEventRequest(
        id = null, name = "evt", userId = null, device = DeviceReq("d1"), properties = null
    )

    private fun waitFor(timeoutMs: Long = 3000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!cond() && System.currentTimeMillis() < end) Thread.sleep(20)
    }

    private fun pending() = storage.getItemList("events", pairAdapter<String, IngestEventRequest>())

    @Test
    fun `HTTP 오류도 기존대로 큐에 다시 넣는다`() {
        coEvery { api.track(any(), any()) } throws IllegalStateException("HTTP 401")
        RetryMarketapBackend(storage, api, deviceManager).track("p1", request())
        waitFor { runCatching { coVerify(atLeast = 1) { api.track(any(), any()) } }.isSuccess }
        Thread.sleep(300)
        assertEquals(1, pending().size)
    }

    @Test
    fun `5xx 408 429는 큐에 다시 넣는다`() {
        listOf(500, 408, 429).forEach { status ->
            storage.queues.clear()
            coEvery { api.track(any(), any()) } throws IllegalStateException("HTTP $status")
            RetryMarketapBackend(storage, api, deviceManager).track("p1", request())
            waitFor { pending().size == 1 }
            Thread.sleep(300)
            assertEquals("status $status", 1, pending().size)
        }
    }

    @Test
    fun `네트워크 오류는 큐에 다시 넣는다`() {
        coEvery { api.track(any(), any()) } throws java.io.IOException("offline")
        RetryMarketapBackend(storage, api, deviceManager).track("p1", request())
        waitFor { pending().size == 1 }
        Thread.sleep(300)
        assertEquals(1, pending().size)
    }

    @Test
    fun `성공하면 큐가 비어 있다`() {
        coEvery { api.track(any(), any()) } returns ok
        RetryMarketapBackend(storage, api, deviceManager).track("p1", request())
        waitFor { runCatching { coVerify(atLeast = 1) { api.track(any(), any()) } }.isSuccess }
        Thread.sleep(300)
        assertEquals(0, pending().size)
    }

    @Test
    fun `프로필 HTTP 오류도 pending에 복구한다`() {
        coEvery { api.updateDevice(any(), any()) } throws IllegalStateException("HTTP 401")
        RetryMarketapBackend(storage, api, deviceManager).updateDevice("p1", DeviceReq("d1"))
        waitFor { runCatching { coVerify(atLeast = 1) { api.updateDevice(any(), any()) } }.isSuccess }
        Thread.sleep(300)
        org.junit.Assert.assertNotNull(storage.getItem("pending_device_profile", pairAdapter<String, DeviceReq>()))
    }

    @Test
    fun `프로필 500은 pending에 복구한다`() {
        coEvery { api.updateDevice(any(), any()) } throws IllegalStateException("HTTP 500")
        RetryMarketapBackend(storage, api, deviceManager).updateDevice("p1", DeviceReq("d1"))
        waitFor { storage.getItem("pending_device_profile", pairAdapter<String, DeviceReq>()) != null }
        Thread.sleep(300)
        assertEquals(
            PairEntry("p1", DeviceReq("d1")).first,
            storage.getItem("pending_device_profile", pairAdapter<String, DeviceReq>())?.first
        )
    }
}
