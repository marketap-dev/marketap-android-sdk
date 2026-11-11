package com.marketap.sdk.client.inapp

import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import com.marketap.sdk.client.CurrentActivityHolder
import com.marketap.sdk.client.inapp.floating.FloatingInAppView
import com.marketap.sdk.domain.repository.InAppView
import com.marketap.sdk.model.internal.inapp.HideType
import com.marketap.sdk.model.internal.inapp.InAppDisplaySlot
import com.marketap.sdk.model.internal.inapp.InAppOrigin
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/** 팝업이 닫힌 뒤 같은 화면의 화면 전환 신호를 무시하는 시간(ms). 화면이 다시 나타나며 부르는 호출만 거른다. */
private const val POPUP_RETURN_WINDOW_MS = 500L

class AndroidInAppView : InAppView, InAppCallback {
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
            InAppDisplaySlot.POPUP -> showPopup(html, session.id)
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

    private fun showPopup(html: String, sessionId: Long) {
        CurrentActivityHolder.useActivity { activity ->
            val intent = Intent(activity, InAppMessageActivity::class.java).apply {
                putExtra("htmlData", html)
                putExtra(InAppMessageActivity.EXTRA_SESSION_ID, sessionId)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }

            activity.startActivity(intent)
        }
    }

    private val popup: InAppSession? get() = slots.current(InAppDisplaySlot.POPUP) as? InAppSession

    internal val popupOrigin: InAppOrigin? get() = popup?.origin

    /** [origin] 웹뷰가 페이지를 벗어났다. 그 웹뷰에서 시작돼 화면에 나타나 있는 "이 페이지에서만" 플로팅과 팝업을 닫는다. 아직 나타나지 않은 메시지는 새 페이지에 뜬다. */
    internal fun closeForPageLeave(origin: InAppOrigin) {
        closePageFloating(shownFrom(origin))
        closePopup(shownFrom(origin))
    }

    /** [origin] 웹뷰가 화면에서 떨어졌다. 페이지 이동과 같이 화면에 나타나 있는 메시지만 닫는다. */
    internal fun closeForOriginGone(origin: InAppOrigin) {
        closeForPageLeave(origin)
    }

    @Volatile
    private var popupToCloseId: Long? = null

    private var popupHostRef: WeakReference<Activity>? = null
    private var popupClosedAtMs = 0L

    /** 팝업 Activity 가 닫히기 시작했다. 그 아래 있던 화면을 기억한다. */
    internal fun onPopupFinishing() {
        synchronized(this) {
            popupHostRef = CurrentActivityHolder.getScreen()?.let { WeakReference(it) }
            popupClosedAtMs = SystemClock.elapsedRealtime()
        }
    }

    override fun consumeReturnFromPopup(): Boolean = synchronized(this) {
        val host = popupHostRef?.get() ?: return false
        popupHostRef = null
        val recent = SystemClock.elapsedRealtime() - popupClosedAtMs <= POPUP_RETURN_WINDOW_MS
        recent && host === CurrentActivityHolder.getScreen()
    }

    override fun closeForAppScreenChange(shownBeforeMs: Long) {
        closePageFloating(shownBefore(shownBeforeMs))
        closePopup(shownBefore(shownBeforeMs))
    }

    private fun closePopup(match: (InAppSession) -> Boolean) {
        val session = popupToClose(popup, match) ?: return
        popupToCloseId = session.id
        val activity = InAppMessageActivity.current ?: return
        activity.runOnUiThread { activity.closeIfShowing(session.id) }
    }

    /** [sessionId] 팝업에 닫기 요청이 와 있으면 true. 팝업 화면이 뜨기 전에 온 요청을 화면이 뜰 때 확인한다. */
    internal fun isPopupCloseRequested(sessionId: Long): Boolean = popupToCloseId == sessionId

    private fun closePageFloating(match: (InAppSession) -> Boolean) {
        val session = synchronized(slots) {
            pageFloatingToClose(slots.current(InAppDisplaySlot.FLOATING), match)
                ?.takeIf { slots.release(InAppDisplaySlot.FLOATING, it) }
        } ?: return
        FloatingInAppView.dismiss(session)
    }

    fun onWebViewLoaded() {
        popup?.shown()
    }

    /** 숨김으로 자리를 비웠지만 화면은 아직 남아 있는 팝업들. 각 화면이 사라질 때까지 이벤트·유저 속성을 받는다. */
    private val hiddenPopups = ConcurrentHashMap<Long, InAppSession>()

    /** [sessionId] 팝업 화면이 닫혔다. 그 팝업이 아직 자리를 쥐고 있을 때만 반납한다. */
    internal fun releasePopup(sessionId: Long) {
        popup?.takeIf { it.id == sessionId }?.let { slots.release(InAppDisplaySlot.POPUP, it) }
        hiddenPopups.remove(sessionId)
    }

    /** [sessionId] 팝업 화면이 그리는 메시지. 숨긴 뒤에도 화면이 사라지기 전까지는 돌려준다. */
    internal fun popupSession(sessionId: Long): InAppSession? = popupSessionFor(sessionId, popup, hiddenPopups[sessionId])

    override fun onHide(hideType: HideType) {
        val session = popup ?: return
        session.onHide(hideType)
        hiddenPopups[session.id] = session
        slots.release(InAppDisplaySlot.POPUP, session)
    }

    override fun onClick(locationId: String): String? = popup?.onClick(locationId)

    override fun onTrack(eventName: String, eventPropertiesJson: String) {
        popup?.onTrack(eventName, eventPropertiesJson)
    }

    override fun onSetUserProperties(userPropertiesJson: String) {
        popup?.onSetUserProperties(userPropertiesJson)
    }
}

internal fun pageFloatingToClose(holder: Any?, match: (InAppSession) -> Boolean): InAppSession? {
    val session = holder as? InAppSession ?: return null
    if (!session.closeOnPageLeave) return null
    return session.takeIf(match)
}

internal fun popupSessionFor(sessionId: Long, current: InAppSession?, hidden: InAppSession?): InAppSession? =
    current?.takeIf { it.id == sessionId } ?: hidden?.takeIf { it.id == sessionId }

internal fun popupToClose(holder: Any?, match: (InAppSession) -> Boolean): InAppSession? =
    (holder as? InAppSession)?.takeIf(match)


internal fun shownFrom(origin: InAppOrigin): (InAppSession) -> Boolean = { it.origin === origin && it.shownAtMs != null }

internal fun shownBefore(cutoffMs: Long): (InAppSession) -> Boolean = { session -> session.shownAtMs?.let { it < cutoffMs } ?: false }
