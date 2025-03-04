package com.marketap.sdk.domain.service.inapp.condition.comparison.types

import com.marketap.sdk.model.internal.inapp.DataType
import com.marketap.sdk.model.internal.inapp.TaxonomyOperator
import com.marketap.sdk.domain.service.inapp.condition.comparison.OperationBuilder
import com.marketap.sdk.domain.service.inapp.condition.comparison.QueryGenerator
import com.marketap.sdk.domain.service.inapp.condition.comparison.SourceComparator
import com.marketap.sdk.domain.service.inapp.condition.comparison.types.util.NOT_NULL_OPERATION
import com.marketap.sdk.domain.service.inapp.condition.comparison.types.util.NULL_OPERATION
import com.marketap.sdk.domain.service.inapp.condition.comparison.types.util.pairDoubleTargetOperator
import com.marketap.sdk.domain.service.inapp.condition.comparison.types.util.singleDoubleTargetOperator

class DoubleOperationBuilder(
    private val comparator: TaxonomyOperator
) : OperationBuilder {
    private val dataType = DataType.DOUBLE
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
                val useTarget = singleDoubleTargetOperator()
                Pair(
                    SourceComparator { source, targets ->
                        useTarget(targets) {
                            sourceTypeOperator<Double>(source) { safeSource ->
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
                val useTarget = singleDoubleTargetOperator()
                Pair(
                    SourceComparator { source, targets ->
                        useTarget(targets) {
                            sourceTypeOperator<Double>(source) { safeSource ->
                                safeSource != it
                            }
                        }
                    },
                    QueryGenerator { column, targets ->
                        useTarget(targets) { "$column != $it" }
                    }
                )
            }

            TaxonomyOperator.GREATER_THAN -> {
                val useTarget = singleDoubleTargetOperator()
                Pair(
                    SourceComparator { source, targets ->
                        useTarget(targets) {
                            sourceTypeOperator<Double>(source) { safeSource ->
                                safeSource > it
                            }
                        }
                    },
                    QueryGenerator { column, targets ->
                        useTarget(targets) { "$column > $it" }
                    }
                )
            }

            TaxonomyOperator.GREATER_THAN_OR_EQUAL -> {
                val useTarget = singleDoubleTargetOperator()
                Pair(
                    SourceComparator { source, targets ->
                        useTarget(targets) {
                            sourceTypeOperator<Double>(source) { safeSource ->
                                safeSource >= it
                            }
                        }
                    },
                    QueryGenerator { column, targets ->
                        useTarget(targets) { "$column >= $it" }
                    }
                )
            }

            TaxonomyOperator.LESS_THAN -> {
                val useTarget = singleDoubleTargetOperator()
                Pair(
                    SourceComparator { source, targets ->
                        useTarget(targets) {
                            sourceTypeOperator<Double>(source) { safeSource ->
                                safeSource < it
                            }
                        }
                    },
                    QueryGenerator { column, targets ->
                        useTarget(targets) { "$column < $it" }
                    }
                )
            }

            TaxonomyOperator.LESS_THAN_OR_EQUAL -> {
                val useTarget = singleDoubleTargetOperator()
                Pair(
                    SourceComparator { source, targets ->
                        useTarget(targets) {
                            sourceTypeOperator<Double>(source) { safeSource ->
                                safeSource <= it
                            }
                        }
                    },
                    QueryGenerator { column, targets ->
                        useTarget(targets) { "$column <= $it" }
                    }
                )
            }

            TaxonomyOperator.BETWEEN -> {
                val useTarget = pairDoubleTargetOperator()
                Pair(
                    SourceComparator { source, targets ->
                        useTarget(targets) {
                            sourceTypeOperator<Double>(source) { safeSource ->
                                val target1 = it.first
                                val target2 = it.second
                                safeSource in target1..target2
                            }
                        }
                    },
                    QueryGenerator { column, targets ->
                        useTarget(targets) {
                            val target1 = it.first
                            val target2 = it.second
                            "$column BETWEEN $target1 AND $target2"
                        }
                    }
                )
            }

            TaxonomyOperator.NOT_BETWEEN -> {
                val useTarget = pairDoubleTargetOperator()
                Pair(
                    SourceComparator { source, targets ->
                        useTarget(targets) {
                            sourceTypeOperator<Double>(source) { safeSource ->
                                val target1 = it.first
                                val target2 = it.second
                                safeSource !in target1..target2
                            }
                        }
                    },
                    QueryGenerator { column, targets ->
                        useTarget(targets) {
                            val target1 = it.first
                            val target2 = it.second
                            "$column NOT BETWEEN $target1 AND $target2"
                        }
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