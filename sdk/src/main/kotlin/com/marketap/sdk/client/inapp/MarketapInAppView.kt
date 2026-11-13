package com.marketap.sdk.client.inapp

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import com.marketap.sdk.utils.logger
import com.marketap.sdk.utils.postToMainThread

@SuppressLint("SetJavaScriptEnabled")
internal class MarketapInAppView(
    context: Context,
) : WebView(context) {
    init {
        configureWebView()
    }

    private fun configureWebView() {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            setSupportMultipleWindows(false)
            cacheMode = WebSettings.LOAD_DEFAULT
            allowFileAccess = false
            allowContentAccess = false
            loadsImagesAutomatically = true
            useWideViewPort = true
            loadWithOverviewMode = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        alpha = 1f
        setBackgroundColor(Color.TRANSPARENT)
        isFocusable = true
        isFocusableInTouchMode = true
        scrollBarStyle = SCROLLBARS_INSIDE_OVERLAY
        logger.d { "WebView configured and ready for use" }
    }


    private fun buildHtml(
        campaignHtml: String,
        topSafeArea: Int,
        bottomSafeArea: Int
    ): String {
        val script = """
        <script>
            window._marketap_native = {
                hide: function(hideType) {
                    if (window.MarketapSDK) {
                        window.MarketapSDK.hideCampaign(hideType);
                    }
                },
                click: function(locationId, url) {
                    if (window.MarketapSDK) {
                        window.MarketapSDK.trackClick(locationId, url);
                    }
                },
                track: function(eventName, eventPropertiesJson) {
                    if (window.MarketapSDK) {
                        window.MarketapSDK.trackEvent(eventName, eventPropertiesJson);
                    }
                },
                setUserProperties: function(userPropertiesJson) {
                    if (window.MarketapSDK) {
                        window.MarketapSDK.setUserProperties(userPropertiesJson);
                    }
                },
                topSafeArea: $topSafeArea / window.devicePixelRatio,
                bottomSafeArea: $bottomSafeArea / window.devicePixelRatio,
            };
        </script>
    """.trimIndent()

        return """
        <!DOCTYPE html>
        <html lang="ko">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Marketap Campaign</title>
            <style>
                body { margin: 0; padding: 0; font-family: Arial, sans-serif; }
            </style>
        </head>
        <body>
            $script
            $campaignHtml
        </body>
        </html>
    """.trimIndent()
    }

    interface PageListener {
        fun onPageFinished()

        /** 첫 문서를 불러오지 못했거나 렌더 프로세스가 종료됐다. 이 웹뷰는 더 쓰지 않는다. */
        fun onPageBroken()
    }

    /** 메인 스레드에서 부른다. [topSafeArea]·[bottomSafeArea] 는 px. */
    fun show(html: String, topSafeArea: Int, bottomSafeArea: Int, listener: PageListener) {
        webViewClient = object : WebViewClient() {
            private var finished = false

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (finished) return
                finished = true
                listener.onPageFinished()
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                if (!finished && request?.isForMainFrame == true) listener.onPageBroken()
            }

            @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                if (!finished && Build.VERSION.SDK_INT < Build.VERSION_CODES.M) listener.onPageBroken()
            }

            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                logger.w { "In-app popup render process gone" }
                listener.onPageBroken()
                return true
            }
        }
        visibility = VISIBLE
        loadDataWithBaseURL(
            null,
            buildHtml(html, topSafeArea, bottomSafeArea),
            "text/html",
            "UTF-8",
            null
        )
    }

    fun applySafeArea(topSafeArea: Int, bottomSafeArea: Int) {
        evaluateJavascript(
            "if(window._marketap_native){window._marketap_native.topSafeArea=$topSafeArea/window.devicePixelRatio;" +
                "window._marketap_native.bottomSafeArea=$bottomSafeArea/window.devicePixelRatio;}" +
                "if(window.marketapApplySafeArea){window.marketapApplySafeArea();}",
            null
        )
    }

    fun closeView() {
        evaluateJavascript("window._marketap_native.hideWithAnimation('CLOSE');") {}
    }

    /** 웹뷰를 비우고 50ms 뒤 파괴한다. [then] 은 정리 중 예외가 나도 메인 스레드에서 한 번 불린다. */
    fun removeView(then: () -> Unit = {}) {
        logger.d { "Removing MarketapInAppView" }
        postToMainThread {
            try {
                visibility = GONE
                loadUrl("about:blank")
                clearHistory()
                removeAllViews()
            } catch (e: Exception) {
                logger.e(e) { "WebView clear failed" }
            }
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    destroy()
                } catch (e: Exception) {
                    logger.e(e) { "WebView destroy failed" }
                } finally {
                    then()
                }
            }, 50)
        }
    }
}