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
    val locationId: String? = null,
    /** 채널 공통 속성 외에 이벤트별로 덧붙이는 값 (예: 푸시 이미지 로딩 진단) */
    val extraProperties: Map<String, Any> = emptyMap()
) {
    fun addLocationId(locationId: String): AppEventProperty {
        return copy(locationId = locationId)
    }

    fun addProperties(properties: Map<String, Any>): AppEventProperty {
        if (properties.isEmpty()) return this
        return copy(extraProperties = extraProperties + properties)
    }

    /** extraProperties 를 먼저 깐다. 채널 핵심 속성(mkt_campaign_id 등)을 덮어쓰지 못하게. */
    fun toMap(): Map<String, Any> {
        return extraProperties + mapOf(
            "mkt_campaign_id" to campaignId,
            "mkt_campaign_category" to campaignCategory,
            "mkt_sub_channel_type" to subChannelType,
            "mkt_channel_type" to channelType,
            "mkt_result_status" to resultStatus,
            "mkt_result_message" to resultMessage,
            "mkt_is_success" to isSuccess,
            "mkt_message_id" to messageId
        ) + (if (locationId != null) mapOf("mkt_location_id" to locationId) else emptyMap()) +
                serverProperties
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