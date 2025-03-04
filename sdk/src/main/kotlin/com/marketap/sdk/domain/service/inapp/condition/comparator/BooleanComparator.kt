package com.marketap.sdk.domain.service.inapp.condition.comparator

import com.marketap.sdk.domain.service.inapp.condition.comparator.TypeConverter.getList
import com.marketap.sdk.domain.service.inapp.condition.comparator.TypeConverter.getSingleOrNull
import com.marketap.sdk.model.internal.inapp.TaxonomyOperator

class BooleanComparator : TypeSafeComparator<Boolean> {
    override fun compare(
        value: Boolean,
        targetValues: List<Any>,
        operator: TaxonomyOperator
    ): Boolean {
        return when (operator) {
            TaxonomyOperator.EQUAL -> targetValues.getSingleOrNull<Boolean>()?.let { value == it }
                ?: false

            TaxonomyOperator.NOT_EQUAL -> targetValues.getSingleOrNull<Boolean>()
                ?.let { value != it } ?: false

            TaxonomyOperator.IN -> targetValues.getList<Boolean>().contains(value)
            TaxonomyOperator.NOT_IN -> !targetValues.getList<Boolean>().contains(value)
            TaxonomyOperator.IS_NULL -> false // Boolean 값은 null이 올 수 없으므로 항상 false
            TaxonomyOperator.IS_NOT_NULL -> true // Boolean 값은 항상 존재함
            else -> throw IllegalArgumentException("Unsupported operator for Boolean: $operator")
        }
    }
}