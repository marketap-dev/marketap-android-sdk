package com.marketap.sdk.domain.service.inapp.condition.comparison

interface OperationBuilder {
    fun buildComparator(): SourceComparator

    fun buildQueryGenerator(): QueryGenerator
}