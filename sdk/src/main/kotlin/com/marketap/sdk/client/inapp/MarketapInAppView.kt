package com.marketap.sdk.client.inapp

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import com.marketap.sdk.utils.SafeAreaUtils
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

    fun show(
        html: String,
        activity: FragmentActivity
    ) {
        postToMainThread {
            visibility = VISIBLE
            loadDataWithBaseURL(
                null,
                buildHtml(
                    html,
                    SafeAreaUtils.getStatusBarHeight(activity),
                    SafeAreaUtils.getNavigationBarHeight(activity)
                ),
                "text/html",
                "UTF-8",
                null
            )
        }
    }

    fun closeView() {
        evaluateJavascript("window._marketap_native.hideWithAnimation('CLOSE');") {}
    }

    fun removeView(then: () -> Unit = {}) {
        logger.d { "Removing MarketapInAppView" }
        postToMainThread {
            visibility = GONE
            try {
                loadUrl("about:blank")
                clearHistory()
                removeAllViews()
                postDelayed({
                    destroy()
                    then()
                }, 50)
            } catch (e: Exception) {
                logger.e(e) { "WebView destroy failed" }
            }
        }
    }
}