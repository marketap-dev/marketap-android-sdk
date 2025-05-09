package com.marketap.sdk.model.internal.inapp

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class EventPropertyCondition(

    val extractionStrategy: ExtractionStrategy,

    val operator: TaxonomyOperator,
    val targetValues: List<Any>
)


