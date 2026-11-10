package com.marketap.sdk.inapp.floating

import com.marketap.sdk.client.inapp.floating.FloatingGhostLedger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingGhostLedgerTest {

    private class Screen(val name: String)

    private val removed = mutableListOf<String>()
    private val ledger = FloatingGhostLedger<Screen, String> { removed += it }
    private val a = Screen("A")
    private val b = Screen("B")
    private val c = Screen("C")

    @Test
    fun `떠나는 화면 스냅샷은 정지돼도 남고 그 화면이 다시 앞에 오면 지운다`() {
        ledger.putLeaving(a, "ghostA")

        ledger.onScreenFront(b)
        assertTrue(removed.isEmpty())

        ledger.onScreenFront(a)
        assertEquals(listOf("ghostA"), removed)
        assertNull(ledger.leavingScreen)
    }

    @Test
    fun `떠나는 화면 스냅샷은 가장 최근 1장만 남긴다`() {
        ledger.putLeaving(a, "ghostA")
        ledger.putLeaving(b, "ghostB")

        assertEquals(listOf("ghostA"), removed)
        assertEquals(b, ledger.leavingScreen)

        ledger.onScreenFront(a)
        assertEquals(listOf("ghostA"), removed)
    }

    @Test
    fun `들어오는 화면 스냅샷은 그 화면에서 처음 그려지면 지운다`() {
        ledger.putArriving(b, "arriveB")

        ledger.onFirstDraw(a)
        assertTrue(removed.isEmpty())

        ledger.onFirstDraw(b)
        assertEquals(listOf("arriveB"), removed)
        assertNull(ledger.arrivingScreen)

        ledger.onFirstDraw(b)
        assertEquals(listOf("arriveB"), removed)
    }

    @Test
    fun `들어오는 화면이 다시 바뀌면 이전 들어오는 스냅샷을 지운다`() {
        ledger.putArriving(b, "arriveB")
        ledger.putArriving(c, "arriveC")

        assertEquals(listOf("arriveB"), removed)
        assertEquals(c, ledger.arrivingScreen)
    }

    @Test
    fun `화면이 파괴되면 그 화면의 스냅샷을 모두 지운다`() {
        ledger.putLeaving(a, "ghostA")
        ledger.putArriving(a, "arriveA")

        ledger.onScreenGone(b)
        assertTrue(removed.isEmpty())

        ledger.onScreenGone(a)
        assertEquals(listOf("ghostA", "arriveA"), removed)
        assertNull(ledger.leavingScreen)
        assertNull(ledger.arrivingScreen)
    }

    @Test
    fun `뒤로가기 B에서 A - A 스냅샷을 지운 뒤 B 스냅샷을 떠나는 쪽에 두고 A에는 들어오는 스냅샷을 둔다`() {
        ledger.putLeaving(a, "ghostA")

        ledger.onScreenFront(a)
        ledger.putLeaving(b, "ghostB")
        ledger.putArriving(a, "arriveA")

        assertEquals(listOf("ghostA"), removed)
        assertEquals(b, ledger.leavingScreen)
        assertEquals(a, ledger.arrivingScreen)
    }

    @Test
    fun `정리하면 남은 스냅샷을 모두 지운다`() {
        ledger.putLeaving(a, "ghostA")
        ledger.putArriving(b, "arriveB")

        ledger.clear()

        assertEquals(setOf("ghostA", "arriveB"), removed.toSet())
        assertNull(ledger.leavingScreen)
        assertNull(ledger.arrivingScreen)
    }
}
