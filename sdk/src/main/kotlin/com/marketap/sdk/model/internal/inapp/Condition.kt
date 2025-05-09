package com.marketap.sdk.model.internal.inapp

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Condition(
    val eventFilter: EventFilter,

    val propertyConditions: List<List<EventPropertyCondition>>? = null
)

