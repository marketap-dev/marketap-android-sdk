package com.marketap.sdk.client.inapp

import android.webkit.JavascriptInterface
import com.marketap.sdk.model.external.MarketapCampaignType
import com.marketap.sdk.model.external.MarketapClickEvent
import com.marketap.sdk.model.internal.inapp.HideType
import com.marketap.sdk.presentation.CustomHandlerStore
import com.marketap.sdk.presentation.InAppClickUrlHandler
import com.marketap.sdk.utils.logger

internal class WebAppInterface(
    private val callBack: InAppCallback?,
    private val inAppMessageActivity: InAppMessageActivity
) {

    @JavascriptInterface
    fun hideCampaign(hideType: String) {
        logger.d { "hideCampaign called with hideType: $hideType" }
        val hideTypeEnum = HideType.valueOf(hideType.uppercase())
        callBack?.onHide(hideTypeEnum)
        inAppMessageActivity.hideQuietly()

    }

    @JavascriptInterface
    fun trackClick(locationId: String, url: String) {
        logger.d { "trackClick called with locationId: $locationId, url: $url" }
        val campaignId = callBack?.onClick(locationId)
        val event = MarketapClickEvent(
            MarketapCampaignType.IN_APP_MESSAGE,
            campaignId ?: "",
            url
        )
        if (CustomHandlerStore.maybeHandleClick(inAppMessageActivity, event)) {
            return
        }

        InAppClickUrlHandler.open(inAppMessageActivity, url)
    }

    @JavascriptInterface
    fun trackEvent(eventName: String, eventPropertiesJson: String) {
        logger.d { "trackEvent called with eventName: $eventName, properties: $eventPropertiesJson" }
        callBack?.onTrack(eventName, eventPropertiesJson)
    }

    @JavascriptInterface
    fun setUserProperties(userPropertiesJson: String) {
        logger.d { "setUserProperties called with properties: $userPropertiesJson" }
        callBack?.onSetUserProperties(userPropertiesJson)
    }
}