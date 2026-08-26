package com.marketap.sdk.push

import com.marketap.sdk.client.push.PushImageDiagnostics
import com.marketap.sdk.model.internal.AppEventProperty
import com.marketap.sdk.model.internal.push.PushData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 콘솔 테스트 발송(targetMode=device)은 deviceId를 내려주지 않는다.
 * 예전에는 그 탓에 deliveryData가 null이 되어 노출/클릭 이벤트가 통째로 유실됐다.
 */
class PushDeliveryDataTest {

    private fun basePayload() = mutableMapOf(
        "source" to "marketap",
        "title" to "이미지 TEST",
        "message" to "이미지 TEST",
        "imageUrl" to "https://static.marketap.io/images/dv0ok8v/57d0zik5.jpg",
        "projectId" to "dcbrbnz",
        "campaignId" to "test_campaign_id",
        "messageId" to "69d496ee-332c-4403-a3f0-87055a5f3a64",
        "notificationId" to "424242",
    )

    @Test
    fun `deviceId가 없어도 deliveryData를 만든다`() {
        val pushData = PushData.fromMap(basePayload())

        assertNotNull(pushData)
        val delivery = assertNotNull(pushData.deliveryData)
        assertNull(delivery.deviceId)
        assertEquals("dcbrbnz", delivery.projectId)
        assertEquals("test_campaign_id", delivery.campaignId)
    }

    @Test
    fun `deviceId가 있으면 그대로 보존한다`() {
        val payload = basePayload().apply {
            put("deviceId", "gaid:1a5cfc5c-9276-4abe-89d3-781028749b5d")
        }

        val delivery = assertNotNull(PushData.fromMap(payload)?.deliveryData)
        assertEquals("gaid:1a5cfc5c-9276-4abe-89d3-781028749b5d", delivery.deviceId)
    }

    @Test
    fun `projectId나 campaignId나 messageId가 없으면 deliveryData는 null이다`() {
        listOf("projectId", "campaignId", "messageId").forEach { missing ->
            val payload = basePayload().apply { remove(missing) }
            val pushData = assertNotNull(PushData.fromMap(payload), "payload는 여전히 유효해야 한다")
            assertNull(pushData.deliveryData, "$missing 누락 시 deliveryData는 null")
        }
    }

    @Test
    fun `title이나 message가 없으면 푸시 자체를 무시한다`() {
        listOf("title", "message").forEach { missing ->
            val payload = basePayload().apply { remove(missing) }
            assertNull(PushData.fromMap(payload), "$missing 누락 시 PushData는 null")
        }
    }

    @Test
    fun `이미지 진단값이 이벤트 속성에 병합된다`() {
        val delivery = assertNotNull(PushData.fromMap(basePayload())?.deliveryData)
        val diagnostics = PushImageDiagnostics.httpError(httpCode = 403, elapsedMs = 128)

        val properties = AppEventProperty.offSite(delivery)
            .addProperties(diagnostics.toEventProperties())
            .toMap()

        assertEquals("http_error", properties[PushImageDiagnostics.KEY_RESULT])
        assertEquals(403, properties[PushImageDiagnostics.KEY_HTTP_CODE])
        assertEquals(128L, properties[PushImageDiagnostics.KEY_ELAPSED_MS])
        // 기존 채널 속성은 그대로 유지되어야 한다
        assertEquals("test_campaign_id", properties["mkt_campaign_id"])
        assertEquals("PUSH", properties["mkt_channel_type"])
    }

    @Test
    fun `진단값이 없으면 이벤트 속성이 늘어나지 않는다`() {
        val delivery = assertNotNull(PushData.fromMap(basePayload())?.deliveryData)

        val withoutDiagnostics = AppEventProperty.offSite(delivery).addProperties(emptyMap()).toMap()

        assertTrue(withoutDiagnostics.keys.none { it.startsWith("mkt_image_") })
    }
}
