package com.marketap.sdk.service.inapp.comparison

fun interface SourceComparator {
    fun compare(source: Any?, vararg targets: Any): Boolean
}