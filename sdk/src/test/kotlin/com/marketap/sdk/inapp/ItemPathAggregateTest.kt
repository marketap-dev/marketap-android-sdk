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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemPathAggregateTest {

    private val conditionChecker = ConditionCheckerImpl(
        PropertyConditionCheckerImpl(
            ValueComparatorImpl()
        )
    )

    @Test
    fun `NOT_LIKE with Item Path - 모든 아이템이 조건을 만족해야 true`() {
        val condition = Condition(
            eventFilter = EventFilter(eventName = "mkt_begin_checkout"),
            propertyConditions = listOf(
                listOf(
                    EventPropertyCondition(
                        extractionStrategy = ExtractionStrategy(
                            PropertySchema(
                                "mkt_product_name",
                                "mkt_product_name",
                                DataType.STRING,
                                Path.ITEM
                            )
                        ),
                        operator = TaxonomyOperator.NOT_LIKE,
                        targetValues = listOf("콜라")
                    )
                )
            )
        )

        // Case 1: 모든 아이템이 "콜라"를 포함하지 않음 → true
        val eventProperty1 = mapOf(
            "mkt_items" to listOf(
                mapOf("mkt_product_name" to "사이다"),
                mapOf("mkt_product_name" to "환타")
            )
        )
        assertTrue(
            "All items without '콜라' should return true",
            conditionChecker.checkCondition(condition, "mkt_begin_checkout", eventProperty1)
        )

        // Case 2: 하나의 아이템이 "콜라"를 포함 → false
        val eventProperty2 = mapOf(
            "mkt_items" to listOf(
                mapOf("mkt_product_name" to "콜라"),
                mapOf("mkt_product_name" to "사이다")
            )
        )
        assertFalse(
            "One item with '콜라' should return false",
            conditionChecker.checkCondition(condition, "mkt_begin_checkout", eventProperty2)
        )

        // Case 3: 모든 아이템이 "콜라"를 포함 → false
        val eventProperty3 = mapOf(
            "mkt_items" to listOf(
                mapOf("mkt_product_name" to "코카콜라"),
                mapOf("mkt_product_name" to "펩시콜라")
            )
        )
        assertFalse(
            "All items with '콜라' should return false",
            conditionChecker.checkCondition(condition, "mkt_begin_checkout", eventProperty3)
        )
    }

    @Test
    fun `LIKE with Item Path - 하나라도 조건을 만족하면 true`() {
        val condition = Condition(
            eventFilter = EventFilter(eventName = "mkt_begin_checkout"),
            propertyConditions = listOf(
                listOf(
                    EventPropertyCondition(
                        extractionStrategy = ExtractionStrategy(
                            PropertySchema(
                                "mkt_product_name",
                                "mkt_product_name",
                                DataType.STRING,
                                Path.ITEM
                            )
                        ),
                        operator = TaxonomyOperator.LIKE,
                        targetValues = listOf("콜라")
                    )
                )
            )
        )

        // Case 1: 하나의 아이템이 "콜라"를 포함 → true
        val eventProperty1 = mapOf(
            "mkt_items" to listOf(
                mapOf("mkt_product_name" to "콜라"),
                mapOf("mkt_product_name" to "사이다")
            )
        )
        assertTrue(
            "One item with '콜라' should return true",
            conditionChecker.checkCondition(condition, "mkt_begin_checkout", eventProperty1)
        )

        // Case 2: 모든 아이템이 "콜라"를 포함하지 않음 → false
        val eventProperty2 = mapOf(
            "mkt_items" to listOf(
                mapOf("mkt_product_name" to "사이다"),
                mapOf("mkt_product_name" to "환타")
            )
        )
        assertFalse(
            "No items with '콜라' should return false",
            conditionChecker.checkCondition(condition, "mkt_begin_checkout", eventProperty2)
        )
    }

    @Test
    fun `복합 조건 - IS_NOT_NULL AND NOT_LIKE 콜라 AND NOT_LIKE 스프라이트`() {
        val condition = Condition(
            eventFilter = EventFilter(eventName = "mkt_begin_checkout"),
            propertyConditions = listOf(
                listOf(
                    EventPropertyCondition(
                        extractionStrategy = ExtractionStrategy(
                            PropertySchema(
                                "mkt_product_name",
                                "mkt_product_name",
                                DataType.STRING,
                                Path.ITEM
                            )
                        ),
                        operator = TaxonomyOperator.IS_NOT_NULL,
                        targetValues = listOf("콜라")
                    ),
                    EventPropertyCondition(
                        extractionStrategy = ExtractionStrategy(
                            PropertySchema(
                                "mkt_product_name",
                                "mkt_product_name",
                                DataType.STRING,
                                Path.ITEM
                            )
                        ),
                        operator = TaxonomyOperator.NOT_LIKE,
                        targetValues = listOf("콜라")
                    ),
                    EventPropertyCondition(
                        extractionStrategy = ExtractionStrategy(
                            PropertySchema(
                                "mkt_product_name",
                                "mkt_product_name",
                                DataType.STRING,
                                Path.ITEM
                            )
                        ),
                        operator = TaxonomyOperator.NOT_LIKE,
                        targetValues = listOf("스프라이트")
                    )
                )
            )
        )

        // Case 1: 콜라도 스프라이트도 없는 아이템들 → true
        val eventProperty1 = mapOf(
            "mkt_items" to listOf(
                mapOf("mkt_product_name" to "사이다"),
                mapOf("mkt_product_name" to "환타")
            )
        )
        assertTrue(
            "Items without '콜라' and '스프라이트' should return true",
            conditionChecker.checkCondition(condition, "mkt_begin_checkout", eventProperty1)
        )

        // Case 2: 콜라가 포함된 아이템 → false
        val eventProperty2 = mapOf(
            "mkt_items" to listOf(
                mapOf("mkt_product_name" to "코카콜라"),
                mapOf("mkt_product_name" to "환타")
            )
        )
        assertFalse(
            "Items with '콜라' should return false",
            conditionChecker.checkCondition(condition, "mkt_begin_checkout", eventProperty2)
        )

        // Case 3: 스프라이트가 포함된 아이템 → false
        val eventProperty3 = mapOf(
            "mkt_items" to listOf(
                mapOf("mkt_product_name" to "사이다"),
                mapOf("mkt_product_name" to "스프라이트")
            )
        )
        assertFalse(
            "Items with '스프라이트' should return false",
            conditionChecker.checkCondition(condition, "mkt_begin_checkout", eventProperty3)
        )
    }

    @Test
    fun `NOT_EQUAL with Item Path - 모든 아이템이 조건을 만족해야 true`() {
        val condition = Condition(
            eventFilter = EventFilter(eventName = "purchase"),
            propertyConditions = listOf(
                listOf(
                    EventPropertyCondition(
                        extractionStrategy = ExtractionStrategy(
                            PropertySchema(
                                "mkt_item_category",
                                "mkt_item_category",
                                DataType.STRING,
                                Path.ITEM
                            )
                        ),
                        operator = TaxonomyOperator.NOT_EQUAL,
                        targetValues = listOf("electronics")
                    )
                )
            )
        )

        // 모든 아이템이 electronics가 아닌 경우 → true
        val eventProperty1 = mapOf(
            "mkt_items" to listOf(
                mapOf("mkt_item_category" to "furniture"),
                mapOf("mkt_item_category" to "home")
            )
        )
        assertTrue(conditionChecker.checkCondition(condition, "purchase", eventProperty1))

        // 하나라도 electronics인 경우 → false
        val eventProperty2 = mapOf(
            "mkt_items" to listOf(
                mapOf("mkt_item_category" to "electronics"),
                mapOf("mkt_item_category" to "furniture")
            )
        )
        assertFalse(conditionChecker.checkCondition(condition, "purchase", eventProperty2))
    }

    @Test
    fun `EQUAL with Item Path - 하나라도 조건을 만족하면 true`() {
        val condition = Condition(
            eventFilter = EventFilter(eventName = "purchase"),
            propertyConditions = listOf(
                listOf(
                    EventPropertyCondition(
                        extractionStrategy = ExtractionStrategy(
                            PropertySchema(
                                "mkt_item_category",
                                "mkt_item_category",
                                DataType.STRING,
                                Path.ITEM
                            )
                        ),
                        operator = TaxonomyOperator.EQUAL,
                        targetValues = listOf("electronics")
                    )
                )
            )
        )

        // 하나라도 electronics인 경우 → true
        val eventProperty1 = mapOf(
            "mkt_items" to listOf(
                mapOf("mkt_item_category" to "electronics"),
                mapOf("mkt_item_category" to "furniture")
            )
        )
        assertTrue(conditionChecker.checkCondition(condition, "purchase", eventProperty1))

        // 모두 electronics가 아닌 경우 → false
        val eventProperty2 = mapOf(
            "mkt_items" to listOf(
                mapOf("mkt_item_category" to "furniture"),
                mapOf("mkt_item_category" to "home")
            )
        )
        assertFalse(conditionChecker.checkCondition(condition, "purchase", eventProperty2))
    }

    @Test
    fun `GREATER_THAN with Item Path - 하나라도 조건을 만족하면 true`() {
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
                        targetValues = listOf(100L)
                    )
                )
            )
        )

        // 하나라도 100 초과인 경우 → true
        val eventProperty1 = mapOf(
            "mkt_items" to listOf(
                mapOf("mkt_item_price" to 50L),
                mapOf("mkt_item_price" to 150L)
            )
        )
        assertTrue(conditionChecker.checkCondition(condition, "purchase", eventProperty1))

        // 모두 100 이하인 경우 → false
        val eventProperty2 = mapOf(
            "mkt_items" to listOf(
                mapOf("mkt_item_price" to 50L),
                mapOf("mkt_item_price" to 80L)
            )
        )
        assertFalse(conditionChecker.checkCondition(condition, "purchase", eventProperty2))
    }

    @Test
    fun `TaxonomyOperator aggregate 함수 테스트`() {
        // Negative operators → all
        assertTrue(TaxonomyOperator.NOT_EQUAL.aggregate(listOf(true, true, true)))
        assertFalse(TaxonomyOperator.NOT_EQUAL.aggregate(listOf(true, false, true)))
        assertFalse(TaxonomyOperator.NOT_EQUAL.aggregate(emptyList()))

        assertTrue(TaxonomyOperator.NOT_LIKE.aggregate(listOf(true, true)))
        assertFalse(TaxonomyOperator.NOT_LIKE.aggregate(listOf(true, false)))

        assertTrue(TaxonomyOperator.NONE.aggregate(listOf(true, true, true)))
        assertFalse(TaxonomyOperator.NONE.aggregate(listOf(false, true, true)))

        // Positive operators → any
        assertTrue(TaxonomyOperator.EQUAL.aggregate(listOf(true, false, false)))
        assertTrue(TaxonomyOperator.EQUAL.aggregate(listOf(false, true, false)))
        assertFalse(TaxonomyOperator.EQUAL.aggregate(listOf(false, false, false)))
        assertFalse(TaxonomyOperator.EQUAL.aggregate(emptyList()))

        assertTrue(TaxonomyOperator.LIKE.aggregate(listOf(false, true)))
        assertFalse(TaxonomyOperator.LIKE.aggregate(listOf(false, false)))
    }

    @Test
    fun `isNegativeOperator 테스트`() {
        // Negative operators
        assertTrue(TaxonomyOperator.NOT_EQUAL.isNegativeOperator())
        assertTrue(TaxonomyOperator.NOT_IN.isNegativeOperator())
        assertTrue(TaxonomyOperator.NOT_BETWEEN.isNegativeOperator())
        assertTrue(TaxonomyOperator.NOT_LIKE.isNegativeOperator())
        assertTrue(TaxonomyOperator.ARRAY_NOT_LIKE.isNegativeOperator())
        assertTrue(TaxonomyOperator.IS_NOT_NULL.isNegativeOperator())
        assertTrue(TaxonomyOperator.NOT_CONTAINS.isNegativeOperator())
        assertTrue(TaxonomyOperator.NONE.isNegativeOperator())

        // Positive operators
        assertFalse(TaxonomyOperator.EQUAL.isNegativeOperator())
        assertFalse(TaxonomyOperator.IN.isNegativeOperator())
        assertFalse(TaxonomyOperator.BETWEEN.isNegativeOperator())
        assertFalse(TaxonomyOperator.LIKE.isNegativeOperator())
        assertFalse(TaxonomyOperator.ARRAY_LIKE.isNegativeOperator())
        assertFalse(TaxonomyOperator.IS_NULL.isNegativeOperator())
        assertFalse(TaxonomyOperator.CONTAINS.isNegativeOperator())
        assertFalse(TaxonomyOperator.ANY.isNegativeOperator())
    }

    // MARK: - Event Trigger 테스트

    @Test
    fun `이벤트 이름이 일치하면 트리거됨`() {
        val condition = Condition(
            eventFilter = EventFilter(eventName = "test_event"),
            propertyConditions = null
        )
        assertTrue(conditionChecker.checkCondition(condition, "test_event", emptyMap()))
    }

    @Test
    fun `이벤트 이름이 불일치하면 트리거 안됨`() {
        val condition = Condition(
            eventFilter = EventFilter(eventName = "expected_event"),
            propertyConditions = null
        )
        assertFalse(conditionChecker.checkCondition(condition, "wrong_event", emptyMap()))
    }

    // MARK: - DataType별 조건 테스트

    @Test
    fun `INT 타입 조건 테스트`() {
        val testValue = 50L

        // EQUAL
        val conditionEqual = createCondition("price", DataType.INT, TaxonomyOperator.EQUAL, listOf(50L))
        assertTrue(conditionChecker.checkCondition(conditionEqual, "test", mapOf("price" to testValue)))

        // NOT_EQUAL
        val conditionNotEqual = createCondition("price", DataType.INT, TaxonomyOperator.NOT_EQUAL, listOf(30L))
        assertTrue(conditionChecker.checkCondition(conditionNotEqual, "test", mapOf("price" to testValue)))

        // GREATER_THAN
        val conditionGt = createCondition("price", DataType.INT, TaxonomyOperator.GREATER_THAN, listOf(30L))
        assertTrue(conditionChecker.checkCondition(conditionGt, "test", mapOf("price" to testValue)))

        // GREATER_THAN_OR_EQUAL
        val conditionGte = createCondition("price", DataType.INT, TaxonomyOperator.GREATER_THAN_OR_EQUAL, listOf(50L))
        assertTrue(conditionChecker.checkCondition(conditionGte, "test", mapOf("price" to testValue)))

        // LESS_THAN
        val conditionLt = createCondition("price", DataType.INT, TaxonomyOperator.LESS_THAN, listOf(60L))
        assertTrue(conditionChecker.checkCondition(conditionLt, "test", mapOf("price" to testValue)))

        // LESS_THAN_OR_EQUAL
        val conditionLte = createCondition("price", DataType.INT, TaxonomyOperator.LESS_THAN_OR_EQUAL, listOf(50L))
        assertTrue(conditionChecker.checkCondition(conditionLte, "test", mapOf("price" to testValue)))

        // BETWEEN (exclusive: 30 < 50 < 60)
        val conditionBetween = createCondition("price", DataType.INT, TaxonomyOperator.BETWEEN, listOf(30L, 60L))
        assertTrue(conditionChecker.checkCondition(conditionBetween, "test", mapOf("price" to testValue)))

        // NOT_BETWEEN
        val conditionNotBetween = createCondition("price", DataType.INT, TaxonomyOperator.NOT_BETWEEN, listOf(60L, 100L))
        assertTrue(conditionChecker.checkCondition(conditionNotBetween, "test", mapOf("price" to testValue)))
    }

    @Test
    fun `DOUBLE 타입 조건 테스트`() {
        val testValue = 4.5

        // EQUAL
        val conditionEqual = createCondition("rating", DataType.DOUBLE, TaxonomyOperator.EQUAL, listOf(4.5))
        assertTrue(conditionChecker.checkCondition(conditionEqual, "test", mapOf("rating" to testValue)))

        // GREATER_THAN
        val conditionGt = createCondition("rating", DataType.DOUBLE, TaxonomyOperator.GREATER_THAN, listOf(4.0))
        assertTrue(conditionChecker.checkCondition(conditionGt, "test", mapOf("rating" to testValue)))

        // BETWEEN (exclusive)
        val conditionBetween = createCondition("rating", DataType.DOUBLE, TaxonomyOperator.BETWEEN, listOf(4.0, 5.0))
        assertTrue(conditionChecker.checkCondition(conditionBetween, "test", mapOf("rating" to testValue)))
    }

    @Test
    fun `STRING 타입 조건 테스트`() {
        val testValue = "ACTIVE"

        // EQUAL
        val conditionEqual = createCondition("status", DataType.STRING, TaxonomyOperator.EQUAL, listOf("ACTIVE"))
        assertTrue(conditionChecker.checkCondition(conditionEqual, "test", mapOf("status" to testValue)))

        // NOT_EQUAL
        val conditionNotEqual = createCondition("status", DataType.STRING, TaxonomyOperator.NOT_EQUAL, listOf("INACTIVE"))
        assertTrue(conditionChecker.checkCondition(conditionNotEqual, "test", mapOf("status" to testValue)))

        // LIKE
        val conditionLike = createCondition("status", DataType.STRING, TaxonomyOperator.LIKE, listOf("ACT"))
        assertTrue(conditionChecker.checkCondition(conditionLike, "test", mapOf("status" to testValue)))

        // NOT_LIKE
        val conditionNotLike = createCondition("status", DataType.STRING, TaxonomyOperator.NOT_LIKE, listOf("INACTIVE"))
        assertTrue(conditionChecker.checkCondition(conditionNotLike, "test", mapOf("status" to testValue)))
    }

    @Test
    fun `BOOLEAN 타입 조건 테스트`() {
        // EQUAL true
        val conditionEqualTrue = createCondition("is_member", DataType.BOOLEAN, TaxonomyOperator.EQUAL, listOf(true))
        assertTrue(conditionChecker.checkCondition(conditionEqualTrue, "test", mapOf("is_member" to true)))
        assertFalse(conditionChecker.checkCondition(conditionEqualTrue, "test", mapOf("is_member" to false)))

        // NOT_EQUAL
        val conditionNotEqual = createCondition("is_member", DataType.BOOLEAN, TaxonomyOperator.NOT_EQUAL, listOf(false))
        assertTrue(conditionChecker.checkCondition(conditionNotEqual, "test", mapOf("is_member" to true)))
    }

    @Test
    fun `DATETIME 타입 조건 테스트`() {
        val isoFormatter = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        isoFormatter.timeZone = java.util.TimeZone.getTimeZone("UTC")

        val now = java.util.Date()
        val calendar = java.util.Calendar.getInstance()

        calendar.time = now
        calendar.add(java.util.Calendar.DAY_OF_MONTH, -1)
        val pastDate = isoFormatter.format(calendar.time)

        calendar.time = now
        calendar.add(java.util.Calendar.DAY_OF_MONTH, 1)
        val futureDate = isoFormatter.format(calendar.time)

        val testDateString = isoFormatter.format(now)

        // GREATER_THAN pastDate
        val conditionGt = createCondition("last_login", DataType.DATETIME, TaxonomyOperator.GREATER_THAN, listOf(pastDate))
        assertTrue(conditionChecker.checkCondition(conditionGt, "test", mapOf("last_login" to testDateString)))

        // LESS_THAN futureDate
        val conditionLt = createCondition("last_login", DataType.DATETIME, TaxonomyOperator.LESS_THAN, listOf(futureDate))
        assertTrue(conditionChecker.checkCondition(conditionLt, "test", mapOf("last_login" to testDateString)))

        // BETWEEN
        val conditionBetween = createCondition("last_login", DataType.DATETIME, TaxonomyOperator.BETWEEN, listOf(pastDate, futureDate))
        assertTrue(conditionChecker.checkCondition(conditionBetween, "test", mapOf("last_login" to testDateString)))
    }

    @Test
    fun `DATE 타입 조건 테스트`() {
        val testValue = "2024-02-20"

        // EQUAL
        val conditionEqual = createCondition("birth_date", DataType.DATE, TaxonomyOperator.EQUAL, listOf("2024-02-20"))
        assertTrue(conditionChecker.checkCondition(conditionEqual, "test", mapOf("birth_date" to testValue)))

        // GREATER_THAN
        val conditionGt = createCondition("birth_date", DataType.DATE, TaxonomyOperator.GREATER_THAN, listOf("2024-02-10"))
        assertTrue(conditionChecker.checkCondition(conditionGt, "test", mapOf("birth_date" to testValue)))

        // BETWEEN (exclusive)
        val conditionBetween = createCondition("birth_date", DataType.DATE, TaxonomyOperator.BETWEEN, listOf("2024-02-10", "2024-02-25"))
        assertTrue(conditionChecker.checkCondition(conditionBetween, "test", mapOf("birth_date" to testValue)))
    }

    @Test
    fun `STRING_ARRAY 타입 조건 테스트`() {
        val testTags = listOf("sports", "news", "tech")

        // CONTAINS
        val conditionContains = createCondition("tags", DataType.STRING_ARRAY, TaxonomyOperator.CONTAINS, listOf("sports"))
        assertTrue(conditionChecker.checkCondition(conditionContains, "test", mapOf("tags" to testTags)))

        // NOT_CONTAINS
        val conditionNotContains = createCondition("tags", DataType.STRING_ARRAY, TaxonomyOperator.NOT_CONTAINS, listOf("adult"))
        assertTrue(conditionChecker.checkCondition(conditionNotContains, "test", mapOf("tags" to testTags)))

        // ANY
        val conditionAny = createCondition("tags", DataType.STRING_ARRAY, TaxonomyOperator.ANY, listOf("sports", "music"))
        assertTrue(conditionChecker.checkCondition(conditionAny, "test", mapOf("tags" to testTags)))

        // NONE
        val conditionNone = createCondition("tags", DataType.STRING_ARRAY, TaxonomyOperator.NONE, listOf("adult", "gambling"))
        assertTrue(conditionChecker.checkCondition(conditionNone, "test", mapOf("tags" to testTags)))
    }

    // MARK: - BETWEEN exclusive 테스트

    @Test
    fun `BETWEEN은 경계값을 포함하지 않음 (exclusive)`() {
        // 경계값 10 → false
        val conditionBetween = createCondition("value", DataType.INT, TaxonomyOperator.BETWEEN, listOf(10L, 20L))
        assertFalse(conditionChecker.checkCondition(conditionBetween, "test", mapOf("value" to 10L)))
        assertFalse(conditionChecker.checkCondition(conditionBetween, "test", mapOf("value" to 20L)))
        assertTrue(conditionChecker.checkCondition(conditionBetween, "test", mapOf("value" to 15L)))

        // NOT_BETWEEN은 경계값 포함
        val conditionNotBetween = createCondition("value", DataType.INT, TaxonomyOperator.NOT_BETWEEN, listOf(10L, 20L))
        assertTrue(conditionChecker.checkCondition(conditionNotBetween, "test", mapOf("value" to 10L)))
        assertTrue(conditionChecker.checkCondition(conditionNotBetween, "test", mapOf("value" to 20L)))
    }

    // MARK: - LIKE case insensitive 테스트

    @Test
    fun `LIKE는 대소문자 구분 안함`() {
        val conditionLike = createCondition("name", DataType.STRING, TaxonomyOperator.LIKE, listOf("hello"))
        assertTrue(conditionChecker.checkCondition(conditionLike, "test", mapOf("name" to "Hello World")))
        assertTrue(conditionChecker.checkCondition(conditionLike, "test", mapOf("name" to "HELLO WORLD")))
        assertTrue(conditionChecker.checkCondition(conditionLike, "test", mapOf("name" to "hello world")))

        val conditionNotLike = createCondition("name", DataType.STRING, TaxonomyOperator.NOT_LIKE, listOf("hello"))
        assertFalse(conditionChecker.checkCondition(conditionNotLike, "test", mapOf("name" to "Hello World")))
    }

    // MARK: - Helper 함수

    private fun createCondition(
        propertyName: String,
        dataType: DataType,
        operator: TaxonomyOperator,
        targetValues: List<Any>
    ): Condition {
        return Condition(
            eventFilter = EventFilter(eventName = "test"),
            propertyConditions = listOf(
                listOf(
                    EventPropertyCondition(
                        extractionStrategy = ExtractionStrategy(
                            PropertySchema(propertyName, propertyName, dataType, Path.EVENT)
                        ),
                        operator = operator,
                        targetValues = targetValues
                    )
                )
            )
        )
    }

    // MARK: - 기본 연산자 테스트

    @Test
    fun `IN and NOT_IN 연산자 테스트`() {
        // IN - 하나라도 일치하면 true
        val conditionIn = Condition(
            eventFilter = EventFilter(eventName = "purchase"),
            propertyConditions = listOf(
                listOf(
                    EventPropertyCondition(
                        extractionStrategy = ExtractionStrategy(
                            PropertySchema("category", "category", DataType.STRING, Path.EVENT)
                        ),
                        operator = TaxonomyOperator.IN,
                        targetValues = listOf("electronics", "furniture", "home")
                    )
                )
            )
        )

        assertTrue(conditionChecker.checkCondition(conditionIn, "purchase", mapOf("category" to "electronics")))
        assertFalse(conditionChecker.checkCondition(conditionIn, "purchase", mapOf("category" to "food")))

        // NOT_IN - 모두 불일치해야 true
        val conditionNotIn = Condition(
            eventFilter = EventFilter(eventName = "purchase"),
            propertyConditions = listOf(
                listOf(
                    EventPropertyCondition(
                        extractionStrategy = ExtractionStrategy(
                            PropertySchema("category", "category", DataType.STRING, Path.EVENT)
                        ),
                        operator = TaxonomyOperator.NOT_IN,
                        targetValues = listOf("electronics", "furniture")
                    )
                )
            )
        )

        assertTrue(conditionChecker.checkCondition(conditionNotIn, "purchase", mapOf("category" to "food")))
        assertFalse(conditionChecker.checkCondition(conditionNotIn, "purchase", mapOf("category" to "electronics")))
    }

    @Test
    fun `IS_NULL 연산자 테스트`() {
        val condition = Condition(
            eventFilter = EventFilter(eventName = "test"),
            propertyConditions = listOf(
                listOf(
                    EventPropertyCondition(
                        extractionStrategy = ExtractionStrategy(
                            PropertySchema("optional_field", "optional_field", DataType.STRING, Path.EVENT)
                        ),
                        operator = TaxonomyOperator.IS_NULL,
                        targetValues = emptyList()
                    )
                )
            )
        )

        // 필드가 없으면 null → true
        assertTrue(conditionChecker.checkCondition(condition, "test", mapOf("other_field" to "value")))
        // 필드가 있으면 → false
        assertFalse(conditionChecker.checkCondition(condition, "test", mapOf("optional_field" to "value")))
    }

    @Test
    fun `CONTAINS and NOT_CONTAINS 연산자 테스트 (배열)`() {
        // CONTAINS - 배열에 값이 포함되어 있는지
        val conditionContains = Condition(
            eventFilter = EventFilter(eventName = "test"),
            propertyConditions = listOf(
                listOf(
                    EventPropertyCondition(
                        extractionStrategy = ExtractionStrategy(
                            PropertySchema("tags", "tags", DataType.STRING_ARRAY, Path.EVENT)
                        ),
                        operator = TaxonomyOperator.CONTAINS,
                        targetValues = listOf("sports")
                    )
                )
            )
        )

        assertTrue(conditionChecker.checkCondition(conditionContains, "test", mapOf("tags" to listOf("sports", "news"))))
        assertFalse(conditionChecker.checkCondition(conditionContains, "test", mapOf("tags" to listOf("news", "tech"))))

        // NOT_CONTAINS
        val conditionNotContains = Condition(
            eventFilter = EventFilter(eventName = "test"),
            propertyConditions = listOf(
                listOf(
                    EventPropertyCondition(
                        extractionStrategy = ExtractionStrategy(
                            PropertySchema("tags", "tags", DataType.STRING_ARRAY, Path.EVENT)
                        ),
                        operator = TaxonomyOperator.NOT_CONTAINS,
                        targetValues = listOf("adult")
                    )
                )
            )
        )

        assertTrue(conditionChecker.checkCondition(conditionNotContains, "test", mapOf("tags" to listOf("sports", "news"))))
        assertFalse(conditionChecker.checkCondition(conditionNotContains, "test", mapOf("tags" to listOf("adult", "news"))))
    }

    @Test
    fun `ANY and NONE 연산자 테스트 (배열)`() {
        // ANY - 타겟 중 하나라도 소스에 있으면 true
        val conditionAny = Condition(
            eventFilter = EventFilter(eventName = "test"),
            propertyConditions = listOf(
                listOf(
                    EventPropertyCondition(
                        extractionStrategy = ExtractionStrategy(
                            PropertySchema("tags", "tags", DataType.STRING_ARRAY, Path.EVENT)
                        ),
                        operator = TaxonomyOperator.ANY,
                        targetValues = listOf("sports", "music")
                    )
                )
            )
        )

        assertTrue(conditionChecker.checkCondition(conditionAny, "test", mapOf("tags" to listOf("sports", "news"))))
        assertFalse(conditionChecker.checkCondition(conditionAny, "test", mapOf("tags" to listOf("news", "tech"))))

        // NONE - 타겟 중 어떤 것도 소스에 없어야 true
        val conditionNone = Condition(
            eventFilter = EventFilter(eventName = "test"),
            propertyConditions = listOf(
                listOf(
                    EventPropertyCondition(
                        extractionStrategy = ExtractionStrategy(
                            PropertySchema("tags", "tags", DataType.STRING_ARRAY, Path.EVENT)
                        ),
                        operator = TaxonomyOperator.NONE,
                        targetValues = listOf("adult", "gambling")
                    )
                )
            )
        )

        assertTrue(conditionChecker.checkCondition(conditionNone, "test", mapOf("tags" to listOf("sports", "news"))))
        assertFalse(conditionChecker.checkCondition(conditionNone, "test", mapOf("tags" to listOf("adult", "news"))))
    }

    @Test
    fun `ARRAY_LIKE and ARRAY_NOT_LIKE 연산자 테스트`() {
        // ARRAY_LIKE - 배열 원소 중 하나라도 타겟을 포함하면 true
        val conditionArrayLike = Condition(
            eventFilter = EventFilter(eventName = "test"),
            propertyConditions = listOf(
                listOf(
                    EventPropertyCondition(
                        extractionStrategy = ExtractionStrategy(
                            PropertySchema("products", "products", DataType.STRING_ARRAY, Path.EVENT)
                        ),
                        operator = TaxonomyOperator.ARRAY_LIKE,
                        targetValues = listOf("콜라")
                    )
                )
            )
        )

        assertTrue(conditionChecker.checkCondition(conditionArrayLike, "test", mapOf("products" to listOf("코카콜라", "사이다"))))
        assertFalse(conditionChecker.checkCondition(conditionArrayLike, "test", mapOf("products" to listOf("사이다", "환타"))))

        // ARRAY_NOT_LIKE - 모든 원소가 타겟을 포함하지 않아야 true
        val conditionArrayNotLike = Condition(
            eventFilter = EventFilter(eventName = "test"),
            propertyConditions = listOf(
                listOf(
                    EventPropertyCondition(
                        extractionStrategy = ExtractionStrategy(
                            PropertySchema("products", "products", DataType.STRING_ARRAY, Path.EVENT)
                        ),
                        operator = TaxonomyOperator.ARRAY_NOT_LIKE,
                        targetValues = listOf("콜라")
                    )
                )
            )
        )

        assertTrue(conditionChecker.checkCondition(conditionArrayNotLike, "test", mapOf("products" to listOf("사이다", "환타"))))
        assertFalse(conditionChecker.checkCondition(conditionArrayNotLike, "test", mapOf("products" to listOf("코카콜라", "사이다"))))
    }

    // MARK: - 날짜 추출 연산자 테스트

    @Test
    fun `YEAR_EQUAL 연산자 테스트`() {
        val condition = Condition(
            eventFilter = EventFilter(eventName = "test"),
            propertyConditions = listOf(
                listOf(
                    EventPropertyCondition(
                        extractionStrategy = ExtractionStrategy(
                            PropertySchema("birth_date", "birth_date", DataType.DATE, Path.EVENT)
                        ),
                        operator = TaxonomyOperator.YEAR_EQUAL,
                        targetValues = listOf(2024L)
                    )
                )
            )
        )

        assertTrue(conditionChecker.checkCondition(condition, "test", mapOf("birth_date" to "2024-06-15")))
        assertTrue(conditionChecker.checkCondition(condition, "test", mapOf("birth_date" to "2024-01-01")))
        assertFalse(conditionChecker.checkCondition(condition, "test", mapOf("birth_date" to "2023-06-15")))
    }

    @Test
    fun `MONTH_EQUAL 연산자 테스트`() {
        val condition = Condition(
            eventFilter = EventFilter(eventName = "test"),
            propertyConditions = listOf(
                listOf(
                    EventPropertyCondition(
                        extractionStrategy = ExtractionStrategy(
                            PropertySchema("event_date", "event_date", DataType.DATE, Path.EVENT)
                        ),
                        operator = TaxonomyOperator.MONTH_EQUAL,
                        targetValues = listOf(6L)
                    )
                )
            )
        )

        assertTrue(conditionChecker.checkCondition(condition, "test", mapOf("event_date" to "2024-06-15")))
        assertTrue(conditionChecker.checkCondition(condition, "test", mapOf("event_date" to "2023-06-01")))
        assertFalse(conditionChecker.checkCondition(condition, "test", mapOf("event_date" to "2024-07-15")))
    }

    @Test
    fun `YEAR_MONTH_EQUAL 연산자 테스트`() {
        val condition = Condition(
            eventFilter = EventFilter(eventName = "test"),
            propertyConditions = listOf(
                listOf(
                    EventPropertyCondition(
                        extractionStrategy = ExtractionStrategy(
                            PropertySchema("event_date", "event_date", DataType.DATE, Path.EVENT)
                        ),
                        operator = TaxonomyOperator.YEAR_MONTH_EQUAL,
                        targetValues = listOf("2024-06")
                    )
                )
            )
        )

        assertTrue(conditionChecker.checkCondition(condition, "test", mapOf("event_date" to "2024-06-15")))
        assertTrue(conditionChecker.checkCondition(condition, "test", mapOf("event_date" to "2024-06-01")))
        assertFalse(conditionChecker.checkCondition(condition, "test", mapOf("event_date" to "2024-07-15")))
        assertFalse(conditionChecker.checkCondition(condition, "test", mapOf("event_date" to "2023-06-15")))
    }

    // MARK: - 상대 날짜 연산자 테스트

    @Test
    fun `BEFORE 연산자 테스트 - 정확히 N일 전`() {
        val condition = Condition(
            eventFilter = EventFilter(eventName = "test"),
            propertyConditions = listOf(
                listOf(
                    EventPropertyCondition(
                        extractionStrategy = ExtractionStrategy(
                            PropertySchema("event_date", "event_date", DataType.DATE, Path.EVENT)
                        ),
                        operator = TaxonomyOperator.BEFORE,
                        targetValues = listOf(3L) // 3일 전
                    )
                )
            )
        )

        val formatter = java.text.SimpleDateFormat("yyyy-MM-dd")
        val calendar = java.util.Calendar.getInstance()

        // 정확히 3일 전 → true
        calendar.time = java.util.Date()
        calendar.add(java.util.Calendar.DAY_OF_MONTH, -3)
        val threeDaysAgo = formatter.format(calendar.time)
        assertTrue(conditionChecker.checkCondition(condition, "test", mapOf("event_date" to threeDaysAgo)))

        // 2일 전 → false
        calendar.time = java.util.Date()
        calendar.add(java.util.Calendar.DAY_OF_MONTH, -2)
        val twoDaysAgo = formatter.format(calendar.time)
        assertFalse(conditionChecker.checkCondition(condition, "test", mapOf("event_date" to twoDaysAgo)))
    }

    @Test
    fun `PAST 연산자 테스트 - N일 이상 지남`() {
        val condition = Condition(
            eventFilter = EventFilter(eventName = "test"),
            propertyConditions = listOf(
                listOf(
                    EventPropertyCondition(
                        extractionStrategy = ExtractionStrategy(
                            PropertySchema("event_date", "event_date", DataType.DATE, Path.EVENT)
                        ),
                        operator = TaxonomyOperator.PAST,
                        targetValues = listOf(3L) // 3일 이상 지남
                    )
                )
            )
        )

        val formatter = java.text.SimpleDateFormat("yyyy-MM-dd")
        val calendar = java.util.Calendar.getInstance()

        // 5일 전 → true (3일 이상 지남)
        calendar.time = java.util.Date()
        calendar.add(java.util.Calendar.DAY_OF_MONTH, -5)
        val fiveDaysAgo = formatter.format(calendar.time)
        assertTrue(conditionChecker.checkCondition(condition, "test", mapOf("event_date" to fiveDaysAgo)))

        // 1일 전 → false (3일 안 지남)
        calendar.time = java.util.Date()
        calendar.add(java.util.Calendar.DAY_OF_MONTH, -1)
        val oneDayAgo = formatter.format(calendar.time)
        assertFalse(conditionChecker.checkCondition(condition, "test", mapOf("event_date" to oneDayAgo)))
    }

    @Test
    fun `WITHIN_PAST 연산자 테스트 - 최근 N일 이내`() {
        val condition = Condition(
            eventFilter = EventFilter(eventName = "test"),
            propertyConditions = listOf(
                listOf(
                    EventPropertyCondition(
                        extractionStrategy = ExtractionStrategy(
                            PropertySchema("event_date", "event_date", DataType.DATE, Path.EVENT)
                        ),
                        operator = TaxonomyOperator.WITHIN_PAST,
                        targetValues = listOf(5L) // 최근 5일 이내
                    )
                )
            )
        )

        val formatter = java.text.SimpleDateFormat("yyyy-MM-dd")
        val calendar = java.util.Calendar.getInstance()

        // 2일 전 → true (5일 이내)
        calendar.time = java.util.Date()
        calendar.add(java.util.Calendar.DAY_OF_MONTH, -2)
        val twoDaysAgo = formatter.format(calendar.time)
        assertTrue(conditionChecker.checkCondition(condition, "test", mapOf("event_date" to twoDaysAgo)))

        // 10일 전 → false (5일 이내 아님)
        calendar.time = java.util.Date()
        calendar.add(java.util.Calendar.DAY_OF_MONTH, -10)
        val tenDaysAgo = formatter.format(calendar.time)
        assertFalse(conditionChecker.checkCondition(condition, "test", mapOf("event_date" to tenDaysAgo)))
    }

    @Test
    fun `AFTER 연산자 테스트 - 정확히 N일 후`() {
        val condition = Condition(
            eventFilter = EventFilter(eventName = "test"),
            propertyConditions = listOf(
                listOf(
                    EventPropertyCondition(
                        extractionStrategy = ExtractionStrategy(
                            PropertySchema("expiry_date", "expiry_date", DataType.DATE, Path.EVENT)
                        ),
                        operator = TaxonomyOperator.AFTER,
                        targetValues = listOf(3L) // 3일 후
                    )
                )
            )
        )

        val formatter = java.text.SimpleDateFormat("yyyy-MM-dd")
        val calendar = java.util.Calendar.getInstance()

        // 정확히 3일 후 → true
        calendar.time = java.util.Date()
        calendar.add(java.util.Calendar.DAY_OF_MONTH, 3)
        val threeDaysLater = formatter.format(calendar.time)
        assertTrue(conditionChecker.checkCondition(condition, "test", mapOf("expiry_date" to threeDaysLater)))

        // 5일 후 → false
        calendar.time = java.util.Date()
        calendar.add(java.util.Calendar.DAY_OF_MONTH, 5)
        val fiveDaysLater = formatter.format(calendar.time)
        assertFalse(conditionChecker.checkCondition(condition, "test", mapOf("expiry_date" to fiveDaysLater)))
    }

    @Test
    fun `REMAINING 연산자 테스트 - N일 이상 남음`() {
        val condition = Condition(
            eventFilter = EventFilter(eventName = "test"),
            propertyConditions = listOf(
                listOf(
                    EventPropertyCondition(
                        extractionStrategy = ExtractionStrategy(
                            PropertySchema("expiry_date", "expiry_date", DataType.DATE, Path.EVENT)
                        ),
                        operator = TaxonomyOperator.REMAINING,
                        targetValues = listOf(5L) // 5일 이상 남음
                    )
                )
            )
        )

        val formatter = java.text.SimpleDateFormat("yyyy-MM-dd")
        val calendar = java.util.Calendar.getInstance()

        // 10일 후 → true (5일 이상 남음)
        calendar.time = java.util.Date()
        calendar.add(java.util.Calendar.DAY_OF_MONTH, 10)
        val tenDaysLater = formatter.format(calendar.time)
        assertTrue(conditionChecker.checkCondition(condition, "test", mapOf("expiry_date" to tenDaysLater)))

        // 2일 후 → false (5일 안 남음)
        calendar.time = java.util.Date()
        calendar.add(java.util.Calendar.DAY_OF_MONTH, 2)
        val twoDaysLater = formatter.format(calendar.time)
        assertFalse(conditionChecker.checkCondition(condition, "test", mapOf("expiry_date" to twoDaysLater)))
    }

    @Test
    fun `WITHIN_REMAINING 연산자 테스트 - N일 이내 남음`() {
        val condition = Condition(
            eventFilter = EventFilter(eventName = "test"),
            propertyConditions = listOf(
                listOf(
                    EventPropertyCondition(
                        extractionStrategy = ExtractionStrategy(
                            PropertySchema("expiry_date", "expiry_date", DataType.DATE, Path.EVENT)
                        ),
                        operator = TaxonomyOperator.WITHIN_REMAINING,
                        targetValues = listOf(5L) // 5일 이내 남음
                    )
                )
            )
        )

        val formatter = java.text.SimpleDateFormat("yyyy-MM-dd")
        val calendar = java.util.Calendar.getInstance()

        // 3일 후 → true (5일 이내 남음)
        calendar.time = java.util.Date()
        calendar.add(java.util.Calendar.DAY_OF_MONTH, 3)
        val threeDaysLater = formatter.format(calendar.time)
        assertTrue(conditionChecker.checkCondition(condition, "test", mapOf("expiry_date" to threeDaysLater)))

        // 10일 후 → false (5일 이내 아님)
        calendar.time = java.util.Date()
        calendar.add(java.util.Calendar.DAY_OF_MONTH, 10)
        val tenDaysLater = formatter.format(calendar.time)
        assertFalse(conditionChecker.checkCondition(condition, "test", mapOf("expiry_date" to tenDaysLater)))

        // 과거 날짜 → false
        calendar.time = java.util.Date()
        calendar.add(java.util.Calendar.DAY_OF_MONTH, -1)
        val oneDayAgo = formatter.format(calendar.time)
        assertFalse(conditionChecker.checkCondition(condition, "test", mapOf("expiry_date" to oneDayAgo)))
    }
}
