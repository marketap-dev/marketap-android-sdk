package com.marketap.sdk.inapp

import com.marketap.sdk.model.external.MarketapLogLevel
import com.marketap.sdk.model.internal.api.InAppCampaignRes
import com.marketap.sdk.model.internal.api.ServerResponse
import com.marketap.sdk.presentation.MarketapRegistry
import com.marketap.sdk.utils.moshi
import com.squareup.moshi.Types
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class CampaignResponseDecodingTest {
    private val adapter = moshi.adapter<ServerResponse<InAppCampaignRes>>(
        Types.newParameterizedType(ServerResponse::class.java, InAppCampaignRes::class.java)
    )
    private val original = javaClass.classLoader!!
        .getResource("in_app_campaigns_response.json")!!.readText()
    private var previousLogLevel = MarketapLogLevel.INFO

    @Before
    fun setUp() {
        previousLogLevel = MarketapRegistry.logLevel
        MarketapRegistry.logLevel = MarketapLogLevel.NONE
    }

    @After
    fun tearDown() {
        MarketapRegistry.logLevel = previousLogLevel
    }

    private fun campaignCount(json: String): Int? = adapter.fromJson(json)?.data?.campaigns?.size

    @Test
    fun `서버 응답을 그대로 읽는다`() {
        assertEquals(4, campaignCount(original))
    }

    @Test
    fun `모르는 조건 값이 있는 캠페인만 제외한다`() {
        assertEquals(3, campaignCount(original.replaceFirst("\"operator\":\"EQUAL\"", "\"operator\":\"ANNIVERSARY_PAST\"")))
        assertEquals(3, campaignCount(original.replaceFirst("\"path\":\"EVENT\"", "\"path\":\"VEHICLE_LIST\"")))
        assertEquals(3, campaignCount(original.replaceFirst("\"dataType\":\"STRING\"", "\"dataType\":\"NEW_TYPE\"")))
    }

    @Test
    fun `읽은 응답은 저장했다 다시 읽어도 같다`() {
        val decoded = adapter.fromJson(original)!!
        assertEquals(decoded, adapter.fromJson(adapter.toJson(decoded)))
    }

    @Test
    fun `캠페인 목록이 없으면 null 이다`() {
        val json = "{\"code\":200,\"message\":\"ok\",\"data\":{\"checksum\":\"c\",\"campaigns\":null}}"
        assertEquals(null, adapter.fromJson(json)?.data?.campaigns)
    }

    @Test
    fun `전체 빈도 규칙을 하나라도 읽을 수 없으면 규칙을 갱신하지 않는다`() {
        val caps = "\"globalFrequencyCaps\":[{\"limit\":3,\"durationMinutes\":60},{\"durationMinutes\":60}]"
        val decoded = adapter.fromJson(original.replaceFirst("\"campaigns\":", "$caps,\"campaigns\":"))?.data
        assertEquals(4, decoded?.campaigns?.size)
        assertNull(decoded?.globalFrequencyCaps)
    }

    @Test
    fun `읽을 수 있는 전체 빈도 규칙은 그대로 읽는다`() {
        val caps = "\"globalFrequencyCaps\":[{\"limit\":3,\"durationMinutes\":60}]"
        val decoded = adapter.fromJson(original.replaceFirst("\"campaigns\":", "$caps,\"campaigns\":"))?.data
        assertEquals(1, decoded?.globalFrequencyCaps?.size)
    }

    @Test
    fun `취소 조건과 플로우 단계의 모르는 값도 그 캠페인만 제외한다`() {
        val unknown = "{\"eventFilter\":{\"eventName\":\"e\"},\"propertyConditions\":[[{\"extractionStrategy\":" +
                "{\"propertySchema\":{\"id\":\"\",\"name\":\"x\",\"dataType\":\"NEW_TYPE\",\"path\":\"EVENT\"}}," +
                "\"operator\":\"EQUAL\",\"targetValues\":[\"x\"]}]]}"
        assertEquals(
            3,
            campaignCount(original.replaceFirst("\"abortConditions\":[]", "\"abortConditions\":[$unknown]"))
        )
        assertEquals(
            3,
            campaignCount(
                original.replaceFirst(
                    "\"abortConditions\":[]",
                    "\"abortConditions\":[],\"flow\":{\"precedingSteps\":[{\"conditions\":[$unknown],\"withinSeconds\":60}]}"
                )
            )
        )
    }
}
