package com.marketap.sdk.client.api

import com.marketap.sdk.client.api.coroutine.WorkerGroup
import com.marketap.sdk.client.policy.RemotePolicyStore
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
    private val pushOnly: Boolean = false,
    sdkType: String? = null,
    sdkVersion: String? = null,
    private val pushApi: MarketapApi = marketapApi,
) : MarketapBackend {
    // 이벤트: 병렬 처리 (순서 무관, 모두 전송 필요)
    private val apiWorkGroup = WorkerGroup().apply { start() }
    // 프로필/디바이스: 단일 워커로 직렬 처리 (최신 상태만 유효)
    private val profileWorker = WorkerGroup(workerCount = 1).apply { start() }
    private val serverInfoMutex = Mutex()
    @Volatile private var serverInfoRetryAt = 0L
    internal val remotePolicy = RemotePolicyStore(storage, sdkType = sdkType, sdkVersion = sdkVersion, readOnly = pushOnly)
    private val eventQueue = if (pushOnly) KEY_PUSH_EVENTS else "events"
    private val collectionBlock = if (pushOnly) "pushTracking" else "collection"

    private fun enqueueEvent(queue: String, block: String, entry: PairEntry<String, IngestEventRequest>, generation: Long): Boolean {
        if (!remotePolicy.allowed(entry.first, block) || generation != remotePolicy.generation(entry.first, block)) return false
        storage.queueItem(queue, entry, pairAdapter<String, IngestEventRequest>())
        remotePolicy.clearIfChanged(entry.first, block, generation)
        return true
    }

    companion object {
        private const val KEY_PENDING_USER_PROFILE = "pending_user_profile"
        private const val KEY_PENDING_DEVICE_PROFILE = "pending_device_profile"
        private const val KEY_PUSH_EVENTS = "push_events"
    }

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
        while (true) {
            val item = storage.getItem(KEY_PENDING_USER_PROFILE, pairAdapter<String, UpdateProfileRequest>()) ?: break
            storage.removeItem(KEY_PENDING_USER_PROFILE)
            if (!remotePolicy.allowed(item.first, "collection")) continue

            val sent = try {
                val device = getSafeDevice(item.second.device.removeUserId)
                    ?: throw IllegalStateException("Device is not ready")
                marketapApi.updateProfile(item.first, item.second.copy(device = device))
                true
            } catch (e: Exception) {
                false
            }

            if (!sent) {
                // 더 새로운 요청이 없을 때만 복구 (있으면 새 요청이 최신 상태)
                if (remotePolicy.allowed(item.first, "collection") &&
                    storage.getItem(KEY_PENDING_USER_PROFILE, pairAdapter<String, UpdateProfileRequest>()) == null) {
                    storage.setItem(KEY_PENDING_USER_PROFILE, item, pairAdapter())
                }
                break
            }
        }
    }

    private suspend fun checkEventQueue() {
        drainEventQueue(eventQueue, collectionBlock)
        if (!pushOnly) drainEventQueue(KEY_PUSH_EVENTS, "pushTracking")
    }

    private suspend fun drainEventQueue(queue: String, block: String) {
        val items = synchronized(RemotePolicyStore.lock) {
            storage.popItems(queue, pairAdapter<String, IngestEventRequest>(), 10).map {
                it to remotePolicy.generation(it.first, block)
            }
        }
        val api = if (queue == KEY_PUSH_EVENTS) pushApi else marketapApi
        items.forEach { (item, generation) ->
            val (projectId, request) = item
            apiWorkGroup.dispatch {
                try {
                    if (!remotePolicy.allowed(projectId, block) || generation != remotePolicy.generation(projectId, block)) return@dispatch
                    val device = getSafeDevice(request.device.removeUserId)
                    if (device != null) {
                        api.track(projectId, request.copy(device = device))
                    } else {
                        throw IllegalStateException("Device is not ready")
                    }
                } catch (e: Exception) {
                    enqueueEvent(queue, block, PairEntry(projectId, request), generation)
                }
            }
        }
    }

    private suspend fun checkDeviceQueue() {
        while (true) {
            val item = storage.getItem(KEY_PENDING_DEVICE_PROFILE, pairAdapter<String, DeviceReq>()) ?: break
            storage.removeItem(KEY_PENDING_DEVICE_PROFILE)

            val sent = try {
                val device = getSafeDevice(item.second.removeUserId)
                    ?: throw IllegalStateException("Device is not ready")
                marketapApi.updateDevice(item.first, device)
                true
            } catch (e: Exception) {
                false
            }

            if (!sent) {
                if (storage.getItem(KEY_PENDING_DEVICE_PROFILE, pairAdapter<String, DeviceReq>()) == null) {
                    storage.setItem(KEY_PENDING_DEVICE_PROFILE, item, pairAdapter())
                }
                break
            }
        }
    }

    override fun updateDevice(projectId: String, request: DeviceReq) {
        logger.d {
            "updating device of project $projectId, " +
                    "deviceId: ${request.deviceId}, properties: ${request.properties}"
        }
        storage.setItem(KEY_PENDING_DEVICE_PROFILE, PairEntry(projectId, request), pairAdapter())
        CoroutineScope(Dispatchers.IO).launch {
            profileWorker.dispatch(::checkDeviceQueue)
        }
    }

    override fun fetchCampaigns(
        request: FetchCampaignsReq,
        inTimeout: ((InAppCampaignRes) -> Unit)?, // timeout 이내에 호출 되면 실행
        onSuccess: (InAppCampaignRes) -> Unit
    ) {
        if (!remotePolicy.allowed(request.projectId, "inApp")) {
            inTimeout?.invoke(InAppCampaignRes("", emptyList()))
            return
        }
        val generation = remotePolicy.generation(request.projectId, "inApp")
        runBlocking {
            logger.d { "fetching campaigns, request: $request" }
            val deferredResponse = CompletableDeferred<InAppCampaignRes>()

            apiWorkGroup.dispatch {
                try {
                    val res = marketapApi.fetchCampaigns(request)
                    synchronized(RemotePolicyStore.lock) {
                        if (res.data != null && remotePolicy.allowed(request.projectId, "inApp") &&
                            generation == remotePolicy.generation(request.projectId, "inApp")) {
                            onSuccess(res.data)
                            deferredResponse.complete(res.data)
                        } else {
                            deferredResponse.cancel()
                        }
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
        if (!remotePolicy.allowed(request.projectId, "inApp")) {
            inTimeout?.invoke(InAppCampaignSingleRes(null))
            return
        }
        val generation = remotePolicy.generation(request.projectId, "inApp")
        runBlocking {
            logger.d { "fetching campaign, campaignId: $campaignId, request: $request" }
            val deferredResponse = CompletableDeferred<InAppCampaignSingleRes>()

            apiWorkGroup.dispatch {
                try {
                    val res = marketapApi.fetchCampaign(campaignId, request)
                    synchronized(RemotePolicyStore.lock) {
                        if (res.data != null && remotePolicy.allowed(request.projectId, "inApp") &&
                            generation == remotePolicy.generation(request.projectId, "inApp")) {
                            onSuccess(res.data)
                            deferredResponse.complete(res.data)
                        } else {
                            deferredResponse.cancel()
                        }
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
            if (!remotePolicy.allowed(projectId, collectionBlock)) return@launch
            val generation = remotePolicy.generation(projectId, collectionBlock)
            if (!enqueueEvent(eventQueue, collectionBlock, PairEntry(projectId, request), generation)) return@launch
            apiWorkGroup.dispatch(::checkEventQueue)
            profileWorker.dispatch(::checkUserQueue)
            profileWorker.dispatch(::checkDeviceQueue)
        }
    }

    override fun updateProfile(projectId: String, request: UpdateProfileRequest) {
        logger.d {
            "updating profile for project $projectId, " +
                    "userId: ${request.userId}, properties: ${request.properties}"
        }
        if (!remotePolicy.allowed(projectId, "collection")) return
        CoroutineScope(Dispatchers.IO).launch {
            request.timestamp = getNowAsString(projectId)
            if (!remotePolicy.allowed(projectId, "collection")) return@launch
            storage.setItem(KEY_PENDING_USER_PROFILE, PairEntry(projectId, request), pairAdapter())
            profileWorker.dispatch(::checkUserQueue)
        }
    }

    fun prefetchServerInfo(projectId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            fetchServerInfoIfNeeded(projectId)
        }
    }

    private suspend fun fetchServerInfoIfNeeded(projectId: String) {
        serverInfoMutex.withLock {
            if (pushOnly || System.currentTimeMillis() < serverInfoRetryAt || (MarketapServerConfig.isCacheValid() && !remotePolicy.refreshDue(projectId))) return

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
                remotePolicy.accept(projectId, serverInfo.sdkPolicy)
            } else {
                serverInfoRetryAt = System.currentTimeMillis() + 60_000L
                remotePolicy.retryLater(projectId)
                logger.w { "Failed to get server info, using defaults" }
            }
        }
    }

    private fun getNowAsString(projectId: String): String {
        val now = System.currentTimeMillis()
        if (!MarketapServerConfig.isCacheValid() || remotePolicy.refreshDue(projectId)) {
            runBlocking { fetchServerInfoIfNeeded(projectId) }
        }
        return getNowByMillis(now + MarketapServerConfig.serverTimeOffset)
    }
}
