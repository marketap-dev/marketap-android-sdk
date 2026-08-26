package com.marketap.sdk.client.push

import android.content.Context
import com.marketap.sdk.client.AndroidDeviceManager
import com.marketap.sdk.SdkMetadataProvider
import com.marketap.sdk.client.SharedPreferenceInternalStorage
import com.marketap.sdk.client.api.MarketapApiImpl
import com.marketap.sdk.client.api.RetryMarketapBackend
import com.marketap.sdk.domain.repository.DeviceManager
import com.marketap.sdk.domain.repository.MarketapBackend
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
    private var marketapBackend: MarketapBackend? = null
    private var deviceManager: DeviceManager? = null

    private fun initWithContext(context: Context) {
        logger.d { "Initializing PushTracker with context" }
        if (marketapBackend != null && deviceManager != null) {
            logger.d { "PushTracker already initialized, skipping re-initialization" }
            return
        }
        val storage = SharedPreferenceInternalStorage(context)
        val config = MarketapRegistry.config
            ?: SdkMetadataProvider.loadIntegrationInfo(storage)?.let {
                SdkMetadataProvider.createConfig(projectId = "", integrationInfo = it)
            }
            ?: SdkMetadataProvider.createNativeConfig(projectId = "")
        deviceManager = AndroidDeviceManager(storage, context, config)
        marketapBackend = RetryMarketapBackend(storage, MarketapApiImpl(), deviceManager!!)
    }

    fun trackImpression(
        context: Context,
        data: PushData,
        imageDiagnostics: PushImageDiagnostics? = null,
    ) {
        logger.d {
            "Tracking Marketap push notification with notificationId, " +
                    data.notificationId.toString()
        }
        val deliveryData = data.deliveryData
        if (deliveryData == null) {
            logger.w {
                "Skipping push impression for notificationId=${data.notificationId}: " +
                        "delivery data missing (projectId/campaignId/messageId 중 누락)"
            }
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                initWithContext(context)
                val device = resolveDevice(deliveryData.deviceId)
                if (device == null) {
                    logger.w { "Skipping push impression: device info unavailable" }
                    return@launch
                }
                marketapBackend?.track(
                    deliveryData.projectId,
                    IngestEventRequest.impression(
                        deliveryData.userId,
                        device,
                        AppEventProperty.offSite(deliveryData)
                            .addProperties(imageDiagnostics?.toEventProperties() ?: emptyMap()),
                        SdkIntegrationState.toJsonString(),
                    )
                ) ?: throw IllegalStateException("MarketapBackend is not initialized")
            } catch (t: Throwable) {
                logger.e(t) { "Failed to track push impression for project ${deliveryData.projectId}: ${t.message}" }
            }
        }
    }

    /**
     * 로컬 기기 정보를 우선 쓰고, 얻지 못하면 서버가 내려준 deviceId로 최소한의 요청을 만든다.
     * 둘 다 없으면 이벤트를 보낼 수 없으므로 null.
     *
     * getDevice()/toReq() 는 null 을 돌려주는 게 아니라 던진다. AndroidDeviceManager.getDevice()
     * 의 catch 블록이 실패한 storage 접근(getOrCreateLocalId)을 다시 호출하고,
     * DeviceReq.getDeviceId() 는 식별자가 하나도 없으면 IllegalStateException 을 던진다.
     * 그래서 반드시 여기서 잡아야 한다. 트래킹 실패가 알림 표시나 딥링크를 막으면 안 된다.
     */
    private fun resolveDevice(fallbackDeviceId: String?): DeviceReq? {
        return try {
            deviceManager?.getDevice()?.toReq() ?: fallbackDeviceId?.let { DeviceReq(it) }
        } catch (t: Throwable) {
            logger.e(t) { "Failed to build device payload for push tracking" }
            fallbackDeviceId?.let { DeviceReq(it) }
        }
    }

    fun trackClick(context: Context, data: DeliveryData) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                initWithContext(context)
                val device = resolveDevice(data.deviceId)
                if (device == null) {
                    logger.w { "Skipping push click: device info unavailable" }
                    return@launch
                }
                marketapBackend?.track(
                    data.projectId,
                    IngestEventRequest.click(
                        data.userId,
                        device,
                        AppEventProperty.offSite(data)
                            .addLocationId("push"),
                        SdkIntegrationState.toJsonString(),
                    )
                ) ?: throw IllegalStateException("MarketapBackend is not initialized")
            } catch (t: Throwable) {
                logger.e(t) { "Failed to track push click for project ${data.projectId}: ${t.message}" }
            }
        }

    }
}
