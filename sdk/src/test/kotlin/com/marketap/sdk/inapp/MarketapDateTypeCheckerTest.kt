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

class MarketapDateTypeCheckerTest {

    private val propertyConditionChecker = PropertyConditionCheckerImpl(ValueComparatorImpl())

    private val eventProperty = mapOf(
        "mkt_user_id" to 123456789L,
        "mkt_order_count" to 5L,

        "mkt_created_date" to "2024-03-01",
        "mkt_last_active_date" to "2024-02-15",

        "mkt_items" to listOf(
            mapOf(
                "mkt_item_price" to 49L,
                "mkt_item_count" to 2L,
                "mkt_expiry_date" to "2024-06-01"
            ),
            mapOf(
                "mkt_item_price" to 79L,
                "mkt_item_count" to 1L,
                "mkt_expiry_date" to "2024-07-15"
            ),
            mapOf(
                "mkt_item_price" to 99L,
                "mkt_item_count" to 5L,
                "mkt_expiry_date" to "2024-08-30"
            )
        )
    )

    @Test
    fun `LONG - EQUAL 연산 성공`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_user_id",
                    "mkt_user_id",
                    DataType.INT
                )
            ),
            operator = TaxonomyOperator.EQUAL,
            targetValues = listOf(123456789L)
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertTrue(result)
    }

    @Test
    fun `LONG - GREATER_THAN 연산 성공`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_order_count",
                    "mkt_order_count",
                    DataType.INT
                )
            ),
            operator = TaxonomyOperator.GREATER_THAN,
            targetValues = listOf(2L)
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertTrue(result)
    }

    @Test
    fun `LONG - BETWEEN 연산 성공`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_order_count",
                    "mkt_order_count",
                    DataType.INT
                )
            ),
            operator = TaxonomyOperator.BETWEEN,
            targetValues = listOf(3L, 10L)
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertTrue(result)
    }

    @Test
    fun `MarketapDate - EQUAL 연산 성공`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_created_date",
                    "mkt_created_date",
                    DataType.DATE
                )
            ),
            operator = TaxonomyOperator.EQUAL,
            targetValues = listOf("2024-03-01")
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertTrue(result)
    }

    @Test
    fun `MarketapDate - MONTH_EQUAL 연산 성공`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_created_date",
                    "mkt_created_date",
                    DataType.DATE
                )
            ),
            operator = TaxonomyOperator.MONTH_EQUAL,
            targetValues = listOf(3)
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertTrue(result)
    }

    @Test
    fun `MarketapDate - YEAR_MONTH_EQUAL 연산 성공`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_created_date",
                    "mkt_created_date",
                    DataType.DATE
                )
            ),
            operator = TaxonomyOperator.YEAR_MONTH_EQUAL,
            targetValues = listOf(2024, 3)
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertTrue(result)
    }

    @Test
    fun `ITEM 속성 - LONG GREATER_THAN 연산 성공`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_item_price",
                    "mkt_item_price",
                    DataType.INT,
                    Path.ITEM
                )
            ),
            operator = TaxonomyOperator.GREATER_THAN,
            targetValues = listOf(50L)
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertTrue(result) // "mkt_items" 내부에서 50 이상인 가격이 하나라도 있으면 true
    }

    @Test
    fun `ITEM 속성 - MarketapDate YEAR_EQUAL 연산 성공`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_expiry_date",
                    "mkt_expiry_date",
                    DataType.DATE,
                    Path.ITEM
                )
            ),
            operator = TaxonomyOperator.YEAR_EQUAL,
            targetValues = listOf(2024)
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertTrue(result) // "mkt_items" 내부에서 2024년인 날짜가 하나라도 있으면 true
    }

    @Test
    fun `LONG - GREATER_THAN_OR_EQUAL 연산 성공`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_order_count",
                    "mkt_order_count",
                    DataType.INT
                )
            ),
            operator = TaxonomyOperator.GREATER_THAN_OR_EQUAL,
            targetValues = listOf(5L)
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertTrue(result)
    }

    @Test
    fun `MarketapDate - YEAR_EQUAL 연산 성공`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_created_date",
                    "mkt_created_date",
                    DataType.DATE
                )
            ),
            operator = TaxonomyOperator.YEAR_EQUAL,
            targetValues = listOf(2024)
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertTrue(result)
    }
}