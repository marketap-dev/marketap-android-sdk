package com.marketap.sdk.client.inapp.popup

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.MutableContextWrapper
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresApi
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.marketap.sdk.client.CurrentActivityHolder
import com.marketap.sdk.client.inapp.DecorAttachment
import com.marketap.sdk.client.inapp.InAppSession
import com.marketap.sdk.client.inapp.MarketapInAppView
import com.marketap.sdk.client.inapp.WebAppInterface
import com.marketap.sdk.client.inapp.decorSafeArea
import com.marketap.sdk.utils.postToMainThread

/** 붙은 화면이 파괴된 뒤 다음 화면을 기다리는 상한(ms). 지나면 팝업을 닫는다. */
internal const val POPUP_SCREEN_WAIT_MS = 2000L

/** 뒤로가기로 닫힘 애니메이션을 요청한 뒤 템플릿의 숨김 신호를 기다리는 상한(ms). 지나면 바로 정리한다. */
internal const val POPUP_CLOSE_WATCHDOG_MS = 500L

/** 플로팅 컨테이너(1000)보다 위. */
private const val CONTAINER_Z = 2000f

/**
 * 팝업 자리 인앱 메시지 하나. 앞에 나와 있는 화면(Activity)의 decor view 에 화면 크기로 붙고, 다른 화면이 앞에 오면 옮겨 간다.
 * [session] 과 JS 브리지는 정리가 끝날 때까지 이 인스턴스가 들고 있다.
 * @param onClosed 정리가 끝나 [PopupState.CLOSED] 가 될 때 메인 스레드에서 한 번.
 */
internal class PopupInAppView(
    private val html: String,
    val session: InAppSession,
    private val onClosed: () -> Unit,
) : CurrentActivityHolder.ScreenListener, PopupLifecycle.Effects<Activity>, MarketapInAppView.PageListener {

    private val lifecycle = PopupLifecycle(this)
    private val attachment = DecorAttachment<PopupContainer>(CONTAINER_Z) { onDetached() }
    private val timers = Handler(Looper.getMainLooper())
    private var webView: MarketapInAppView? = null
    private var loaded = false
    private val afterClose = mutableListOf<() -> Unit>()

    private val accessibility = AccessibilityLedger<Activity, View>(
        read = { it.importantForAccessibility },
        write = { view, value -> view.importantForAccessibility = value },
    )
    private var bound: Bound? = null

    /** 붙어 있는 동안 그 화면에 건 것들. 뗄 때 모두 푼다. */
    private class Bound(val screen: Activity, val decor: ViewGroup) {
        var removeBack: (() -> Unit)? = null
        var previousFocus: View? = null
        var layoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null
        var focusListener: ViewTreeObserver.OnWindowFocusChangeListener? = null
    }

    val acceptsClicks: Boolean get() = lifecycle.acceptsClicks

    /** 지금 붙어 있는 화면. 어느 스레드에서든 읽을 수 있다. */
    val host: Activity? get() = attachment.host

    fun show() = postToMainThread {
        if (lifecycle.state == PopupState.CLOSED) return@postToMainThread
        CurrentActivityHolder.addScreenListener(this)
        lifecycle.start(CurrentActivityHolder.resumedScreen())
    }

    /** 닫힘 애니메이션 없이 닫는다. [then] 은 정리가 끝난 뒤 메인 스레드에서 부른다. */
    fun close(then: () -> Unit = {}) = postToMainThread {
        if (lifecycle.state == PopupState.CLOSED) {
            then()
            return@postToMainThread
        }
        afterClose.add(then)
        lifecycle.close()
    }

    fun onHideSignal() = postToMainThread { lifecycle.onHideSignal() }

    override fun onScreenResumed(activity: Activity) {
        lifecycle.onScreenResumed(activity, activity.javaClass.name)
    }

    override fun onScreenDestroyed(activity: Activity) {
        lifecycle.onScreenDestroyed(activity, activity.isChangingConfigurations)
    }

    override fun onPageFinished() = lifecycle.onPageFinished()

    override fun onPageBroken() = lifecycle.onPageBroken()

    override fun attach(screen: Activity): Boolean {
        if (screen.isFinishing || screen.isDestroyed) return false
        val box = attachment.view ?: createViews(screen)
        if (!attachment.attachTo(screen)) return false
        onAttached(screen, box)
        val (top, bottom) = ViewCompat.getRootWindowInsets(box)?.let(::decorSafeArea) ?: (0 to 0)
        val view = webView ?: return true
        if (loaded) {
            view.applySafeArea(top, bottom)
        } else {
            loaded = true
            view.show(html, top, bottom, this)
        }
        return true
    }

    override fun detach() = attachment.detach()

    override fun startWaitTimer(generation: Int) {
        timers.postDelayed({ lifecycle.onWaitExpired(generation) }, POPUP_SCREEN_WAIT_MS)
    }

    override fun requestAnimatedClose() {
        webView?.closeView()
    }

    override fun startCloseWatchdog() {
        timers.postDelayed({ lifecycle.onCloseWatchdog() }, POPUP_CLOSE_WATCHDOG_MS)
    }

    override fun tearDown() {
        CurrentActivityHolder.removeScreenListener(this)
        timers.removeCallbacksAndMessages(null)
        val view = webView
        webView = null
        attachment.clear()
        if (view == null) {
            lifecycle.onTornDown()
        } else {
            view.removeView { lifecycle.onTornDown() }
        }
    }

    override fun recordImpression() = session.shown()

    override fun release() {
        onClosed()
        val actions = afterClose.toList()
        afterClose.clear()
        actions.forEach { it() }
    }

    private fun onAttached(screen: Activity, box: PopupContainer) {
        val decor = box.parent as? ViewGroup ?: return
        val state = Bound(screen, decor)
        bound = state
        takeInputFocus(state, box)
        hideOthersFromAccessibility(state, box)
        state.layoutListener = ViewTreeObserver.OnGlobalLayoutListener { hideOthersFromAccessibility(state, box) }
            .also { decor.viewTreeObserver.addOnGlobalLayoutListener(it) }
        registerBack(state, box)
        if (decor.hasWindowFocus()) {
            focusForAccessibility(box)
        } else {
            state.focusListener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
                if (hasFocus && bound === state) {
                    takeInputFocus(state, box)
                    focusForAccessibility(box)
                    removeFocusListener(state)
                }
            }.also { decor.viewTreeObserver.addOnWindowFocusChangeListener(it) }
        }
    }

    private fun onDetached() {
        val state = bound ?: return
        bound = null
        state.removeBack?.invoke()
        state.layoutListener?.let { state.decor.viewTreeObserver.removeOnGlobalLayoutListener(it) }
        removeFocusListener(state)
        accessibility.restore(state.screen)
        state.previousFocus?.takeIf { it.isAttachedToWindow && it.rootView === state.decor }?.requestFocus()
    }

    /** 앱 입력칸의 포커스를 팝업 웹뷰로 가져와 키보드를 내린다. 원래 포커스는 뗄 때 돌려준다. 새 화면이 늦게 잡은 포커스도 가져오도록 창이 포커스를 얻을 때 한 번 더 부른다. */
    private fun takeInputFocus(state: Bound, box: View) {
        box.post {
            val view = webView ?: return@post
            if (bound !== state || lifecycle.state == PopupState.CLOSED) return@post
            val current = state.decor.findFocus()
            if (state.previousFocus == null && current != null && !isInside(current, box)) state.previousFocus = current
            view.requestFocus()
            state.screen.window?.let { WindowCompat.getInsetsController(it, state.decor).hide(WindowInsetsCompat.Type.ime()) }
        }
    }

    private fun isInside(view: View, box: View): Boolean {
        var current: View? = view
        while (current != null) {
            if (current === box) return true
            current = current.parent as? View
        }
        return false
    }

    private fun hideOthersFromAccessibility(state: Bound, box: View) {
        for (i in 0 until state.decor.childCount) {
            val child = state.decor.getChildAt(i)
            if (child !== box) accessibility.hide(state.screen, child)
        }
    }

    private fun focusForAccessibility(box: View) {
        box.post {
            val view = webView ?: return@post
            if (lifecycle.state == PopupState.ATTACHED) {
                view.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)
            }
        }
    }

    private fun removeFocusListener(state: Bound) {
        state.focusListener?.let { state.decor.viewTreeObserver.removeOnWindowFocusChangeListener(it) }
        state.focusListener = null
    }

    private fun registerBack(state: Bound, box: PopupContainer) {
        val screen = state.screen
        val removers = mutableListOf<() -> Unit>()
        for (mechanism in popupBackMechanisms(screen is ComponentActivity, Build.VERSION.SDK_INT)) {
            when (mechanism) {
                PopupBackMechanism.ON_BACK_PRESSED -> {
                    val owner = screen as ComponentActivity
                    val callback = object : OnBackPressedCallback(true) {
                        override fun handleOnBackPressed() = lifecycle.back()
                    }
                    owner.onBackPressedDispatcher.addCallback(owner, callback)
                    removers.add { callback.remove() }
                }
                PopupBackMechanism.ON_BACK_INVOKED -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        removers.add(BackInvoked.register(screen) { lifecycle.back() })
                    }
                }
                PopupBackMechanism.KEY_EVENT -> {
                    if (state.previousFocus == null) state.previousFocus = state.decor.findFocus()
                    box.isFocusable = true
                    box.isFocusableInTouchMode = true
                    box.requestFocus()
                }
            }
        }
        state.removeBack = { removers.forEach { it() } }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private object BackInvoked {
        fun register(screen: Activity, onBack: () -> Unit): () -> Unit {
            val callback = OnBackInvokedCallback { onBack() }
            val dispatcher = screen.onBackInvokedDispatcher
            dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_OVERLAY, callback)
            return { dispatcher.unregisterOnBackInvokedCallback(callback) }
        }
    }

    @SuppressLint("JavascriptInterface")
    private fun createViews(screen: Activity): PopupContainer {
        val wrapper = MutableContextWrapper(screen)
        val view = MarketapInAppView(wrapper)
        view.addJavascriptInterface(WebAppInterface(this, session), "MarketapSDK")
        webView = view
        val box = PopupContainer(wrapper) { lifecycle.back() }
        box.addView(view)
        ViewCompat.setOnApplyWindowInsetsListener(box) { _, insets ->
            val (top, bottom) = decorSafeArea(insets)
            webView?.let { web -> web.post { web.applySafeArea(top, bottom) } }
            insets
        }
        attachment.start(box, wrapper)
        return box
    }

    /** 팝업 아래 화면을 조작할 수 없게 모든 터치를 받고, 포커스 경로에 있으면 뒤로가기 키를 받는다. */
    private class PopupContainer(context: Context, private val onBack: () -> Unit) : FrameLayout(context) {
        init {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean = true

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode != KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event)
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) onBack()
            return true
        }
    }
}
