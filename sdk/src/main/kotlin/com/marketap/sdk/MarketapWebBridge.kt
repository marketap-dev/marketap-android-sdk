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
 * @param handleInAppInWebView 인앱 메시지를 웹뷰에서 처리할지 여부 (기본값: true, false면 네이티브에서 처리)
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

        /**
         * 외부 인앱 메시지 콜백 (Flutter, React Native 등에서 등록)
         */
        private var externalInAppMessageCallback: ((Map<String, Any?>, String, Boolean) -> Unit)? = null

        /**
         * 외부 웹브릿지가 활성화되었는지 여부
         */
        private var isExternalWebBridgeActive: Boolean = false

        /**
         * 현재 활성화된 웹브릿지가 있는지 확인
         */
        @JvmStatic
        fun hasActiveWebBridge(): Boolean {
            // 네이티브 웹브릿지 또는 외부 웹브릿지가 활성화되어 있는지 확인
            return activeInstanceRef?.get() != null || isExternalWebBridgeActive
        }

        /**
         * 현재 활성화된 웹브릿지로 캠페인 전달
         * 전달 후 activeInstanceRef를 클리어하여 다음 이벤트에서 올바른 웹브릿지를 사용하도록 함
         */
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
        } catch (e: Exception) {
            logger.e(e) { "Failed to handle track event: $params" }
            if (handleInAppInWebView) {
                activeInstanceRef = null
            }
        }
    }

    private fun handleIdentifyEvent(params: String) {
        try {
            val data = params.deserialize(adapter<BridgeUserReq>())
            Marketap.identify(data.userId, data.userProperties)
        } catch (e: Exception) {
            logger.e(e) { "Failed to handle identify event: $params" }
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
        } catch (e: Exception) {
            logger.e(e) { "Failed to handle inAppMessageImpression: $params" }
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
        } catch (e: Exception) {
            logger.e(e) { "Failed to handle inAppMessageClick: $params" }
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
        } catch (e: Exception) {
            logger.e(e) { "Failed to handle inAppMessageHide: $params" }
        }
    }

    private fun handleInAppTrack(params: String) {
        try {
            val data = params.deserialize(adapter<InAppTrackParams>())
            val eventName = data.eventName
            val eventProperties = data.eventProperties

            logger.d { "Web InApp Track: eventName=$eventName" }

            Marketap.track(eventName, eventProperties)
        } catch (e: Exception) {
            logger.e(e) { "Failed to handle inAppMessageTrack: $params" }
        }
    }

    private fun handleInAppSetUserProperties(params: String) {
        try {
            val data = params.deserialize(adapter<InAppSetUserPropertiesParams>())
            val userProperties = data.userProperties

            logger.d { "Web InApp SetUserProperties" }

            MarketapPlugin.setUserProperties(userProperties)
        } catch (e: Exception) {
            logger.e(e) { "Failed to handle inAppMessageSetUserProperties: $params" }
        }
    }

    // MARK: - 캠페인을 웹으로 전달

    /**
     * 캠페인을 웹으로 전달
     */
    internal fun sendCampaignToWeb(campaign: InAppCampaign, messageId: String) {
        this.currentCampaign = campaign
        this.currentMessageId = messageId

        // 캠페인 정보를 JSON으로 직렬화
        val campaignJson = try {
            campaign.serialize(adapter())
        } catch (e: Exception) {
            logger.e(e) { "sendCampaignToWeb: failed to encode campaign" }
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
