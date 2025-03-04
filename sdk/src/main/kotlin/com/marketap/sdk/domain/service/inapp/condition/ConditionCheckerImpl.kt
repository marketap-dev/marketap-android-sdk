package com.marketap.sdk.domain.service.inapp.condition

import com.marketap.sdk.model.internal.inapp.Condition

class ConditionCheckerImpl(
    private val propertyChecker: PropertyConditionChecker
) : ConditionChecker {
    override fun checkCondition(
        condition: Condition,
        eventName: String,
        eventProperty: Map<String, Any>?,
    ): Boolean {
        return if (condition.eventFilter.eventName != eventName) {
            false
        } else {
            condition.propertyConditions.isNullOrEmpty() || condition.propertyConditions.any { row ->
                row.all { propertyCondition ->
                    propertyChecker.check(
                        propertyCondition,
                        eventProperty,
                    )
                }
            }
        }
    }
}