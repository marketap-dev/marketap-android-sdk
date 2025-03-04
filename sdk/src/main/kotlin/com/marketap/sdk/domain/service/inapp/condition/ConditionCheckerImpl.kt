package com.marketap.sdk.domain.service.inapp.condition

import com.marketap.sdk.domain.service.inapp.condition.comparison.TypeOperationBuilder
import com.marketap.sdk.model.internal.inapp.Condition
import com.marketap.sdk.model.internal.inapp.EventPropertyCondition

class ConditionCheckerImpl : ConditionChecker {
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
                    checkPropertyCondition(
                        propertyCondition,
                        eventProperty,
                    )
                }
            }
        }
    }

    override fun checkPropertyCondition(
        eventPropertyCondition: EventPropertyCondition,
        eventProperty: Map<String, Any>?,
    ): Boolean {
        val comparator =
            TypeOperationBuilder(
                eventPropertyCondition.extractionStrategy.propertySchema.dataType,
                eventPropertyCondition.operator
            ).buildComparator()
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
                    *eventPropertyCondition.targetValues.toTypedArray()
                )
            }
        } catch (e: Exception) {
            false
        }
        return result
    }
}