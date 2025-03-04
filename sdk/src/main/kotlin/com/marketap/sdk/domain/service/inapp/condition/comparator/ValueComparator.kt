package com.marketap.sdk.domain.service.inapp.condition.comparator

import com.marketap.sdk.model.internal.inapp.DataType
import com.marketap.sdk.model.internal.inapp.TaxonomyOperator

interface ValueComparator {
    fun compare(
        value: Any?,
        dataType: DataType,
        targetValues: List<Any>,
        operator: TaxonomyOperator
    ): Boolean
}