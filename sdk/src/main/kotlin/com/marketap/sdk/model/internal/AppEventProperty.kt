package com.marketap.sdk.model.internal

import java.util.UUID

internal data class AppEventProperty(
    val campaignId: String,
    val campaignCategory: String,
    val subChannelType: String,
    val channelType: String,
    val messageId: String,
    val resultStatus: Int = 200,
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
        ) + (if (locationId != null) mapOf("mkt_location_id" to locationId) else emptyMap())
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

        fun offSite(campaignId: String, messageId: String): AppEventProperty {
            return AppEventProperty(
                campaignId = campaignId,
                campaignCategory = "OFF_SITE",
                subChannelType = "ANDROID",
                channelType = "PUSH",
                messageId = messageId
            )
        }
    }
}