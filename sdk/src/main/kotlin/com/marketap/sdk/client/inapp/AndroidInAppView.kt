package com.marketap.sdk.client.inapp

import android.content.Intent
import com.marketap.sdk.client.CurrentActivityHolder
import com.marketap.sdk.domain.repository.InAppView
import com.marketap.sdk.model.internal.inapp.HideType
import com.marketap.sdk.utils.deserialize
import com.marketap.sdk.utils.logger
import com.marketap.sdk.utils.mapAdapter

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

    private var onClick: ((String) -> String)? = null
    private var onHide: ((HideType) -> Unit)? = null
    private var onTrack: ((eventName: String, properties: Map<String, Any>?) -> Unit)? = null
    private var onSetUserProperties: ((properties: Map<String, Any>) -> Unit)? = null
    private var isShown = false

    override fun show(
        html: String,
        onShow: () -> Unit,
        onClick: (String) -> String,
        onHide: (HideType) -> Unit,
        onTrack: (eventName: String, properties: Map<String, Any>?) -> Unit,
        onSetUserProperties: (properties: Map<String, Any>) -> Unit,
    ) {
        if (isShown) {
            return
        }
        isShown = true
        onShow()
        this.onClick = onClick
        this.onHide = onHide
        this.onTrack = onTrack
        this.onSetUserProperties = onSetUserProperties

        CurrentActivityHolder.useActivity { activity ->
            val intent = Intent(activity, InAppMessageActivity::class.java).apply {
                putExtra("htmlData", html)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }

            activity.startActivity(intent)
        }
    }

    override fun onHide(hideType: HideType) {
        onHide?.invoke(hideType)
        onHide = null
        isShown = false
        onClick = null
    }

    override fun onClick(locationId: String): String? {
        return onClick?.invoke(locationId)
    }

    override fun onTrack(eventName: String, eventPropertiesJson: String) {
        try {
            val eventProperties = eventPropertiesJson.deserialize(mapAdapter<String, Any>())
            onTrack?.invoke(eventName, eventProperties)
        } catch (error: Exception) {
            logger.e(error) { "Error parsing event properties JSON: $eventPropertiesJson" }
        }
    }

    override fun onSetUserProperties(userPropertiesJson: String) {
        try {
            val userProperties = userPropertiesJson.deserialize(mapAdapter<String, Any>())
            onSetUserProperties?.invoke(userProperties)
        } catch (error: Exception) {
            logger.e(error) { "Error parsing user properties JSON: $userPropertiesJson" }
        }
    }
}