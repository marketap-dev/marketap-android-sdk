package com.marketap.sdk.domain.service.inapp.condition.comparator

import com.marketap.sdk.domain.service.inapp.condition.comparator.TypeConverter.getList
import com.marketap.sdk.domain.service.inapp.condition.comparator.TypeConverter.getPairOrNull
import com.marketap.sdk.domain.service.inapp.condition.comparator.TypeConverter.getSingleOrNull
import com.marketap.sdk.model.internal.inapp.TaxonomyOperator

class LongComparator : TypeSafeComparator<Long> {
    override fun compare(
        value: Long,
        targetValues: List<Any>,
        operator: TaxonomyOperator
    ): Boolean {
        return when (operator) {
            TaxonomyOperator.EQUAL -> targetValues.getSingleOrNull<Long>()?.let { value == it }
                ?: false

            TaxonomyOperator.NOT_EQUAL -> targetValues.getSingleOrNull<Long>()?.let { value != it }
                ?: false

            TaxonomyOperator.GREATER_THAN -> targetValues.getSingleOrNull<Long>()
                ?.let { value > it } ?: false

            TaxonomyOperator.GREATER_THAN_OR_EQUAL -> targetValues.getSingleOrNull<Long>()
                ?.let { value >= it } ?: false

            TaxonomyOperator.LESS_THAN -> targetValues.getSingleOrNull<Long>()?.let { value < it }
                ?: false

            TaxonomyOperator.LESS_THAN_OR_EQUAL -> targetValues.getSingleOrNull<Long>()
                ?.let { value <= it } ?: false

            TaxonomyOperator.BETWEEN -> targetValues.getPairOrNull<Long>()
                ?.let { (start, end) -> value in (start + 1) until end } ?: false

            TaxonomyOperator.NOT_BETWEEN -> targetValues.getPairOrNull<Long>()
                ?.let { (start, end) -> value <= start || value >= end } ?: false

            TaxonomyOperator.IN -> targetValues.getList<Long>().contains(value)
            TaxonomyOperator.NOT_IN -> !targetValues.getList<Long>().contains(value)
            TaxonomyOperator.IS_NULL -> false // Long은 null이 될 수 없으므로 항상 false
            TaxonomyOperator.IS_NOT_NULL -> true // Long은 항상 존재함
            else -> throw IllegalArgumentException("Unsupported operator for Long: $operator")
        }
    }
}