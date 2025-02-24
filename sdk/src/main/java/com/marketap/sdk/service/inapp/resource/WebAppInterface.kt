package com.marketap.sdk.service.inapp.resource

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.JavascriptInterface
import com.marketap.sdk.model.internal.inapp.HideType
import com.marketap.sdk.service.inapp.InAppCallBack

internal class WebAppInterface(
    private val callBack: InAppCallBack?,
    private val inAppMessageActivity: InAppMessageActivity // ✅ Activity 참조 추가
) {

    @JavascriptInterface
    fun hideCampaign(campaignId: String, hideType: String) {
        val hideTypeEnum = HideType.valueOf(hideType.uppercase())
        callBack?.hideCampaign(campaignId, hideTypeEnum)

        // ✅ 콜백이 끝난 후 조용히 Activity 종료
        inAppMessageActivity.hideQuietly()

    }

    @JavascriptInterface
    fun trackClick(campaignId: String, locationId: String, url: String) {
        val uri = Uri.parse(url)
        callBack?.click(
            campaignId,
            locationId,
            uri
        )

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
                Log.e("DeepLink", "딥링크를 처리할 액티비티가 없음: $uri")
            }
        }

        // ✅ 콜백이 끝난 후 조용히 Activity 종료
        inAppMessageActivity.hideQuietly()
    }
}