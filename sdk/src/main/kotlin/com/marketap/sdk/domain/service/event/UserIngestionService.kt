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

internal enum class DevicePushPolicy { CHANGE_ONLY, LIVENESS }

internal class UserIngestionService(
    private val clientStateManager: ClientStateManager,
    private val deviceManager: DeviceManager,
    private val marketapBackend: MarketapBackend,
    private val storage: InternalStorage,
) {
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

    private val deviceLock = Any()

    /**
     * device 정보를 서버에 전송하는 단일 게이트.
     * - CHANGE_ONLY: 내용(해시)이 바뀐 경우에만 전송. init/optIn 등 비-이벤트 트리거용.
     *   변경 없는 백그라운드 프로세스 재기동에서는 전송 0회
     * - LIVENESS: 내용 변경 OR 마지막 전송 후 TTL(24h) 경과 시 전송. 실제 사용자 이벤트 전용.
     * @param prebuilt 이미 만들어진 DeviceReq(이벤트 경로에서 재사용, getDevice 중복 호출 방지). null이면 새로 빌드.
     */
    fun pushDevice(policy: DevicePushPolicy, prebuilt: DeviceReq? = null) {
        try {
            synchronized(deviceLock) {
                val deviceReq = prebuilt ?: deviceManager.getDevice().toReq()
                val deviceKey = canonicalKey(deviceReq)

                // 영속 캐시(키+시각)만으로 판정. (in-memory 단축경로는 LIVENESS의 TTL 검사를 건너뛰는 버그가 있어 제거)
                val storedKey = storage.getItem(KEY_LAST_SENT_DEVICE_REQ, stringAdapter)
                val storedAt = storage.getItem(KEY_LAST_SENT_DEVICE_REQ_AT, longAdapter) ?: 0L
                val unchanged = deviceKey == storedKey
                val isExpired = System.currentTimeMillis() - storedAt > TTL_MS

                // CHANGE_ONLY: 내용 변경 시에만. LIVENESS: 내용 변경 OR TTL(24h) 경과 시.
                val shouldSend = !unchanged || (policy == DevicePushPolicy.LIVENESS && isExpired)
                if (!shouldSend) {
                    logger.d { "Device unchanged (policy=$policy), skipping update" }
                    return
                }

                marketapBackend.updateDevice(clientStateManager.getProjectId(), deviceReq)
                storage.setItem(KEY_LAST_SENT_DEVICE_REQ, deviceKey, stringAdapter)
                storage.setItem(KEY_LAST_SENT_DEVICE_REQ_AT, System.currentTimeMillis(), longAdapter)
            }
        } catch (t: Throwable) {
            logger.e(t) { "Failed to push device" }
        }
    }

    /**
     * device 변경감지용 정규화 키.
     * - JSON 객체의 key는 정렬해서 비교 → key 순서가 달라도 내용이 같으면 동일 판정.
     * - 배열(list)은 순서를 유지 → 리스트 원소 순서가 바뀌면 변경으로 판정.
     * 양쪽(현재/저장)을 같은 방식으로 정규화하므로 직렬화 round-trip의 타입 코어싱 함정도 피함.
     */
    private fun canonicalKey(deviceReq: DeviceReq): String =
        canonicalize(deviceReqAdapter.toJsonValue(deviceReq))

    private fun canonicalize(value: Any?): String = when (value) {
        null -> "null"
        is Map<*, *> -> value.entries
            .sortedBy { it.key.toString() }
            .joinToString(separator = ",", prefix = "{", postfix = "}") { (k, v) ->
                "${quote(k.toString())}:${canonicalize(v)}"
            }
        is List<*> -> value.joinToString(separator = ",", prefix = "[", postfix = "]") {
            canonicalize(it)
        }
        is String -> quote(value)
        is Boolean -> value.toString()
        is Number -> value.toString()
        else -> quote(value.toString())
    }

    /** 구조 문자(,{}[]= 등)와 따옴표를 escape해 canonical key의 injectivity를 보장. */
    private fun quote(s: String): String {
        val sb = StringBuilder(s.length + 2)
        sb.append('"')
        for (c in s) when (c) {
            '\\' -> sb.append("\\\\")
            '"' -> sb.append("\\\"")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> sb.append(c)
        }
        sb.append('"')
        return sb.toString()
    }
}
