package com.marketap.sdk.model.internal.api

import com.marketap.sdk.model.internal.InAppCampaign


internal data class InAppCampaignRes(
    val checksum: String,
    val campaigns: List<InAppCampaign>
)
