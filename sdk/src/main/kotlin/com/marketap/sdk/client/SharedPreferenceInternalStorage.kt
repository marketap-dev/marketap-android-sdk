package com.marketap.sdk.client

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.marketap.sdk.domain.repository.InternalStorage
import com.marketap.sdk.utils.deserialize
import com.marketap.sdk.utils.serialize
import com.squareup.moshi.JsonAdapter
import java.util.concurrent.ConcurrentHashMap


class SharedPreferenceInternalStorage(
    application: Application
) : InternalStorage {
    private val sharedPreference: SharedPreferences = application.getSharedPreferences(
        "_marketap_sdk_storage",
        Context.MODE_PRIVATE
    )


    private val locks = ConcurrentHashMap<String, Any>()

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