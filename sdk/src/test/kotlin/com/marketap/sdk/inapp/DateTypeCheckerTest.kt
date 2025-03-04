package com.marketap.sdk.inapp

import com.marketap.sdk.domain.service.inapp.condition.PropertyConditionCheckerImpl
import com.marketap.sdk.domain.service.inapp.condition.comparator.ValueComparatorImpl
import com.marketap.sdk.model.internal.inapp.DataType
import com.marketap.sdk.model.internal.inapp.EventPropertyCondition
import com.marketap.sdk.model.internal.inapp.ExtractionStrategy
import com.marketap.sdk.model.internal.inapp.Path
import com.marketap.sdk.model.internal.inapp.PropertySchema
import com.marketap.sdk.model.internal.inapp.TaxonomyOperator
import com.marketap.sdk.utils.getNow
import com.marketap.sdk.utils.toUTCString
import junit.framework.TestCase.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

class DateTypeCheckerTest {

    private val now: Date = Date()
    private val yesterday: Date = daysAgo(1)
    private val lastWeek: Date = daysAgo(7)
    private val lastMonth: Date = daysAgo(30)
    private val tomorrow: Date = daysFromNow(1)
    private val nextWeek: Date = daysFromNow(7)
    private val nextMonth: Date = daysFromNow(30)
    private fun daysAgo(days: Int): Date {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -days)
        return calendar.time
    }

    private fun daysFromNow(days: Int): Date {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, days)
        getNow()
        return calendar.time
    }

    private val eventProperty = mapOf(
        "mkt_created_datetime" to now.toUTCString(),
        "mkt_last_purchase_date" to lastWeek.toUTCString(),
        "mkt_items" to listOf(
            mapOf("mkt_expiry_date" to nextMonth.toUTCString()),
            mapOf("mkt_expiry_date" to nextWeek.toUTCString()),
            mapOf("mkt_expiry_date" to yesterday.toUTCString())
        )
    )
    private val propertyConditionChecker = PropertyConditionCheckerImpl(
        ValueComparatorImpl()
    )

    @Test
    fun `DATE - EQUAL 연산 성공`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_created_datetime",
                    "mkt_created_datetime",
                    DataType.DATETIME
                )
            ),
            operator = TaxonomyOperator.EQUAL,
            targetValues = listOf(now)
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertTrue(result)
    }

    @Test
    fun `DATE - NOT_EQUAL 연산 성공`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_created_datetime",
                    "mkt_created_datetime",
                    DataType.DATETIME
                )
            ),
            operator = TaxonomyOperator.NOT_EQUAL,
            targetValues = listOf(yesterday)
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertTrue(result)
    }

    @Test
    fun `DATE - GREATER_THAN 연산 성공`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_created_datetime",
                    "mkt_created_datetime",
                    DataType.DATETIME
                )
            ),
            operator = TaxonomyOperator.GREATER_THAN,
            targetValues = listOf(lastWeek)
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertTrue(result)
    }

    @Test
    fun `DATE - LESS_THAN 연산 성공`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_last_purchase_date",
                    "mkt_last_purchase_date",
                    DataType.DATETIME
                )
            ),
            operator = TaxonomyOperator.LESS_THAN,
            targetValues = listOf(now)
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertTrue(result)
    }

    @Test
    fun `DATE - BETWEEN 연산 성공`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_created_datetime",
                    "mkt_created_datetime",
                    DataType.DATETIME
                )
            ),
            operator = TaxonomyOperator.BETWEEN,
            targetValues = listOf(lastWeek, nextWeek)
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertTrue(result)
    }

    @Test
    fun `DATE - NOT_BETWEEN 연산 성공`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_last_purchase_date",
                    "mkt_last_purchase_date",
                    DataType.DATETIME
                )
            ),
            operator = TaxonomyOperator.NOT_BETWEEN,
            targetValues = listOf(tomorrow, nextMonth)
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertTrue(result)
    }

    @Test
    fun `ITEM 속성 - DATE GREATER_THAN 연산 성공`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_expiry_date",
                    "mkt_expiry_date",
                    DataType.DATETIME,
                    Path.ITEM
                )
            ),
            operator = TaxonomyOperator.GREATER_THAN,
            targetValues = listOf(now)
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertTrue(result) // "mkt_items" 내부에서 미래 날짜가 하나라도 있으면 true
    }

    @Test
    fun `ITEM 속성 - DATE IS_NOT_NULL 연산 성공`() {
        val condition = EventPropertyCondition(
            extractionStrategy = ExtractionStrategy(
                PropertySchema(
                    "mkt_expiry_date",
                    "mkt_expiry_date",
                    DataType.DATETIME,
                    Path.ITEM
                )
            ),
            operator = TaxonomyOperator.IS_NOT_NULL,
            targetValues = emptyList()
        )

        val result = propertyConditionChecker.check(condition, eventProperty)

        assertTrue(result) // "mkt_items" 내부에서 "mkt_expiry_date" 존재
    }
}