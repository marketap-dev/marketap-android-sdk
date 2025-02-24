package com.marketap.sdk.service.inapp

import com.marketap.sdk.model.internal.inapp.Condition
import com.marketap.sdk.model.internal.inapp.EventPropertyCondition

internal interface ConditionChecker {
    fun checkCondition(
        condition: Condition,
        eventName: String,
        eventProperty: Map<String, Any>?,
    ): Boolean

    fun checkPropertyCondition(
        eventPropertyCondition: EventPropertyCondition,
        eventProperty: Map<String, Any>?,
    ): Boolean
}