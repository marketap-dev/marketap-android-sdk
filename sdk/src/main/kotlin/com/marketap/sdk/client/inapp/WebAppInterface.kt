package com.marketap.sdk.client.inapp

import android.webkit.JavascriptInterface
import com.marketap.sdk.client.inapp.popup.PopupInAppView
import com.marketap.sdk.model.external.MarketapCampaignType
import com.marketap.sdk.model.external.MarketapClickEvent
import com.marketap.sdk.model.internal.inapp.HideType
import com.marketap.sdk.presentation.AppClickRoute
import com.marketap.sdk.presentation.AppClickRouting
import com.marketap.sdk.presentation.CustomHandlerStore
import com.marketap.sdk.presentation.InAppClickUrlHandler
import com.marketap.sdk.utils.logger
import com.marketap.sdk.utils.postToMainThread

/** 팝업 웹뷰의 JS 브리지. [popup] 이 정리될 때까지 [session] 으로만 전달한다. */
internal class WebAppInterface(
    private val popup: PopupInAppView,
    private val session: InAppSession,
) {

    @JavascriptInterface
    fun hideCampaign(hideType: String) {
        logger.d { "hideCampaign called with hideType: $hideType" }
        val type = runCatching { HideType.valueOf(hideType.uppercase()) }.getOrDefault(HideType.CLOSE)
        session.onHide(type)
        popup.onHideSignal()
    }

    /** 클릭 기록은 JS 가 바로 뒤에 부르는 [hideCampaign] 보다 먼저 이 스레드에서 하고, 화면이 필요한 처리만 메인 스레드로 넘긴다. */
    @JavascriptInterface
    fun trackClick(locationId: String, url: String) {
        logger.d { "trackClick called with locationId: $locationId, url: $url" }
        if (!popup.acceptsClicks) return
        val campaignId = session.onClick(locationId)
        postToMainThread { routeClick(campaignId, url) }
    }

    private fun routeClick(campaignId: String?, url: String) {
        val activity = popup.host ?: return
        val event = MarketapClickEvent(
            MarketapCampaignType.IN_APP_MESSAGE,
            campaignId ?: "",
            url
        )
        if (CustomHandlerStore.maybeHandleClick(activity, event)) {
            return
        }

        val origin = session.origin
        if (origin != null && AppClickRouting.route(origin) == AppClickRoute.WEB_VIEW) {
            popup.close { origin.openUrl(url) }
        } else {
            InAppClickUrlHandler.open(activity, url)
        }
    }

    @JavascriptInterface
    fun trackEvent(eventName: String, eventPropertiesJson: String) {
        logger.d { "trackEvent called with eventName: $eventName, properties: $eventPropertiesJson" }
        session.onTrack(eventName, eventPropertiesJson)
    }

    @JavascriptInterface
    fun setUserProperties(userPropertiesJson: String) {
        logger.d { "setUserProperties called with properties: $userPropertiesJson" }
        session.onSetUserProperties(userPropertiesJson)
    }
}
