package com.marketap.sdk.domain.service.inapp.condition.comparison

fun interface EventConditionChecker {
    suspend fun check(eventName: String, eventProperty: Map<String, Any>?): Boolean
}