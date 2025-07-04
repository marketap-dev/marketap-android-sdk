package com.marketap.sdk.presentation

import android.app.Activity
import android.content.Context
import com.marketap.sdk.model.external.MarketapClickEvent
import com.marketap.sdk.model.external.MarketapClickHandler
import com.marketap.sdk.presentation.MarketapRegistry.marketapClickHandler
import com.marketap.sdk.utils.ManifestUtils

internal object CustomHandlerStore {
    private fun isCustomized(context: Context): Boolean {
        return ManifestUtils.getSystemBoolean(
            context, ManifestUtils.SystemBooleanConstant.IS_CLICK_CUSTOMIZED
        )
    }

    private var pendingClick: MarketapClickEvent? = null
    fun maybeHandleClick(
        activity: Activity,
        click: MarketapClickEvent,
    ): Boolean {
        val handler = marketapClickHandler
        return if (isCustomized(activity.applicationContext)) {
            if (handler == null) {
                pendingClick = click
                if (activity.isTaskRoot) {
                    activity.packageManager.getLaunchIntentForPackage(activity.packageName)?.let {
                        activity.startActivity(it)
                    }
                }
            } else {
                handler.handleClick(click)
            }
            true
        } else {
            false
        }
    }

    fun setClickHandler(handler: MarketapClickHandler?) {
        marketapClickHandler = handler
        if (handler != null && pendingClick != null) {
            handler.handleClick(pendingClick!!)
            pendingClick = null
        }
    }
}