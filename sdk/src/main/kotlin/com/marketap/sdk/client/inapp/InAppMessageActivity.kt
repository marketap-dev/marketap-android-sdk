package com.marketap.sdk.client.inapp

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity


class InAppMessageActivity : AppCompatActivity() {
    private var messageView: MarketapInAppView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        window.setBackgroundDrawableResource(android.R.color.transparent)

        val rootView = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        setContentView(rootView)

        messageView = createWebView()
        rootView.addView(messageView)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                messageView?.closeView()
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        loadWebViewData()
    }

    override fun onDestroy() {
        super.onDestroy()
        messageView?.destroy()
        messageView = null
    }

    fun hideQuietly() {
        messageView?.removeView {
            finish()
        }
    }

    private fun createWebView(): MarketapInAppView {
        return MarketapInAppView(this).apply {
            messageView = this
            addJavascriptInterface(
                WebAppInterface(AndroidInAppView.getInstance(), this@InAppMessageActivity),
                "MarketapSDK"
            )
        }
    }

    private fun loadWebViewData() {
        val htmlData = intent.getStringExtra("htmlData")
        htmlData?.let {
            messageView?.show(it, this)
        }
    }
}