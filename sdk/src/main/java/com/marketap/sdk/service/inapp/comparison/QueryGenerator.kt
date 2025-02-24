package com.marketap.sdk.service.inapp.comparison

fun interface QueryGenerator {
    fun generateQuery(column: String, vararg targets: Any): String
}