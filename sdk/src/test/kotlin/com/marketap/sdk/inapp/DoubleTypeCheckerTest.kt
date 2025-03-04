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

class DoubleTypeCheckerTest {
    private val propertyConditionChecker = PropertyConditionCheckerImpl(ValueComparatorImpl())

    private val eventProperty = mapOf(
        // 일반 속성 (EVENT)
        "mkt_option_price" to 29.99,
        "mkt_discount" to 10.0,

        // 배열 속성 (ITEMS)
        "mkt_items" to listOf(
            mapOf("mkt_item_price" to 49.99, "mkt_item_discount" to 5.0),
            mapOf("mkt_item_price" to 79.99, "mkt_item_discount" to 15.0),
            mapOf("mkt_item_price" to 99.99, "mkt_item_discount" to 20.0)
        )
    )

    @Test
    fun `DOUBLE - EQUAL 연산 성공`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_option_price",
                    "mkt_option_price",
                    DataType.DOUBLE
                )
            ),
            operator = TaxonomyOperator.EQUAL,
            targetValues = listOf(29.99)
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertTrue(result)
    }

    @Test
    fun `DOUBLE - NOT_EQUAL 연산 성공`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_option_price",
                    "mkt_option_price",
                    DataType.DOUBLE
                )
            ),
            operator = TaxonomyOperator.NOT_EQUAL,
            targetValues = listOf(19.99)
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertTrue(result)
    }

    @Test
    fun `DOUBLE - GREATER_THAN 연산 성공`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_option_price",
                    "mkt_option_price",
                    DataType.DOUBLE
                )
            ),
            operator = TaxonomyOperator.GREATER_THAN,
            targetValues = listOf(20.0)
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertTrue(result)
    }

    @Test
    fun `DOUBLE - LESS_THAN 연산 성공`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_discount",
                    "mkt_discount",
                    DataType.DOUBLE
                )
            ),
            operator = TaxonomyOperator.LESS_THAN,
            targetValues = listOf(15.0)
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertTrue(result)
    }

    @Test
    fun `DOUBLE - BETWEEN 연산 성공`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_option_price",
                    "mkt_option_price",
                    DataType.DOUBLE
                )
            ),
            operator = TaxonomyOperator.BETWEEN,
            targetValues = listOf(20.0, 40.0)
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertTrue(result)
    }

    @Test
    fun `DOUBLE - NOT_BETWEEN 연산 성공`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_discount",
                    "mkt_discount",
                    DataType.DOUBLE
                )
            ),
            operator = TaxonomyOperator.NOT_BETWEEN,
            targetValues = listOf(15.0, 20.0)
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertTrue(result)
    }

    @Test
    fun `DOUBLE - IN 연산 성공`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_option_price",
                    "mkt_option_price",
                    DataType.DOUBLE
                )
            ),
            operator = TaxonomyOperator.IN,
            targetValues = listOf(19.99, 29.99, 39.99)
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertTrue(result)
    }

    @Test
    fun `DOUBLE - NOT_IN 연산 성공`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_option_price",
                    "mkt_option_price",
                    DataType.DOUBLE
                )
            ),
            operator = TaxonomyOperator.NOT_IN,
            targetValues = listOf(19.99, 39.99, 49.99)
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertTrue(result)
    }

    @Test
    fun `ITEM 속성 - DOUBLE GREATER_THAN 연산 성공`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_item_price",
                    "mkt_item_price",
                    DataType.DOUBLE,
                    Path.ITEM
                )
            ),
            operator = TaxonomyOperator.GREATER_THAN,
            targetValues = listOf(50.0)
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertTrue(result) // "mkt_items" 내부에서 50.0 이상인 가격이 하나라도 있으면 true
    }

    @Test
    fun `ITEM 속성 - DOUBLE LESS_THAN 연산 성공`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_item_discount",
                    "mkt_item_discount",
                    DataType.DOUBLE,
                    Path.ITEM
                )
            ),
            operator = TaxonomyOperator.LESS_THAN,
            targetValues = listOf(10.0)
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertTrue(result) // "mkt_items" 내부에서 10.0 미만인 할인율이 있으면 true
    }

    @Test
    fun `ITEM 속성 - DOUBLE IS_NOT_NULL 연산 성공`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_item_price",
                    "mkt_item_price",
                    DataType.DOUBLE,
                    Path.ITEM
                )
            ),
            operator = TaxonomyOperator.IS_NOT_NULL,
            targetValues = emptyList()
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertTrue(result) // "mkt_items" 내부에서 "mkt_item_price" 속성이 존재
    }
}