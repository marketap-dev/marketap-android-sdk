package com.marketap.sdk.domain.service.inapp.condition.comparison.types

import com.marketap.sdk.model.internal.inapp.DataType
import com.marketap.sdk.model.internal.inapp.TaxonomyOperator
import com.marketap.sdk.domain.service.inapp.condition.comparison.OperationBuilder
import com.marketap.sdk.domain.service.inapp.condition.comparison.QueryGenerator
import com.marketap.sdk.domain.service.inapp.condition.comparison.SourceComparator
import com.marketap.sdk.domain.service.inapp.condition.comparison.types.util.NOT_NULL_OPERATION
import com.marketap.sdk.domain.service.inapp.condition.comparison.types.util.NULL_OPERATION
import com.marketap.sdk.domain.service.inapp.condition.comparison.types.util.singleTargetOperator

class BooleanOperationBuilder(
    private val comparator: TaxonomyOperator
) : OperationBuilder {
    private val dataType = DataType.BOOLEAN
    private val sourceTypeOperator = SourceTypeOperator(dataType)

    private fun build(): Pair<SourceComparator, QueryGenerator> {
        val res = when (comparator) {
            TaxonomyOperator.IS_NULL -> {
                NULL_OPERATION
            }

            TaxonomyOperator.IS_NOT_NULL -> {
                NOT_NULL_OPERATION
            }

            TaxonomyOperator.EQUAL -> {
                val useTarget = singleTargetOperator<Boolean>()

                Pair(
                    SourceComparator { source, targets ->
                        useTarget(targets) {
                            sourceTypeOperator<Boolean>(source) { safeSource ->
                                safeSource == it
                            }
                        }
                    },
                    QueryGenerator { column, targets ->
                        useTarget(targets) { "$column = $it" }
                    }
                )
            }

            TaxonomyOperator.NOT_EQUAL -> {
                val useTarget = singleTargetOperator<Boolean>()

                Pair(
                    SourceComparator { source, targets ->
                        useTarget(targets) {
                            sourceTypeOperator<Boolean>(source) { safeSource ->
                                safeSource != it
                            }
                        }
                    },
                    QueryGenerator { column, targets ->
                        useTarget(targets) { "$column != $it" }
                    }
                )
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