package com.marketap.sdk.service.inapp.comparison.types

import com.marketap.sdk.model.internal.inapp.DataType
import com.marketap.sdk.model.internal.inapp.TaxonomyOperator
import com.marketap.sdk.service.inapp.comparison.OperationBuilder
import com.marketap.sdk.service.inapp.comparison.QueryGenerator
import com.marketap.sdk.service.inapp.comparison.SourceComparator
import com.marketap.sdk.service.inapp.comparison.types.util.NOT_NULL_OPERATION
import com.marketap.sdk.service.inapp.comparison.types.util.NULL_OPERATION
import com.marketap.sdk.service.inapp.comparison.types.util.getRecentDays
import com.marketap.sdk.service.inapp.comparison.types.util.month
import com.marketap.sdk.service.inapp.comparison.types.util.pairDateTargetOperator
import com.marketap.sdk.service.inapp.comparison.types.util.pairTargetOperator
import com.marketap.sdk.service.inapp.comparison.types.util.singleDateTargetOperator
import com.marketap.sdk.service.inapp.comparison.types.util.singleIntTargetOperator
import com.marketap.sdk.service.inapp.comparison.types.util.toLocalDate
import com.marketap.sdk.service.inapp.comparison.types.util.year


class DateOperationBuilder(
    private val comparator: TaxonomyOperator
) : OperationBuilder {
    private val dataType = DataType.DATE
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
                val useTarget = singleDateTargetOperator()

                Pair(
                    SourceComparator { source, targets ->
                        useTarget(targets) {
                            sourceTypeOperator<String>(source) { safeSource ->
                                safeSource == it
                            }
                        }
                    },
                    QueryGenerator { column, targets ->
                        useTarget(targets) { "$column = DATE '$it'" }
                    }
                )
            }

            TaxonomyOperator.NOT_EQUAL -> {
                val useTarget = singleDateTargetOperator()

                Pair(
                    SourceComparator { source, targets ->
                        useTarget(targets) {
                            sourceTypeOperator<String>(source) { safeSource ->
                                safeSource != it
                            }
                        }
                    },
                    QueryGenerator { column, targets ->
                        useTarget(targets) { "$column = DATE '$it'" }
                    }
                )
            }

            TaxonomyOperator.GREATER_THAN -> {
                val useTarget = singleDateTargetOperator()

                Pair(
                    SourceComparator { source, targets ->
                        useTarget(targets) {
                            sourceTypeOperator<String>(source) { safeSource ->
                                safeSource > it
                            }
                        }
                    },
                    QueryGenerator { column, targets ->
                        useTarget(targets) { "$column > DATE '$targets'" }
                    }
                )
            }

            TaxonomyOperator.GREATER_THAN_OR_EQUAL -> {
                val useTarget = singleDateTargetOperator()

                Pair(
                    SourceComparator { source, targets ->
                        useTarget(targets) {
                            sourceTypeOperator<String>(source) { safeSource ->
                                safeSource >= it
                            }
                        }
                    },
                    QueryGenerator { column, targets ->
                        useTarget(targets) { "$column >= DATE '$it'" }
                    }
                )
            }

            TaxonomyOperator.LESS_THAN -> {
                val useTarget = singleDateTargetOperator()

                Pair(
                    SourceComparator { source, targets ->
                        useTarget(targets) {
                            sourceTypeOperator<String>(source) { safeSource ->
                                safeSource < it
                            }
                        }
                    },
                    QueryGenerator { column, targets ->
                        useTarget(targets) { "$column < DATE '$it'" }
                    }
                )
            }

            TaxonomyOperator.LESS_THAN_OR_EQUAL -> {
                val useTarget = singleDateTargetOperator()

                Pair(
                    SourceComparator { source, targets ->
                        useTarget(targets) {
                            sourceTypeOperator<String>(source) { safeSource ->
                                safeSource <= it
                            }
                        }
                    },
                    QueryGenerator { column, targets ->
                        useTarget(targets) { "$column <= DATE '$it'" }
                    }
                )
            }

            TaxonomyOperator.BETWEEN -> {
                val useTarget = pairDateTargetOperator()

                Pair(
                    SourceComparator { source, targets ->
                        useTarget(targets) {
                            sourceTypeOperator<String>(source) { safeSource ->
                                safeSource >= it.first && safeSource <= it.second
                            }
                        }
                    },
                    QueryGenerator { column, targets ->
                        useTarget(targets) { (start, end) ->
                            "$column between DATE '$start' and DATE '$end'"
                        }
                    }
                )
            }

            TaxonomyOperator.NOT_BETWEEN -> {
                val useTarget = pairDateTargetOperator()

                Pair(
                    SourceComparator { source, targets ->
                        useTarget(targets) {
                            sourceTypeOperator<String>(source) { safeSource ->
                                safeSource < it.first || safeSource > it.second
                            }
                        }
                    },
                    QueryGenerator { column, targets ->
                        useTarget(targets) { (start, end) ->
                            "$column not between DATE '$start' and DATE '$end'"
                        }
                    }
                )
            }

            TaxonomyOperator.YEAR_EQUAL -> {
                val useTarget = singleIntTargetOperator()
                Pair(
                    SourceComparator { source, targets ->
                        useTarget(targets) {
                            sourceTypeOperator<String>(source) { safeSource ->
                                safeSource.year == it
                            }
                        }
                    },
                    QueryGenerator { column, targets ->
                        useTarget(targets) { "YEAR($column) = $it" }
                    }
                )
            }

            TaxonomyOperator.MONTH_EQUAL -> {
                val useTarget = singleIntTargetOperator()
                Pair(
                    SourceComparator { source, targets ->
                        useTarget(targets) {
                            sourceTypeOperator<String>(source) { safeSource ->
                                safeSource.month == it
                            }
                        }
                    },
                    QueryGenerator { column, targets ->
                        useTarget(targets) { "MONTH($column) = $it" }
                    }
                )
            }

            TaxonomyOperator.YEAR_MONTH_EQUAL -> {
                val useTarget = pairTargetOperator<Int>()
                Pair(
                    SourceComparator { source, targets ->
                        useTarget(targets.toString().split("-")) {
                            sourceTypeOperator<String>(source) { safeSource ->
                                safeSource.year == it.first && safeSource.month == it.second
                            }
                        }
                    },
                    QueryGenerator { column, targets ->
                        useTarget(targets) { (year, month) ->
                            "YEAR($column) = $year and MONTH($column) = $month"
                        }
                    }
                )
            }

            TaxonomyOperator.BEFORE -> {
                val useTarget = singleIntTargetOperator()
                Pair(
                    SourceComparator { source, targets ->
                        useTarget(targets) {
                            sourceTypeOperator<String>(source) { safeSource ->
                                val (start, _) = it.getRecentDays()
                                start.toLocalDate() == safeSource
                            }
                        }
                    },
                    QueryGenerator { column, targets ->
                        useTarget(targets) { "date_diff('day', CURRENT_DATE, CAST($column AS DATE)) = $it" }
                    }
                )
            }

            TaxonomyOperator.PAST -> {
                val useTarget = singleIntTargetOperator()
                Pair(
                    SourceComparator { source, targets ->
                        useTarget(targets) {
                            sourceTypeOperator<String>(source) { safeSource ->
                                val (start, _) = it.getRecentDays()
                                safeSource < start.toLocalDate()
                            }
                        }
                    },
                    QueryGenerator { column, targets ->
                        useTarget(targets) { "date_diff('day', CURRENT_DATE, CAST($column AS DATE)) = $it" }
                    }
                )
            }

            TaxonomyOperator.WITHIN_PAST -> {
                val useTarget = singleIntTargetOperator()
                Pair(
                    SourceComparator { source, targets ->
                        useTarget(targets) {
                            sourceTypeOperator<String>(source) { safeSource ->
                                val (start, end) = it.getRecentDays()
                                safeSource in start.toLocalDate()..end.toLocalDate()
                            }
                        }
                    },
                    QueryGenerator { column, targets ->
                        useTarget(targets) { "date_diff('day', CURRENT_DATE, CAST($column AS DATE)) between 0 and $it" }
                    }
                )
            }

            TaxonomyOperator.AFTER -> {
                val useTarget = singleIntTargetOperator()
                Pair(
                    SourceComparator { source, targets ->
                        useTarget(targets) {
                            sourceTypeOperator<String>(source) { safeSource ->
                                val (_, end) = it.getRecentDays()
                                safeSource == end.toLocalDate()
                            }
                        }
                    },
                    QueryGenerator { column, targets ->
                        useTarget(targets) { "date_diff('day', CAST($column AS DATE), CURRENT_DATE) = $it" }
                    }
                )
            }

            TaxonomyOperator.REMAINING -> {
                val useTarget = singleIntTargetOperator()
                Pair(
                    SourceComparator { source, targets ->
                        useTarget(targets) {
                            sourceTypeOperator<String>(source) { safeSource ->
                                val (_, end) = it.getRecentDays()
                                safeSource > end.toLocalDate()
                            }
                        }
                    },
                    QueryGenerator { column, targets ->
                        useTarget(targets) { "date_diff('day', CAST($column AS DATE), CURRENT_DATE) = $it" }
                    }
                )
            }

            TaxonomyOperator.WITHIN_REMAINING -> {
                val useTarget = singleIntTargetOperator()
                Pair(
                    SourceComparator { source, targets ->
                        useTarget(targets) {
                            sourceTypeOperator<String>(source) { safeSource ->
                                val (start, end) = it.getRecentDays()
                                safeSource > start.toLocalDate() && safeSource < end.toLocalDate()
                            }
                        }
                    },
                    QueryGenerator { column, targets ->
                        useTarget(targets) { "date_diff('day', CAST($column AS DATE), CURRENT_DATE) between 0 and $it" }
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