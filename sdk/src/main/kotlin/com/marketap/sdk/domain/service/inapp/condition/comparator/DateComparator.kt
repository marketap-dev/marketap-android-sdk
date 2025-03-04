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

            TaxonomyOperator.YEAR_MONTH_EQUAL -> targetValues.getPairOrNull<Int>()
                ?.let { (year, month) ->
                    val calendar = Calendar.getInstance()
                    calendar.time = value
                    calendar.get(Calendar.YEAR) == year && calendar.get(Calendar.MONTH) == month - 1
                } ?: false

            TaxonomyOperator.LIKE,
            TaxonomyOperator.NOT_LIKE,
            TaxonomyOperator.CONTAINS,
            TaxonomyOperator.NOT_CONTAINS,
            TaxonomyOperator.ANY,
            TaxonomyOperator.NONE -> {
                throw UnsupportedOperationException("Date type does not support $operator")
            }

            else -> TODO()
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
}