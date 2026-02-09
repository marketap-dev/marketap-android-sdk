package com.marketap.sdk

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.marketap.sdk.model.internal.Device
import com.marketap.sdk.model.internal.InAppCampaign
import com.marketap.sdk.model.internal.bridge.BridgeEventReq
import com.marketap.sdk.model.internal.bridge.BridgeUserReq
import com.marketap.sdk.model.internal.bridge.InAppClickParams
import com.marketap.sdk.model.internal.bridge.InAppHideParams
import com.marketap.sdk.model.internal.bridge.InAppImpressionParams
import com.marketap.sdk.model.internal.bridge.BridgeDeviceOptInReq
import com.marketap.sdk.model.internal.bridge.InAppSetUserPropertiesParams
import com.marketap.sdk.model.internal.bridge.InAppTrackParams
import com.marketap.sdk.presentation.CustomHandlerStore
import com.marketap.sdk.utils.adapter
import com.marketap.sdk.utils.deserialize
import com.marketap.sdk.utils.logger
import com.marketap.sdk.utils.serialize
import java.lang.ref.WeakReference

/**
 * MarketapWebBridge
 * @param webView 웹뷰 인스턴스
 * @param handleInAppInWebView 인앱 메시지를 웹뷰에서 처리할지 여부
 */
class MarketapWebBridge @JvmOverloads constructor(
    private val webView: WebView,
    private val handleInAppInWebView: Boolean = true
) {
    private var currentCampaign: InAppCampaign? = null
    private var currentMessageId: String? = null

    init {
        logger.d { "MarketapWebBridge initialized (handleInAppInWebView=$handleInAppInWebView)" }
    }

    companion object {
        const val NAME = "marketap"

        private var activeInstanceRef: WeakReference<MarketapWebBridge>? = null
        private var externalInAppMessageCallback: ((Map<String, Any?>, String, Boolean) -> Unit)? = null
        private var isExternalWebBridgeActive: Boolean = false

        @JvmStatic
        fun hasActiveWebBridge(): Boolean {
            // 네이티브 웹브릿지 또는 외부 웹브릿지가 활성화되어 있는지 확인
            return activeInstanceRef?.get() != null || isExternalWebBridgeActive
        }

        @JvmStatic
        internal fun sendCampaignToActiveWeb(campaign: InAppCampaign, messageId: String) {
            // 외부 웹브릿지가 활성화된 경우 외부로 전달
            if (isExternalWebBridgeActive) {
                isExternalWebBridgeActive = false  // 전달 후 클리어
                externalInAppMessageCallback?.let { callback ->
                    // InAppCampaign을 Map으로 변환
                    val campaignMap = campaign.toMap()
                    val hasCustomClickHandler = CustomHandlerStore.isCustomized()
                    callback(campaignMap, messageId, hasCustomClickHandler)
                }
                return
            }

            // 네이티브 웹브릿지로 전달
            val bridge = activeInstanceRef?.get()
            activeInstanceRef = null  // 전달 전에 클리어 (sendCampaignToWeb이 실패해도 클리어)
            bridge?.sendCampaignToWeb(campaign, messageId)
        }

        // MARK: - External Bridge Support

        /**
         * 외부 인앱 메시지 콜백 등록 (Flutter, React Native 등에서 사용)
         * @param callback 인앱 메시지를 받을 콜백 함수
         */
        @JvmStatic
        fun setExternalInAppMessageCallback(callback: ((Map<String, Any?>, String, Boolean) -> Unit)?) {
            externalInAppMessageCallback = callback
        }

        /**
         * 외부 웹브릿지 활성화 상태 설정
         * 외부에서 trackFromWebBridge 호출 시 true로 설정
         */
        @JvmStatic
        fun setExternalWebBridgeActive(active: Boolean) {
            isExternalWebBridgeActive = active
        }
    }

    @JavascriptInterface
    fun postMessage(type: String, params: String) {
        logger.d { "MarketapWebBridge.postMessage called - type: $type, params: $params" }
        when (type) {
            "track" -> handleTrackEvent(params)
            "identify" -> handleIdentifyEvent(params)
            "resetIdentity" -> Marketap.resetIdentity()
            "marketapBridgeCheck" -> handleBridgeCheck()
            // 웹에서 인앱 메시지 이벤트 처리
            "inAppMessageImpression" -> handleInAppImpression(params)
            "inAppMessageClick" -> handleInAppClick(params)
            "inAppMessageHide" -> handleInAppHide(params)
            "inAppMessageTrack" -> handleInAppTrack(params)
            "inAppMessageSetUserProperties" -> handleInAppSetUserProperties(params)
            "setDeviceOptIn" -> handleSetDeviceOptIn(params)
            else -> {
                logger.e { "MarketapWebBridge received unknown type: $type" }
            }
        }
    }

    private fun handleTrackEvent(params: String) {
        try {
            // 웹뷰에서 인앱 메시지를 처리하는 경우에만 활성 인스턴스로 등록
            if (handleInAppInWebView) {
                activeInstanceRef = WeakReference(this)
            }
            val data = params.deserialize(adapter<BridgeEventReq>())
            // 웹브릿지 컨텍스트 표시하여 track 호출
            MarketapPlugin.trackEvent(data.eventName, data.eventProperties)
        } catch (t: Throwable) {
            logger.e(t) { "Failed to handle track event: $params" }
            if (handleInAppInWebView) {
                activeInstanceRef = null
            }
        }
    }

    private fun handleIdentifyEvent(params: String) {
        try {
            val data = params.deserialize(adapter<BridgeUserReq>())
            Marketap.identify(data.userId, data.userProperties)
        } catch (t: Throwable) {
            logger.e(t) { "Failed to handle identify event: $params" }
        }
    }

    private fun handleBridgeCheck() {
        val device = Device()

        evaluateJavaScript("""
            window.postMessage({
                type: 'marketapBridgeAck',
                metadata: {
                    sdk_type: 'android',
                    sdk_version: '${device.libraryVersion}',
                    platform: 'android'
                }
            }, '*');
        """.trimIndent())
    }

    // MARK: - 인앱 메시지 이벤트 핸들러

    private fun handleInAppImpression(params: String) {
        try {
            val data = params.deserialize(adapter<InAppImpressionParams>())
            val campaignId = data.campaignId
            val messageId = data.messageId

            // 캠페인 정보가 있으면 impression 이벤트 전송
            val campaign = currentCampaign
            if (campaign != null && campaign.id == campaignId) {
                MarketapPlugin.trackInAppImpression(
                    campaignId = campaign.id,
                    messageId = messageId,
                    layoutSubType = campaign.layout.layoutSubType
                )
            }
        } catch (t: Throwable) {
            logger.e(t) { "Failed to handle inAppMessageImpression: $params" }
        }
    }

    private fun handleInAppClick(params: String) {
        try {
            val data = params.deserialize(adapter<InAppClickParams>())
            val campaignId = data.campaignId
            val messageId = data.messageId
            val locationId = data.locationId
            val url = data.url

            val campaign = currentCampaign
            if (campaign != null && campaign.id == campaignId) {
                MarketapPlugin.trackInAppClick(
                    campaignId = campaign.id,
                    messageId = messageId,
                    locationId = locationId,
                    url = url,
                    layoutSubType = campaign.layout.layoutSubType
                )
            }
        } catch (t: Throwable) {
            logger.e(t) { "Failed to handle inAppMessageClick: $params" }
        }
    }

    private fun handleInAppHide(params: String) {
        try {
            val data = params.deserialize(adapter<InAppHideParams>())
            val campaignId = data.campaignId
            val hideTypeString = data.hideType

            // 캠페인 숨김 처리
            MarketapPlugin.hideInAppMessage(campaignId, hideTypeString)

            // 현재 캠페인 정보 클리어
            if (currentCampaign?.id == campaignId) {
                currentCampaign = null
                currentMessageId = null
            }
        } catch (t: Throwable) {
            logger.e(t) { "Failed to handle inAppMessageHide: $params" }
        }
    }

    private fun handleInAppTrack(params: String) {
        try {
            val data = params.deserialize(adapter<InAppTrackParams>())
            val eventName = data.eventName
            val eventProperties = data.eventProperties

            logger.d { "Web InApp Track: eventName=$eventName" }

            Marketap.track(eventName, eventProperties)
        } catch (t: Throwable) {
            logger.e(t) { "Failed to handle inAppMessageTrack: $params" }
        }
    }

    private fun handleSetDeviceOptIn(params: String) {
        try {
            val data = params.deserialize(adapter<BridgeDeviceOptInReq>())
            Marketap.setDeviceOptIn(data.optIn)
        } catch (t: Throwable) {
            logger.e(t) { "Failed to handle setDeviceOptIn: $params" }
        }
    }

    private fun handleInAppSetUserProperties(params: String) {
        try {
            val data = params.deserialize(adapter<InAppSetUserPropertiesParams>())
            val userProperties = data.userProperties

            logger.d { "Web InApp SetUserProperties" }

            MarketapPlugin.setUserProperties(userProperties)
        } catch (t: Throwable) {
            logger.e(t) { "Failed to handle inAppMessageSetUserProperties: $params" }
        }
    }

    internal fun sendCampaignToWeb(campaign: InAppCampaign, messageId: String) {
        this.currentCampaign = campaign
        this.currentMessageId = messageId

        // 캠페인 정보를 JSON으로 직렬화
        val campaignJson = try {
            campaign.serialize(adapter())
        } catch (t: Throwable) {
            logger.e(t) { "sendCampaignToWeb: failed to encode campaign" }
            return
        }

        // 커스텀 클릭 핸들러 등록 여부
        val hasCustomClickHandler = CustomHandlerStore.isCustomized()

        logger.d { "Sending campaign to web: ${campaign.id}, hasCustomClickHandler: $hasCustomClickHandler" }

        evaluateJavaScript("""
            window.postMessage({
                type: 'marketapShowInAppMessage',
                campaign: $campaignJson,
                messageId: '$messageId',
                hasCustomClickHandler: $hasCustomClickHandler
            }, '*');
        """.trimIndent())
    }

    private fun evaluateJavaScript(script: String) {
        Handler(Looper.getMainLooper()).post {
            webView.evaluateJavascript(script) { result ->
                if (result != null && result != "null") {
                    logger.v { "JavaScript evaluation result: $result" }
                }
            }
        }
    }
}
