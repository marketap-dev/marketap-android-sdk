package com.marketap.sdk.domain.service.inapp.condition.comparator

import com.marketap.sdk.domain.service.inapp.condition.comparator.TypeConverter.getList
import com.marketap.sdk.domain.service.inapp.condition.comparator.TypeConverter.getPairOrNull
import com.marketap.sdk.domain.service.inapp.condition.comparator.TypeConverter.getSingleOrNull
import com.marketap.sdk.model.internal.inapp.TaxonomyOperator
import com.marketap.sdk.utils.MarketapDate
import java.util.Calendar

class MarketapDateComparator : TypeSafeComparator<MarketapDate> {
    override fun compare(
        value: MarketapDate,
        targetValues: List<Any>,
        operator: TaxonomyOperator
    ): Boolean {
        return when (operator) {
            TaxonomyOperator.EQUAL -> targetValues.getSingleOrNull<MarketapDate>()
                ?.let { value == it } ?: false

            TaxonomyOperator.NOT_EQUAL -> targetValues.getSingleOrNull<MarketapDate>()
                ?.let { value != it } ?: false

            TaxonomyOperator.GREATER_THAN -> targetValues.getSingleOrNull<MarketapDate>()
                ?.let { value > it } ?: false

            TaxonomyOperator.GREATER_THAN_OR_EQUAL -> targetValues.getSingleOrNull<MarketapDate>()
                ?.let { value >= it } ?: false

            TaxonomyOperator.LESS_THAN -> targetValues.getSingleOrNull<MarketapDate>()
                ?.let { value < it } ?: false

            TaxonomyOperator.LESS_THAN_OR_EQUAL -> targetValues.getSingleOrNull<MarketapDate>()
                ?.let { value <= it } ?: false

            TaxonomyOperator.BETWEEN -> targetValues.getPairOrNull<MarketapDate>()
                ?.let { (start, end) -> value > start && value < end } ?: false

            TaxonomyOperator.NOT_BETWEEN -> targetValues.getPairOrNull<MarketapDate>()
                ?.let { (start, end) -> value <= start || value >= end } ?: false

            TaxonomyOperator.IN -> targetValues.getList<MarketapDate>().contains(value)
            TaxonomyOperator.NOT_IN -> !targetValues.getList<MarketapDate>().contains(value)
            TaxonomyOperator.IS_NULL -> false
            TaxonomyOperator.IS_NOT_NULL -> true

            TaxonomyOperator.YEAR_EQUAL -> targetValues.getSingleOrNull<Int>()
                ?.let { value.year == it } ?: false

            TaxonomyOperator.MONTH_EQUAL -> targetValues.getSingleOrNull<Int>()
                ?.let { value.month == it } ?: false

            TaxonomyOperator.YEAR_MONTH_EQUAL -> {
                val target = targetValues.getSingleOrNull<String>()
                val (year, month) = target?.split("-")?.map { it.toInt() } ?: return false
                value.year == year && value.month == month
            }

            TaxonomyOperator.BEFORE -> targetValues.getSingleOrNull<Int>()?.let { days ->
                val targetDate = daysAgo(days)
                value == targetDate
            } ?: false

            TaxonomyOperator.PAST -> targetValues.getSingleOrNull<Int>()?.let { days ->
                val targetDate = daysAgo(days)
                value < targetDate
            } ?: false

            TaxonomyOperator.WITHIN_PAST -> targetValues.getSingleOrNull<Int>()?.let { days ->
                val targetDate = daysAgo(days)
                val today = today()
                value > targetDate && value < today
            } ?: false

            TaxonomyOperator.AFTER -> targetValues.getSingleOrNull<Int>()?.let { days ->
                val targetDate = daysFromNow(days)
                value == targetDate
            } ?: false

            TaxonomyOperator.REMAINING -> targetValues.getSingleOrNull<Int>()?.let { days ->
                val targetDate = daysFromNow(days)
                value > targetDate
            } ?: false

            TaxonomyOperator.WITHIN_REMAINING -> targetValues.getSingleOrNull<Int>()?.let { days ->
                val targetDate = daysFromNow(days)
                val today = today()
                value > today && value < targetDate
            } ?: false

            else -> throw IllegalArgumentException("Unsupported operator for MarketapDate: $operator")
        }
    }

    private operator fun MarketapDate.compareTo(other: MarketapDate): Int {
        return when {
            this.year != other.year -> this.year.compareTo(other.year)
            this.month != other.month -> this.month.compareTo(other.month)
            else -> this.day.compareTo(other.day)
        }
    }

    private fun today(): MarketapDate {
        val calendar = Calendar.getInstance()
        return MarketapDate(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    private fun daysAgo(days: Int): MarketapDate {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -days)
        return MarketapDate(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    private fun daysFromNow(days: Int): MarketapDate {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, days)
        return MarketapDate(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }
}