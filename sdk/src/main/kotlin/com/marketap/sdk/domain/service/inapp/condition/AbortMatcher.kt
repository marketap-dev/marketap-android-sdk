package com.marketap.sdk.domain.service.inapp.condition

import com.marketap.sdk.model.internal.api.IngestEventRequest
import com.marketap.sdk.model.internal.inapp.Condition

internal fun ConditionChecker.matchesAbort(
    abortConditions: List<Condition>?,
    event: IngestEventRequest,
): Boolean = abortConditions.orEmpty().any { checkCondition(it, event.name, event.properties) }
