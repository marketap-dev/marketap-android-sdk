package com.marketap.sdk.inapp

import com.marketap.sdk.client.inapp.ExternalWebViewOrigins
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalWebViewOriginsTest {

    @Test
    fun `웹뷰마다 출처가 다르다`() {
        val origins = ExternalWebViewOrigins()
        origins.attach("a")
        origins.attach("b")

        assertSame(origins.originFor("a"), origins.originFor("a"))
        assertNotSame(origins.originFor("a"), origins.originFor("b"))
        assertNotSame(origins.originFor("a"), origins.originFor(ExternalWebViewOrigins.DEFAULT_ID))
    }

    @Test
    fun `해제한 웹뷰의 출처만 사라진 것으로 본다`() {
        val origins = ExternalWebViewOrigins()
        origins.commandCallback = { _, _ -> }
        origins.attach("a")
        origins.attach("b")
        val a = origins.originFor("a")
        val b = origins.originFor("b")

        assertSame(a, origins.detach("a"))

        assertTrue(a.isGone)
        assertFalse(a.canOpenUrl())
        assertFalse(b.isGone)
        assertTrue(b.canOpenUrl())
        assertNull(origins.existing("a"))
        assertNull("등록된 적 없는 웹뷰는 닫을 출처가 없다", origins.detach("c"))
    }

    @Test
    fun `기본 출처는 해제해도 남고 다시 붙일 수 있다`() {
        val origins = ExternalWebViewOrigins()
        origins.commandCallback = { _, _ -> }
        origins.attach(ExternalWebViewOrigins.DEFAULT_ID)
        val origin = origins.originFor(ExternalWebViewOrigins.DEFAULT_ID)

        origins.detach(ExternalWebViewOrigins.DEFAULT_ID)
        assertTrue(origin.isGone)

        origins.attach(ExternalWebViewOrigins.DEFAULT_ID)
        assertSame(origin, origins.originFor(ExternalWebViewOrigins.DEFAULT_ID))
        assertFalse(origin.isGone)
        assertTrue(origin.canOpenUrl())
    }

    @Test
    fun `링크 열기 명령에 웹뷰 id 를 싣는다`() {
        val origins = ExternalWebViewOrigins()
        val commands = mutableListOf<Pair<String, Map<String, Any?>>>()
        origins.commandCallback = { type, payload -> commands += type to payload }

        origins.originFor("a").openUrl("https://shop.example.com/a")
        origins.originFor(ExternalWebViewOrigins.DEFAULT_ID).openUrl("https://shop.example.com/b")

        assertEquals(
            listOf(
                "openUrl" to mapOf("url" to "https://shop.example.com/a", "webViewId" to "a"),
                "openUrl" to mapOf<String, Any?>("url" to "https://shop.example.com/b"),
            ),
            commands,
        )
    }
}
