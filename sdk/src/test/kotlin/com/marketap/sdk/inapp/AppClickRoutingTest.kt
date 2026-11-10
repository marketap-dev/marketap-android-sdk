package com.marketap.sdk.inapp

import com.marketap.sdk.presentation.AppClickRoute
import com.marketap.sdk.presentation.AppClickRouting
import org.junit.Assert.assertEquals
import org.junit.Test

class AppClickRoutingTest {

    private fun decide(
        origin: Boolean = true,
        custom: Boolean = false,
        routing: Boolean = true,
        available: Boolean = true,
    ) = AppClickRouting.decide(origin, custom, routing, available)

    @Test
    fun `REG-25 출처가 있고 웹 클릭 처리가 켜져 있고 출처 웹뷰를 쓸 수 있으면 그 웹뷰에서 연다`() {
        assertEquals(AppClickRoute.WEB_VIEW, decide())
    }

    @Test
    fun `REG-25 커스텀 클릭 핸들러가 있거나 설정이 꺼져 있으면 앱 처리로 간다`() {
        assertEquals(AppClickRoute.APP, decide(custom = true))
        assertEquals(AppClickRoute.APP, decide(routing = false))
    }

    @Test
    fun `출처 웹뷰가 떨어졌거나 화면이 덮였으면 앱 처리로 간다`() {
        assertEquals(AppClickRoute.APP, decide(available = false))
    }

    @Test
    fun `REG-26 출처 없는 메시지는 웹뷰를 쓸 수 있어도 앱 처리로 간다`() {
        assertEquals(AppClickRoute.APP, decide(origin = false))
        assertEquals(AppClickRoute.APP, decide(origin = false, available = true, routing = true))
    }
}
