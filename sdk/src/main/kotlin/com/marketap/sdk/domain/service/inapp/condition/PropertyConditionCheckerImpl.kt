package com.marketap.sdk.domain.service.inapp.condition

import com.marketap.sdk.domain.service.inapp.condition.comparator.ValueComparator
import com.marketap.sdk.model.internal.inapp.EventPropertyCondition
import com.marketap.sdk.utils.logger

class PropertyConditionCheckerImpl(
    private val comparator: ValueComparator
) : PropertyConditionChecker {
    override fun check(
        eventPropertyCondition: EventPropertyCondition,
        eventProperty: Map<String, Any>?,
    ): Boolean {
        val result = try {
            val operator = eventPropertyCondition.operator
            val results = EventExtractor.extract(
                eventProperty,
                eventPropertyCondition.extractionStrategy
            ).map { value ->
                comparator.compare(
                    value,
                    eventPropertyCondition.extractionStrategy.propertySchema.dataType,
                    eventPropertyCondition.targetValues,
                    operator
                )
            }
            operator.aggregate(results)
        } catch (e: Exception) {
            logger.e(e) {
                "Error checking property condition, " +
                        "Property: ${eventPropertyCondition.extractionStrategy.propertySchema.name}, " +
                        "Operator: ${eventPropertyCondition.operator}, " +
                        "Target Values: ${eventPropertyCondition.targetValues}"
            }
            false
        }
        return result
    }
}