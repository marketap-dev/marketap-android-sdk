package com.marketap.sdk.service.inapp.comparison


import com.marketap.sdk.model.internal.inapp.DataType
import com.marketap.sdk.model.internal.inapp.TaxonomyOperator
import com.marketap.sdk.service.inapp.comparison.types.BooleanOperationBuilder
import com.marketap.sdk.service.inapp.comparison.types.DateOperationBuilder
import com.marketap.sdk.service.inapp.comparison.types.DateTimeOperationBuilder
import com.marketap.sdk.service.inapp.comparison.types.DoubleOperationBuilder
import com.marketap.sdk.service.inapp.comparison.types.IntOperationBuilder
import com.marketap.sdk.service.inapp.comparison.types.ObjectArrayOperationBuilder
import com.marketap.sdk.service.inapp.comparison.types.StringArrayOperationBuilder
import com.marketap.sdk.service.inapp.comparison.types.StringOperationBuilder

class TypeOperationBuilder(
    private val dataType: DataType,
    private val comparator: TaxonomyOperator
) : OperationBuilder {
    private fun getBuilder(): OperationBuilder {
        return when (dataType) {
            DataType.STRING -> StringOperationBuilder(comparator)
            DataType.BOOLEAN -> BooleanOperationBuilder(comparator)
            DataType.DATE -> DateOperationBuilder(comparator)
            DataType.DATETIME -> DateTimeOperationBuilder(comparator)
            DataType.DOUBLE -> DoubleOperationBuilder(comparator)
            DataType.INT -> IntOperationBuilder(comparator)
            DataType.STRING_ARRAY -> StringArrayOperationBuilder(comparator)
            DataType.OBJECT_ARRAY -> ObjectArrayOperationBuilder(comparator)
            DataType.BIGINT -> IntOperationBuilder(comparator)
            DataType.OBJECT -> StringOperationBuilder(comparator)
        }
    }

    override fun buildComparator(): SourceComparator {
        return getBuilder().buildComparator()
    }

    override fun buildQueryGenerator(): QueryGenerator {
        return getBuilder().buildQueryGenerator()
    }
}