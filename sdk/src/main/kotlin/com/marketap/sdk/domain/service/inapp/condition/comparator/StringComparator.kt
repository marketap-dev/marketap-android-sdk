package com.marketap.sdk.domain.service.inapp.condition.comparator

import com.marketap.sdk.domain.service.inapp.condition.comparator.TypeConverter.getList
import com.marketap.sdk.domain.service.inapp.condition.comparator.TypeConverter.getSingleOrNull
import com.marketap.sdk.model.internal.inapp.TaxonomyOperator

class StringComparator : TypeSafeComparator<String> {
    override fun compare(
        value: String,
        targetValues: List<Any>,
        operator: TaxonomyOperator
    ): Boolean {
        return when (operator) {
            TaxonomyOperator.EQUAL -> targetValues.getSingleOrNull<String>()?.let { value == it }
                ?: false

            TaxonomyOperator.NOT_EQUAL -> targetValues.getSingleOrNull<String>()
                ?.let { value != it } ?: false

            TaxonomyOperator.IN -> targetValues.getList<String>().contains(value)
            TaxonomyOperator.NOT_IN -> !targetValues.getList<String>().contains(value)

            TaxonomyOperator.LIKE -> targetValues.getSingleOrNull<String>()
                ?.let { value.contains(it, ignoreCase = true) } ?: false

            TaxonomyOperator.NOT_LIKE -> targetValues.getSingleOrNull<String>()
                ?.let { !value.contains(it, ignoreCase = true) } ?: false

            TaxonomyOperator.CONTAINS -> targetValues.getSingleOrNull<String>()
                ?.let { value.contains(it) } ?: false

            TaxonomyOperator.NOT_CONTAINS -> targetValues.getSingleOrNull<String>()
                ?.let { !value.contains(it) } ?: false

            TaxonomyOperator.IS_NULL -> false // String은 null이 될 수 없으므로 항상 false
            TaxonomyOperator.IS_NOT_NULL -> true // String은 항상 존재함

            else -> throw IllegalArgumentException("Unsupported operator for String: $operator")
        }
    }
}