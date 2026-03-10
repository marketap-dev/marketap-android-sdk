package com.marketap.sdk.client.api

import com.marketap.sdk.client.api.coroutine.WorkerGroup
import com.marketap.sdk.domain.repository.DeviceManager
import com.marketap.sdk.domain.repository.InternalStorage
import com.marketap.sdk.domain.repository.MarketapBackend
import com.marketap.sdk.model.internal.api.DeviceReq
import com.marketap.sdk.model.internal.api.DeviceReq.Companion.toReq
import com.marketap.sdk.model.internal.api.FetchCampaignReq
import com.marketap.sdk.model.internal.api.FetchCampaignsReq
import com.marketap.sdk.model.internal.api.InAppCampaignRes
import com.marketap.sdk.model.internal.api.InAppCampaignSingleRes
import com.marketap.sdk.model.internal.api.IngestEventRequest
import com.marketap.sdk.model.internal.api.UpdateProfileRequest
import com.marketap.sdk.utils.PairEntry
import com.marketap.sdk.utils.getNowByMillis
import com.marketap.sdk.utils.logger
import com.marketap.sdk.model.internal.MarketapServerConfig
import com.marketap.sdk.utils.pairAdapter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

internal class RetryMarketapBackend(
    private val storage: InternalStorage,
    private val marketapApi: MarketapApi,
    private val deviceManager: DeviceManager,
) : MarketapBackend {
    private val apiWorkGroup = WorkerGroup().apply { start() }
    private val serverInfoMutex = Mutex()

    private fun getSafeDevice(removeUserId: Boolean?): DeviceReq? {
        return try {
            if (deviceManager.isDeviceReady()) {
                deviceManager.getDevice().toReq(removeUserId)
            } else {
                null
            }
        } catch (t: Throwable) {
            logger.e(t) { "Failed to build device payload" }
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
        CoroutineScope(Dispatchers.IO).launch {
            apiWorkGroup.dispatch(::checkDeviceQueue)
        }
    }

    override fun fetchCampaigns(
        request: FetchCampaignsReq,
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

            val result = withTimeoutOrNull(1000) {
                deferredResponse.await()
            }

            if (result != null) {
                inTimeout?.invoke(result)
            } else {
                logger.w { "fetchCampaigns timed out for request: $request" }
            }
        }
    }

    override fun fetchCampaign(
        campaignId: String,
        request: FetchCampaignReq,
        inTimeout: ((InAppCampaignSingleRes) -> Unit)?,
        onSuccess: (InAppCampaignSingleRes) -> Unit
    ) {
        runBlocking {
            logger.d { "fetching campaign, campaignId: $campaignId, request: $request" }
            val deferredResponse = CompletableDeferred<InAppCampaignSingleRes>()

            apiWorkGroup.dispatch {
                try {
                    val res = marketapApi.fetchCampaign(campaignId, request)
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

            val result = withTimeoutOrNull(1000) {
                deferredResponse.await()
            }

            if (result != null) {
                inTimeout?.invoke(result)
            } else {
                logger.w { "fetchCampaign timed out for campaignId: $campaignId, request: $request" }
            }
        }
    }

    override fun track(projectId: String, request: IngestEventRequest) {
        logger.d {
            "tracking event for project $projectId, " +
                    "eventName: ${request.name}, properties: ${request.properties}"
        }

        CoroutineScope(Dispatchers.IO).launch {
            request.timestamp = getNowAsString(projectId)
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
            request.timestamp = getNowAsString(projectId)
            storage.queueItem("users", PairEntry(projectId, request), pairAdapter())
            apiWorkGroup.dispatch(::checkEventQueue)
            apiWorkGroup.dispatch(::checkUserQueue)
            apiWorkGroup.dispatch(::checkDeviceQueue)
        }
    }

    fun prefetchServerInfo(projectId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            fetchServerInfoIfNeeded(projectId)
        }
    }

    private suspend fun fetchServerInfoIfNeeded(projectId: String) {
        serverInfoMutex.withLock {
            if (MarketapServerConfig.isCacheValid()) return

            val serverInfo = try {
                withTimeoutOrNull(5000) {
                    marketapApi.getServerInfo(projectId)
                }
            } catch (t: Throwable) {
                logger.e(t) { "Failed to fetch server info for projectId: $projectId, using defaults" }
                null
            }

            if (serverInfo != null) {
                logger.d { "Server time offset: ${serverInfo.serverTimeOffset} ms" }
                MarketapServerConfig.serverTimeOffset = serverInfo.serverTimeOffset
                MarketapServerConfig.useWebClickRouting = serverInfo.useWebClickRouting
                MarketapServerConfig.lastFetchedAtMs = System.currentTimeMillis()
            } else {
                logger.w { "Failed to get server info, using defaults" }
            }
        }
    }

    private fun getNowAsString(projectId: String): String {
        val now = System.currentTimeMillis()
        if (!MarketapServerConfig.isCacheValid()) {
            runBlocking { fetchServerInfoIfNeeded(projectId) }
        }
        return getNowByMillis(now + MarketapServerConfig.serverTimeOffset)
    }
}
