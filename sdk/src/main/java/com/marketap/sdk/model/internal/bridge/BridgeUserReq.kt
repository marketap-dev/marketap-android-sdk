package com.marketap.sdk.model.internal.bridge

data class BridgeUserReq(
    val userId: String,
    val userProperties: Map<String, Any>
)