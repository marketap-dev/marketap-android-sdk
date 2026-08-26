package com.marketap.sdk.push

import com.marketap.sdk.client.push.PushImageDiagnostics
import kotlin.test.Test
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
        val diagnostics = PushImageDiagnostics.exception(
            java.net.UnknownHostException("static.marketap.io"),
            elapsedMs = 42,
        )

        val properties = diagnostics.toEventProperties()
        assertEquals("UnknownHostException", properties[PushImageDiagnostics.KEY_ERROR])
        assertFalse(
            properties.values.any { it.toString().contains("static.marketap.io") },
            "호스트명이 이벤트 속성으로 새어나가면 안 된다"
        )
    }

    @Test
    fun `null 필드는 이벤트 속성에서 제외된다`() {
        val properties = PushImageDiagnostics.none().toEventProperties()

        assertEquals(mapOf<String, Any>(PushImageDiagnostics.KEY_RESULT to "none"), properties)
    }

    @Test
    fun `describe는 logcat 한 줄로 원인을 구분할 수 있어야 한다`() {
        assertEquals("http_error http=403 128ms", PushImageDiagnostics.httpError(403, 128).describe())
        assertEquals("decode_null http=200 55ms", PushImageDiagnostics.decodeNull(200, 55).describe())
        assertTrue(
            PushImageDiagnostics.exception(OutOfMemoryError(), 7).describe()
                .contains("error=OutOfMemoryError")
        )
    }
}
