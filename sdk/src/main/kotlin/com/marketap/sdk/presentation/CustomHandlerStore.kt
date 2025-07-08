package com.marketap.sdk.presentation

import android.app.Activity
import android.content.Context
import android.content.Intent
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
            logger.d { "Marketap SDK click handled by custom handler" }
            if (activity.isTaskRoot) {
                activity.packageManager.getLaunchIntentForPackage(activity.packageName)?.apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                }?.let {
                    activity.startActivity(it)
                }
            }
            if (handler == null) {
                logger.w { "Marketap SDK click handler is not set, pending click and launching app if task root" }
                pendingClick = click
            } else {
                try {
                    handler.handleClick(click)
                    logger.d { "Marketap SDK click handled by custom handler successfully" }
                } catch (e: Exception) {
                    logger.e(e) { "Error handling click with custom handler: ${e.message}" }
                }
            }
            true
        } else {
            false
        }
    }

    fun setClickHandler(handler: MarketapClickHandler?) {
        marketapClickHandler = handler
        if (handler != null && pendingClick != null) {
            logger.d { "Marketap SDK pending click handled by custom handler: $pendingClick" }
            handler.handleClick(pendingClick!!)
            pendingClick = null
        }
    }
}