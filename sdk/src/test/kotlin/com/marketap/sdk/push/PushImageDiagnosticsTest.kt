package com.marketap.sdk.push

import com.marketap.sdk.client.push.PushImageDiagnostics
import com.marketap.sdk.utils.deserialize
import com.marketap.sdk.utils.mapAdapter
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PushImageDiagnosticsTest {

    @Test
    fun `실패 유형별로 서로 다른 result 값을 낸다`() {
        assertEquals("none", PushImageDiagnostics.none().result)
        assertEquals("not_attempted", PushImageDiagnostics.notAttempted().result)
        assertEquals("http_error", PushImageDiagnostics.httpError(404, 10).result)
        assertEquals("decode_null", PushImageDiagnostics.decodeNull(200, 10).result)
        assertEquals(
            "exception",
            PushImageDiagnostics.exception(java.net.SocketTimeoutException(), 10).result
        )
    }

    @Test
    fun `예외는 클래스명만 싣고 메시지는 싣지 않는다`() {
        val message = PushImageDiagnostics.exception(
            java.net.UnknownHostException("static.marketap.io"),
            elapsedMs = 42,
        ).toResultMessage()

        assertContains(message, "UnknownHostException")
        assertFalse(
            message.contains("static.marketap.io"),
            "호스트명이 이벤트 속성으로 새어나가면 안 된다: $message"
        )
    }

    @Test
    fun `null 필드는 JSON 에서 제외된다`() {
        val message = PushImageDiagnostics.none().toResultMessage()

        assertEquals("""{"image_result":"none"}""", message)
    }

    @Test
    fun `mkt_result_message 는 파싱 가능한 JSON 이다`() {
        // 새 이벤트 속성 키를 만들 수 없어 여기에 접어 넣는다. 깨지면 원격 진단이 통째로 무의미해진다.
        val message = PushImageDiagnostics.ok(200, 128, 720, 350, 1_008_000).toResultMessage()

        val parsed = message.deserialize(mapAdapter<String, Any>())
        assertEquals("ok", parsed["image_result"])
        assertEquals("720x350", parsed["image_size"])
        assertEquals(200.0, parsed["image_http_code"])
    }

    @Test
    fun `image_size 에는 맵 크기가 아니라 이미지 크기가 실린다`() {
        // 회귀 방지: buildMap 수신 객체 때문에 size 프로퍼티가 Map.size 로 해석돼
        // 맵 크기 1 이 실린 적이 있다.
        val message = PushImageDiagnostics.ok(200, 10, 720, 350, 1_008_000).toResultMessage()

        assertContains(message, """"image_size":"720x350"""")
    }

    @Test
    fun `describe는 logcat 한 줄로 원인을 구분할 수 있어야 한다`() {
        assertEquals("http_error http=403 128ms", PushImageDiagnostics.httpError(403, 128).describe())
        assertEquals("decode_null http=200 55ms", PushImageDiagnostics.decodeNull(200, 55).describe())
        assertTrue(
            PushImageDiagnostics.exception(OutOfMemoryError(), 7).describe()
                .contains("error=OutOfMemoryError")
        )
        assertEquals(
            "ok http=200 size=720x350 bytes=1008000 128ms",
            PushImageDiagnostics.ok(200, 128, 720, 350, 1_008_000).describe()
        )
    }
}
