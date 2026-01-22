package com.marketap.sdk

import com.marketap.sdk.model.external.MarketapCampaignType
import com.marketap.sdk.model.external.MarketapClickEvent
import com.marketap.sdk.model.internal.inapp.HideType
import com.marketap.sdk.presentation.CustomHandlerStore
import com.marketap.sdk.presentation.MarketapRegistry
import com.marketap.sdk.presentation.MarketapRegistry.marketapCore
import com.marketap.sdk.utils.logger

/**
 * MarketapPlugin - 플러그인(Flutter/React Native) 및 웹브릿지에서 사용하는 API
 */
object MarketapPlugin {

    // MARK: - 인앱 이벤트 처리 (플러그인용)

    @JvmStatic
    fun trackInAppImpression(
        campaignId: String,
        messageId: String,
        layoutSubType: String?
    ) {
        logger.d { "trackInAppImpression: campaignId=$campaignId, messageId=$messageId" }
        val props = InAppEventBuilder.impressionEventProperties(campaignId, messageId, layoutSubType)
        Marketap.track("mkt_delivery_message", props)
    }

    @JvmStatic
    fun trackInAppClick(
        campaignId: String,
        messageId: String,
        locationId: String,
        url: String?,
        layoutSubType: String?
    ) {
        logger.d { "trackInAppClick: campaignId=$campaignId, locationId=$locationId, url=$url" }

        // 클릭 핸들러 호출 (커스텀 핸들러가 등록된 경우에만)
        if (url != null && CustomHandlerStore.isCustomized()) {
            val clickEvent = MarketapClickEvent(MarketapCampaignType.IN_APP_MESSAGE, campaignId, url)
            MarketapRegistry.marketapClickHandler?.handleClick(clickEvent)
        }

        // 클릭 이벤트 트래킹
        val props = InAppEventBuilder.clickEventProperties(campaignId, messageId, locationId, url, layoutSubType)
        Marketap.track("mkt_click_message", props)
    }

    @JvmStatic
    fun hideInAppMessage(campaignId: String, hideType: String?) {
        logger.d { "hideInAppMessage: campaignId=$campaignId, hideType=$hideType" }
        if (hideType != null) {
            try {
                val type = HideType.valueOf(hideType.uppercase())
                marketapCore?.hideCampaign(campaignId, type)
            } catch (e: IllegalArgumentException) {
                logger.w { "Unknown hideType: $hideType" }
            }
        }
    }

    // MARK: - 이벤트 처리 (플러그인용)

    @JvmOverloads
    @JvmStatic
    fun trackEvent(name: String, properties: Map<String, Any>? = null) {
        marketapCore?.trackFromWebBridge(name, properties)
    }

    @JvmStatic
    fun setUserProperties(properties: Map<String, Any>) {
        marketapCore?.setUserProperties(properties)
    }
}
