package com.marketap.sdk.domain.service.inapp.condition.comparator

import com.marketap.sdk.domain.service.inapp.condition.comparator.TypeConverter.getList
import com.marketap.sdk.domain.service.inapp.condition.comparator.TypeConverter.getPairOrNull
import com.marketap.sdk.domain.service.inapp.condition.comparator.TypeConverter.getSingleOrNull
import com.marketap.sdk.model.internal.inapp.TaxonomyOperator
import com.marketap.sdk.utils.MarketapDate

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
            TaxonomyOperator.IS_NULL -> false // MarketapDate는 null이 될 수 없으므로 항상 false
            TaxonomyOperator.IS_NOT_NULL -> true // MarketapDate는 항상 존재함

            // 날짜 관련 연산자
            TaxonomyOperator.YEAR_EQUAL -> targetValues.getSingleOrNull<Int>()
                ?.let { value.year == it } ?: false

            TaxonomyOperator.MONTH_EQUAL -> targetValues.getSingleOrNull<Int>()
                ?.let { value.month == it } ?: false

            TaxonomyOperator.YEAR_MONTH_EQUAL -> {
                val target = targetValues.getSingleOrNull<String>()
                val (year, month) = target?.split("-")?.map { it.toInt() } ?: return false
                value.year == year && value.month == month
            }

            else -> throw IllegalArgumentException("Unsupported operator for MarketapDate: $operator")
        }
    }

    // MarketapDate 비교 연산을 위해 Comparable 인터페이스를 구현
    private operator fun MarketapDate.compareTo(other: MarketapDate): Int {
        return when {
            this.year != other.year -> this.year.compareTo(other.year)
            this.month != other.month -> this.month.compareTo(other.month)
            else -> this.day.compareTo(other.day)
        }
    }
}