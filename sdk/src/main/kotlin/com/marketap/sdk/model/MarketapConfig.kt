package com.marketap.sdk.model

data class MarketapConfig(
    val projectId: String,
    val sdkType: String,
    val sdkVersion: String,
    val debug: Boolean = false
)