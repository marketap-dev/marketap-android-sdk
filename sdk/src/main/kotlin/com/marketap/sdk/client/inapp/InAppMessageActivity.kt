package com.marketap.sdk.client.inapp

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import com.marketap.sdk.utils.logger


class InAppMessageActivity : FragmentActivity() {
    private var messageView: MarketapInAppView? = null
    private var loaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logger.d { "InAppMessage Activity created" }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isStatusBarContrastEnforced = false
                window.isNavigationBarContrastEnforced = false
            }
        }

        val htmlData = intent.getStringExtra("htmlData")
        if (htmlData == null) {
            finish()
            return
        }

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
        loaded = false
    }

    override fun onResume() {
        super.onResume()
        if (!loaded) {
            loaded = true
            window.decorView.post {
                loadWebViewData()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        messageView?.destroy()
        messageView = null
        if (isFinishing) {
            AndroidInAppView.getInstance().resetIfNeeded()
        }
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
        val htmlData = intent.getStringExtra("htmlData") ?: run {
            finish()
            return
        }
        messageView?.show(htmlData, this) {
            AndroidInAppView.getInstance().onWebViewLoaded()
        }
    }
}