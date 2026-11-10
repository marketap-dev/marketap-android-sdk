package com.marketap.sdk.inapp.floating

import com.marketap.sdk.client.inapp.floating.FloatingArea
import com.marketap.sdk.client.inapp.floating.FloatingLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingLayoutTest {

    @Test
    fun `CSS 영역을 뷰 폭 비율로 픽셀 영역으로 바꾼다`() {
        val layout = FloatingLayout.parse("collapsed", "10,700,400,760", 400f, 1080, 2.625f)

        assertEquals(FloatingArea(27f, 1890f, 1080f, 2052f), layout.area)
        assertFalse(layout.expanded)
        assertTrue(layout.ready)
    }

    @Test
    fun `뷰 폭을 모르면 density 로 바꾼다`() {
        val layout = FloatingLayout.parse("collapsed", "0,0,10,10", 0f, 0, 3f)

        assertEquals(FloatingArea(0f, 0f, 30f, 30f), layout.area)
    }

    @Test
    fun `접힌 동안은 영역 안에서 시작한 터치만 받는다`() {
        val layout = FloatingLayout.parse("collapsed", "0,700,400,760", 400f, 400, 1f)

        assertTrue(layout.capturesTouch(200f, 730f))
        assertFalse(layout.capturesTouch(200f, 699f))
        assertFalse(layout.capturesTouch(200f, 760f))
    }

    @Test
    fun `펼친 동안은 전 영역 터치를 받는다`() {
        val layout = FloatingLayout.parse("expanded", "0,700,400,760", 400f, 400, 1f)

        assertTrue(layout.expanded)
        assertTrue(layout.capturesTouch(5f, 5f))
    }

    @Test
    fun `영역이 없으면 접힌 동안 아무 터치도 받지 않는다`() {
        val layout = FloatingLayout.parse("collapsed", "", 400f, 400, 1f)

        assertNull(layout.area)
        assertFalse(layout.capturesTouch(200f, 730f))
        assertTrue(layout.ready)
    }

    @Test
    fun `상태도 영역도 없으면 아직 보이지 않는다`() {
        assertFalse(FloatingLayout.parse("", "", 400f, 400, 1f).ready)
        assertFalse(FloatingLayout.INITIAL.ready)
    }

    @Test
    fun `확장형이 아닌 메시지는 상태 없이 영역만으로 보인다`() {
        val layout = FloatingLayout.parse("", "20,500,380,760", 400f, 400, 1f)

        assertTrue(layout.ready)
        assertFalse(layout.expanded)
        assertTrue(layout.capturesTouch(100f, 600f))
    }

    @Test
    fun `폭이나 높이가 0 이하인 영역과 깨진 값은 버린다`() {
        assertNull(FloatingLayout.parse("collapsed", "10,10,10,20", 400f, 400, 1f).area)
        assertNull(FloatingLayout.parse("collapsed", "10,20,30", 400f, 400, 1f).area)
        assertNull(FloatingLayout.parse("collapsed", "a,b,c,d", 400f, 400, 1f).area)
    }
}
