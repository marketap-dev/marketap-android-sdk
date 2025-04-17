package com.marketap.sdk.model.internal.api

import com.google.gson.annotations.SerializedName


internal data class UpdateProfileRequest(
    @SerializedName("user_id")
    val userId: String,
    val properties: Map<String, Any> = emptyMap(),
    val device: DeviceReq
) : IngestRequest