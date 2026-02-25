package com.marketap.sdk.model.internal.api

import com.marketap.sdk.model.internal.InAppCampaign
import com.squareup.moshi.JsonClass


@JsonClass(generateAdapter = true)
internal data class InAppCampaignRes(
    val checksum: String,
    val campaigns: List<InAppCampaign>?
)

@JsonClass(generateAdapter = true)
internal data class InAppCampaignSingleRes(
    val campaign: InAppCampaign?
)