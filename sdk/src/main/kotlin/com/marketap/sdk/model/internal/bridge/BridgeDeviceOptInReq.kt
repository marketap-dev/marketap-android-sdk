package com.marketap.sdk.model.internal.bridge

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class BridgeDeviceOptInReq(
    val optIn: Boolean?
)
