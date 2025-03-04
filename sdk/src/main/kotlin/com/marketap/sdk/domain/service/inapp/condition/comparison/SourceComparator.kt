package com.marketap.sdk.domain.service.inapp.condition.comparison

fun interface SourceComparator {
    fun compare(source: Any?, vararg targets: Any): Boolean
}