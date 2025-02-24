package com.marketap.sdk.service.inapp.comparison.types

import com.marketap.sdk.model.internal.inapp.DataType
import com.marketap.sdk.model.internal.inapp.TaxonomyOperator
import com.marketap.sdk.service.inapp.comparison.OperationBuilder
import com.marketap.sdk.service.inapp.comparison.QueryGenerator
import com.marketap.sdk.service.inapp.comparison.SourceComparator
import com.marketap.sdk.service.inapp.comparison.types.util.NOT_NULL_OPERATION
import com.marketap.sdk.service.inapp.comparison.types.util.NULL_OPERATION
import com.marketap.sdk.service.inapp.comparison.types.util.pairIntTargetOperator
import com.marketap.sdk.service.inapp.comparison.types.util.singleIntTargetOperator

class IntOperationBuilder(
    private val comparator: TaxonomyOperator
) : OperationBuilder {
    private val dataType = DataType.INT
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
                val useTarget = singleIntTargetOperator()
                Pair(
                    SourceComparator { source, targets ->
                        useTarget(targets) {
                            sourceTypeOperator<Int>(source) { safeSource ->
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
                val useTarget = singleIntTargetOperator()
                Pair(
                    SourceComparator { source, targets ->
                        useTarget(targets) {
                            sourceTypeOperator<Int>(source) { safeSource ->
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
                val useTarget = singleIntTargetOperator()
                Pair(
                    SourceComparator { source, targets ->
                        useTarget(targets) {
                            sourceTypeOperator<Int>(source) { safeSource ->
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
                val useTarget = singleIntTargetOperator()
                Pair(
                    SourceComparator { source, targets ->
                        useTarget(targets) {
                            sourceTypeOperator<Int>(source) { safeSource ->
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
                val useTarget = singleIntTargetOperator()
                Pair(
                    SourceComparator { source, targets ->
                        useTarget(targets) {
                            sourceTypeOperator<Int>(source) { safeSource ->
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
                val useTarget = singleIntTargetOperator()
                Pair(
                    SourceComparator { source, targets ->
                        useTarget(targets) {
                            sourceTypeOperator<Int>(source) { safeSource ->
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
                val useTarget = pairIntTargetOperator()
                Pair(
                    SourceComparator { source, targets ->
                        useTarget(targets) {
                            sourceTypeOperator<Int>(source) { safeSource ->
                                safeSource in it.first..it.second
                            }
                        }
                    },
                    QueryGenerator { column, targets ->
                        useTarget(targets) { "$column BETWEEN ${it.first} AND ${it.second}" }
                    }
                )
            }

            TaxonomyOperator.NOT_BETWEEN -> {
                val useTarget = pairIntTargetOperator()
                Pair(
                    SourceComparator { source, targets ->
                        useTarget(targets) {
                            sourceTypeOperator<Int>(source) { safeSource ->
                                safeSource !in it.first..it.second
                            }
                        }
                    },
                    QueryGenerator { column, targets ->
                        useTarget(targets) { "$column NOT BETWEEN ${it.first} AND ${it.second}" }
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