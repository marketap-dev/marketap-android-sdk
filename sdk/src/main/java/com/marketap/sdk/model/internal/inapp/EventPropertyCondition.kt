package com.marketap.sdk.model.internal.inapp


data class EventPropertyCondition(

    val extractionStrategy: ExtractionStrategy,

    val operator: TaxonomyOperator,
    val targetValues: List<Any>
)


