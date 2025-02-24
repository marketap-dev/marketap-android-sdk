package com.marketap.sdk.service.inapp.comparison

interface OperationBuilder {
    fun buildComparator(): SourceComparator

    fun buildQueryGenerator(): QueryGenerator
}