package com.marketap.sdk.model.internal.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class IngestRes(
    @Json(name = "is_success")
    val isSuccess: Boolean
)