package com.marketap.sdk.inapp.popup

import com.marketap.sdk.client.inapp.popup.AccessibilityLedger
import org.junit.Assert.assertEquals
import org.junit.Test

class AccessibilityLedgerTest {

    private class Node(var value: Int)

    private val ledger = AccessibilityLedger<String, Node>(
        read = { it.value },
        write = { node, value -> node.value = value },
        hidden = HIDDEN,
    )

    @Test
    fun `숨긴 값을 기억했다가 되돌린다`() {
        val auto = Node(AUTO)
        val yes = Node(YES)
        ledger.hide("A", auto)
        ledger.hide("A", yes)
        assertEquals(HIDDEN, auto.value)
        assertEquals(HIDDEN, yes.value)

        ledger.restore("A")

        assertEquals(AUTO, auto.value)
        assertEquals(YES, yes.value)
    }

    @Test
    fun `같은 View 를 두 번 숨겨도 처음 값으로 되돌린다`() {
        val node = Node(YES)
        ledger.hide("A", node)
        ledger.hide("A", node)
        ledger.restore("A")

        assertEquals(YES, node.value)
    }

    @Test
    fun `그사이 앱이 바꾼 값은 되돌리지 않는다`() {
        val node = Node(AUTO)
        ledger.hide("A", node)
        node.value = YES
        ledger.restore("A")

        assertEquals(YES, node.value)
    }

    @Test
    fun `원래 숨겨져 있던 View 는 되돌릴 때도 그대로 둔다`() {
        val node = Node(HIDDEN)
        ledger.hide("A", node)
        ledger.restore("A")

        assertEquals(HIDDEN, node.value)
    }

    @Test
    fun `화면별로 되돌리고 다른 화면 것은 건드리지 않는다`() {
        val onA = Node(AUTO)
        val onB = Node(AUTO)
        ledger.hide("A", onA)
        ledger.hide("B", onB)

        ledger.restore("A")
        assertEquals(AUTO, onA.value)
        assertEquals(HIDDEN, onB.value)

        ledger.restoreAll()
        assertEquals(AUTO, onB.value)
    }

    @Test
    fun `되돌린 뒤 다시 숨기면 그때 값을 기억한다`() {
        val node = Node(AUTO)
        ledger.hide("A", node)
        ledger.restore("A")
        node.value = YES
        ledger.hide("A", node)
        ledger.restore("A")

        assertEquals(YES, node.value)
    }

    private companion object {
        const val AUTO = 0
        const val YES = 1
        const val HIDDEN = 4
    }
}
