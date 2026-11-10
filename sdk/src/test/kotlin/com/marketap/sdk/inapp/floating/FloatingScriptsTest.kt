package com.marketap.sdk.inapp.floating

import com.marketap.sdk.client.inapp.floating.FloatingScripts
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingScriptsTest {

    private val document = FloatingScripts.buildDocument("<div id=\"x\"></div>", 63, 126, 1_730_000_000_123L)

    @Test
    fun `타이머 기준은 넘겨받은 노출 시각이다`() {
        assertTrue(document.contains("timerStartAt: 1730000000123"))
        assertFalse(document.contains("Date.now()"))
    }

    @Test
    fun `안전 영역은 픽셀을 CSS 픽셀로 나눠 넘긴다`() {
        assertTrue(document.contains("topSafeArea: 63 / window.devicePixelRatio"))
        assertTrue(document.contains("bottomSafeArea: 126 / window.devicePixelRatio"))
    }

    @Test
    fun `브리지는 팝업과 다른 이름을 쓴다`() {
        assertTrue(document.contains("window.MarketapFloating"))
        assertFalse(document.contains("MarketapSDK"))
    }

    @Test
    fun `템플릿 뒤에 초기화 이벤트와 감시 스크립트를 순서대로 둔다`() {
        val template = document.indexOf("<div id=\"x\"></div>")
        val force = document.indexOf("ForceDOMContentLoaded")
        val watcher = document.indexOf("__mktFloatingSync")

        assertTrue(template in 0 until force)
        assertTrue(force < watcher)
    }

    @Test
    fun `안전 영역 갱신 뒤 터치 영역을 다시 받는다`() {
        val script = FloatingScripts.applySafeArea(10, 20)

        assertTrue(script.indexOf("marketapApplySafeArea") < script.indexOf("__mktFloatingSync"))
        assertTrue(script.contains("topSafeArea=10/"))
        assertTrue(script.contains("bottomSafeArea=20/"))
    }

    @Test
    fun `뒤로가기 접기는 템플릿의 Escape 접기를 먼저 쓴다`() {
        val script = FloatingScripts.COLLAPSE_ANIMATED

        assertTrue(script.indexOf("Escape") < script.indexOf("CLOSE"))
    }

    @Test
    fun `템플릿 공개 접기 함수가 있으면 기존 방식보다 먼저 쓴다`() {
        val animated = FloatingScripts.COLLAPSE_ANIMATED
        val instant = FloatingScripts.COLLAPSE_INSTANT

        assertTrue(animated.contains("marketapCollapse({ animated: true })"))
        assertTrue(animated.indexOf("marketapCollapse") < animated.indexOf("Escape"))
        assertTrue(instant.contains("marketapCollapse({ animated: false })"))
        assertTrue(instant.indexOf("marketapCollapse(") < instant.indexOf("_mktReset()"))
    }
}
