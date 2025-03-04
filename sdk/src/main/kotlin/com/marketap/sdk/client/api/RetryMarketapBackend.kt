package com.marketap.sdk.client.api

import com.marketap.sdk.client.api.coroutine.WorkerGroup
import com.marketap.sdk.domain.repository.InternalStorage
import com.marketap.sdk.domain.repository.MarketapBackend
import com.marketap.sdk.model.internal.api.DeviceReq
import com.marketap.sdk.model.internal.api.FetchCampaignReq
import com.marketap.sdk.model.internal.api.InAppCampaignRes
import com.marketap.sdk.model.internal.api.IngestEventRequest
import com.marketap.sdk.model.internal.api.UpdateProfileRequest
import com.marketap.sdk.utils.getTypeToken
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

internal class RetryMarketapBackend(
    private val storage: InternalStorage,
    private val marketapApi: MarketapApi
) : MarketapBackend {
    private val apiWorkGroup = WorkerGroup().apply { start() }

    private suspend fun checkUserQueue() {
        val items =
            storage.popItems(
                "users",
                getTypeToken<Pair<String, UpdateProfileRequest>>(),
                10
            )
        items.forEach { (projectId, request) ->
            apiWorkGroup.dispatch {
                try {
                    marketapApi.updateProfile(projectId, request)
                } catch (e: Exception) {
                    storage.queueItem("users", Pair(projectId, request))
                }
            }
        }
    }

    private suspend fun checkEventQueue() {
        val items =
            storage.popItems("events", getTypeToken<Pair<String, IngestEventRequest>>(), 10)
        items.forEach { (projectId, request) ->
            apiWorkGroup.dispatch {
                try {
                    marketapApi.track(projectId, request)
                } catch (e: Exception) {
                    storage.queueItem("events", Pair(projectId, request))
                }
            }
        }
    }

    override fun updateDevice(projectId: String, request: DeviceReq) {
        apiWorkGroup.dispatch {
            marketapApi.updateDevice(projectId, request)
        }
    }

    override fun fetchCampaigns(
        request: FetchCampaignReq,
        inTimeout: ((InAppCampaignRes) -> Unit)?,
        onSuccess: (InAppCampaignRes) -> Unit
    ) {
        runBlocking {
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
            }
        }
    }

    override fun track(projectId: String, request: IngestEventRequest) {
        storage.queueItem("events", Pair(projectId, request))
        apiWorkGroup.dispatch(::checkEventQueue)
        apiWorkGroup.dispatch(::checkUserQueue)
    }

    override fun updateProfile(projectId: String, request: UpdateProfileRequest) {
        storage.queueItem("users", Pair(projectId, request))
        apiWorkGroup.dispatch(::checkEventQueue)
        apiWorkGroup.dispatch(::checkUserQueue)
    }
}