package com.marketap.sdk.service.inapp.comparison.types

import com.marketap.sdk.model.internal.inapp.DataType
import com.marketap.sdk.model.internal.inapp.TaxonomyOperator
import com.marketap.sdk.service.inapp.comparison.OperationBuilder
import com.marketap.sdk.service.inapp.comparison.QueryGenerator
import com.marketap.sdk.service.inapp.comparison.SourceComparator
import com.marketap.sdk.service.inapp.comparison.types.util.NOT_NULL_OPERATION
import com.marketap.sdk.service.inapp.comparison.types.util.NULL_OPERATION
import com.marketap.sdk.service.inapp.comparison.types.util.getRecentDays
import com.marketap.sdk.service.inapp.comparison.types.util.pairTargetOperator
import com.marketap.sdk.service.inapp.comparison.types.util.singleIntTargetOperator
import com.marketap.sdk.service.inapp.comparison.types.util.singleTargetOperator
import java.util.Date

class DateTimeOperationBuilder(
    private val comparator: TaxonomyOperator
) : OperationBuilder {
    private val dataType = DataType.DATETIME
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
                val useTarget = singleTargetOperator<Date>()
                val sqlTarget = singleTargetOperator<String>()
                Pair(
                    SourceComparator { source, targets ->
                        useTarget(targets) {
                            sourceTypeOperator<Date>(source) { safeSource ->
                                safeSource == it
                            }
                        }
                    },
                    QueryGenerator { column, targets ->
                        sqlTarget(targets) { "$column = TIMESTAMP '${it.format()}'" }
                    }
                )
            }

            TaxonomyOperator.NOT_EQUAL -> {
                val useTarget = singleTargetOperator<Date>()
                val sqlTarget = singleTargetOperator<String>()
                Pair(
                    SourceComparator { source, targets ->
                        useTarget(targets) {
                            sourceTypeOperator<Date>(source) { safeSource ->
                                safeSource != it
                            }
                        }
                    },
                    QueryGenerator { column, targets ->
                        sqlTarget(targets) { "$column != TIMESTAMP '${it.format()}'" }
                    }
                )
            }

            TaxonomyOperator.GREATER_THAN -> {
                val useTarget = singleTargetOperator<Date>()
                val sqlTarget = singleTargetOperator<String>()
                Pair(
                    SourceComparator { source, targets ->
                        useTarget(targets) {
                            sourceTypeOperator<Date>(source) { safeSource ->
                                safeSource > it
                            }
                        }
                    },
                    QueryGenerator { column, targets ->
                        sqlTarget(targets) { "$column > TIMESTAMP '${it.format()}'" }
                    }
                )
            }

            TaxonomyOperator.GREATER_THAN_OR_EQUAL -> {
                val useTarget = singleTargetOperator<Date>()
                val sqlTarget = singleTargetOperator<String>()
                Pair(
                    SourceComparator { source, targets ->
                        useTarget(targets) {
                            sourceTypeOperator<Date>(source) { safeSource ->
                                safeSource >= it
                            }
                        }
                    },
                    QueryGenerator { column, targets ->
                        sqlTarget(targets) { "$column >= TIMESTAMP '${it.format()}'" }
                    }
                )
            }

            TaxonomyOperator.LESS_THAN -> {
                val useTarget = singleTargetOperator<Date>()
                val sqlTarget = singleTargetOperator<String>()
                Pair(
                    SourceComparator { source, targets ->
                        useTarget(targets) {
                            sourceTypeOperator<Date>(source) { safeSource ->
                                safeSource < it
                            }
                        }
                    },
                    QueryGenerator { column, targets ->
                        sqlTarget(targets) { "$column < TIMESTAMP '${it.format()}'" }
                    }
                )
            }

            TaxonomyOperator.LESS_THAN_OR_EQUAL -> {
                val useTarget = singleTargetOperator<Date>()
                val sqlTarget = singleTargetOperator<String>()
                Pair(
                    SourceComparator { source, targets ->
                        useTarget(targets) {
                            sourceTypeOperator<Date>(source) { safeSource ->
                                safeSource <= it
                            }
                        }
                    },
                    QueryGenerator { column, targets ->
                        sqlTarget(targets) { "$column <= TIMESTAMP '${it.format()}'" }
                    }
                )
            }

            TaxonomyOperator.BETWEEN -> {
                val useTarget = pairTargetOperator<Date>()
                val sqlTarget = pairTargetOperator<String>()
                Pair(
                    SourceComparator { source, targets ->
                        useTarget(targets) {
                            sourceTypeOperator<Date>(source) { safeSource ->
                                val (start, end) = it
                                safeSource in start..end
                            }
                        }
                    },
                    QueryGenerator { column, targets ->
                        sqlTarget(targets) { "$column BETWEEN TIMESTAMP '${it.first.format()}' AND TIMESTAMP '${it.second.format()}'" }
                    }
                )
            }

            TaxonomyOperator.NOT_BETWEEN -> {
                val useTarget = pairTargetOperator<Date>()
                val sqlTarget = pairTargetOperator<String>()
                Pair(
                    SourceComparator { source, targets ->
                        useTarget(targets) {
                            sourceTypeOperator<Date>(source) { safeSource ->
                                val (start, end) = it
                                safeSource < start || safeSource > end
                            }
                        }
                    },
                    QueryGenerator { column, targets ->
                        sqlTarget(targets) { "$column NOT BETWEEN TIMESTAMP '${it.first.format()}' AND TIMESTAMP '${it.second.format()}'" }
                    }
                )
            }

            TaxonomyOperator.BEFORE -> {
                val useTarget = singleIntTargetOperator()
                Pair(
                    SourceComparator { source, targets ->
                        useTarget(targets) {
                            sourceTypeOperator<Date>(source) { safeSource ->
                                val (start, _) = it.getRecentDays()
                                safeSource == start
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
                            sourceTypeOperator<Date>(source) { safeSource ->
                                val (start, _) = it.getRecentDays()
                                safeSource < start
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
                            sourceTypeOperator<Date>(source) { safeSource ->
                                val (start, end) = it.getRecentDays()
                                safeSource > start && safeSource < end
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
                            sourceTypeOperator<Date>(source) { safeSource ->
                                val (_, start) = it.getRecentDays().toList().sorted()
                                safeSource > start
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
                            sourceTypeOperator<Date>(source) { safeSource ->
                                val (_, end) = (-it).getRecentDays().toList().sorted()
                                safeSource > end
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
                            sourceTypeOperator<Date>(source) { safeSource ->
                                val (start, end) = (-it).getRecentDays().toList().sorted()
                                safeSource > start && safeSource < end
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

    private fun String.format(): String { // TODO
        return when {
            this.contains("T") && this.endsWith("Z") -> {
                TODO()
                ""
            }

            this.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}(\\.\\d{3})?")) -> {
                this
            }

            else -> throw IllegalArgumentException("지원하지 않는 날짜 형식입니다: $this")
        }
    }
}