package com.marketap.sdk.model.internal.inapp

import com.squareup.moshi.JsonClass


@JsonClass(generateAdapter = true)
data class EventTriggerCondition(
    val condition: Condition,

    /** 하나라도 이벤트에 맞으면 트리거된다. 없으면(구 응답·캐시) [condition] 하나로 판정한다. */
    val conditions: List<Condition>? = null,

    val frequencyCap: FrequencyCap? = null,

    val delayMinutes: Int? = null,

    val delaySeconds: Int? = null,

    val occurrenceCondition: OccurrenceCondition? = null,

    val abortConditions: List<Condition>? = null,

    val sendableTimeWindow: SendableTimeWindow? = null,

    /** 있으면 단계를 차례로 거친 뒤의 트리거 이벤트만 도달로 본다. */
    val flow: EventFlow? = null,
)

internal val EventTriggerCondition.effectiveConditions: List<Condition>
    get() = conditions?.takeIf { it.isNotEmpty() } ?: listOf(condition)
