package com.marketap.sdk.model.internal.bridge

import com.squareup.moshi.JsonClass


@JsonClass(generateAdapter = true)
data class BridgeUserReq(
    val userId: String,
    val userProperties: Map<String, Any>
)