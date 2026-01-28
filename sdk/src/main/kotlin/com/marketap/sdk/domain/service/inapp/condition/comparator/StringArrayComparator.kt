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
            TaxonomyOperator.CONTAINS -> targetValues.getSingleOrNull<String>()?.let { it in value }
                ?: false

            TaxonomyOperator.NOT_CONTAINS -> targetValues.getSingleOrNull<String>()
                ?.let { it !in value } ?: false

            TaxonomyOperator.ANY -> targetValues.getList<String>().any { it in value }
            TaxonomyOperator.NONE -> targetValues.getList<String>().none { it in value }

            TaxonomyOperator.ARRAY_LIKE -> {
                val targets = targetValues.getList<String>()
                targets.any { target ->
                    value.any { it.contains(target, ignoreCase = true) }
                }
            }

            TaxonomyOperator.ARRAY_NOT_LIKE -> {
                val targets = targetValues.getList<String>()
                targets.all { target ->
                    value.none { it.contains(target, ignoreCase = true) }
                }
            }

            else -> throw IllegalArgumentException("Unsupported operator for List<String>: $operator")
        }
    }
}