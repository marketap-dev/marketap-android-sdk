package com.marketap.sdk.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle

internal object ManifestUtils {
    fun logSystemConstants(context: Context) {
        logger.d { "Logging Marketap system constants from manifest..." }
        SystemBooleanConstant.values().forEach { constant ->
            logger.d {
                "Manifest boolean constant: ${constant.key}" +
                        " = ${getSystemBoolean(context, constant)}"
            }
        }
        SystemStringConstant.values().forEach { constant ->
            logger.d {
                "Manifest string constant: ${constant.key}" +
                        " = ${getSystemString(context, constant)}"
            }
        }
    }

    fun getSystemBoolean(
        context: Context,
        key: SystemBooleanConstant
    ): Boolean {
        return getBundle(context)?.getBoolean(key.key) ?: key.defaultValue
    }

    fun getSystemString(
        context: Context,
        key: SystemStringConstant
    ): String {
        return getBundle(context)?.getString(key.key) ?: key.defaultValue
    }

    private fun getBundle(context: Context): Bundle? {
        return try {
            context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA,
            ).metaData
        } catch (e: Exception) {
            null
        }
    }

    enum class SystemBooleanConstant(val key: String, val defaultValue: Boolean) {
        IS_CLICK_CUSTOMIZED("com_marketap_is_click_customized", false),
    }

    enum class SystemStringConstant(val key: String, val defaultValue: String) {
        CHANNEL_ID("com_marketap_push_channel_id", "default_channel_id"),
        CHANNEL_NAME("com_marketap_push_channel_name", "알림"),
        CHANNEL_DESC("com_marketap_push_channel_description", "푸시 알림 채널"),
    }
}