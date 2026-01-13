package com.marketap.sdk.model.internal.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class FetchCampaignsReq(
    @Json(name = "project_id")
    val projectId: String,

    @Json(name = "user_id")
    val userId: String?,

    val device: DeviceReq
)

@JsonClass(generateAdapter = true)
internal data class FetchCampaignReq(
    @Json(name = "project_id")
    val projectId: String,

    @Json(name = "user_id")
    val userId: String?,

    val device: DeviceReq,

    @Json(name = "event_name")
    val eventName: String?,

    @Json(name = "event_properties")
    val eventProperties: Map<String, Any>?,
)
