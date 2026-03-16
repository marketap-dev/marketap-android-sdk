package com.marketap.sdk.model.external

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MarketapIntegrationInfo(
    val sdkType: String,
    val sdkVersion: String
)
