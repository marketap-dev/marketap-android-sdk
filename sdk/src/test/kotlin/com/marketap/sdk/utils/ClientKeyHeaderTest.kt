package com.marketap.sdk.utils

import com.marketap.sdk.client.api.MarketapApiImpl
import com.marketap.sdk.client.policy.RemotePolicyStore
import com.marketap.sdk.model.MarketapConfig
import com.marketap.sdk.model.internal.api.*
import com.marketap.sdk.policy.PolicyMemoryStorage
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.android.Android
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.*
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(io.ktor.util.InternalAPI::class)
class ClientKeyHeaderTest {
    private val requests = mutableListOf<HttpRequestData>()
    private var status = HttpStatusCode.OK
    private var body = """{"data":{"is_success":true},"code":0,"message":"ok"}"""

    @Before
    fun setup() {
        mockkObject(Android)
        every { Android.create(any()) } returns object : HttpClientEngineBase("test") {
            override val config = HttpClientEngineConfig()
            override val dispatcher = kotlinx.coroutines.Dispatchers.IO
            override suspend fun execute(data: HttpRequestData): HttpResponseData {
                requests.add(data)
                return HttpResponseData(status, GMTDate(), headersOf(HttpHeaders.ContentType, "application/json"), HttpProtocolVersion.HTTP_1_1, ByteReadChannel(body), io.ktor.client.engine.callContext())
            }
        }
    }

    @After
    fun cleanup() { unmockkObject(Android) }

    @Test
    fun `모든 API 요청에 키와 식별 헤더를 붙인다`() = runBlocking {
        val api = MarketapApiImpl(clientKeyProvider = { "ck_abc" }, config = MarketapConfig("p", "flutter", "2.3"))
        api.updateDevice("p", DeviceReq("d"))
        api.track("p", IngestEventRequest(null, "evt", null, DeviceReq("d"), null))
        api.updateProfile("p", UpdateProfileRequest("u", emptyMap(), DeviceReq("d")))
        body = """{"data":{"checksum":"c","campaigns":[]},"code":0,"message":"ok"}"""
        api.fetchCampaigns(FetchCampaignsReq("p", null, DeviceReq("d"), null))
        body = """{"data":{"campaign":null},"code":0,"message":"ok"}"""
        api.fetchCampaign("c", FetchCampaignReq("p", null, DeviceReq("d"), "evt", null))
        body = """{"data":{"serverTimeOffset":42,"useWebClickRouting":true,"sdkPolicy":{"version":1,"revision":1,"collection":{"mode":"stop"}}},"code":0,"message":"ok"}"""
        val response = api.getServerInfo("p")
        assertEquals(42L, response.serverTimeOffset)
        assertTrue(response.useWebClickRouting)
        val policy = RemotePolicyStore(PolicyMemoryStorage())
        policy.accept("p", response.sdkPolicy)
        assertFalse(policy.allowed("p", "collection"))
        assertEquals(6, requests.size)
        requests.forEach {
            assertEquals(listOf("ck_abc"), it.headers.getAll(CLIENT_KEY_HEADER))
            sdkHeaders("app", MarketapConfig("p", "flutter", "2.3")).forEach { (name, value) ->
                assertEquals(listOf(value), it.headers.getAll(name))
            }
        }
    }

    @Test
    fun `키 변경은 다음 요청부터 반영하고 null이면 생략한다`() = runBlocking {
        var key: String? = null
        val api = MarketapApiImpl(clientKeyProvider = { key })
        api.updateDevice("p", DeviceReq("d"))
        key = "new_key"
        api.updateDevice("p", DeviceReq("d"))
        key = null
        api.updateDevice("p", DeviceReq("d"))
        assertEquals(listOf(null, "new_key", null), requests.map { it.headers[CLIENT_KEY_HEADER] })
        requests.forEach { assertEquals("android", it.headers["X-Marketap-SDK-Platform"]) }
    }

    @Test
    fun `푸시 요청은 키 없이도 android-push 출처를 보낸다`() = runBlocking {
        MarketapApiImpl(source = "android-push", config = MarketapConfig("p", "react_native", "1.2")).updateDevice("p", DeviceReq("d"))
        assertEquals("android-push", requests.single().headers["X-Marketap-SDK-Source"])
        assertEquals("react-native", requests.single().headers["X-Marketap-SDK-Wrapper"])
        assertNull(requests.single().headers[CLIENT_KEY_HEADER])
    }

    @Test
    fun `HTTP 오류 응답도 기존처럼 응답 어댑터로 처리한다`() = runBlocking {
        status = HttpStatusCode.Unauthorized
        body = """{"data":null,"code":401002,"message":"Invalid client key"}"""
        assertEquals(401002, MarketapApiImpl().updateDevice("p", DeviceReq("d")).code)
    }

    @Test
    fun `정책 없는 server-info도 기존 필드를 파싱한다`() = runBlocking {
        body = """{"data":{"serverTimeOffset":-7,"useWebClickRouting":false},"code":0,"message":"ok"}"""
        val response = MarketapApiImpl().getServerInfo("p")
        assertEquals(-7L, response.serverTimeOffset)
        assertFalse(response.useWebClickRouting)
        assertNull(response.sdkPolicy)
    }
}
