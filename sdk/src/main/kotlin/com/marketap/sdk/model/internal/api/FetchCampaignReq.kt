package com.marketap.sdk.model.internal.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass


@JsonClass(generateAdapter = true)
internal data class FetchCampaignReq(
    @Json(name = "project_id")
    val projectId: String,

    @Json(name = "user_id")
    val userId: String?,

    val device: DeviceReq
)