package com.marketap.sdk.model.internal.inapp


data class EventTriggerCondition(
    val condition: Condition,

    val frequencyCap: FrequencyCap? = null,

    val delayMinutes: Int? = null
)


