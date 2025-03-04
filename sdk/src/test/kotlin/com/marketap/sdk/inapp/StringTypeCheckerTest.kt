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

class StringTypeCheckerTest {
    val eventProperty = mapOf(
        // 일반 속성 (EVENT)
        "mkt_page_url" to "https://example.com/product/123",
        "mkt_page_referrer" to "https://google.com",
        "mkt_currency" to "USD",
        "mkt_utm_source" to "facebook",
        "mkt_utm_campaign" to "summer_sale",
        "mkt_utm_term" to "discount_code",
        "mkt_page_title" to "Awesome Product - Best Price",
        "mkt_category1" to "Electronics",
        "mkt_category2" to "Smartphones",
        "mkt_brand" to "Apple",

        // 배열 속성 (ITEMS)
        "mkt_items" to listOf(
            mapOf("mkt_category1" to "Electronics", "mkt_brand" to "Samsung"),
            mapOf("mkt_category1" to "Fashion", "mkt_brand" to "Nike"),
            mapOf("mkt_category1" to "Home", "mkt_brand" to "Dyson")
        )
    )
    private val propertyConditionChecker = PropertyConditionCheckerImpl(ValueComparatorImpl())

    @Test
    fun `EVENT 속성 - EQUAL 비교 성공`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_currency",
                    "mkt_currency",
                    DataType.STRING
                )
            ),
            operator = TaxonomyOperator.EQUAL,
            targetValues = listOf("USD")
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertTrue(result)
    }

    @Test
    fun `EVENT 속성 - EQUAL 비교 실패`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_currency",
                    "mkt_currency",
                    DataType.STRING
                )
            ),
            operator = TaxonomyOperator.EQUAL,
            targetValues = listOf("KRW")
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertFalse(result)
    }

    @Test
    fun `EVENT 속성 - LIKE 비교 성공`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_page_url",
                    "mkt_page_url",
                    DataType.STRING
                )
            ),
            operator = TaxonomyOperator.LIKE,
            targetValues = listOf("example.com")
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertTrue(result)
    }

    @Test
    fun `ITEM 속성 - EQUAL 비교 성공 (mkt_items 내부에서 일치하는 값 존재)`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_brand",
                    "mkt_brand",
                    DataType.STRING,
                    Path.ITEM
                )
            ),
            operator = TaxonomyOperator.EQUAL,
            targetValues = listOf("Nike")
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertTrue(result) // mkt_items 내부에서 "Nike"가 있기 때문에 true
    }

    @Test
    fun `ITEM 속성 - EQUAL 비교 실패 (mkt_items 내부에서 모두 불일치)`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_brand",
                    "mkt_brand",
                    DataType.STRING,
                    Path.ITEM
                )
            ),
            operator = TaxonomyOperator.EQUAL,
            targetValues = listOf("Gucci")
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertFalse(result) // mkt_items 내부에 "Gucci" 없음
    }

    @Test
    fun `ITEM 속성 - IN 비교 성공 (mkt_items 내부에서 하나라도 포함됨)`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_category1",
                    "mkt_category1",
                    DataType.STRING,
                    Path.ITEM
                )
            ),
            operator = TaxonomyOperator.IN,
            targetValues = listOf("Fashion", "Electronics")
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertTrue(result) // mkt_items 내부에서 "Fashion"과 "Electronics" 중 하나라도 있으므로 true
    }

    @Test
    fun `ITEM 속성 - IN 비교 실패 (mkt_items 내부에서 모두 불일치)`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_category1",
                    "mkt_category1",
                    DataType.STRING,
                    Path.ITEM
                )
            ),
            operator = TaxonomyOperator.IN,
            targetValues = listOf("Jewelry", "Watches")
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertFalse(result) // mkt_items 내부에 해당하는 값이 없음
    }

    @Test
    fun `CONTAINS 연산 - 문자열 포함 여부 확인`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_page_url",
                    "mkt_page_url",
                    DataType.STRING
                )
            ),
            operator = TaxonomyOperator.CONTAINS,
            targetValues = listOf("product")
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertTrue(result)
    }

    @Test
    fun `NOT_CONTAINS 연산 - 문자열 미포함 여부 확인`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_page_url",
                    "mkt_page_url",
                    DataType.STRING
                )
            ),
            operator = TaxonomyOperator.NOT_CONTAINS,
            targetValues = listOf("checkout")
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertTrue(result)
    }

    @Test
    fun `IS_NULL 연산 - 값이 존재하지 않는 경우`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_discount",
                    "mkt_discount",
                    DataType.STRING
                )
            ),
            operator = TaxonomyOperator.IS_NULL,
            targetValues = emptyList()
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertTrue(result)
    }

    @Test
    fun `IS_NOT_NULL 연산 - 값이 존재하는 경우`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_currency",
                    "mkt_currency",
                    DataType.STRING
                )
            ),
            operator = TaxonomyOperator.IS_NOT_NULL,
            targetValues = emptyList()
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertTrue(result)
    }

    @Test
    fun `NOT_IN 연산 - 리스트에 포함되지 않는 경우`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_utm_source",
                    "mkt_utm_source",
                    DataType.STRING
                )
            ),
            operator = TaxonomyOperator.NOT_IN,
            targetValues = listOf("twitter", "linkedin")
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertTrue(result)
    }

    @Test
    fun `NOT_EQUAL 연산 - 값이 다른 경우`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_currency",
                    "mkt_currency",
                    DataType.STRING
                )
            ),
            operator = TaxonomyOperator.NOT_EQUAL,
            targetValues = listOf("EUR")
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertTrue(result)
    }

    @Test
    fun `NOT_LIKE 연산 - 특정 패턴과 불일치하는 경우`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_page_title",
                    "mkt_page_title",
                    DataType.STRING
                )
            ),
            operator = TaxonomyOperator.NOT_LIKE,
            targetValues = listOf("%Worst Price%")
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertTrue(result)
    }
}