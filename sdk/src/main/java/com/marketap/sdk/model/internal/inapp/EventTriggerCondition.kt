package com.marketap.sdk.model.internal.inapp

import com.google.gson.annotations.SerializedName


data class EventTriggerCondition(
    val condition: Condition,

    @SerializedName("frequency_cap")
    val frequencyCap: FrequencyCap? = null,

    @SerializedName("delay_minutes")
    val delayMinutes: Int? = null
)


