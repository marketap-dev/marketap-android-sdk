package com.marketap.sdk.model.internal.inapp

import com.squareup.moshi.JsonClass


@JsonClass(generateAdapter = true)
data class EventTriggerCondition(
    val condition: Condition,

    val frequencyCap: FrequencyCap? = null,

    val delayMinutes: Int? = null
)


