package com.marketap.sdk.inapp.trigger

import com.marketap.sdk.domain.repository.InternalStorage
import com.squareup.moshi.JsonAdapter
import java.util.concurrent.ConcurrentHashMap

internal class InMemoryStorage : InternalStorage {
    val values = ConcurrentHashMap<String, String>()
    private val sets = ConcurrentHashMap<String, Set<String>>()

    override fun <T> setItem(key: String, value: T?, adapter: JsonAdapter<T>) {
        if (value == null) values.remove(key) else values[key] = adapter.toJson(value)
    }

    override fun <T> getItem(key: String, adapter: JsonAdapter<T>): T? = values[key]?.let { adapter.fromJson(it) }

    override fun <T> cacheAndGetItem(key: String, value: () -> T, adapter: JsonAdapter<T>, invalidationTime: Long): T? =
        getItem(key, adapter) ?: value().also { setItem(key, it, adapter) }

    override fun <T> queueItem(key: String, value: T, adapter: JsonAdapter<T>) {
        sets.merge(key, setOf(adapter.toJson(value))) { a, b -> a + b }
    }

    override fun <T> getItemList(key: String, adapter: JsonAdapter<T>): List<T> =
        sets[key].orEmpty().map { adapter.fromJson(it)!! }

    override fun <T> popItems(key: String, adapter: JsonAdapter<T>, count: Int): List<T> = emptyList()

    override fun removeItem(key: String) {
        values.remove(key)
        sets.remove(key)
    }
}
