package com.marketap.sdk

import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.marketap.sdk.model.internal.Device
import com.marketap.sdk.model.internal.bridge.BridgeEventReq
import com.marketap.sdk.model.internal.bridge.BridgeUserReq
import com.marketap.sdk.utils.adapter
import com.marketap.sdk.utils.deserialize
import com.marketap.sdk.utils.logger

class MarketapWebBridge(private val webView: WebView) {
    init {
        logger.d { "MarketapWebBridge initialized" }
    }

    companion object {
        const val NAME = "marketap"
    }

    @JavascriptInterface
    fun postMessage(type: String, params: String) {
        logger.d { "MarketapWebBridge.postMessage called - type: $type, params: $params" }
        when (type) {
            "track" -> {
                val data = params.deserialize(adapter<BridgeEventReq>())
                Marketap.track(data.eventName, data.eventProperties)
            }

            "identify" -> {
                val data = params.deserialize(adapter<BridgeUserReq>())
                Marketap.identify(data.userId, data.userProperties)
            }

            "resetIdentity" -> {
                Marketap.resetIdentity()
            }

            "marketapBridgeCheck" -> {
                handleBridgeCheck()
            }

            else -> {
                logger.e { "MarketapWebBridge received unknown type: $type" }
            }
        }
    }

    private fun handleBridgeCheck() {
        val device = Device()

        webView.post {
            webView.evaluateJavascript("""
                window.postMessage({ 
                    type: 'marketapBridgeAck',
                    metadata: {
                        sdk_type: 'android',
                        sdk_version: '${device.libraryVersion}',
                        platform: 'android'
                    }
                }, '*');
            """.trimIndent(), null)
        }
    }
}