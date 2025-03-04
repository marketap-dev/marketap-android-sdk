package com.marketap.sdk.model.internal.api

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.annotations.SerializedName
import com.marketap.sdk.model.internal.Device


internal data class DeviceReq(
    @SerializedName("device_id")
    val deviceId: String,
    val gaid: String? = null,

    @SerializedName("app_set_id")
    val appSetId: String? = null,

    @SerializedName("app_local_id")
    val appLocalId: String? = null,
    val token: String? = null,
    val properties: JsonObject? = null,

    @SerializedName("remove_user_id")
    val removeUserId: Boolean? = false
) : IngestRequest {
    val platform = "android"

    companion object {
        fun Device.toReq(removeUserId: Boolean? = false): DeviceReq {
            val browser = browserName?.let { "$it $browserVersion" }
            val screen = screen?.let { "${it.width}x${it.height}x${it.colorDepth}" }

            return DeviceReq(
                deviceId = getDeviceId(this),
                gaid = gaid,
                appSetId = appSetId,
                appLocalId = appLocalId,
                token = token,
                properties = JsonObject().apply {
                    mapOf<String, JsonElement?>(
                        "os_version" to osVersion?.let { JsonPrimitive(it) },
                        "app_version" to appVersion?.let { JsonPrimitive(it) },
                        "brand" to brand?.let { JsonPrimitive(it) },
                        "model" to model?.let { JsonPrimitive(it) },
                        "manufacturer" to manufacturer?.let { JsonPrimitive(it) },
                        "os" to os?.let { JsonPrimitive(it) },
                        "library_version" to libraryVersion?.let { JsonPrimitive(it) },
                        "browser" to browser?.let { JsonPrimitive(it) },
                        "screen" to screen?.let { JsonPrimitive(it) },
                        "user_agent" to userAgent?.let { JsonPrimitive(it) },
                        "timezone" to timezone?.let { JsonPrimitive(it) },
                        "locale" to locale?.let { JsonPrimitive(it) },
                        "cpu_arch" to cpuArch?.let { JsonPrimitive(it) },
                        "memory_total" to memoryTotal?.let { JsonPrimitive(it) },
                        "storage_total" to storageTotal?.let { JsonPrimitive(it) },
                        "battery_level" to batteryLevel?.let { JsonPrimitive(it) },
                        "is_charging" to isCharging?.let { JsonPrimitive(it) },
                        "network_type" to networkType?.let { JsonPrimitive(it) },
                        "carrier" to carrier?.let { JsonPrimitive(it) },
                        "has_sim" to hasSim?.let { JsonPrimitive(it) },
                        "max_touch_points" to maxTouchPoints?.let { JsonPrimitive(it) },
                        "camera" to camera?.let { JsonPrimitive(it) },
                        "microphone" to microphone?.let { JsonPrimitive(it) },
                        "location" to location?.let { JsonPrimitive(it) },
                        "notifications" to notifications?.let { JsonPrimitive(it) }
                    ).forEach { (key, value) -> add(key, value) }
                },
                removeUserId = removeUserId
            )
        }
    }
}

private fun getDeviceId(device: Device): String {
    return when {
        device.gaid != null -> "gaid:${device.gaid}"
        device.appSetId != null -> "app_set_id:${device.appSetId}"
        device.appLocalId != null -> "app_local_id:${device.appLocalId}"
        else -> throw IllegalStateException("Device ID is not set")
    }
}