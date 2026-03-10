package com.marketap.sdk.presentation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.marketap.sdk.utils.logger

internal object InAppClickUrlHandler {

    fun open(context: Context, url: String) {
        val uri = Uri.parse(url)

        if (uri.scheme == "http" || uri.scheme == "https") {
            val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(browserIntent)
            return
        }

        val deepLinkIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        try {
            context.startActivity(deepLinkIntent)
        } catch (e: ActivityNotFoundException) {
            logger.e(e) { "딥링크를 처리할 액티비티가 없음: $uri" }
        }
    }
}
