package com.marketap.sdk.domain.repository

import com.marketap.sdk.model.internal.api.DeviceReq
import com.marketap.sdk.model.internal.api.FetchCampaignReq
import com.marketap.sdk.model.internal.api.FetchCampaignsReq
import com.marketap.sdk.model.internal.api.InAppCampaignRes
import com.marketap.sdk.model.internal.api.InAppCampaignSingleRes
import com.marketap.sdk.model.internal.api.IngestEventRequest
import com.marketap.sdk.model.internal.api.UpdateProfileRequest

internal interface MarketapBackend {
    fun updateDevice(
        projectId: String,
        request: DeviceReq
    )

    fun fetchCampaigns(
        request: FetchCampaignsReq,
        inTimeout: ((InAppCampaignRes) -> Unit)? = null,
        onSuccess: (InAppCampaignRes) -> Unit,
    )

    fun fetchCampaign(
        campaignId: String,
        request: FetchCampaignReq,
        inTimeout: ((InAppCampaignSingleRes) -> Unit)? = null,
        onSuccess: (InAppCampaignSingleRes) -> Unit,
    )

    fun track(
        projectId: String,
        request: IngestEventRequest
    )

    fun updateProfile(
        projectId: String,
        request: UpdateProfileRequest
    )
}