package com.marketap.sdk.service.inapp.comparison

fun interface EventConditionChecker {
    suspend fun check(eventName: String, eventProperty: Map<String, Any>?): Boolean
}