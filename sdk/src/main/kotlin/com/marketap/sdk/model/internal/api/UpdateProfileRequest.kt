package com.marketap.sdk.model.internal.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class UpdateProfileRequest(
    @Json(name = "user_id")
    val userId: String,

    val properties: Map<String, Any> = emptyMap(),
    val device: DeviceReq,
    var timestamp: String? = null,
) : IngestRequest