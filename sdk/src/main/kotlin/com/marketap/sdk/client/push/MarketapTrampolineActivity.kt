package com.marketap.sdk.client.push

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationManagerCompat
import com.marketap.sdk.client.push.MarketapNotificationOpenHandler.Companion.CAMPAIGN_KEY
import com.marketap.sdk.client.push.MarketapNotificationOpenHandler.Companion.IS_NOTIFICATION_FROM_MARKETAP
import com.marketap.sdk.client.push.MarketapNotificationOpenHandler.Companion.NOTIFICATION_DEEP_LINK_KEY
import com.marketap.sdk.client.push.MarketapNotificationOpenHandler.Companion.NOTIFICATION_URL_KEY
import com.marketap.sdk.model.external.MarketapCampaignType
import com.marketap.sdk.model.external.MarketapClickEvent
import com.marketap.sdk.model.internal.push.DeliveryData
import com.marketap.sdk.presentation.CustomHandlerStore
import com.marketap.sdk.utils.logger

class MarketapTrampolineActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logger.d("MarketapTrampolineActivity(Push handling activity) onCreate() called")

        if (intent.getBooleanExtra(IS_NOTIFICATION_FROM_MARKETAP, false)) {
            val deepLink = intent.getStringExtra(NOTIFICATION_DEEP_LINK_KEY)
            val url = intent.getStringExtra(NOTIFICATION_URL_KEY)
            val data: DeliveryData? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getSerializableExtra(CAMPAIGN_KEY, DeliveryData::class.java)
            } else {
                intent.getSerializableExtra(CAMPAIGN_KEY) as? DeliveryData?
            }
            if (data != null) {
                PushTracker.trackClick(this, data)
                if (CustomHandlerStore.maybeHandleClick(
                        this, MarketapClickEvent(
                            MarketapCampaignType.PUSH,
                            data.campaignId,
                            deepLink ?: url
                        )
                    )
                ) {
                    logger.d("Push Click handled by custom click handler")
                    quit()
                    return
                }
            }

            // 딥링크 or 앱 런치
            val launchIntent = if (!deepLink.isNullOrEmpty()) {
                Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            } else if (!url.isNullOrEmpty()) {
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            } else {
                packageManager.getLaunchIntentForPackage(packageName)?.apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            }

            logger.d("Launching Marketap notification with deepLink: $deepLink, url: $url")
            launchIntent?.let {
                startActivity(it)
            }
        } else {
            logger.w("MarketapTrampolineActivity launched without valid Marketap notification data")
            startActivity(packageManager.getLaunchIntentForPackage(packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            })
        }

        quit()
    }

    private fun quit() {
        val id = intent.getIntExtra(
            MarketapNotificationOpenHandler.NOTIFICATION_ID_KEY,
            -1
        )
        if (id != -1) {
            NotificationManagerCompat.from(this).cancel(id)
        }

        // 안전하게 finish() (onPause() 보장 안 될 수 있어서 딜레이 후 종료)
        window.decorView.postDelayed({
            finish()
        }, 200)
    }


    override fun onPause() {
        super.onPause()
        finish()
    }
}