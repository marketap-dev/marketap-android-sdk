package com.marketap.sdk.domain.service.inapp.condition.comparator

import com.marketap.sdk.domain.service.inapp.condition.comparator.TypeConverter.getList
import com.marketap.sdk.domain.service.inapp.condition.comparator.TypeConverter.getPairOrNull
import com.marketap.sdk.domain.service.inapp.condition.comparator.TypeConverter.getSingleOrNull
import com.marketap.sdk.model.internal.inapp.TaxonomyOperator

class DoubleComparator : TypeSafeComparator<Double> {
    override fun compare(
        value: Double,
        targetValues: List<Any>,
        operator: TaxonomyOperator
    ): Boolean {
        return when (operator) {
            TaxonomyOperator.EQUAL -> targetValues.getSingleOrNull<Double>()?.let { value == it }
                ?: false

            TaxonomyOperator.NOT_EQUAL -> targetValues.getSingleOrNull<Double>()
                ?.let { value != it } ?: false

            TaxonomyOperator.GREATER_THAN -> targetValues.getSingleOrNull<Double>()
                ?.let { value > it } ?: false

            TaxonomyOperator.GREATER_THAN_OR_EQUAL -> targetValues.getSingleOrNull<Double>()
                ?.let { value >= it } ?: false

            TaxonomyOperator.LESS_THAN -> targetValues.getSingleOrNull<Double>()?.let { value < it }
                ?: false

            TaxonomyOperator.LESS_THAN_OR_EQUAL -> targetValues.getSingleOrNull<Double>()
                ?.let { value <= it } ?: false

            TaxonomyOperator.BETWEEN -> targetValues.getPairOrNull<Double>()
                ?.let { (start, end) -> value > start && value < end } ?: false

            TaxonomyOperator.NOT_BETWEEN -> targetValues.getPairOrNull<Double>()
                ?.let { (start, end) -> value <= start || value >= end } ?: false

            TaxonomyOperator.IN -> targetValues.getList<Double>().contains(value)
            TaxonomyOperator.NOT_IN -> !targetValues.getList<Double>().contains(value)
            TaxonomyOperator.IS_NULL -> false // Double 값은 null이 올 수 없으므로 항상 false
            TaxonomyOperator.IS_NOT_NULL -> true // Double 값은 항상 존재함
            else -> throw IllegalArgumentException("Unsupported operator for Double: $operator")
        }
    }
}