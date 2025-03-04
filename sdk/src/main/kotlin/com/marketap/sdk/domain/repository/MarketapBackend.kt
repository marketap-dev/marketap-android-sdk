package com.marketap.sdk.domain.repository

import com.marketap.sdk.model.internal.api.DeviceReq
import com.marketap.sdk.model.internal.api.FetchCampaignReq
import com.marketap.sdk.model.internal.api.InAppCampaignRes
import com.marketap.sdk.model.internal.api.IngestEventRequest
import com.marketap.sdk.model.internal.api.UpdateProfileRequest

internal interface MarketapBackend {
    fun updateDevice(
        projectId: String,
        request: DeviceReq
    )

    fun fetchCampaigns(
        request: FetchCampaignReq,
        inTimeout: (suspend (InAppCampaignRes) -> Unit)? = null,
        onSuccess: suspend (InAppCampaignRes) -> Unit,
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