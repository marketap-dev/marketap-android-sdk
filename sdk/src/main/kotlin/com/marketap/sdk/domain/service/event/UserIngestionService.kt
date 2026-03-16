package com.marketap.sdk.domain.service.event

import com.marketap.sdk.domain.repository.DeviceManager
import com.marketap.sdk.domain.repository.InternalStorage
import com.marketap.sdk.domain.repository.MarketapBackend
import com.marketap.sdk.domain.service.state.ClientStateManager
import com.marketap.sdk.model.internal.api.DeviceReq
import com.marketap.sdk.model.internal.api.DeviceReq.Companion.toReq
import com.marketap.sdk.model.internal.api.UpdateProfileRequest
import com.marketap.sdk.utils.adapter
import com.marketap.sdk.utils.longAdapter
import com.marketap.sdk.utils.logger
import com.marketap.sdk.utils.stringAdapter

internal class UserIngestionService(
    private val clientStateManager: ClientStateManager,
    private val deviceManager: DeviceManager,
    private val marketapBackend: MarketapBackend,
    private val storage: InternalStorage,
) {
    @Volatile
    private var lastSentDeviceReqJson: String? = null

    private val deviceReqAdapter = adapter<DeviceReq>()

    companion object {
        private const val KEY_LAST_SENT_DEVICE_REQ = "last_sent_device_req"
        private const val KEY_LAST_SENT_DEVICE_REQ_AT = "last_sent_device_req_at"
        private const val TTL_MS = 24 * 60 * 60 * 1000L
    }

    fun identify(userId: String, userProperties: Map<String, Any>?) {
        clientStateManager.setUserId(userId)
        try {
            marketapBackend.updateProfile(
                clientStateManager.getProjectId(),
                UpdateProfileRequest(
                    userId,
                    (userProperties ?: emptyMap()),
                    deviceManager.getDevice().toReq()
                )
            )
        } catch (t: Throwable) {
            logger.e(t) { "Failed to identify user: $userId" }
        }
    }

    fun setUserProperties(userProperties: Map<String, Any>) {
        val currentUserId = clientStateManager.getUserId() ?: return

        try {
            marketapBackend.updateProfile(
                clientStateManager.getProjectId(),
                UpdateProfileRequest(
                    currentUserId,
                    userProperties,
                    deviceManager.getDevice().toReq()
                )
            )
        } catch (t: Throwable) {
            logger.e(t) { "Failed to set user properties" }
        }
    }

    fun resetIdentity() {
        clientStateManager.setUserId(null)
        try {
            val device = deviceManager.getDevice().toReq(true)
            marketapBackend.updateDevice(clientStateManager.getProjectId(), device)
        } catch (t: Throwable) {
            logger.e(t) { "Failed to reset identity" }
        }
    }

    fun pushDevice() {
        try {
            val deviceReq = deviceManager.getDevice().toReq()
            val deviceReqJson = deviceReqAdapter.toJson(deviceReq)

            if (deviceReqJson == lastSentDeviceReqJson) {
                logger.d { "Device info unchanged (in-memory), skipping update" }
                return
            }

            val storedJson = storage.getItem(KEY_LAST_SENT_DEVICE_REQ, stringAdapter)
            val storedAt = storage.getItem(KEY_LAST_SENT_DEVICE_REQ_AT, longAdapter) ?: 0L
            val isExpired = System.currentTimeMillis() - storedAt > TTL_MS

            if (!isExpired && deviceReqJson == storedJson) {
                logger.d { "Device info unchanged and within TTL, skipping update" }
                lastSentDeviceReqJson = deviceReqJson
                return
            }

            marketapBackend.updateDevice(clientStateManager.getProjectId(), deviceReq)
            lastSentDeviceReqJson = deviceReqJson
            storage.setItem(KEY_LAST_SENT_DEVICE_REQ, deviceReqJson, stringAdapter)
            storage.setItem(KEY_LAST_SENT_DEVICE_REQ_AT, System.currentTimeMillis(), longAdapter)
        } catch (t: Throwable) {
            logger.e(t) { "Failed to push device" }
        }
    }
}
