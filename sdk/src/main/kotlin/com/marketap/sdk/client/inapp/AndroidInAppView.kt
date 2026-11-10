package com.marketap.sdk.client.inapp

import android.content.Intent
import com.marketap.sdk.client.CurrentActivityHolder
import com.marketap.sdk.client.inapp.floating.FloatingInAppView
import com.marketap.sdk.domain.repository.InAppView
import com.marketap.sdk.model.internal.inapp.HideType
import com.marketap.sdk.model.internal.inapp.InAppDisplaySlot

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

    private val slots = InAppSlots<InAppSession>()

    override fun isShowing(slot: InAppDisplaySlot): Boolean = slots.isOccupied(slot)

    override fun show(
        html: String,
        slot: InAppDisplaySlot,
        onShow: () -> Unit,
        onClick: (String) -> String,
        onHide: (HideType) -> Unit,
        onTrack: (eventName: String, properties: Map<String, Any>?) -> Unit,
        onSetUserProperties: (properties: Map<String, Any>) -> Unit,
    ): Boolean {
        val session = InAppSession(onShow, onClick, onHide, onTrack, onSetUserProperties)
        if (!slots.claim(slot, session)) return false
        when (slot) {
            InAppDisplaySlot.POPUP -> showPopup(html)
            InAppDisplaySlot.FLOATING -> FloatingInAppView.show(
                html = html,
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

    private val popup: InAppSession? get() = slots.current(InAppDisplaySlot.POPUP)

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
