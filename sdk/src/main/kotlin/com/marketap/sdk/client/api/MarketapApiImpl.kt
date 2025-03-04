package com.marketap.sdk.client.api

import com.marketap.sdk.model.internal.api.DEVICE_ENDPOINT
import com.marketap.sdk.model.internal.api.DeviceReq
import com.marketap.sdk.model.internal.api.FetchCampaignReq
import com.marketap.sdk.model.internal.api.INGESTION_ENDPOINT
import com.marketap.sdk.model.internal.api.IN_APP_MESSAGING_ENDPOINT
import com.marketap.sdk.model.internal.api.InAppCampaignRes
import com.marketap.sdk.model.internal.api.IngestEventRequest
import com.marketap.sdk.model.internal.api.IngestRes
import com.marketap.sdk.model.internal.api.PROFILE_ENDPOINT
import com.marketap.sdk.model.internal.api.ServerResponse
import com.marketap.sdk.model.internal.api.UpdateProfileRequest
import com.marketap.sdk.utils.CustomClient
import io.ktor.http.headers

internal class MarketapApiImpl(
    private val deviceEndpoint: String = DEVICE_ENDPOINT,
    private val ingestionEndpoint: String = INGESTION_ENDPOINT,
    private val inAppMessagingEndpoint: String = IN_APP_MESSAGING_ENDPOINT,
    private val profileEndpoint: String = PROFILE_ENDPOINT
) : MarketapApi {
    private val client = CustomClient {
        headers {
            append("Content-Type", "application/json")
        }
    }

    override suspend fun updateDevice(
        projectId: String,
        request: DeviceReq
    ): ServerResponse<IngestRes> {
        return client.post<DeviceReq, ServerResponse<IngestRes>>(
            "$deviceEndpoint?project_id=$projectId",
            request
        )
    }

    override suspend fun fetchCampaigns(
        request: FetchCampaignReq,
    ): ServerResponse<InAppCampaignRes> {
        val res = client.post<FetchCampaignReq, ServerResponse<InAppCampaignRes>>(
            inAppMessagingEndpoint,
            request
        )
        return res
    }

    override suspend fun track(
        projectId: String,
        request: IngestEventRequest,
    ): ServerResponse<IngestRes> {
        return client.post<IngestEventRequest, ServerResponse<IngestRes>>(
            "$ingestionEndpoint?project_id=$projectId",
            request
        )
    }

    override suspend fun updateProfile(
        projectId: String,
        request: UpdateProfileRequest,
    ): ServerResponse<IngestRes> {
        return client.post<UpdateProfileRequest, ServerResponse<IngestRes>>(
            "$profileEndpoint?project_id=$projectId",
            request
        )
    }
}