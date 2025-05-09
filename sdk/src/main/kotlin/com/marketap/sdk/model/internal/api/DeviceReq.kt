package com.marketap.sdk.model.internal.api

import com.marketap.sdk.model.internal.Device
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass


@JsonClass(generateAdapter = true)
internal data class DeviceReq(
    @Json(name = "device_id")
    val deviceId: String,
    val gaid: String? = null,

    @Json(name = "app_set_id")
    val appSetId: String? = null,

    @Json(name = "app_local_id")
    val appLocalId: String? = null,
    val token: String? = null,
    val properties: Map<String, Any?>? = null,

    @Json(name = "remove_user_id")
    val removeUserId: Boolean? = false,

    // FIXED: Android only
    val platform: String = "android"
) : IngestRequest {

    companion object {
        fun Device.toReq(removeUserId: Boolean? = false): DeviceReq {
            val browser = browserName?.let { "$it $browserVersion" }
            val screen = screen?.let { "${it.width}x${it.height}x${it.colorDepth}" }

            val props: Map<String, Any?> = mapOf(
                "os_version" to osVersion,
                "app_version" to appVersion,
                "brand" to brand,
                "model" to model,
                "manufacturer" to manufacturer,
                "os" to os,
                "library_version" to libraryVersion,
                "browser" to browser,
                "screen" to screen,
                "user_agent" to userAgent,
                "timezone" to timezone,
                "locale" to locale,
                "cpu_arch" to cpuArch,
                "memory_total" to memoryTotal,
                "storage_total" to storageTotal,
                "battery_level" to batteryLevel,
                "is_charging" to isCharging,
                "network_type" to networkType,
                "carrier" to carrier,
                "has_sim" to hasSim,
                "max_touch_points" to maxTouchPoints,
                "camera" to camera,
                "microphone" to microphone,
                "location" to location,
                "notifications" to notifications
            ).filterValues { it != null }

            return DeviceReq(
                deviceId = getDeviceId(this),
                gaid = gaid,
                appSetId = appSetId,
                appLocalId = appLocalId,
                token = token,
                properties = props,
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