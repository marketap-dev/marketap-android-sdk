package com.marketap.sdk.inapp

import com.marketap.sdk.domain.service.inapp.condition.PropertyConditionCheckerImpl
import com.marketap.sdk.domain.service.inapp.condition.comparator.ValueComparatorImpl
import com.marketap.sdk.model.internal.inapp.DataType
import com.marketap.sdk.model.internal.inapp.EventPropertyCondition
import com.marketap.sdk.model.internal.inapp.ExtractionStrategy
import com.marketap.sdk.model.internal.inapp.Path
import com.marketap.sdk.model.internal.inapp.PropertySchema
import com.marketap.sdk.model.internal.inapp.TaxonomyOperator
import junit.framework.TestCase.assertTrue
import org.junit.Test

class StringArrayCheckerTest {

    private val propertyConditionChecker = PropertyConditionCheckerImpl(ValueComparatorImpl())

    private val eventProperty = mapOf(
        "mkt_tags" to listOf("electronics", "sale", "new"),
        "mkt_categories" to listOf("home_appliances", "furniture"),

        "mkt_items" to listOf(
            mapOf("mkt_item_tags" to listOf("luxury", "fashion")),
            mapOf("mkt_item_tags" to listOf("gadget", "electronics")),
            mapOf("mkt_item_tags" to listOf("fitness", "sports"))
        )
    )

    @Test
    fun `STRING_ARRAY - CONTAINS 연산 성공`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_tags",
                    "mkt_tags",
                    DataType.STRING_ARRAY
                )
            ),
            operator = TaxonomyOperator.CONTAINS,
            targetValues = listOf("sale")
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertTrue(result) // "sale"이 mkt_tags 리스트에 포함되어 있음
    }

    @Test
    fun `STRING_ARRAY - NOT_CONTAINS 연산 성공`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_tags",
                    "mkt_tags",
                    DataType.STRING_ARRAY
                )
            ),
            operator = TaxonomyOperator.NOT_CONTAINS,
            targetValues = listOf("discount")
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertTrue(result) // "discount"은 mkt_tags 리스트에 없음
    }

    @Test
    fun `STRING_ARRAY - ANY 연산 성공`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_categories",
                    "mkt_categories",
                    DataType.STRING_ARRAY
                )
            ),
            operator = TaxonomyOperator.ANY,
            targetValues = listOf("furniture", "toys")
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertTrue(result) // "furniture"가 mkt_categories 리스트에 포함되어 있음
    }

    @Test
    fun `STRING_ARRAY - NONE 연산 성공`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_categories",
                    "mkt_categories",
                    DataType.STRING_ARRAY
                )
            ),
            operator = TaxonomyOperator.NONE,
            targetValues = listOf("toys", "gadgets")
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertTrue(result) // "toys"와 "gadgets" 모두 mkt_categories 리스트에 없음
    }

    @Test
    fun `ITEM 속성 - STRING_ARRAY CONTAINS 연산 성공`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_item_tags",
                    "mkt_item_tags",
                    DataType.STRING_ARRAY,
                    Path.ITEM
                )
            ),
            operator = TaxonomyOperator.CONTAINS,
            targetValues = listOf("electronics")
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertTrue(result) // "electronics"가 mkt_items 내부 리스트에 포함되어 있음
    }

    @Test
    fun `ITEM 속성 - STRING_ARRAY NOT_CONTAINS 연산 성공`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_item_tags",
                    "mkt_item_tags",
                    DataType.STRING_ARRAY,
                    Path.ITEM
                )
            ),
            operator = TaxonomyOperator.NOT_CONTAINS,
            targetValues = listOf("automotive")
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertTrue(result) // "automotive"는 mkt_items 내부 어디에도 없음
    }

    @Test
    fun `ITEM 속성 - STRING_ARRAY ANY 연산 성공`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_item_tags",
                    "mkt_item_tags",
                    DataType.STRING_ARRAY,
                    Path.ITEM
                )
            ),
            operator = TaxonomyOperator.ANY,
            targetValues = listOf("fashion", "gadgets")
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertTrue(result) // "fashion"과 "gadgets" 중 하나라도 포함되어 있음
    }

    @Test
    fun `ITEM 속성 - STRING_ARRAY NONE 연산 성공`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_item_tags",
                    "mkt_item_tags",
                    DataType.STRING_ARRAY,
                    Path.ITEM
                )
            ),
            operator = TaxonomyOperator.NONE,
            targetValues = listOf("automotive", "gaming")
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertTrue(result) // "automotive"와 "gaming"은 어디에도 없음
    }
}