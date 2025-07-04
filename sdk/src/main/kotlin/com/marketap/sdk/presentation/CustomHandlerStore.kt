package com.marketap.sdk.presentation

import android.app.Activity
import android.content.Context
import com.marketap.sdk.model.external.MarketapClickEvent
import com.marketap.sdk.model.external.MarketapClickHandler
import com.marketap.sdk.presentation.MarketapRegistry.marketapClickHandler
import com.marketap.sdk.utils.ManifestUtils
import com.marketap.sdk.utils.logger

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
            logger.d("Marketap SDK click handled by custom handler")
            if (handler == null) {
                logger.w("Marketap SDK click handler is not set, pending click and launching app if task root")
                pendingClick = click
                if (activity.isTaskRoot) {
                    activity.packageManager.getLaunchIntentForPackage(activity.packageName)?.let {
                        activity.startActivity(it)
                    }
                }
            } else {
                try {
                    handler.handleClick(click)
                    logger.d("Marketap SDK click handled by custom handler successfully")
                } catch (e: Exception) {
                    logger.e(
                        "Error handling click with custom handler: ${e.message}",
                        exception = e
                    )
                }
            }
            true
        } else {
            false
        }
    }

    fun setClickHandler(handler: MarketapClickHandler?) {
        logger.d("Marketap SDK set click handler")
        marketapClickHandler = handler
        if (handler != null && pendingClick != null) {
            logger.d("Marketap SDK pending click handled by custom handler", "$pendingClick")
            handler.handleClick(pendingClick!!)
            pendingClick = null
        }
    }
}