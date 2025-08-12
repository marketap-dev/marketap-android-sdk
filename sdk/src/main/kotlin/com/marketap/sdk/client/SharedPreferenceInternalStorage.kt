package com.marketap.sdk.client

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.view.ViewConfiguration
import androidx.core.content.edit
import com.marketap.sdk.domain.repository.InternalStorage
import com.marketap.sdk.utils.deserialize
import com.marketap.sdk.utils.longAdapter
import com.marketap.sdk.utils.serialize
import com.squareup.moshi.JsonAdapter
import java.util.concurrent.ConcurrentHashMap


internal class SharedPreferenceInternalStorage(
    context: Context
) : InternalStorage {
    companion object {
        const val MARKETAP_SDK_STORAGE = "_marketap_sdk_storage"
    }

    private val sharedPreference: SharedPreferences = context.getSharedPreferences(
        MARKETAP_SDK_STORAGE,
        Context.MODE_PRIVATE
    )

    private val locks: ConcurrentHashMap<String, Any> = ConcurrentHashMap<String, Any>()

    fun initialize(context: Context): SharedPreferenceInternalStorage {
        setMaxTouchPoints(context)
        return this
    }

    private fun setMaxTouchPoints(context: Context) {
        val packageManager = context.packageManager
        packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_DISTINCT)
        val config = try {
            ViewConfiguration.get(context).scaledMaximumFlingVelocity / 1000
        } catch (e: Exception) {
            1
        }
        setItem(
            "max_touch_points",
            config.coerceAtLeast(1).toLong(),
            longAdapter
        )
    }

    private fun getLockForKey(key: String): Any {
        return locks.getOrPut(key) { Any() }
    }

    override fun <T> setItem(key: String, value: T?, adapter: JsonAdapter<T>) {
        synchronized(getLockForKey(key)) {
            sharedPreference.edit { putString(key, value?.serialize(adapter)) }
        }
    }

    override fun <T> getItem(key: String, adapter: JsonAdapter<T>): T? {
        return synchronized(getLockForKey(key)) {
            val value = sharedPreference.getString(key, null)
            value?.deserialize(adapter)
        }
    }

    override fun <T> cacheAndGetItem(
        key: String,
        value: () -> T,
        adapter: JsonAdapter<T>,
        invalidationTime: Long
    ): T? {
        return synchronized(getLockForKey(key)) {
            val cachedValue = sharedPreference.getString(key, null)?.deserialize(adapter)
            if (cachedValue != null && System.currentTimeMillis() < (getItem(
                    "${key}_inv_time",
                    longAdapter
                ) ?: 0L)
            ) {
                return cachedValue
            }

            val newValue = try {
                value()
            } catch (e: Exception) {
                // If the value function fails, we return null
                // This is to avoid crashing the app due to an exception in the value function
                null
            } ?: return null
            sharedPreference.edit { putString(key, newValue.serialize(adapter)) }
            setItem(
                "${key}_inv_time",
                System.currentTimeMillis() + invalidationTime,
                longAdapter
            )
            newValue
        }
    }

    override fun <T> queueItem(key: String, value: T, adapter: JsonAdapter<T>) {
        synchronized(getLockForKey(key)) {
            sharedPreference.getStringSet(key, mutableSetOf())?.let {
                sharedPreference.edit { putStringSet(key, it.plus(value.serialize(adapter))) }
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

            sharedPreference.edit { putStringSet(key, remain) }
            res
        }
    }

    override fun removeItem(key: String) {
        synchronized(getLockForKey(key)) {
            sharedPreference.edit { remove(key) }
        }
    }
}