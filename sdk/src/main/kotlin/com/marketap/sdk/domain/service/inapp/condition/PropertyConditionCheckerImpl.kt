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
            // Extract the value from the event
            // Items에 포함된 값들은 배열로 반환
            // 배열의 각 값들을 비교하여 하나라도 일치하면 true 반환
            // 나중에 로직 변경시 여기 수정
            EventExtractor.extract(
                eventProperty,
                eventPropertyCondition.extractionStrategy
            ).any { value ->
                comparator.compare(
                    value,
                    eventPropertyCondition.extractionStrategy.propertySchema.dataType,
                    eventPropertyCondition.targetValues,
                    eventPropertyCondition.operator
                )
            }
        } catch (e: Exception) {
            logger.e(
                "Error checking property condition",
                "Property: ${eventPropertyCondition.extractionStrategy.propertySchema.name}, " +
                        "Operator: ${eventPropertyCondition.operator}, " +
                        "Target Values: ${eventPropertyCondition.targetValues}",
                exception = e
            )
            false
        }
        return result
    }
}