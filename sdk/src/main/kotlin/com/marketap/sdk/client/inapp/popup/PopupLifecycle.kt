package com.marketap.sdk.client.inapp.popup

import com.marketap.sdk.client.inapp.floating.DestroyAction
import com.marketap.sdk.client.inapp.floating.FloatingAttachPolicy
import com.marketap.sdk.client.inapp.floating.ResumeAction
import java.lang.ref.WeakReference

internal enum class PopupState {
    /** 붙을 화면을 기다린다. 처음 띄울 때와 붙은 화면이 파괴된 뒤. */
    WAITING,
    ATTACHED,

    /** 닫힘 애니메이션을 기다리거나 웹뷰를 정리하는 중. 클릭은 받지 않고 이벤트·유저 속성은 받는다. */
    CLOSING,
    CLOSED,
}

/**
 * 팝업 하나의 붙임·닫힘 상태. 메인 스레드에서만 부른다([state] 읽기는 어느 스레드든 된다).
 * 정리([Effects.tearDown])는 한 번만 시작되고, [onTornDown] 이후 자리 반납([Effects.release])도 한 번만 일어난다.
 */
internal class PopupLifecycle<S : Any>(private val effects: Effects<S>) {

    interface Effects<S> {
        /** [screen] 에 붙인다. 붙일 수 없으면 false. */
        fun attach(screen: S): Boolean
        fun detach()

        /** [generation] 을 기억했다가 시한이 지나면 [onWaitExpired] 로 돌려준다. */
        fun startWaitTimer(generation: Int)
        fun requestAnimatedClose()

        /** 시한이 지나면 [onCloseWatchdog] 를 부른다. */
        fun startCloseWatchdog()

        /** 화면에서 떼고 웹뷰를 정리한 뒤, 예외가 나더라도 [onTornDown] 을 부른다. */
        fun tearDown()
        fun recordImpression()
        fun release()
    }

    @Volatile
    var state: PopupState = PopupState.WAITING
        private set

    private var host: WeakReference<S>? = null
    private var waitGeneration = 0
    private var tearingDown = false
    private var pageFinished = false
    private var impressionRecorded = false

    val acceptsClicks: Boolean get() = state == PopupState.ATTACHED

    /** @param resumed 지금 앞에 나와 있는 화면. 없으면 다음에 앞에 오는 화면을 기다린다. */
    fun start(resumed: S?) {
        if (state != PopupState.WAITING || tearingDown) return
        if (resumed != null) attachTo(resumed)
    }

    fun onScreenResumed(screen: S, screenClass: String) {
        when (state) {
            PopupState.WAITING -> if (!tearingDown) attachTo(screen)
            PopupState.ATTACHED -> {
                val action = FloatingAttachPolicy.onResumed(
                    isHost = host?.get() === screen,
                    recreatingClass = null,
                    activityClass = screenClass,
                    stayOnHost = false,
                )
                if (action == ResumeAction.MOVE) {
                    effects.detach()
                    host = null
                    if (!attachTo(screen)) enterWaiting()
                }
            }
            PopupState.CLOSING, PopupState.CLOSED -> Unit
        }
    }

    fun onScreenDestroyed(screen: S, isChangingConfigurations: Boolean) {
        val isHost = host?.get() === screen
        when (state) {
            PopupState.ATTACHED -> {
                val action = FloatingAttachPolicy.onDestroyed(isHost, isChangingConfigurations, stayOnHost = false)
                if (action != DestroyAction.IGNORE) {
                    effects.detach()
                    host = null
                    enterWaiting()
                }
            }
            PopupState.CLOSING -> if (isHost) finish()
            PopupState.WAITING, PopupState.CLOSED -> Unit
        }
    }

    fun onWaitExpired(generation: Int) {
        if (state == PopupState.WAITING && generation == waitGeneration) finish()
    }

    /** 닫기 조건. 닫힘 애니메이션 없이 바로 정리한다. 붙기 전이면 붙이지 않고 닫는다. */
    fun close() = finish()

    /** 뒤로가기. 템플릿의 닫힘 애니메이션을 요청하고, 숨김 신호가 오지 않으면 감시 시한에 정리한다. */
    fun back() {
        if (state != PopupState.ATTACHED) return
        state = PopupState.CLOSING
        effects.startCloseWatchdog()
        effects.requestAnimatedClose()
    }

    fun onCloseWatchdog() {
        if (state == PopupState.CLOSING) finish()
    }

    fun onHideSignal() = finish()

    /** 렌더 프로세스 종료, 첫 페이지 로드 실패. */
    fun onPageBroken() = finish()

    /** 노출은 화면에 붙어 있을 때 문서가 다 불러와진 뒤 한 번 기록한다. 화면을 기다리는 동안 끝났으면 다시 붙을 때 기록한다. */
    fun onPageFinished() {
        pageFinished = true
        if (state == PopupState.ATTACHED) recordImpressionOnce()
    }

    fun onTornDown() {
        if (state == PopupState.CLOSED) return
        state = PopupState.CLOSED
        effects.release()
    }

    private fun attachTo(screen: S): Boolean {
        if (!effects.attach(screen)) return false
        host = WeakReference(screen)
        waitGeneration += 1
        state = PopupState.ATTACHED
        if (pageFinished) recordImpressionOnce()
        return true
    }

    private fun enterWaiting() {
        state = PopupState.WAITING
        waitGeneration += 1
        effects.startWaitTimer(waitGeneration)
    }

    private fun finish() {
        if (tearingDown || state == PopupState.CLOSED) return
        tearingDown = true
        waitGeneration += 1
        state = PopupState.CLOSING
        host = null
        effects.tearDown()
    }

    private fun recordImpressionOnce() {
        if (impressionRecorded) return
        impressionRecorded = true
        effects.recordImpression()
    }
}
