package com.marketap.sdk.client.inapp.floating

import android.annotation.SuppressLint
import android.app.Activity
import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.marketap.sdk.client.CurrentActivityHolder
import com.marketap.sdk.client.inapp.DecorAttachment
import com.marketap.sdk.client.inapp.InAppCallback
import com.marketap.sdk.client.inapp.decorSafeArea
import com.marketap.sdk.model.external.MarketapCampaignType
import com.marketap.sdk.model.external.MarketapClickEvent
import com.marketap.sdk.model.internal.inapp.HideType
import com.marketap.sdk.model.internal.inapp.InAppOrigin
import com.marketap.sdk.presentation.AppClickRouting
import com.marketap.sdk.presentation.CustomHandlerStore
import com.marketap.sdk.utils.logger
import com.marketap.sdk.utils.postToMainThread

private const val CONTAINER_Z = 1000f

/**
 * 플로팅 자리 인앱 메시지. 지금 화면(Activity)의 decor view 에 화면 크기 웹뷰를 붙이고,
 * 접힌 동안은 메시지 영역 밖 터치를 아래 화면으로 넘긴다. 한 번에 하나만 표시한다.
 */
internal object FloatingInAppView : CurrentActivityHolder.ScreenListener {

    private val attachment = DecorAttachment<FloatingContainer>(CONTAINER_Z) { removeBackCallback() }
    private val container: FloatingContainer? get() = attachment.view
    private var webView: WebView? = null
    @Volatile
    private var session = 0

    @Volatile
    private var callback: InAppCallback? = null
    private var origin: InAppOrigin? = null
    private var stayOnHost = false
    private var onShown: (() -> Unit)? = null
    private var onClosed: (() -> Unit)? = null
    private var backCallback: OnBackPressedCallback? = null
    private val ghosts = FloatingGhostLedger<Activity, View> { ghost ->
        (ghost.parent as? ViewGroup)?.removeView(ghost)
    }

    /** 메인 스레드에서 부른다. */
    fun isHostedBy(activity: Activity): Boolean = attachment.host === activity

    /**
     * @param stayOnHost 다른 화면이 앞에 와도 옮기지 않고, 붙은 화면이 사라지면 닫는다.
     * @param timerStartAtMillis 노출 시작 시각(epoch ms).
     * @param onShown 웹뷰가 문서를 다 불러왔을 때 한 번.
     * @param onClosed 어떤 이유로든(사용자 닫기, [dismiss], 붙을 화면 없음, 이미 표시 중) 이 요청이 끝날 때 한 번.
     */
    fun show(
        html: String,
        origin: InAppOrigin?,
        stayOnHost: Boolean,
        timerStartAtMillis: Long,
        callback: InAppCallback,
        onShown: () -> Unit,
        onClosed: () -> Unit,
    ) {
        postToMainThread {
            if (container != null) {
                logger.w { "Floating: already shown" }
                onClosed()
                return@postToMainThread
            }
            val activity = CurrentActivityHolder.getScreen()
            val decor = activity?.window?.decorView as? ViewGroup
            if (activity == null || decor == null) {
                logger.w { "Floating: no screen to attach" }
                onClosed()
                return@postToMainThread
            }
            session += 1
            this.callback = callback
            this.origin = origin
            this.stayOnHost = stayOnHost
            this.onShown = onShown
            this.onClosed = onClosed
            val wrapper = MutableContextWrapper(activity)
            val view = createWebView(wrapper, session)
            webView = view
            val box = FloatingContainer(wrapper).also { box ->
                box.addView(view)
                box.visibility = View.INVISIBLE
                ViewCompat.setOnApplyWindowInsetsListener(box) { _, insets ->
                    pushSafeArea(insets)
                    insets
                }
            }
            attachment.start(box, wrapper)
            CurrentActivityHolder.addScreenListener(this)
            attachTo(activity)
            val (top, bottom) = ViewCompat.getRootWindowInsets(decor)?.let(::decorSafeArea) ?: (0 to 0)
            view.loadDataWithBaseURL(
                null,
                FloatingScripts.buildDocument(html, top, bottom, timerStartAtMillis),
                "text/html",
                "UTF-8",
                null
            )
        }
    }

    /** [expected] 요청이 아직 떠 있으면 사용자 닫기 기록 없이 닫는다. */
    fun dismiss(expected: InAppCallback) {
        postToMainThread { if (callback === expected) dispose() }
    }

    override fun onScreenResumed(activity: Activity) {
        val box = container ?: return
        ghosts.onScreenFront(activity)
        val current = attachment.host
        val action = FloatingAttachPolicy.onResumed(
            isHost = current === activity,
            recreatingClass = attachment.recreatingClass,
            activityClass = activity.javaClass.name,
            stayOnHost = stayOnHost,
        )
        when (action) {
            ResumeAction.IGNORE -> Unit
            ResumeAction.RESTORE -> {
                attachment.recreatingClass = null
                attachTo(activity)
            }
            ResumeAction.MOVE -> {
                attachment.recreatingClass = null
                move(box, current, activity)
            }
        }
    }

    override fun onScreenDestroyed(activity: Activity) {
        if (container == null) return
        ghosts.onScreenGone(activity)
        val action = FloatingAttachPolicy.onDestroyed(
            isHost = attachment.host === activity,
            isChangingConfigurations = activity.isChangingConfigurations,
            stayOnHost = stayOnHost,
        )
        when (action) {
            DestroyAction.IGNORE -> Unit
            DestroyAction.DETACH_FOR_RECREATE -> {
                detach()
                attachment.recreatingClass = activity.javaClass.name
            }
            DestroyAction.DETACH_AND_WAIT -> detach()
            DestroyAction.CLOSE -> dispose()
        }
    }

    private fun move(box: FloatingContainer, from: Activity?, to: Activity) {
        val expanded = box.layout.expanded
        val snapshot = if (FloatingAttachPolicy.shouldSnapshot(ResumeAction.MOVE, from != null, expanded, box.layout)) {
            captureBar(box)
        } else null
        if (expanded) collapseInstantly()
        if (from != null && snapshot != null) {
            addGhost(from, snapshot)?.let { ghosts.putLeaving(from, it) }
        }
        attachTo(to)
        if (snapshot != null) {
            addGhost(to, snapshot)?.let {
                ghosts.putArriving(to, it)
                clearArrivingAfterFirstDraw(to)
            }
        }
    }

    private fun attachTo(activity: Activity): Boolean {
        if (!attachment.attachTo(activity)) return false
        updateBackCallback()
        return true
    }

    private fun detach() = attachment.detach()

    private fun dispose() {
        if (container == null) return
        session += 1
        CurrentActivityHolder.removeScreenListener(this)
        ghosts.clear()
        detach()
        webView?.apply {
            removeJavascriptInterface(FloatingScripts.BRIDGE_NAME)
            loadUrl("about:blank")
            destroy()
        }
        val closed = onClosed
        webView = null
        attachment.clear()
        callback = null
        origin = null
        stayOnHost = false
        onShown = null
        onClosed = null
        closed?.invoke()
    }

    private fun collapseInstantly() {
        val box = container ?: return
        if (!box.layout.expanded) return
        webView?.evaluateJavascript(FloatingScripts.COLLAPSE_INSTANT, null)
        box.layout = box.layout.copy(expanded = false)
        updateBackCallback()
    }

    private fun updateBackCallback() {
        val owner = attachment.host as? ComponentActivity
        if (container?.layout?.expanded != true || owner == null) {
            removeBackCallback()
            return
        }
        if (backCallback != null) return
        backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                webView?.evaluateJavascript(FloatingScripts.COLLAPSE_ANIMATED, null)
            }
        }.also { owner.onBackPressedDispatcher.addCallback(owner, it) }
    }

    private fun removeBackCallback() {
        backCallback?.remove()
        backCallback = null
    }

    private fun pushSafeArea(insets: WindowInsetsCompat) {
        val view = webView ?: return
        val (top, bottom) = decorSafeArea(insets)
        view.post { view.evaluateJavascript(FloatingScripts.applySafeArea(top, bottom), null) }
    }

    private class BarSnapshot(val bitmap: Bitmap, val left: Int, val top: Int)

    private fun captureBar(box: FloatingContainer): BarSnapshot? {
        val area = box.layout.area ?: return null
        if (box.parent == null || box.width == 0 || box.height == 0 || box.visibility != View.VISIBLE) return null
        val left = area.left.toInt().coerceIn(0, box.width)
        val top = area.top.toInt().coerceIn(0, box.height)
        val width = area.width.toInt().coerceAtMost(box.width - left)
        val height = area.height.toInt().coerceAtMost(box.height - top)
        if (width <= 0 || height <= 0) return null
        return try {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).apply {
                translate(-left.toFloat(), -top.toFloat())
                box.draw(this)
            }
            BarSnapshot(bitmap, left, top)
        } catch (e: Throwable) {
            logger.e(e) { "Floating snapshot failed" }
            null
        }
    }

    private fun addGhost(activity: Activity, snapshot: BarSnapshot): View? {
        val decor = activity.window?.decorView as? ViewGroup ?: return null
        val image = ImageView(activity).apply {
            setImageBitmap(snapshot.bitmap)
            translationX = snapshot.left.toFloat()
            translationY = snapshot.top.toFloat()
            translationZ = GHOST_Z
        }
        decor.addView(image, FrameLayout.LayoutParams(snapshot.bitmap.width, snapshot.bitmap.height))
        return image
    }

    private fun clearArrivingAfterFirstDraw(activity: Activity) {
        val view = webView ?: return
        val clear = Runnable { ghosts.onFirstDraw(activity) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            view.postVisualStateCallback(0L, object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) {
                    view.post(clear)
                }
            })
        } else {
            view.postDelayed(clear, LEGACY_GHOST_MS)
        }
        view.postDelayed(clear, GHOST_TIMEOUT_MS)
    }

    private fun applyLayout(state: String, rect: String, viewportWidth: Float) {
        val box = container ?: return
        val next = FloatingLayout.parse(state, rect, viewportWidth, box.width, box.resources.displayMetrics.density)
        val expandedChanged = box.layout.expanded != next.expanded
        box.layout = next
        if (next.ready && box.visibility != View.VISIBLE) box.visibility = View.VISIBLE
        if (expandedChanged) updateBackCallback()
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun createWebView(context: MutableContextWrapper, session: Int): WebView {
        return WebView(context).apply {
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
            setBackgroundColor(Color.TRANSPARENT)
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            addJavascriptInterface(Bridge(session), FloatingScripts.BRIDGE_NAME)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    onShown?.invoke()
                    onShown = null
                }
            }
        }
    }

    private class Bridge(private val session: Int) {
        private val current: Boolean get() = session == FloatingInAppView.session

        private fun onMain(block: () -> Unit) = postToMainThread { if (current) block() }

        @JavascriptInterface
        fun hideCampaign(hideType: String) {
            if (!current) return
            val type = runCatching { HideType.valueOf(hideType.uppercase()) }.getOrDefault(HideType.CLOSE)
            callback?.onHide(type)
            onMain { dispose() }
        }

        @JavascriptInterface
        fun trackClick(locationId: String, url: String) {
            if (!current) return
            val campaignId = callback?.onClick(locationId)
            if (url.isBlank()) return
            onMain {
                val activity = attachment.host ?: return@onMain
                val event = MarketapClickEvent(MarketapCampaignType.IN_APP_MESSAGE, campaignId ?: "", url)
                if (CustomHandlerStore.maybeHandleClick(activity, event)) return@onMain
                AppClickRouting.open(activity, url, origin)
            }
        }

        @JavascriptInterface
        fun trackEvent(eventName: String, eventPropertiesJson: String) {
            if (!current) return
            callback?.onTrack(eventName, eventPropertiesJson)
        }

        @JavascriptInterface
        fun setUserProperties(userPropertiesJson: String) {
            if (!current) return
            callback?.onSetUserProperties(userPropertiesJson)
        }

        @JavascriptInterface
        fun onLayout(state: String, rect: String, viewportWidth: Float) {
            onMain { applyLayout(state, rect, viewportWidth) }
        }
    }

    private const val GHOST_Z = 999f
    private const val LEGACY_GHOST_MS = 100L
    private const val GHOST_TIMEOUT_MS = 1000L
}
