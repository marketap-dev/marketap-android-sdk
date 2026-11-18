package com.marketap.sdk.client.push

import android.content.Context
import com.marketap.sdk.client.AndroidDeviceManager
import com.marketap.sdk.client.AndroidSessionManager
import com.marketap.sdk.SdkMetadataProvider
import com.marketap.sdk.client.SharedPreferenceInternalStorage
import com.marketap.sdk.client.api.MarketapApiImpl
import com.marketap.sdk.client.api.RetryMarketapBackend
import com.marketap.sdk.domain.repository.DeviceManager
import com.marketap.sdk.domain.repository.InternalStorage
import com.marketap.sdk.domain.service.event.SessionUtmStore
import com.marketap.sdk.model.internal.AppEventProperty
import com.marketap.sdk.model.internal.SdkIntegrationState
import com.marketap.sdk.model.internal.api.DeviceReq
import com.marketap.sdk.model.internal.api.DeviceReq.Companion.toReq
import com.marketap.sdk.model.internal.api.IngestEventRequest
import com.marketap.sdk.model.internal.push.DeliveryData
import com.marketap.sdk.model.internal.push.PushData
import com.marketap.sdk.presentation.MarketapRegistry
import com.marketap.sdk.utils.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal object PushTracker {
    private var marketapBackend: RetryMarketapBackend? = null
    private var storage: InternalStorage? = null
    private var deviceManager: DeviceManager? = null

    private fun initWithContext(context: Context) {
        logger.d { "Initializing PushTracker with context" }
        if (marketapBackend != null && deviceManager != null) {
            logger.d { "PushTracker already initialized, skipping re-initialization" }
            return
        }
        val storage = SharedPreferenceInternalStorage(context)
        this.storage = storage
        val config = MarketapRegistry.config
            ?: SdkMetadataProvider.loadIntegrationInfo(storage)?.let {
                SdkMetadataProvider.createConfig(projectId = "", integrationInfo = it)
            }
            ?: SdkMetadataProvider.createNativeConfig(projectId = "")
        deviceManager = AndroidDeviceManager(storage, context, config)
        marketapBackend = RetryMarketapBackend(
            storage,
            MarketapApiImpl(
                clientKeyProvider = { projectId -> SdkMetadataProvider.currentClientKey(storage, projectId) },
                source = "android-push",
                config = config
            ),
            deviceManager!!,
            pushOnly = true,
            sdkType = config.sdkType,
            sdkVersion = config.sdkVersion
        )
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
                            SdkIntegrationState.toJsonString(),
                        )
                    ) ?: throw IllegalStateException("MarketapBackend is not initialized")
                } catch (t: Throwable) {
                    logger.e(t) { "Failed to track push impression for project ${it.projectId}: ${t.message}" }
                }
            }
        }
    }

    /** 푸시를 누른 시점에 진행 중인 세션의 UTM. 세션을 새로 열지 않는다. */
    private fun activeSessionUtm(projectId: String): Map<String, String> {
        val storage = storage ?: return emptyMap()
        val sessionId = AndroidSessionManager(storage).activeSessionId() ?: return emptyMap()
        return SessionUtmStore(
            storage,
            enabled = { marketapBackend?.remotePolicy?.currentlyAllowed(projectId, "sessionUtm") ?: true },
        ).stored(sessionId)
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
                        SdkIntegrationState.toJsonString(),
                        activeSessionUtm(data.projectId),
                    )
                ) ?: throw IllegalStateException("MarketapBackend is not initialized")
            } catch (t: Throwable) {
                logger.e(t) { "Failed to track push click for project ${data.projectId}: ${t.message}" }
            }
        }

    }
}
