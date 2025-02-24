package com.marketap.sdk.model.internal.api

import com.google.gson.annotations.SerializedName


internal data class FetchCampaignReq(
    @SerializedName("project_id")
    val projectId: String,

    @SerializedName("user_id")
    val userId: String?,
    val device: DeviceReq
)
