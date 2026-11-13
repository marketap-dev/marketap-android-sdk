package com.marketap.sdk.client.inapp

import com.marketap.sdk.client.CurrentActivityHolder
import com.marketap.sdk.client.inapp.floating.FloatingInAppView
import com.marketap.sdk.client.inapp.popup.PopupHolder
import com.marketap.sdk.client.inapp.popup.PopupInAppView
import com.marketap.sdk.domain.repository.InAppView
import com.marketap.sdk.model.internal.inapp.HideType
import com.marketap.sdk.model.internal.inapp.InAppDisplaySlot
import com.marketap.sdk.model.internal.inapp.InAppOrigin

class AndroidInAppView : InAppView {
    companion object {
        private var instance: AndroidInAppView? = null
        fun getInstance(): AndroidInAppView {
            if (instance == null) {
                instance = AndroidInAppView()
            }
            return instance!!
        }
    }

    private val slots = InAppSlots<Any>()

    init {
        CurrentActivityHolder.addObscuredListener { activity ->
            closePageFloating { it.origin != null && FloatingInAppView.isHostedBy(activity) }
        }
    }

    override fun isShowing(slot: InAppDisplaySlot): Boolean = slots.isOccupied(slot)

    override fun show(
        html: String,
        slot: InAppDisplaySlot,
        origin: InAppOrigin?,
        closeOnPageLeave: Boolean,
        onShow: () -> Unit,
        onClick: (String) -> String,
        onHide: (HideType) -> Unit,
        onTrack: (eventName: String, properties: Map<String, Any>?) -> Unit,
        onSetUserProperties: (properties: Map<String, Any>) -> Unit,
    ): Boolean {
        val session = InAppSession(onShow, onClick, onHide, onTrack, onSetUserProperties, origin, closeOnPageLeave)
        if (!slots.claim(slot, session)) return false
        when (slot) {
            InAppDisplaySlot.POPUP -> showPopup(html, session)
            InAppDisplaySlot.FLOATING -> FloatingInAppView.show(
                html = html,
                origin = origin,
                stayOnHost = closeOnPageLeave && origin != null,
                timerStartAtMillis = System.currentTimeMillis(),
                callback = session,
                onShown = { session.shown() },
                onClosed = { slots.release(InAppDisplaySlot.FLOATING, session) },
            )
        }
        return true
    }

    /** 자리는 팝업 View 가 정리를 마칠 때 반납된다. */
    private val popupHolder = PopupHolder<PopupInAppView>()

    private fun showPopup(html: String, session: InAppSession) {
        lateinit var view: PopupInAppView
        view = PopupInAppView(html, session) {
            synchronized(slots) {
                slots.release(InAppDisplaySlot.POPUP, session)
                popupHolder.clear(view)
            }
        }
        popupHolder.publishThenShow(view) { it.show() }
    }

    /** [origin] 웹뷰가 페이지를 벗어났다. 그 웹뷰에서 시작돼 화면에 나타나 있는 "이 페이지에서만" 플로팅과 팝업을 닫는다. 아직 나타나지 않은 메시지는 새 페이지에 뜬다. */
    internal fun closeForPageLeave(origin: InAppOrigin) {
        closePageFloating(shownFrom(origin))
        closePopup(shownFrom(origin))
    }

    /** [origin] 웹뷰가 화면에서 떨어졌다. 페이지 이동과 같이 화면에 나타나 있는 메시지만 닫는다. */
    internal fun closeForOriginGone(origin: InAppOrigin) {
        closeForPageLeave(origin)
    }

    override fun closeForAppScreenChange(shownBeforeMs: Long) {
        closePageFloating(shownBefore(shownBeforeMs))
        closePopup(shownBefore(shownBeforeMs))
    }

    private fun closePopup(match: (InAppSession) -> Boolean) {
        val view = popupHolder.current ?: return
        popupToClose(view.session, match) ?: return
        view.close()
    }

    private fun closePageFloating(match: (InAppSession) -> Boolean) {
        val session = synchronized(slots) {
            pageFloatingToClose(slots.current(InAppDisplaySlot.FLOATING), match)
                ?.takeIf { slots.release(InAppDisplaySlot.FLOATING, it) }
        } ?: return
        FloatingInAppView.dismiss(session)
    }
}

internal fun pageFloatingToClose(holder: Any?, match: (InAppSession) -> Boolean): InAppSession? {
    val session = holder as? InAppSession ?: return null
    if (!session.closeOnPageLeave) return null
    return session.takeIf(match)
}

internal fun popupToClose(holder: Any?, match: (InAppSession) -> Boolean): InAppSession? =
    (holder as? InAppSession)?.takeIf(match)


internal fun shownFrom(origin: InAppOrigin): (InAppSession) -> Boolean = { it.origin === origin && it.shownAtMs != null }

internal fun shownBefore(cutoffMs: Long): (InAppSession) -> Boolean = { session -> session.shownAtMs?.let { it < cutoffMs } ?: false }
