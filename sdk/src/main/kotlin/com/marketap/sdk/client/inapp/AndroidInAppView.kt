package com.marketap.sdk.client.inapp

import android.content.Intent
import com.marketap.sdk.client.CurrentActivityHolder
import com.marketap.sdk.client.inapp.floating.FloatingInAppView
import com.marketap.sdk.domain.repository.InAppView
import com.marketap.sdk.model.internal.inapp.HideType
import com.marketap.sdk.model.internal.inapp.InAppDisplaySlot
import com.marketap.sdk.model.internal.inapp.InAppOrigin

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
        CurrentActivityHolder.addObscuredListener { activity -> closePageFloating { it.isShownIn(activity) } }
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
            InAppDisplaySlot.POPUP -> showPopup(html)
            InAppDisplaySlot.FLOATING -> FloatingInAppView.show(
                html = html,
                origin = origin,
                stayOnHost = closeOnPageLeave,
                timerStartAtMillis = System.currentTimeMillis(),
                callback = session,
                onShown = { session.shown() },
                onClosed = { slots.release(InAppDisplaySlot.FLOATING, session) },
            )
        }
        return true
    }

    private fun showPopup(html: String) {
        CurrentActivityHolder.useActivity { activity ->
            val intent = Intent(activity, InAppMessageActivity::class.java).apply {
                putExtra("htmlData", html)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }

            activity.startActivity(intent)
        }
    }

    private val popup: InAppSession? get() = slots.current(InAppDisplaySlot.POPUP) as? InAppSession

    internal val popupOrigin: InAppOrigin? get() = popup?.origin

    /** [origin] 웹뷰가 페이지를 벗어났다. 그 웹뷰에서 시작된 "이 페이지에서만" 플로팅을 닫는다. */
    internal fun closePageFloating(origin: InAppOrigin) = closePageFloating { it === origin }

    private fun closePageFloating(match: (InAppOrigin) -> Boolean) {
        val session = synchronized(slots) {
            pageFloatingToClose(slots.current(InAppDisplaySlot.FLOATING), match)
                ?.takeIf { slots.release(InAppDisplaySlot.FLOATING, it) }
        } ?: return
        FloatingInAppView.dismiss(session)
    }

    fun onWebViewLoaded() {
        popup?.shown()
    }

    fun resetIfNeeded() {
        popup?.let { slots.release(InAppDisplaySlot.POPUP, it) }
    }

    override fun onHide(hideType: HideType) {
        val session = popup ?: return
        session.onHide(hideType)
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

internal fun pageFloatingToClose(holder: Any?, match: (InAppOrigin) -> Boolean): InAppSession? {
    val session = holder as? InAppSession ?: return null
    if (!session.closeOnPageLeave) return null
    val origin = session.origin ?: return null
    return session.takeIf { match(origin) }
}
