package com.marketap.sdk.client.inapp

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.webkit.JavascriptInterface
import com.marketap.sdk.model.external.MarketapCampaignType
import com.marketap.sdk.model.external.MarketapClickEvent
import com.marketap.sdk.model.internal.inapp.HideType
import com.marketap.sdk.presentation.CustomHandlerStore
import com.marketap.sdk.utils.logger

internal class WebAppInterface(
    private val callBack: InAppCallback?,
    private val inAppMessageActivity: InAppMessageActivity
) {

    @JavascriptInterface
    fun hideCampaign(hideType: String) {
        val hideTypeEnum = HideType.valueOf(hideType.uppercase())
        callBack?.onHide(hideTypeEnum)
        inAppMessageActivity.hideQuietly()

    }

    @JavascriptInterface
    fun trackClick(locationId: String, url: String) {
        val uri = Uri.parse(url)
        val campaignId = callBack?.onClick(locationId)
        val event = MarketapClickEvent(
            MarketapCampaignType.IN_APP_MESSAGE,
            campaignId ?: "",
            url
        )
        if (CustomHandlerStore.maybeHandleClick(inAppMessageActivity, event)) {
            return
        }

        // URL이 딥링크인지 확인
        if (uri.scheme == "http" || uri.scheme == "https") {
            // 일반 웹 URL -> 기본 브라우저에서 열기
            val browserIntent = Intent(Intent.ACTION_VIEW, uri)
            browserIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            inAppMessageActivity.startActivity(browserIntent)
        } else {
            // 딥링크 (예: myapp://notification/detail)
            val deepLinkIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            try {
                inAppMessageActivity.startActivity(deepLinkIntent)
            } catch (e: ActivityNotFoundException) {
                logger.e(e) { "딥링크를 처리할 액티비티가 없음: $uri" }
            }
        }
    }
}