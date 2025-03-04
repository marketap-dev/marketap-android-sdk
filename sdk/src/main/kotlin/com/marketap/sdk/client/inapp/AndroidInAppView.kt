package com.marketap.sdk.client.inapp

import android.app.Application
import android.content.Intent
import com.marketap.sdk.domain.repository.InAppView
import com.marketap.sdk.model.internal.inapp.HideType

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

    private var onClick: ((String) -> Unit)? = null
    private var onHide: ((HideType) -> Unit)? = null
    private var isShown = false
    private var application: Application? = null

    fun init(application: Application) {
        this.application = application
    }

    override fun show(
        html: String,
        onShow: () -> Unit,
        onClick: (String) -> Unit,
        onHide: (HideType) -> Unit
    ) {
        val app = this.application
        if (isShown || app == null) {
            return
        }
        isShown = true
        onShow()

        val intent = Intent(app, InAppMessageActivity::class.java).apply {
            putExtra("htmlData", html)
            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        app.startActivity(intent)
    }

    override fun onHide(hideType: HideType) {
        onHide?.invoke(hideType)
        onHide = null
        isShown = false
    }

    override fun onClick(locationId: String) {
        onClick?.invoke(locationId)
        onClick = null
    }

}