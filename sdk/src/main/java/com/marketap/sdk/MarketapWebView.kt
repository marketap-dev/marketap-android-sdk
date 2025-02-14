package com.marketap.sdk

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

@SuppressLint("SetJavaScriptEnabled")
internal class MarketapWebView(context: Context) : WebView(context) {
    private var isLoaded = false
    private val eventQueue = mutableListOf<String>()

    init {
        onInit()
    }

    private fun onInit() {
        setBackgroundColor(Color.TRANSPARENT)

        background
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        visibility = View.GONE // 👈 기본적으로 숨김 상태

        loadSdk()

        // ✅ JS → Android 연결
        addJavascriptInterface(MarketapBridge(this), "AndroidBridge")

        // ✅ 콘솔 로그 확인
        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                Log.d("MarketapSDK", "Marketap: ${consoleMessage?.message()}")
                return true
            }
        }

        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d("MarketapSDK", "WebView 로딩 완료됨!")
                val jsCode = """
                    window._marketap_native.topSafeArea = 100;
                    window._marketap_native.bottomSafeArea = 100;
                """.trimIndent()
                evaluateJavascript(jsCode, null)
                isLoaded = true
                flushEventQueue()
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url.toString()
                Log.d("MarketapWebView", "URL 변경 감지됨: $url")

                return if (url.startsWith("marketap://")) {
                    Log.d("Good web view", "혀용된 URL: $url")
                    true // ✅ WebView에서 처리하지 않음
                } else {
                    Log.w("MarketapWebView", "🚨 허용되지 않은 URL 차단: $url")
                    true // ✅ WebView에서 로드하지 않음 (무시)
                }
            }
        }
    }

    private fun loadSdk() {
        loadUrl("https://static.marketap.io/sdk/test-and.html") // SDK를 포함한 HTML 파일 로드
    }

    private fun flushEventQueue() {
        Log.d("MarketapSDK", "Event Queue 처리 중...")
        eventQueue.forEach { evaluateJavascript(it, null) }
        eventQueue.clear()
        Log.d("MarketapSDK", "Event Queue 처리 완료!")
    }

    fun showWebView() {
        (context as Activity).runOnUiThread {
            bringToFront() // 항상 최상위에 유지
        }
        post { visibility = View.VISIBLE }
    }

    fun hideWebView() {
        post { visibility = View.GONE }  // InAppMessage 숨길 때 WebView 숨기기
    }

    fun useMarketapCore(functionName: String, vararg args: String) {
        val jsCode = """
        window._marketap_core.$functionName(${args.joinToString(", ")});
    """.trimIndent()

        if (isLoaded) {
            evaluateJavascript(jsCode, null)
        } else {
            Log.d("MarketapSDK", "WebView 로딩 중이므로 Event Queue에 추가함: $jsCode")
            eventQueue.add(jsCode)
        }
    }
}