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
import java.lang.ref.WeakReference


class InAppMessageActivity : FragmentActivity() {
    companion object {
        private var currentRef: WeakReference<InAppMessageActivity>? = null

        internal val current: InAppMessageActivity? get() = currentRef?.get()

        internal const val EXTRA_SESSION_ID = "sessionId"
        private const val NO_SESSION = -1L
    }

    private var sessionId = NO_SESSION


    /** 웹뷰에 마지막으로 그리기 시작한 페이지의 메시지. */
    private var pageSessionId = NO_SESSION

    /** 새 페이지로 교체됐지만 아직 정리하지 않은 이전 페이지의 메시지. */
    private var replacedSessionId = NO_SESSION
    private var messageView: MarketapInAppView? = null
    private var loaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logger.d { "InAppMessage Activity created" }
        currentRef = WeakReference(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isStatusBarContrastEnforced = false
                window.isNavigationBarContrastEnforced = false
            }
        }

        sessionId = intent.getLongExtra(EXTRA_SESSION_ID, NO_SESSION)
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
        sessionId = intent.getLongExtra(EXTRA_SESSION_ID, NO_SESSION)
        loaded = false
    }

    override fun onPause() {
        super.onPause()
        if (isFinishing) {
            AndroidInAppView.getInstance().onPopupFinishing()
        }
    }

    override fun onResume() {
        super.onResume()
        currentRef = WeakReference(this)
        if (AndroidInAppView.getInstance().isPopupCloseRequested(sessionId)) {
            hideQuietly()
            return
        }
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
        if (current === this) currentRef = null
        if (replacedSessionId != NO_SESSION) {
            AndroidInAppView.getInstance().releasePopup(replacedSessionId)
            replacedSessionId = NO_SESSION
        }
        if (isFinishing) {
            AndroidInAppView.getInstance().releasePopup(sessionId)
            if (pageSessionId != sessionId) AndroidInAppView.getInstance().releasePopup(pageSessionId)
        }
    }

    /** 이 화면이 [sessionId] 팝업을 그리고 있을 때만 닫는다. */
    internal fun closeIfShowing(sessionId: Long) {
        if (sessionId == this.sessionId && !isFinishing) hideQuietly()
    }

    /** @param then 화면을 닫은 뒤 메인 스레드에서 부른다. */
    fun hideQuietly(then: () -> Unit = {}) {
        val view = messageView
        if (view == null) {
            finish()
            then()
            return
        }
        view.removeView {
            finish()
            then()
        }
    }

    private fun createWebView(): MarketapInAppView {
        return MarketapInAppView(this).apply {
            messageView = this
        }
    }

    private fun loadWebViewData() {
        val htmlData = intent.getStringExtra("htmlData") ?: run {
            finish()
            return
        }
        val previous = pageSessionId
        val current = sessionId
        pageSessionId = current
        if (previous != NO_SESSION && previous != current) replacedSessionId = previous
        // 페이지마다 새 브리지를 건다. 이전 페이지는 다음 로드 전까지 자기 브리지를 계속 쓰므로 늦은 호출도 자기 메시지로 간다.
        messageView?.addJavascriptInterface(
            WebAppInterface(AndroidInAppView.getInstance(), this, current),
            "MarketapSDK"
        )
        messageView?.show(htmlData, this) {
            if (previous != NO_SESSION && previous != current) {
                AndroidInAppView.getInstance().releasePopup(previous)
                if (replacedSessionId == previous) replacedSessionId = NO_SESSION
            }
            AndroidInAppView.getInstance().onWebViewLoaded()
        }
    }
}