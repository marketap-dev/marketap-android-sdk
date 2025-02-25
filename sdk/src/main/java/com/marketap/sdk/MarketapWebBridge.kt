package com.marketap.sdk

import android.util.Log
import android.webkit.JavascriptInterface
import com.marketap.sdk.model.internal.bridge.BridgeEventReq
import com.marketap.sdk.model.internal.bridge.BridgeUserReq
import com.marketap.sdk.utils.deserialize

class MarketapWebBridge {
    companion object {
        const val NAME = "marketap"
    }

    @JavascriptInterface
    fun postMessage(type: String, params: String) {
        when (type) {
            "track" -> {
                val data = params.deserialize<BridgeEventReq>()
                Marketap.track(data.eventName, data.eventProperties)
            }

            "identify" -> {
                val data = params.deserialize<BridgeUserReq>()
                Marketap.identify(data.userId, data.userProperties)
            }

            "resetIdentity" -> {
                Marketap.resetIdentity()
            }

            else -> {
                Log.e("SDK", "Unknown type: $type")
            }
        }
    }
}