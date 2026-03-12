package com.marketap.sdk.model.internal.api


import com.marketap.sdk.model.internal.AppEventProperty
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class IngestEventRequest(
    val id: String?,
    val name: String,

    @Json(name = "user_id")
    val userId: String?,

    val device: DeviceReq,
    val properties: Map<String, Any>?,
    var timestamp: String? = null
) : IngestRequest {

    companion object {
        fun delivery(
            userId: String?,
            device: DeviceReq,
            properties: AppEventProperty,
            sdkIntegrationState: String,
        ): IngestEventRequest {
            return IngestEventRequest(
                id = null,
                name = "mkt_delivery_message",
                userId = userId,
                device = device,
                properties = properties.toMap().withSdkIntegrationState(sdkIntegrationState),
            )
        }

        fun impression(
            userId: String?,
            device: DeviceReq,
            properties: AppEventProperty,
            sdkIntegrationState: String
        ): IngestEventRequest {
            return IngestEventRequest(
                id = null,
                name = "mkt_push_impression",
                userId = userId,
                device = device,
                properties = properties.toMap().withSdkIntegrationState(sdkIntegrationState),
            )
        }

        fun click(
            userId: String?,
            device: DeviceReq,
            properties: AppEventProperty,
            sdkIntegrationState: String,
        ): IngestEventRequest {
            return IngestEventRequest(
                id = null,
                name = "mkt_click_message",
                userId = userId,
                device = device,
                properties = properties.toMap().withSdkIntegrationState(sdkIntegrationState),
            )
        }

    }
}

internal fun Map<String, Any>.withSdkIntegrationState(sdkIntegrationState: String): Map<String, Any> =
    this + ("sdk_integration_state" to sdkIntegrationState)
