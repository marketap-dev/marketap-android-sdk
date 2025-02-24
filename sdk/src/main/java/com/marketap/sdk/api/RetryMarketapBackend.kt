package com.marketap.sdk.api

import com.marketap.sdk.api.coroutine.WorkerGroup
import com.marketap.sdk.model.internal.api.DeviceReq
import com.marketap.sdk.model.internal.api.FetchCampaignReq
import com.marketap.sdk.model.internal.api.InAppCampaignRes
import com.marketap.sdk.model.internal.api.IngestEventRequest
import com.marketap.sdk.model.internal.api.UpdateProfileRequest
import com.marketap.sdk.service.state.InternalStorage
import com.marketap.sdk.utils.getTypeToken
import kotlinx.coroutines.CompletableDeferred
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
        inTimeout: (suspend (InAppCampaignRes) -> Unit)?,
        onSuccess: suspend (InAppCampaignRes) -> Unit
    ) {
        apiWorkGroup.dispatch {
            val deferredResponse = CompletableDeferred<InAppCampaignRes>()

            // ✅ API 요청을 비동기적으로 실행 (한 번만 요청)
            apiWorkGroup.dispatch {
                try {
                    val res = marketapApi.fetchCampaigns(request)
                    if (res.data != null) {
                        deferredResponse.complete(res.data)  // ✅ 결과 저장
                    } else {
                        deferredResponse.cancel()
                    }
                } catch (e: Exception) {
                    deferredResponse.cancel()
                }
            }

            // ✅ 0.5초 안에 응답이 오면 onSuccess + beforeTimeout 실행
            val result = withTimeoutOrNull(500) {
                deferredResponse.await()
            }

            if (result != null) {
                onSuccess(result)
                inTimeout?.invoke(result)
            } else {
                // ✅ 0.5초 초과 시 → API 요청이 끝날 때까지 기다렸다가 beforeTimeout 실행
                try {
                    onSuccess.invoke(deferredResponse.await())
                } catch (e: Exception) {
                    // 요청 실패 시 아무것도 안 함
                }
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