package com.marketap.sdk.client

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.view.ViewConfiguration
import com.marketap.sdk.domain.repository.InternalStorage
import com.marketap.sdk.utils.deserialize
import com.marketap.sdk.utils.longAdapter
import com.marketap.sdk.utils.serialize
import com.squareup.moshi.JsonAdapter
import java.util.concurrent.ConcurrentHashMap


internal class SharedPreferenceInternalStorage(
    context: Context
) : InternalStorage {
    init {
        setMaxTouchPoints(context)
    }

    companion object {
        const val MARKETAP_SDK_STORAGE = "_marketap_sdk_storage"
    }

    private val sharedPreference: SharedPreferences = context.getSharedPreferences(
        MARKETAP_SDK_STORAGE,
        Context.MODE_PRIVATE
    )


    private val locks = ConcurrentHashMap<String, Any>()

    private fun setMaxTouchPoints(context: Context) {
        val packageManager = context.packageManager
        packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_DISTINCT)
        val config = ViewConfiguration.get(context)
        setItem(
            "max_touch_points",
            (config.scaledMaximumFlingVelocity / 1000).coerceAtLeast(1).toLong(),
            longAdapter
        )
    }

    private fun getLockForKey(key: String): Any {
        return locks.getOrPut(key) { Any() }
    }

    override fun <T> setItem(key: String, value: T?, adapter: JsonAdapter<T>) {
        synchronized(getLockForKey(key)) {
            sharedPreference.edit().putString(key, value?.serialize(adapter)).apply()
        }
    }

    override fun <T> getItem(key: String, adapter: JsonAdapter<T>): T? {
        return synchronized(getLockForKey(key)) {
            val value = sharedPreference.getString(key, null)
            value?.deserialize(adapter)
        }
    }

    override fun <T> queueItem(key: String, value: T, adapter: JsonAdapter<T>) {
        synchronized(getLockForKey(key)) {
            sharedPreference.getStringSet(key, mutableSetOf())?.let {
                sharedPreference.edit().putStringSet(key, it.plus(value.serialize(adapter))).apply()
            }
        }
    }

    override fun <T> getItemList(key: String, adapter: JsonAdapter<T>): List<T> {
        return synchronized(getLockForKey(key)) {
            val value = sharedPreference.getStringSet(key, mutableSetOf())
            value?.map { it.deserialize(adapter) } ?: emptyList()
        }
    }

    override fun <T> popItems(key: String, adapter: JsonAdapter<T>, count: Int): List<T> {
        return synchronized(getLockForKey(key)) {
            val value = sharedPreference.getStringSet(key, mutableSetOf())
            val res = mutableListOf<T>()
            val remain = mutableSetOf<String>()
            value?.forEach {
                if (res.size < count) {
                    res.add(it.deserialize(adapter))
                } else {
                    remain.add(it)
                }
            }

            sharedPreference.edit().putStringSet(key, remain).apply()
            res
        }
    }

    override fun removeItem(key: String) {
        synchronized(getLockForKey(key)) {
            sharedPreference.edit().remove(key).apply()
        }
    }
}