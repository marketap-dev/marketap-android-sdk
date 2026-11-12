package com.marketap.sdk.inapp.popup

import com.marketap.sdk.client.inapp.InAppSlots
import com.marketap.sdk.client.inapp.popup.PopupHolder
import com.marketap.sdk.model.internal.inapp.InAppDisplaySlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PopupHolderTest {

    private class Popup {
        var closed = false
    }

    private val main = ArrayDeque<() -> Unit>()
    private val slots = InAppSlots<Popup>()
    private val holder = PopupHolder<Popup>()

    private fun showPopup(popup: Popup) {
        slots.claim(InAppDisplaySlot.POPUP, popup)
        holder.publishThenShow(popup) { shown -> main.addLast { shown.closed } }
    }

    private fun closeCurrent() {
        val popup = holder.current ?: return
        main.addLast {
            if (popup.closed) return@addLast
            popup.closed = true
            slots.release(InAppDisplaySlot.POPUP, popup)
            holder.clear(popup)
        }
    }

    private fun drain() {
        while (main.isNotEmpty()) main.removeFirst()()
    }

    @Test
    fun `표시가 메인 스레드에서 시작되기 전에 온 닫기도 대상을 찾아 닫고 자리를 반납한다`() {
        val popup = Popup()
        showPopup(popup)
        closeCurrent()
        drain()

        assertTrue(popup.closed)
        assertNull(holder.current)
        assertFalse(slots.isOccupied(InAppDisplaySlot.POPUP))
    }

    @Test
    fun `이전 팝업의 늦은 정리는 다음 팝업 기록을 지우지 않는다`() {
        val first = Popup()
        showPopup(first)
        drain()
        closeCurrent()
        drain()
        val second = Popup()
        showPopup(second)

        holder.clear(first)

        assertSame(second, holder.current)
        assertEquals(second, slots.current(InAppDisplaySlot.POPUP))
    }
}
