package com.marketap.sdk.model.internal.bridge

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class InAppImpressionParams(
    val campaignId: String,
    val messageId: String
)

@JsonClass(generateAdapter = true)
internal data class InAppClickParams(
    val campaignId: String,
    val messageId: String,
    val locationId: String,
    val url: String? = null
)

@JsonClass(generateAdapter = true)
internal data class InAppHideParams(
    val campaignId: String,
    val hideType: String? = null
)

@JsonClass(generateAdapter = true)
internal data class InAppTrackParams(
    val eventName: String,
    val eventProperties: Map<String, Any>? = null
)

@JsonClass(generateAdapter = true)
internal data class InAppSetUserPropertiesParams(
    val userProperties: Map<String, Any>
)
