package com.marketap.sdk.client.push

import android.content.Context
import com.marketap.sdk.client.AndroidDeviceManager
import com.marketap.sdk.client.SharedPreferenceInternalStorage
import com.marketap.sdk.client.api.MarketapApiImpl
import com.marketap.sdk.client.api.RetryMarketapBackend
import com.marketap.sdk.domain.repository.DeviceManager
import com.marketap.sdk.domain.repository.MarketapBackend
import com.marketap.sdk.model.internal.AppEventProperty
import com.marketap.sdk.model.internal.api.DeviceReq
import com.marketap.sdk.model.internal.api.DeviceReq.Companion.toReq
import com.marketap.sdk.model.internal.api.IngestEventRequest
import com.marketap.sdk.model.internal.push.DeliveryData
import com.marketap.sdk.model.internal.push.PushData
import com.marketap.sdk.utils.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal object PushTracker {
    private var marketapBackend: MarketapBackend? = null
    private var deviceManager: DeviceManager? = null

    private fun initWithContext(context: Context) {
        logger.d { "Initializing PushTracker with context" }
        if (marketapBackend != null && deviceManager != null) {
            logger.d { "PushTracker already initialized, skipping re-initialization" }
            return
        }
        val storage = SharedPreferenceInternalStorage(context)
        deviceManager = AndroidDeviceManager(storage)
        marketapBackend = RetryMarketapBackend(storage, MarketapApiImpl(), deviceManager!!)
    }

    fun trackImpression(context: Context, data: PushData) {
        logger.d {
            "Tracking Marketap push notification with notificationId, " +
                    data.notificationId.toString()
        }
        initWithContext(context)
        data.deliveryData?.let {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    marketapBackend?.track(
                        it.projectId,
                        IngestEventRequest.impression(
                            it.userId,
                            deviceManager?.getDevice()?.toReq() ?: DeviceReq(it.deviceId),
                            AppEventProperty.offSite(it),
                        )
                    ) ?: throw IllegalStateException("MarketapBackend is not initialized")
                } catch (e: Exception) {
                    logger.e(e) { "Failed to track push impression for project ${it.projectId}: ${e.message}" }
                }
            }
        }
    }

    fun trackClick(context: Context, data: DeliveryData) {
        initWithContext(context)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                marketapBackend?.track(
                    data.projectId,
                    IngestEventRequest.click(
                        data.userId,
                        deviceManager?.getDevice()?.toReq() ?: DeviceReq(data.deviceId),
                        AppEventProperty.offSite(data)
                            .addLocationId("push"),
                    )
                ) ?: throw IllegalStateException("MarketapBackend is not initialized")
            } catch (e: Exception) {
                logger.e(e) { "Failed to track push click for project ${data.projectId}: ${e.message}" }
            }
        }

    }
}