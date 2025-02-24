package com.marketap.sdk.model.internal.inapp



data class PropertySchema(
    val id: String,
    val name: String,
    val dataType: DataType,
    val path: Path? = null
)

