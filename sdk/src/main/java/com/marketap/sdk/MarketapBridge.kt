package com.marketap.sdk

import android.webkit.JavascriptInterface

internal class MarketapBridge(private val webView: MarketapWebView) {

    @JavascriptInterface
    fun onInAppMessageShow() {
        webView.showWebView()
    }

    @JavascriptInterface
    fun onInAppMessageHide() {
        webView.hideWebView()
    }
}