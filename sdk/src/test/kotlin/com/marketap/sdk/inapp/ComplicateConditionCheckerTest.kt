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
import org.junit.Test

class ComplicateConditionCheckerTest {

    private val conditionChecker = ConditionCheckerImpl(
        PropertyConditionCheckerImpl(
            ValueComparatorImpl()
        )
    )

    private val eventProperty = mapOf(
        "mkt_currency" to "USD",
        "mkt_order_count" to 5L,
        "mkt_total_price" to 129.99,
        "mkt_is_first_order" to true,
        "mkt_created_date" to "2024-03-01",
        "mkt_tags" to listOf("electronics", "sale", "new"),

        "mkt_items" to listOf(
            mapOf(
                "mkt_item_price" to 49L,
                "mkt_item_discount" to 5.0,
                "mkt_item_category" to "electronics"
            ),
            mapOf(
                "mkt_item_price" to 79L,
                "mkt_item_discount" to 15.0,
                "mkt_item_category" to "furniture"
            ),
            mapOf(
                "mkt_item_price" to 99L,
                "mkt_item_discount" to 20.0,
                "mkt_item_category" to "home"
            )
        )
    )

    @Test
    fun `복잡한 AND 조건 - 모든 조건 만족`() {
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
                    ),
                    EventPropertyCondition(
                        extractionStrategy = ExtractionStrategy(
                            PropertySchema(
                                "mkt_order_count",
                                "mkt_order_count",
                                DataType.INT
                            )
                        ),
                        operator = TaxonomyOperator.GREATER_THAN,
                        targetValues = listOf(3L)
                    ),
                    EventPropertyCondition(
                        extractionStrategy = ExtractionStrategy(
                            PropertySchema(
                                "mkt_total_price",
                                "mkt_total_price",
                                DataType.DOUBLE
                            )
                        ),
                        operator = TaxonomyOperator.BETWEEN,
                        targetValues = listOf(100.0, 200.0)
                    )
                )
            )
        )

        val result = conditionChecker.checkCondition(condition, "purchase", eventProperty)

        assertTrue(result)
    }

    @Test
    fun `복잡한 OR 조건 - 하나만 만족`() {
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
                        targetValues = listOf("EUR")
                    ),
                    EventPropertyCondition(
                        extractionStrategy = ExtractionStrategy(
                            PropertySchema(
                                "mkt_order_count",
                                "mkt_order_count",
                                DataType.INT
                            )
                        ),
                        operator = TaxonomyOperator.GREATER_THAN,
                        targetValues = listOf(10L)
                    )
                ),
                listOf(
                    EventPropertyCondition(
                        extractionStrategy = ExtractionStrategy(
                            PropertySchema(
                                "mkt_total_price",
                                "mkt_total_price",
                                DataType.DOUBLE
                            )
                        ),
                        operator = TaxonomyOperator.GREATER_THAN,
                        targetValues = listOf(100.0)
                    )
                )
            )
        )

        val result = conditionChecker.checkCondition(condition, "purchase", eventProperty)

        assertTrue(result)
    }

    @Test
    fun `복잡한 DATE 조건 - 특정 연도, 월 검증`() {
        val condition = Condition(
            eventFilter = EventFilter(eventName = "purchase"),
            propertyConditions = listOf(
                listOf(
                    EventPropertyCondition(
                        extractionStrategy = ExtractionStrategy(
                            PropertySchema(
                                "mkt_created_date",
                                "mkt_created_date",
                                DataType.DATE
                            )
                        ),
                        operator = TaxonomyOperator.YEAR_EQUAL,
                        targetValues = listOf(2024)
                    ),
                    EventPropertyCondition(
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
                )
            )
        )

        val result = conditionChecker.checkCondition(condition, "purchase", eventProperty)

        assertTrue(result)
    }

    @Test
    fun `복잡한 ITEM 조건 - 상품 목록 필터링`() {
        val condition = Condition(
            eventFilter = EventFilter(eventName = "purchase"),
            propertyConditions = listOf(
                listOf(
                    EventPropertyCondition(
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
                    ),
                    EventPropertyCondition(
                        extractionStrategy = ExtractionStrategy(
                            PropertySchema(
                                "mkt_item_discount",
                                "mkt_item_discount",
                                DataType.DOUBLE,
                                Path.ITEM
                            )
                        ),
                        operator = TaxonomyOperator.LESS_THAN,
                        targetValues = listOf(20.0)
                    )
                )
            )
        )

        val result = conditionChecker.checkCondition(condition, "purchase", eventProperty)

        assertTrue(result)
    }

    @Test
    fun `초복잡한 AND 조건 - 모든 조건 만족`() {
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
                    ),
                    EventPropertyCondition(
                        extractionStrategy = ExtractionStrategy(
                            PropertySchema(
                                "mkt_order_count",
                                "mkt_order_count",
                                DataType.INT
                            )
                        ),
                        operator = TaxonomyOperator.GREATER_THAN,
                        targetValues = listOf(3L)
                    ),
                    EventPropertyCondition(
                        extractionStrategy = ExtractionStrategy(
                            PropertySchema(
                                "mkt_total_price",
                                "mkt_total_price",
                                DataType.DOUBLE
                            )
                        ),
                        operator = TaxonomyOperator.BETWEEN,
                        targetValues = listOf(100.0, 200.0)
                    )
                )
            )
        )

        val result = conditionChecker.checkCondition(condition, "purchase", eventProperty)

        assertTrue(result)
    }

    @Test
    fun `초복잡한 OR 조건 - 여러 그룹 중 하나 만족`() {
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
                        targetValues = listOf("EUR")
                    ),
                    EventPropertyCondition(
                        extractionStrategy = ExtractionStrategy(
                            PropertySchema(
                                "mkt_order_count",
                                "mkt_order_count",
                                DataType.INT
                            )
                        ),
                        operator = TaxonomyOperator.GREATER_THAN,
                        targetValues = listOf(10L)
                    )
                ),
                listOf(
                    EventPropertyCondition(
                        extractionStrategy = ExtractionStrategy(
                            PropertySchema(
                                "mkt_total_price",
                                "mkt_total_price",
                                DataType.DOUBLE
                            )
                        ),
                        operator = TaxonomyOperator.GREATER_THAN,
                        targetValues = listOf(100.0)
                    )
                )
            )
        )

        val result = conditionChecker.checkCondition(condition, "purchase", eventProperty)

        assertTrue(result)
    }

    @Test
    fun `ITEM 속성 - 특정 상품의 가격이 특정 범위 내에 있는 경우`() {
        val condition = Condition(
            eventFilter = EventFilter(eventName = "purchase"),
            propertyConditions = listOf(
                listOf(
                    EventPropertyCondition(
                        extractionStrategy = ExtractionStrategy(
                            PropertySchema(
                                "mkt_item_price",
                                "mkt_item_price",
                                DataType.INT,
                                Path.ITEM
                            )
                        ),
                        operator = TaxonomyOperator.BETWEEN,
                        targetValues = listOf(70L, 90L)
                    )
                )
            )
        )

        val result = conditionChecker.checkCondition(condition, "purchase", eventProperty)

        assertTrue(result)
    }

    @Test
    fun `복잡한 연도 및 월 비교 - 특정 날짜 필터링`() {
        val condition = Condition(
            eventFilter = EventFilter(eventName = "purchase"),
            propertyConditions = listOf(
                listOf(
                    EventPropertyCondition(
                        extractionStrategy = ExtractionStrategy(
                            PropertySchema(
                                "mkt_created_date",
                                "mkt_created_date",
                                DataType.DATE
                            )
                        ),
                        operator = TaxonomyOperator.YEAR_EQUAL,
                        targetValues = listOf(2024)
                    ),
                    EventPropertyCondition(
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
                )
            )
        )

        val result = conditionChecker.checkCondition(condition, "purchase", eventProperty)

        assertTrue(result)
    }

    @Test
    fun `STRING_ARRAY - 특정 태그가 포함되는 경우`() {
        val condition = Condition(
            eventFilter = EventFilter(eventName = "purchase"),
            propertyConditions = listOf(
                listOf(
                    EventPropertyCondition(
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
                )
            )
        )

        val result = conditionChecker.checkCondition(condition, "purchase", eventProperty)

        assertTrue(result)
    }
}