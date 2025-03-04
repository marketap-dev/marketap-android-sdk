package com.marketap.sdk.domain.service.inapp.condition

import com.marketap.sdk.model.internal.inapp.Condition

internal interface ConditionChecker {
    fun checkCondition(
        condition: Condition,
        eventName: String,
        eventProperty: Map<String, Any>?,
    ): Boolean
}