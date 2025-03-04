package com.marketap.sdk.model.internal.bridge

data class BridgeEventReq(
    val eventName: String,
    val eventProperties: Map<String, Any>
)
