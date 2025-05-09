package com.marketap.sdk.model.internal.inapp

import com.squareup.moshi.JsonClass


@JsonClass(generateAdapter = true)
data class PropertySchema(
    val id: String,
    val name: String,
    val dataType: DataType,
    val path: Path? = null
)

