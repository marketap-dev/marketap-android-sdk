package com.marketap.sdk.model.internal.inapp

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ExtractionStrategy(
    val propertySchema: PropertySchema,
)
