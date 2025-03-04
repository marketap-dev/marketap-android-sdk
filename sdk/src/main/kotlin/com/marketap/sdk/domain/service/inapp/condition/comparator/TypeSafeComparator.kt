package com.marketap.sdk.domain.service.inapp.condition.comparator

import com.marketap.sdk.model.internal.inapp.TaxonomyOperator

interface TypeSafeComparator<T> {
    fun compare(
        value: T,
        targetValues: List<Any>,
        operator: TaxonomyOperator
    ): Boolean
}