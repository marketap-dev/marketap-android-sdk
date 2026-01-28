package com.marketap.sdk.domain.service.inapp.condition.comparator

import com.marketap.sdk.domain.service.inapp.condition.comparator.TypeConverter.getList
import com.marketap.sdk.domain.service.inapp.condition.comparator.TypeConverter.getPairOrNull
import com.marketap.sdk.domain.service.inapp.condition.comparator.TypeConverter.getSingleOrNull
import com.marketap.sdk.model.internal.inapp.TaxonomyOperator
import java.util.Calendar
import java.util.Date

class DateComparator : TypeSafeComparator<Date> {
    override fun compare(
        value: Date,
        targetValues: List<Any>,
        operator: TaxonomyOperator
    ): Boolean {
        return when (operator) {
            TaxonomyOperator.EQUAL -> targetValues.getSingleOrNull<Date>()?.let { value == it }
                ?: false

            TaxonomyOperator.NOT_EQUAL -> targetValues.getSingleOrNull<Date>()?.let { value != it }
                ?: false

            TaxonomyOperator.GREATER_THAN -> targetValues.getSingleOrNull<Date>()
                ?.let { value.after(it) } ?: false

            TaxonomyOperator.GREATER_THAN_OR_EQUAL -> targetValues.getSingleOrNull<Date>()
                ?.let { !value.before(it) } ?: false

            TaxonomyOperator.LESS_THAN -> targetValues.getSingleOrNull<Date>()
                ?.let { value.before(it) } ?: false

            TaxonomyOperator.LESS_THAN_OR_EQUAL -> targetValues.getSingleOrNull<Date>()
                ?.let { !value.after(it) } ?: false

            TaxonomyOperator.BETWEEN -> targetValues.getPairOrNull<Date>()
                ?.let { (start, end) -> value.after(start) && value.before(end) } ?: false

            TaxonomyOperator.NOT_BETWEEN -> targetValues.getPairOrNull<Date>()
                ?.let { (start, end) -> value.before(start) || value.after(end) } ?: false

            TaxonomyOperator.IN -> targetValues.getList<Date>().contains(value)
            TaxonomyOperator.NOT_IN -> !targetValues.getList<Date>().contains(value)
            TaxonomyOperator.IS_NULL -> false // Date는 null이 올 수 없으므로 항상 false
            TaxonomyOperator.IS_NOT_NULL -> true // Date는 항상 존재함

            TaxonomyOperator.YEAR_EQUAL -> targetValues.getSingleOrNull<Int>()?.let { year ->
                val calendar = Calendar.getInstance()
                calendar.time = value
                calendar.get(Calendar.YEAR) == year
            } ?: false

            TaxonomyOperator.MONTH_EQUAL -> targetValues.getSingleOrNull<Int>()?.let { month ->
                val calendar = Calendar.getInstance()
                calendar.time = value
                calendar.get(Calendar.MONTH) == month - 1
            } ?: false

            TaxonomyOperator.YEAR_MONTH_EQUAL -> {
                val target = targetValues.getSingleOrNull<String>()
                val (year, month) = target?.split("-")?.map { it.toInt() } ?: return false
                val calendar = Calendar.getInstance()
                calendar.time = value
                calendar.get(Calendar.YEAR) == year && calendar.get(Calendar.MONTH) == month - 1
            }

            TaxonomyOperator.BEFORE -> targetValues.getSingleOrNull<Int>()?.let { days ->
                val targetDate = startOfDay(daysAgo(days))
                val sourceDate = startOfDay(value)
                sourceDate == targetDate
            } ?: false

            TaxonomyOperator.PAST -> targetValues.getSingleOrNull<Int>()?.let { days ->
                val targetDate = daysAgo(days)
                value.before(targetDate)
            } ?: false

            TaxonomyOperator.WITHIN_PAST -> targetValues.getSingleOrNull<Int>()?.let { days ->
                val targetDate = daysAgo(days)
                val now = Date()
                value.after(targetDate) && value.before(now)
            } ?: false

            TaxonomyOperator.AFTER -> targetValues.getSingleOrNull<Int>()?.let { days ->
                val targetDate = startOfDay(daysFromNow(days))
                val sourceDate = startOfDay(value)
                sourceDate == targetDate
            } ?: false

            TaxonomyOperator.REMAINING -> targetValues.getSingleOrNull<Int>()?.let { days ->
                val targetDate = daysFromNow(days)
                value.after(targetDate)
            } ?: false

            TaxonomyOperator.WITHIN_REMAINING -> targetValues.getSingleOrNull<Int>()?.let { days ->
                val targetDate = daysFromNow(days)
                val now = Date()
                value.after(now) && value.before(targetDate)
            } ?: false

            TaxonomyOperator.LIKE,
            TaxonomyOperator.NOT_LIKE,
            TaxonomyOperator.ARRAY_LIKE,
            TaxonomyOperator.ARRAY_NOT_LIKE,
            TaxonomyOperator.CONTAINS,
            TaxonomyOperator.NOT_CONTAINS,
            TaxonomyOperator.ANY,
            TaxonomyOperator.NONE -> {
                throw UnsupportedOperationException("Date type does not support $operator")
            }
        }
    }

    private fun daysAgo(days: Int): Date {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -days)
        return calendar.time
    }

    private fun daysFromNow(days: Int): Date {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, days)
        return calendar.time
    }

    private fun startOfDay(date: Date): Date {
        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.time
    }
}