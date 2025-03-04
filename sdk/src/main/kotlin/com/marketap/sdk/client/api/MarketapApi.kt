package com.marketap.sdk.client.api

import com.marketap.sdk.model.internal.api.DeviceReq
import com.marketap.sdk.model.internal.api.FetchCampaignReq
import com.marketap.sdk.model.internal.api.InAppCampaignRes
import com.marketap.sdk.model.internal.api.IngestEventRequest
import com.marketap.sdk.model.internal.api.IngestRes
import com.marketap.sdk.model.internal.api.ServerResponse
import com.marketap.sdk.model.internal.api.UpdateProfileRequest

internal interface MarketapApi {
    suspend fun updateDevice(
        projectId: String,
        request: DeviceReq
    ): ServerResponse<IngestRes>

    suspend fun fetchCampaigns(
        request: FetchCampaignReq,
    ): ServerResponse<InAppCampaignRes>

    suspend fun track(
        projectId: String,
        request: IngestEventRequest,
    ): ServerResponse<IngestRes>

    suspend fun updateProfile(
        projectId: String,
        request: UpdateProfileRequest,
    ): ServerResponse<IngestRes>
}