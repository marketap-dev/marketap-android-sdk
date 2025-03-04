package com.marketap.sdk.inapp

import com.marketap.sdk.domain.service.inapp.condition.ConditionCheckerImpl
import com.marketap.sdk.domain.service.inapp.condition.PropertyConditionCheckerImpl
import com.marketap.sdk.domain.service.inapp.condition.comparator.ValueComparatorImpl
import com.marketap.sdk.model.internal.inapp.Condition
import com.marketap.sdk.model.internal.inapp.DataType
import com.marketap.sdk.model.internal.inapp.EventFilter
import com.marketap.sdk.model.internal.inapp.EventPropertyCondition
import com.marketap.sdk.model.internal.inapp.ExtractionStrategy
import com.marketap.sdk.model.internal.inapp.Path
import com.marketap.sdk.model.internal.inapp.PropertySchema
import com.marketap.sdk.model.internal.inapp.TaxonomyOperator
import junit.framework.TestCase.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class ConditionCheckerTest {

    private val conditionChecker = ConditionCheckerImpl(
        PropertyConditionCheckerImpl(
            ValueComparatorImpl()
        )
    )

    @Test
    fun `이벤트 이름만 체크 - 일치할 경우 true`() {
        val condition = Condition(eventFilter = EventFilter(eventName = "purchase"))

        val result = conditionChecker.checkCondition(condition, "purchase", null)

        assertTrue(result)
    }

    @Test
    fun `이벤트 이름만 체크 - 일치하지 않을 경우 false`() {
        val condition = Condition(eventFilter = EventFilter(eventName = "purchase"))

        val result = conditionChecker.checkCondition(condition, "view", null)

        assertFalse(result)
    }

    @Test
    fun `EVENT 속성 - EQUAL 비교 성공`() {
        val condition = Condition(
            eventFilter = EventFilter(eventName = "purchase"),
            propertyConditions = listOf(
                listOf(
                    EventPropertyCondition(
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
                )
            )
        )

        val eventProperty = mapOf("mkt_currency" to "USD")

        val result = conditionChecker.checkCondition(condition, "purchase", eventProperty)

        assertTrue(result)
    }

    @Test
    fun `EVENT 속성 - EQUAL 비교 실패`() {
        val condition = Condition(
            eventFilter = EventFilter(eventName = "purchase"),
            propertyConditions = listOf(
                listOf(
                    EventPropertyCondition(
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
                )
            )
        )

        val eventProperty = mapOf("mkt_currency" to "KRW")

        val result = conditionChecker.checkCondition(condition, "purchase", eventProperty)

        assertFalse(result)
    }

    @Test
    fun `ITEM 속성 - EQUAL 비교 (items 내부 하나라도 만족)`() {
        val condition = Condition(
            eventFilter = EventFilter(eventName = "purchase"),
            propertyConditions = listOf(
                listOf(
                    EventPropertyCondition(
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
                )
            )
        )

        val eventProperty = mapOf(
            "mkt_items" to listOf(
                mapOf("mkt_brand" to "Adidas"),
                mapOf("mkt_brand" to "Nike") // 하나라도 일치하면 true
            )
        )

        val result = conditionChecker.checkCondition(condition, "purchase", eventProperty)

        assertTrue(result)
    }

    @Test
    fun `ITEM 속성 - EQUAL 비교 실패 (모두 불일치)`() {
        val condition = Condition(
            eventFilter = EventFilter(eventName = "purchase"),
            propertyConditions = listOf(
                listOf(
                    EventPropertyCondition(
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
                )
            )
        )

        val eventProperty = mapOf(
            "mkt_items" to listOf(
                mapOf("mkt_brand" to "Adidas"),
                mapOf("mkt_brand" to "Puma")
            )
        )

        val result = conditionChecker.checkCondition(condition, "purchase", eventProperty)

        assertFalse(result)
    }

    @Test
    fun `ITEM 속성 - BETWEEN 비교 성공 (mkt_option_price)`() {
        val condition = Condition(
            eventFilter = EventFilter(eventName = "purchase"),
            propertyConditions = listOf(
                listOf(
                    EventPropertyCondition(
                        extractionStrategy = ExtractionStrategy(
                            PropertySchema(
                                "mkt_option_price",
                                "mkt_option_price",
                                DataType.DOUBLE,
                                Path.ITEM
                            )
                        ),
                        operator = TaxonomyOperator.BETWEEN,
                        targetValues = listOf(10.0, 50.0)
                    )
                )
            )
        )

        val eventProperty = mapOf(
            "mkt_items" to listOf(
                mapOf("mkt_option_price" to 5.0),
                mapOf("mkt_option_price" to 30.0) // 하나라도 범위 안에 들어가면 true
            )
        )

        val result = conditionChecker.checkCondition(condition, "purchase", eventProperty)

        assertTrue(result)
    }

    @Test
    fun `ITEM 속성 - BETWEEN 비교 실패 (모두 범위 밖)`() {
        val condition = Condition(
            eventFilter = EventFilter(eventName = "purchase"),
            propertyConditions = listOf(
                listOf(
                    EventPropertyCondition(
                        extractionStrategy = ExtractionStrategy(
                            PropertySchema(
                                "mkt_option_price",
                                "mkt_option_price",
                                DataType.DOUBLE,
                                Path.ITEM
                            )
                        ),
                        operator = TaxonomyOperator.BETWEEN,
                        targetValues = listOf(10.0, 50.0)
                    )
                )
            )
        )

        val eventProperty = mapOf(
            "mkt_items" to listOf(
                mapOf("mkt_option_price" to 5.0),
                mapOf("mkt_option_price" to 55.0) // 모두 범위 밖
            )
        )

        val result = conditionChecker.checkCondition(condition, "purchase", eventProperty)

        assertFalse(result)
    }

    @Test
    fun `EVENT 속성 - IS_NULL 비교 성공`() {
        val condition = Condition(
            eventFilter = EventFilter(eventName = "purchase"),
            propertyConditions = listOf(
                listOf(
                    EventPropertyCondition(
                        extractionStrategy = ExtractionStrategy(
                            PropertySchema(
                                "mkt_utm_term",
                                "mkt_utm_term",
                                DataType.STRING
                            )
                        ),
                        operator = TaxonomyOperator.IS_NULL,
                        targetValues = emptyList()
                    )
                )
            )
        )

        val eventProperty = mapOf("mkt_currency" to "USD") // mkt_utm_term 없음

        val result = conditionChecker.checkCondition(condition, "purchase", eventProperty)

        assertTrue(result)
    }

    @Test
    fun `EVENT 속성 - IS_NOT_NULL 비교 성공`() {
        val condition = Condition(
            eventFilter = EventFilter(eventName = "purchase"),
            propertyConditions = listOf(
                listOf(
                    EventPropertyCondition(
                        extractionStrategy = ExtractionStrategy(
                            PropertySchema(
                                "mkt_utm_term",
                                "mkt_utm_term",
                                DataType.STRING
                            )
                        ),
                        operator = TaxonomyOperator.IS_NOT_NULL,
                        targetValues = emptyList()
                    )
                )
            )
        )

        val eventProperty = mapOf("mkt_utm_term" to "discount")

        val result = conditionChecker.checkCondition(condition, "purchase", eventProperty)

        assertTrue(result)
    }
}