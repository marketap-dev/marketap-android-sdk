package com.marketap.sdk.domain.service.inapp.condition

import com.marketap.sdk.model.internal.inapp.EventPropertyCondition

interface PropertyConditionChecker {
    fun check(
        eventPropertyCondition: EventPropertyCondition,
        eventProperty: Map<String, Any>?,
    ): Boolean
}