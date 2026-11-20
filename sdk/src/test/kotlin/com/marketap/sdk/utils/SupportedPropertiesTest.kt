package com.marketap.sdk.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.util.Date

class SupportedPropertiesTest {
    private enum class Level { HIGH }
    private data class Item(val id: Int, val name: String)

    @Test
    fun `기본 타입과 중첩 값은 보낼 수 있다`() {
        val properties = mapOf(
            "s" to "a", "i" to 1, "l" to 2L, "d" to 1.5, "f" to 1.5f, "b" to true,
            "list" to listOf("x", 1, mapOf("k" to "v", "n" to null)),
            "map" to mapOf("a" to listOf(1, 2)),
            "array" to arrayOf("a", "b"),
            "ints" to intArrayOf(1, 2),
            "enum" to Level.HIGH,
            "char" to 'a',
            "numberKeys" to mapOf(1 to "a"),
            "item" to Item(1, "n"),
        )

        assertNull(properties.findUnsupportedPropertyName())
        properties.serialize(mapAdapter<String, Any>())
    }

    @Test
    fun `보낼 수 없는 값은 속성 이름으로 알려 준다`() {
        assertEquals("at", mapOf("ok" to 1, "at" to Date(0)).findUnsupportedPropertyName())
        assertEquals("amount", mapOf("amount" to BigDecimal("12900")).findUnsupportedPropertyName())
        assertEquals("nan", mapOf("nan" to Double.NaN).findUnsupportedPropertyName())
        assertEquals("inf", mapOf("inf" to Float.POSITIVE_INFINITY).findUnsupportedPropertyName())
        assertEquals("items", mapOf("items" to listOf(mapOf("id" to 1, "at" to Date(0)))).findUnsupportedPropertyName())
        val cycle = HashMap<String, Any>().also { it["self"] = it }
        assertEquals("cycle", mapOf("cycle" to cycle).findUnsupportedPropertyName())
        assertNull((null as Map<String, Any>?).findUnsupportedPropertyName())
    }

    @Test
    fun `유저 속성은 보낼 수 없는 속성만 뺀다`() {
        val dropped = ArrayList<String>()
        val result = mapOf("name" to "kim", "at" to Date(0), "age" to 3)
            .filterSupportedProperties { dropped.add(it) }

        assertEquals(mapOf("name" to "kim", "age" to 3), result)
        assertEquals(listOf("at"), dropped)
    }
}
