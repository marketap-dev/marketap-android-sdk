package com.marketap.sdk

import android.app.Activity
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.marketap.sdk.client.CurrentActivityHolder
import com.marketap.sdk.client.inapp.AndroidInAppView
import com.marketap.sdk.domain.service.inapp.InAppPageScope
import com.marketap.sdk.model.internal.Device
import com.marketap.sdk.model.internal.bridge.BridgeEventReq
import com.marketap.sdk.model.internal.bridge.BridgeUserReq
import com.marketap.sdk.model.internal.bridge.InAppHideParams
import com.marketap.sdk.model.internal.bridge.BridgeDeviceOptInReq
import com.marketap.sdk.model.internal.bridge.InAppSetUserPropertiesParams
import com.marketap.sdk.model.internal.bridge.InAppTrackParams
import com.marketap.sdk.model.internal.inapp.InAppOrigin
import com.marketap.sdk.presentation.MarketapRegistry
import com.marketap.sdk.utils.adapter
import com.marketap.sdk.utils.deserialize
import com.marketap.sdk.utils.logger
import com.marketap.sdk.utils.toJsStringLiteral
import java.lang.ref.WeakReference

/**
 * MarketapWebBridge
 * @param webView 웹뷰 인스턴스
 * @param handleInAppInWebView true 면 이 웹뷰 이벤트로 뜬 인앱 메시지의 링크를 웹 클릭 처리 설정에 따라 이 웹뷰에서 열고,
 * "이 페이지에서만" 플로팅을 웹뷰가 페이지를 벗어나거나 화면에서 떨어질 때 닫는다. 인앱 메시지는 값과 상관없이 앱이 그린다.
 */
class MarketapWebBridge @JvmOverloads constructor(
    private val webView: WebView,
    private val handleInAppInWebView: Boolean = true
) {
    private val origin: InAppOrigin? = if (handleInAppInWebView) NativeOrigin(this) else null

    @Volatile
    private var addressChangeReceived = false

    init {
        logger.d { "MarketapWebBridge initialized (handleInAppInWebView=$handleInAppInWebView)" }
        MarketapPlugin.onWebBridgeConnected(handleInAppInWebView)
        if (origin != null) {
            latestBridgeRef = WeakReference(this)
            webView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {}
                override fun onViewDetachedFromWindow(v: View) {
                    AndroidInAppView.getInstance().closePageFloating(origin)
                }
            })
        }
    }

    companion object {
        const val NAME = "marketap"

        private const val COMMAND_OPEN_URL = "openUrl"

        @Volatile
        private var isExternalWebBridgeActive: Boolean = false

        @Volatile
        private var latestBridgeRef: WeakReference<MarketapWebBridge>? = null

        @Volatile
        private var externalWebViewCommandCallback: ((String, Map<String, Any?>) -> Unit)? = null

        @Volatile
        private var externalWebViewAttached: Boolean = false

        @Volatile
        private var externalAddressChangeReceived: Boolean = false

        /** 앱→래퍼 명령 콜백. type 은 `openUrl`(payload url), 메인 스레드에서 부른다. */
        @JvmStatic
        fun setExternalWebViewCommandCallback(callback: ((type: String, payload: Map<String, Any?>) -> Unit)?) {
            externalWebViewCommandCallback = callback
        }

        /** 래퍼 브릿지 생성 시 true, 해제(dispose) 시 false. */
        @JvmStatic
        fun setExternalWebViewAttached(attached: Boolean) {
            externalWebViewAttached = attached
            if (attached) {
                externalAddressChangeReceived = false
            } else {
                AndroidInAppView.getInstance().closePageFloating(externalOrigin)
            }
        }

        /** 래퍼 웹뷰가 보낸 `addressChanged` 를 전달한다. */
        @JvmStatic
        fun handleExternalAddressChanged() {
            logger.d { "External web view address changed" }
            externalAddressChangeReceived = true
            AndroidInAppView.getInstance().closePageFloating(externalOrigin)
        }

        private val externalOrigin = object : InAppOrigin {
            override fun canOpenUrl(): Boolean = externalWebViewCommandCallback != null && externalWebViewAttached

            override fun openUrl(url: String) {
                logger.d { "Opening in-app click url in external web view" }
                externalWebViewCommandCallback?.invoke(COMMAND_OPEN_URL, mapOf("url" to url))
            }

            override fun isShownIn(screen: Any): Boolean = false
        }

        /** 래퍼 웹뷰 이벤트의 출처. 인앱 처리를 맡은 래퍼가 아니면 null. 이벤트를 판정하기 전에 부른다. */
        internal fun externalOriginForEvent(eventName: String): InAppOrigin? {
            if (!isExternalWebBridgeActive) return null
            closePageFloatingOnPageView(externalOrigin, eventName, externalAddressChangeReceived)
            return externalOrigin
        }

        private fun closePageFloatingOnPageView(origin: InAppOrigin, eventName: String, addressChangeReceived: Boolean) {
            if (InAppPageScope.pageViewLeavesPage(eventName, addressChangeReceived)) {
                AndroidInAppView.getInstance().closePageFloating(origin)
            }
        }

        private fun WebView.hostActivity(): Activity? {
            var context = context
            while (context is ContextWrapper) {
                if (context is Activity) return context
                context = context.baseContext
            }
            return null
        }

        /** 인앱 처리를 맡은 웹브릿지(네이티브 또는 래퍼)가 있으면 true. */
        @JvmStatic
        fun hasActiveWebBridge(): Boolean = latestBridgeRef?.get() != null || isExternalWebBridgeActive

        /** 래퍼용. 앱이 캠페인을 웹으로 넘기지 않으므로 등록한 콜백은 호출되지 않는다. */
        @JvmStatic
        fun setExternalInAppMessageCallback(callback: ((Map<String, Any?>, String, Boolean) -> Unit)?) {
            logger.d { "setExternalInAppMessageCallback: campaigns are not delivered to web (registered=${callback != null})" }
        }

        /** 래퍼가 웹뷰 이벤트를 [MarketapPlugin.trackEvent] 로 넘기기 전에 그 브릿지의 인앱 처리 여부를 알린다. */
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
            "addressChanged" -> handleAddressChanged(params)
            else -> {
                logger.e { "MarketapWebBridge received unknown type: $type" }
            }
        }
    }

    private fun handleTrackEvent(params: String) {
        try {
            if (handleInAppInWebView) latestBridgeRef = WeakReference(this)
            val data = params.deserialize(adapter<BridgeEventReq>())
            if (origin != null) closePageFloatingOnPageView(origin, data.eventName, addressChangeReceived)
            MarketapPlugin.trackEventFromWebView(data.eventName, data.eventProperties, origin)
        } catch (t: Throwable) {
            logger.e(t) { "Failed to handle track event: $params" }
        }
    }

    private fun handleAddressChanged(params: String) {
        logger.d { "Web view address changed: $params" }
        addressChangeReceived = true
        origin?.let { AndroidInAppView.getInstance().closePageFloating(it) }
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
        val projectId = MarketapRegistry.config?.projectId
            ?: "undefined"

        evaluateJavaScript("""
            window.postMessage({
                type: 'marketapBridgeAck',
                metadata: {
                    sdk_type: 'android',
                    sdk_version: '${device.libraryVersion}',
                    platform: 'android',
                    project_id: '${projectId}',
                    handles_address_change: true
                }
            }, '*');
        """.trimIndent())

        MarketapPlugin.onWebSdkInitialized()
    }

    // MARK: - 인앱 메시지 이벤트 핸들러

    private fun handleInAppImpression(params: String) {
        logger.d { "Ignoring inAppMessageImpression: campaigns are not delivered to web ($params)" }
    }

    private fun handleInAppClick(params: String) {
        logger.d { "Ignoring inAppMessageClick: campaigns are not delivered to web ($params)" }
    }

    private fun handleInAppHide(params: String) {
        try {
            val data = params.deserialize(adapter<InAppHideParams>())
            val campaignId = data.campaignId
            val hideTypeString = data.hideType

            MarketapPlugin.hideInAppMessage(campaignId, hideTypeString)
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

    private fun evaluateJavaScript(script: String) {
        Handler(Looper.getMainLooper()).post {
            webView.evaluateJavascript(script) { result ->
                if (result != null && result != "null") {
                    logger.v { "JavaScript evaluation result: $result" }
                }
            }
        }
    }

    private class NativeOrigin(bridge: MarketapWebBridge) : InAppOrigin {
        private val bridgeRef = WeakReference(bridge)

        override fun canOpenUrl(): Boolean {
            val webView = bridgeRef.get()?.webView ?: return false
            val activity = webView.hostActivity() ?: return false
            return webView.isAttachedToWindow && CurrentActivityHolder.visibility.isStarted(activity)
        }

        override fun openUrl(url: String) {
            logger.d { "Opening in-app click url in web view" }
            bridgeRef.get()?.webView?.evaluateJavascript("window.location.href = ${url.toJsStringLiteral()};", null)
        }

        override fun isShownIn(screen: Any): Boolean = bridgeRef.get()?.webView?.hostActivity() === screen
    }
}
