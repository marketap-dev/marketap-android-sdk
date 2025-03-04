package com.marketap.sdk.domain.service.inapp.condition.comparison

fun interface QueryGenerator {
    fun generateQuery(column: String, vararg targets: Any): String
}