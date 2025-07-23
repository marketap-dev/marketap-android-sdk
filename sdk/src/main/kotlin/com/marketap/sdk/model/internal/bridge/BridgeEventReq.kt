package com.marketap.sdk.model.internal.bridge

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BridgeEventReq(
    val eventName: String,
    val eventProperties: Map<String, Any>
)
