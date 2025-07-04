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
import com.marketap.sdk.utils.adapter
import com.marketap.sdk.utils.moshi
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Types
import io.ktor.http.headers

internal class MarketapApiImpl(
    private val deviceEndpoint: String = DEVICE_ENDPOINT,
    private val ingestionEndpoint: String = INGESTION_ENDPOINT,
    private val inAppMessagingEndpoint: String = IN_APP_MESSAGING_ENDPOINT,
    private val profileEndpoint: String = PROFILE_ENDPOINT,
) : MarketapApi {
    private val client = CustomClient {
        headers {
            append("Content-Type", "application/json")
        }
    }

    private inline fun <reified T> serverAdapter(): JsonAdapter<ServerResponse<T>> {
        val type = Types.newParameterizedType(ServerResponse::class.java, T::class.java)
        return moshi.adapter(type)
    }

    override suspend fun updateDevice(
        projectId: String,
        request: DeviceReq
    ): ServerResponse<IngestRes> {
        return client.post(
            "$deviceEndpoint?project_id=$projectId",
            request,
            adapter<DeviceReq>(),
            serverAdapter<IngestRes>()
        )
    }

    override suspend fun fetchCampaigns(
        request: FetchCampaignReq,
    ): ServerResponse<InAppCampaignRes> {
        val res = client.post(
            inAppMessagingEndpoint,
            request,
            adapter<FetchCampaignReq>(),
            serverAdapter<InAppCampaignRes>()
        )
        return res
    }

    override suspend fun track(
        projectId: String,
        request: IngestEventRequest,
    ): ServerResponse<IngestRes> {
        return client.post(
            "$ingestionEndpoint?project_id=$projectId",
            request,
            adapter<IngestEventRequest>(),
            serverAdapter<IngestRes>()
        )
    }

    override suspend fun updateProfile(
        projectId: String,
        request: UpdateProfileRequest,
    ): ServerResponse<IngestRes> {
        return client.post(
            "$profileEndpoint?project_id=$projectId",
            request,
            adapter<UpdateProfileRequest>(),
            serverAdapter<IngestRes>()
        )
    }
}