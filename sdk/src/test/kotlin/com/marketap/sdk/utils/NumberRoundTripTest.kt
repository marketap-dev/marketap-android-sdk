package com.marketap.sdk.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NumberRoundTripTest {
    private val adapter = pairAdapter<String, Map<String, Any>>()

    private fun roundTrip(properties: Map<String, Any>): Pair<Map<String, Any>, String> {
        val stored = PairEntry("project", properties).serialize(adapter)
        val restored = stored.deserialize(adapter)
        return restored.second to restored.serialize(adapter)
    }

    @Test
    fun `정수는 저장 후 다시 읽어도 정수로 전송된다`() {
        val (restored, resent) = roundTrip(mapOf("qty" to 3, "order_no" to 1234567890123456789L))

        assertEquals(3L, restored["qty"])
        assertEquals(1234567890123456789L, restored["order_no"])
        assertTrue(resent.contains("\"qty\":3,") || resent.contains("\"qty\":3}"))
        assertTrue(resent.contains("\"order_no\":1234567890123456789"))
    }

    @Test
    fun `실수는 실수로 유지된다`() {
        val (restored, resent) = roundTrip(mapOf("price" to 12900.0, "rate" to 0.15, "big" to 1.0E20))

        assertEquals(12900.0, restored["price"])
        assertEquals(0.15, restored["rate"])
        assertEquals(1.0E20, restored["big"])
        assertTrue(resent.contains("\"price\":12900.0"))
    }

    @Test
    fun `중첩된 목록과 객체 안의 숫자도 유지된다`() {
        val (restored, _) = roundTrip(
            mapOf(
                "mkt_items" to listOf(mapOf("mkt_quantity" to 2, "mkt_product_price" to 9900.5)),
                "flag" to true,
                "name" to "x"
            )
        )

        val item = (restored["mkt_items"] as List<*>)[0] as Map<*, *>
        assertEquals(2L, item["mkt_quantity"])
        assertEquals(9900.5, item["mkt_product_price"])
        assertEquals(true, restored["flag"])
        assertEquals("x", restored["name"])
    }

    @Test
    fun `Long 범위를 넘는 정수는 실수로 읽는다`() {
        val restored = "{\"n\":123456789012345678901234567890}".deserialize(mapAdapter<String, Any>())

        assertEquals(1.2345678901234568E29, restored["n"])
    }
}
