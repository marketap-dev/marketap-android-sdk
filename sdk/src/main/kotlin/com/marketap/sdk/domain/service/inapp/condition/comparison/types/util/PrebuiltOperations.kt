package com.marketap.sdk.domain.service.inapp.condition.comparison.types.util

import com.marketap.sdk.domain.service.inapp.condition.comparison.QueryGenerator
import com.marketap.sdk.domain.service.inapp.condition.comparison.SourceComparator

val NOT_NULL_OPERATION = run {
    val useTarget = noTargetOperator()
    Pair(
        SourceComparator { source, _ ->
            useTarget { source != null }
        },
        QueryGenerator { column, _ ->
            useTarget { "$column IS NOT NULL" }
        }
    )
}

val NULL_OPERATION = run {
    val useTarget = noTargetOperator()
    Pair(
        SourceComparator { source, _ ->
            useTarget { source == null }
        },
        QueryGenerator { column, _ ->
            useTarget { "$column IS NULL" }
        }
    )
}