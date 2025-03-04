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
import org.junit.Assert.assertFalse
import org.junit.Test

class BooleanTypeCheckerTest {
    private val propertyConditionChecker = PropertyConditionCheckerImpl(ValueComparatorImpl())

    private val eventProperty = mapOf(
        // 일반 속성 (EVENT)
        "mkt_is_first_order" to true,
        "mkt_has_discount" to false,

        // 배열 속성 (ITEMS)
        "mkt_items" to listOf(
            mapOf("mkt_is_gift" to true, "mkt_is_returnable" to false),
            mapOf("mkt_is_gift" to false, "mkt_is_returnable" to true),
            mapOf("mkt_is_gift" to false, "mkt_is_returnable" to false)
        )
    )

    @Test
    fun `BOOLEAN - EQUAL 연산 성공`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_is_first_order",
                    "mkt_is_first_order",
                    DataType.BOOLEAN
                )
            ),
            operator = TaxonomyOperator.EQUAL,
            targetValues = listOf(true)
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertTrue(result) // "mkt_is_first_order" == true
    }

    @Test
    fun `BOOLEAN - EQUAL 연산 실패`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_is_first_order",
                    "mkt_is_first_order",
                    DataType.BOOLEAN
                )
            ),
            operator = TaxonomyOperator.EQUAL,
            targetValues = listOf(false)
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertFalse(result) // "mkt_is_first_order" != false
    }

    @Test
    fun `BOOLEAN - NOT_EQUAL 연산 성공`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_has_discount",
                    "mkt_has_discount",
                    DataType.BOOLEAN
                )
            ),
            operator = TaxonomyOperator.NOT_EQUAL,
            targetValues = listOf(true)
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertTrue(result) // "mkt_has_discount" != true
    }

    @Test
    fun `BOOLEAN - NOT_EQUAL 연산 실패`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_has_discount",
                    "mkt_has_discount",
                    DataType.BOOLEAN
                )
            ),
            operator = TaxonomyOperator.NOT_EQUAL,
            targetValues = listOf(false)
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertFalse(result) // "mkt_has_discount" == false
    }

    @Test
    fun `BOOLEAN - IS_NULL 연산 성공`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_unknown_flag",
                    "mkt_unknown_flag",
                    DataType.BOOLEAN
                )
            ),
            operator = TaxonomyOperator.IS_NULL,
            targetValues = emptyList()
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertTrue(result) // "mkt_unknown_flag"가 없으므로 true
    }

    @Test
    fun `BOOLEAN - IS_NOT_NULL 연산 성공`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_is_first_order",
                    "mkt_is_first_order",
                    DataType.BOOLEAN
                )
            ),
            operator = TaxonomyOperator.IS_NOT_NULL,
            targetValues = emptyList()
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertTrue(result) // "mkt_is_first_order" 존재하므로 true
    }

    @Test
    fun `ITEM 속성 - BOOLEAN EQUAL 연산 성공 (mkt_items 내부에서 하나라도 true)`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_is_gift",
                    "mkt_is_gift",
                    DataType.BOOLEAN,
                    Path.ITEM
                )
            ),
            operator = TaxonomyOperator.EQUAL,
            targetValues = listOf(true)
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertTrue(result) // mkt_items 내부에 "mkt_is_gift" == true인 아이템 있음
    }

    @Test
    fun `ITEM 속성 - BOOLEAN EQUAL 연산 실패 (mkt_items 내부에서 하나도 조건 만족 안함)`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_is_gift",
                    "mkt_is_gift",
                    DataType.BOOLEAN,
                    Path.ITEM
                )
            ),
            operator = TaxonomyOperator.EQUAL,
            targetValues = listOf(false)
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertTrue(result) // mkt_items 내부에서 "mkt_is_gift" == false인 아이템 있음
    }

    @Test
    fun `ITEM 속성 - BOOLEAN IS_NULL 연산 성공 (mkt_items 내부에서 값이 없는 경우)`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_is_promotion",
                    "mkt_is_promotion",
                    DataType.BOOLEAN,
                    Path.ITEM
                )
            ),
            operator = TaxonomyOperator.IS_NULL,
            targetValues = emptyList()
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertTrue(result) // mkt_items 내부에 "mkt_is_promotion" 속성 없음
    }

    @Test
    fun `ITEM 속성 - BOOLEAN IS_NOT_NULL 연산 성공 (mkt_items 내부에서 값이 존재하는 경우)`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_is_gift",
                    "mkt_is_gift",
                    DataType.BOOLEAN,
                    Path.ITEM
                )
            ),
            operator = TaxonomyOperator.IS_NOT_NULL,
            targetValues = emptyList()
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertTrue(result) // mkt_items 내부에 "mkt_is_gift" 속성 존재
    }
}