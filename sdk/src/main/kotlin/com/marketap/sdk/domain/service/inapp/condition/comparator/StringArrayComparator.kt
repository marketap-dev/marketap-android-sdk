package com.marketap.sdk.domain.service.inapp.condition.comparator

import com.marketap.sdk.domain.service.inapp.condition.comparator.TypeConverter.getList
import com.marketap.sdk.domain.service.inapp.condition.comparator.TypeConverter.getSingleOrNull
import com.marketap.sdk.model.internal.inapp.TaxonomyOperator

class StringArrayComparator : TypeSafeComparator<List<String>> {
    override fun compare(
        value: List<String>,
        targetValues: List<Any>,
        operator: TaxonomyOperator
    ): Boolean {
        return when (operator) {
            // `value`가 `targetValues` 리스트 중 하나라도 포함하면 true
            TaxonomyOperator.CONTAINS -> targetValues.getSingleOrNull<String>()?.let { it in value }
                ?: false

            TaxonomyOperator.NOT_CONTAINS -> targetValues.getSingleOrNull<String>()
                ?.let { it !in value } ?: false

            // `value` 리스트에 `targetValues`의 모든 요소가 포함되면 true
            TaxonomyOperator.ANY -> targetValues.getList<String>().any { it in value }
            TaxonomyOperator.NONE -> targetValues.getList<String>().none { it in value }

            else -> throw IllegalArgumentException("Unsupported operator for List<String>: $operator")
        }
    }
}