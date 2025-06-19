package com.marketap.sdk.client.push

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationManagerCompat
import com.marketap.sdk.Marketap.config
import com.marketap.sdk.client.SharedPreferenceInternalStorage
import com.marketap.sdk.client.api.MarketapApiImpl
import com.marketap.sdk.client.push.MarketapNotificationOpenHandler.Companion.CAMPAIGN_KEY
import com.marketap.sdk.client.push.MarketapNotificationOpenHandler.Companion.IS_NOTIFICATION_FROM_MARKETAP
import com.marketap.sdk.client.push.MarketapNotificationOpenHandler.Companion.NOTIFICATION_DEEP_LINK_KEY
import com.marketap.sdk.client.push.MarketapNotificationOpenHandler.Companion.NOTIFICATION_URL_KEY
import com.marketap.sdk.model.external.MarketapCampaignType
import com.marketap.sdk.model.external.MarketapClickEvent
import com.marketap.sdk.model.internal.AppEventProperty
import com.marketap.sdk.model.internal.api.DeviceReq
import com.marketap.sdk.model.internal.api.IngestEventRequest
import com.marketap.sdk.model.internal.push.DeliveryData
import com.marketap.sdk.presentation.CustomHandlerStore
import com.marketap.sdk.utils.PairEntry
import com.marketap.sdk.utils.getNow
import com.marketap.sdk.utils.pairAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MarketapTrampolineActivity : Activity() {
    private val marketapApi = MarketapApiImpl(debug = config?.debug == true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        if (intent.getBooleanExtra(IS_NOTIFICATION_FROM_MARKETAP, false)) {
            val deepLink = intent.getStringExtra(NOTIFICATION_DEEP_LINK_KEY)
            val url = intent.getStringExtra(NOTIFICATION_URL_KEY)
            val data: DeliveryData? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getSerializableExtra(CAMPAIGN_KEY, DeliveryData::class.java)
            } else {
                intent.getSerializableExtra(CAMPAIGN_KEY) as? DeliveryData?
            }
            if (data != null) {
                track(data)
                if (CustomHandlerStore.useClickHandler {
                        it.handleClick(
                            MarketapClickEvent(
                                MarketapCampaignType.PUSH,
                                data.campaignId,
                                deepLink ?: url
                            )
                        )
                        true
                    }
                ) {
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

            launchIntent?.let {
                startActivity(it)
            }
        } else {
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

    private fun track(data: DeliveryData) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                marketapApi.track(
                    data.projectId,
                    IngestEventRequest.click(
                        data.userId,
                        DeviceReq(data.deviceId),
                        AppEventProperty.offSite(data)
                            .addLocationId("push"),
                        getNow()
                    )
                )
            } catch (e: Exception) {
                val storage = SharedPreferenceInternalStorage(this@MarketapTrampolineActivity)
                storage.queueItem(
                    "events", PairEntry(
                        data.projectId, IngestEventRequest.click(
                            data.userId,
                            DeviceReq(data.deviceId),
                            AppEventProperty.offSite(data)
                                .addLocationId("push"),
                            getNow()
                        )
                    ), pairAdapter()
                )
            }
        }

    }

    override fun onPause() {
        super.onPause()
        finish()
    }
}