package com.marketap.sdk.model.internal

import com.marketap.sdk.model.internal.push.DeliveryData
import com.marketap.sdk.utils.deserialize
import com.marketap.sdk.utils.mapAdapter
import java.util.UUID

internal data class AppEventProperty(
    val campaignId: String,
    val campaignCategory: String,
    val subChannelType: String,
    val channelType: String,
    val messageId: String,
    val serverProperties: Map<String, String> = emptyMap(),
    val resultStatus: Int = 200000,
    val resultMessage: String = "SUCCESS",
    val isSuccess: Boolean = true,
    val locationId: String? = null
) {
    fun addLocationId(locationId: String): AppEventProperty {
        return copy(locationId = locationId)
    }

    fun toMap(): Map<String, Any> {
        return mapOf(
            "mkt_campaign_id" to campaignId,
            "mkt_campaign_category" to campaignCategory,
            "mkt_sub_channel_type" to subChannelType,
            "mkt_channel_type" to channelType,
            "mkt_result_status" to resultStatus,
            "mkt_result_message" to resultMessage,
            "mkt_is_success" to isSuccess,
            "mkt_message_id" to messageId
        ) + (if (locationId != null) mapOf("mkt_location_id" to locationId) else emptyMap()) + serverProperties
    }

    companion object {
        fun onSite(campaign: InAppCampaign): AppEventProperty {
            return AppEventProperty(
                campaignId = campaign.id,
                campaignCategory = "ON_SITE",
                subChannelType = campaign.layout.layoutSubType,
                channelType = "IN_APP_MESSAGE",
                messageId = UUID.randomUUID().toString()
            )
        }

        fun offSite(deliveryData: DeliveryData): AppEventProperty {
            val campaignId = deliveryData.campaignId
            val messageId = deliveryData.messageId
            val serverProperties = try {
                deliveryData.serverProperties?.deserialize(mapAdapter<String, String>())
                    ?: emptyMap()
            } catch (e: Exception) {
                emptyMap()
            }

            return AppEventProperty(
                campaignId = campaignId,
                campaignCategory = "OFF_SITE",
                subChannelType = "ANDROID",
                channelType = "PUSH",
                messageId = messageId,
                serverProperties = serverProperties
            )
        }
    }
}