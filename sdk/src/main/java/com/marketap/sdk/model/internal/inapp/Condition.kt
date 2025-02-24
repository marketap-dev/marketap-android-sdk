package com.marketap.sdk.model.internal.inapp

data class Condition(
    val eventFilter: EventFilter,

    val propertyConditions: List<List<EventPropertyCondition>>? = null
)

