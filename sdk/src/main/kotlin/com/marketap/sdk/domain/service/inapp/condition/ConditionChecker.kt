package com.marketap.sdk.domain.service.inapp.condition

import com.marketap.sdk.model.internal.inapp.Condition
import com.marketap.sdk.model.internal.inapp.EventTriggerCondition
import com.marketap.sdk.model.internal.inapp.effectiveConditions

internal interface ConditionChecker {
    fun checkCondition(
        condition: Condition,
        eventName: String,
        eventProperty: Map<String, Any>?,
    ): Boolean
}

internal fun ConditionChecker.matchesTrigger(
    trigger: EventTriggerCondition,
    eventName: String,
    eventProperty: Map<String, Any>?,
): Boolean = trigger.effectiveConditions.any { checkCondition(it, eventName, eventProperty) }
