package com.marketap.sdk.client.api

import com.marketap.sdk.client.api.coroutine.WorkerGroup
import com.marketap.sdk.domain.repository.DeviceManager
import com.marketap.sdk.domain.repository.InternalStorage
import com.marketap.sdk.domain.repository.MarketapBackend
import com.marketap.sdk.model.internal.api.DeviceReq
import com.marketap.sdk.model.internal.api.DeviceReq.Companion.toReq
import com.marketap.sdk.model.internal.api.FetchCampaignReq
import com.marketap.sdk.model.internal.api.InAppCampaignRes
import com.marketap.sdk.model.internal.api.IngestEventRequest
import com.marketap.sdk.model.internal.api.UpdateProfileRequest
import com.marketap.sdk.utils.PairEntry
import com.marketap.sdk.utils.getNowByMillis
import com.marketap.sdk.utils.logger
import com.marketap.sdk.utils.longAdapter
import com.marketap.sdk.utils.pairAdapter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

internal class RetryMarketapBackend(
    private val storage: InternalStorage,
    private val marketapApi: MarketapApi,
    private val deviceManager: DeviceManager,
) : MarketapBackend {
    private val apiWorkGroup = WorkerGroup().apply { start() }

    private fun getSafeDevice(removeUserId: Boolean?): DeviceReq? {
        return if (deviceManager.isDeviceReady()) {
            deviceManager.getDevice().toReq(removeUserId)
        } else {
            null
        }
    }

    private suspend fun checkUserQueue() {
        val items =
            storage.popItems(
                "users",
                pairAdapter<String, UpdateProfileRequest>(),
                10
            )
        items.forEach { (projectId, request) ->
            apiWorkGroup.dispatch {
                try {
                    val device = getSafeDevice(request.device.removeUserId)
                    if (device != null) {
                        marketapApi.updateProfile(projectId, request.copy(device = device))
                    } else {
                        throw IllegalStateException("Device is not ready")
                    }
                } catch (e: Exception) {
                    storage.queueItem(
                        "users",
                        PairEntry(projectId, request),
                        pairAdapter<String, UpdateProfileRequest>()
                    )
                }
            }
        }
    }

    private suspend fun checkEventQueue() {
        val items =
            storage.popItems("events", pairAdapter<String, IngestEventRequest>(), 10)
        items.forEach { (projectId, request) ->
            apiWorkGroup.dispatch {
                try {
                    val device = getSafeDevice(request.device.removeUserId)
                    if (device != null) {
                        marketapApi.track(projectId, request.copy(device = device))
                    } else {
                        throw IllegalStateException("Device is not ready")
                    }
                } catch (e: Exception) {
                    storage.queueItem(
                        "events",
                        PairEntry(projectId, request),
                        pairAdapter<String, IngestEventRequest>()
                    )
                }
            }
        }
    }

    private suspend fun checkDeviceQueue() {
        val items =
            storage.popItems("devices", pairAdapter<String, DeviceReq>(), 10)
        items.forEach { (projectId, request) ->
            apiWorkGroup.dispatch {
                try {
                    val device = getSafeDevice(request.removeUserId)
                    if (device != null) {
                        marketapApi.updateDevice(projectId, device)
                    } else {
                        throw IllegalStateException("Device is not ready")
                    }
                } catch (e: Exception) {
                    storage.queueItem(
                        "devices",
                        PairEntry(projectId, request),
                        pairAdapter<String, DeviceReq>()
                    )
                }
            }
        }
    }

    override fun updateDevice(projectId: String, request: DeviceReq) {
        logger.d {
            "updating device of project $projectId, " +
                    "deviceId: ${request.deviceId}, properties: ${request.properties}"
        }
        storage.queueItem("devices", PairEntry(projectId, request), pairAdapter())
        apiWorkGroup.dispatch(::checkDeviceQueue)
    }

    override fun fetchCampaigns(
        request: FetchCampaignReq,
        inTimeout: ((InAppCampaignRes) -> Unit)?, // timeout 이내에 호출 되면 실행
        onSuccess: (InAppCampaignRes) -> Unit
    ) {
        runBlocking {
            logger.d { "fetching campaigns, request: $request" }
            val deferredResponse = CompletableDeferred<InAppCampaignRes>()

            apiWorkGroup.dispatch {
                try {
                    val res = marketapApi.fetchCampaigns(request)
                    if (res.data != null) {
                        onSuccess(res.data)
                        deferredResponse.complete(res.data)
                    } else {
                        deferredResponse.cancel()
                    }
                } catch (e: Exception) {
                    deferredResponse.cancel()
                }
            }

            val result = withTimeoutOrNull(500) {
                deferredResponse.await()
            }

            if (result != null) {
                inTimeout?.invoke(result)
            } else {
                logger.w { "fetchCampaigns timed out for request: $request" }
            }
        }
    }

    override fun track(projectId: String, request: IngestEventRequest) {
        logger.d {
            "tracking event for project $projectId, " +
                    "eventName: ${request.name}, properties: ${request.properties}"
        }

        CoroutineScope(Dispatchers.IO).launch {
            request.timestamp = getNowAsString()
            storage.queueItem("events", PairEntry(projectId, request), pairAdapter())
            apiWorkGroup.dispatch(::checkEventQueue)
            apiWorkGroup.dispatch(::checkUserQueue)
            apiWorkGroup.dispatch(::checkDeviceQueue)
        }
    }

    override fun updateProfile(projectId: String, request: UpdateProfileRequest) {
        logger.d {
            "updating profile for project $projectId, " +
                    "userId: ${request.userId}, properties: ${request.properties}"
        }
        CoroutineScope(Dispatchers.IO).launch {
            request.timestamp = getNowAsString()
            storage.queueItem("users", PairEntry(projectId, request), pairAdapter())
            apiWorkGroup.dispatch(::checkEventQueue)
            apiWorkGroup.dispatch(::checkUserQueue)
            apiWorkGroup.dispatch(::checkDeviceQueue)
        }
    }


    private fun getNowAsString(): String {
        val now = System.currentTimeMillis()
        val offset = storage.cacheAndGetItem(
            "server_time_offset",
            {
                runBlocking {
                    val offset = withTimeoutOrNull(5000) {
                        marketapApi.getServerTimeOffset()
                    }

                    if (offset != null) {
                        logger.d { "Server time offset: $offset ms" }
                        offset
                    } else {
                        logger.w { "Failed to get server time offset, using 0" }
                        0L
                    }
                }
            },
            longAdapter,
            60 * 1000L
        ) ?: 0L
        return getNowByMillis(now + offset)
    }
}