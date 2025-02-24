package com.marketap.sdk.service.inapp.comparison.types

import com.marketap.sdk.model.internal.inapp.DataType
import com.marketap.sdk.model.internal.inapp.TaxonomyOperator
import com.marketap.sdk.service.inapp.comparison.OperationBuilder
import com.marketap.sdk.service.inapp.comparison.QueryGenerator
import com.marketap.sdk.service.inapp.comparison.SourceComparator
import com.marketap.sdk.service.inapp.comparison.types.util.NOT_NULL_OPERATION
import com.marketap.sdk.service.inapp.comparison.types.util.NULL_OPERATION

class ObjectArrayOperationBuilder(
    private val comparator: TaxonomyOperator
) : OperationBuilder {
    private val dataType = DataType.OBJECT_ARRAY
    private val sourceTypeOperator = SourceTypeOperator(dataType)

    private fun build(): Pair<SourceComparator, QueryGenerator> {
        val res = when (comparator) {
            TaxonomyOperator.IS_NULL -> {
                NULL_OPERATION
            }

            TaxonomyOperator.IS_NOT_NULL -> {
                NOT_NULL_OPERATION
            }

            else -> throw UnsupportedOperationException("Unsupported operator for $comparator")
        }

        return res
    }

    override fun buildComparator(): SourceComparator {
        return build().first
    }

    override fun buildQueryGenerator(): QueryGenerator {
        return build().second
    }
}