package com.marketap.sdk.policy

import com.marketap.sdk.domain.repository.InternalStorage
import com.squareup.moshi.JsonAdapter

internal class PolicyMemoryStorage : InternalStorage {
    private val items = mutableMapOf<String, String>()
    private val queues = mutableMapOf<String, MutableList<String>>()

    @Synchronized
    override fun <T> setItem(key: String, value: T?, adapter: JsonAdapter<T>) {
        if (value == null) items.remove(key) else items[key] = adapter.toJson(value)
    }

    @Synchronized
    override fun <T> getItem(key: String, adapter: JsonAdapter<T>): T? = items[key]?.let { adapter.fromJson(it) }

    override fun <T> cacheAndGetItem(key: String, value: () -> T, adapter: JsonAdapter<T>, invalidationTime: Long): T? = value()

    @Synchronized
    override fun <T> queueItem(key: String, value: T, adapter: JsonAdapter<T>) {
        queues.getOrPut(key) { mutableListOf() }.add(adapter.toJson(value))
    }

    @Synchronized
    override fun <T> getItemList(key: String, adapter: JsonAdapter<T>): List<T> = queues[key].orEmpty().map { adapter.fromJson(it)!! }

    @Synchronized
    override fun <T> popItems(key: String, adapter: JsonAdapter<T>, count: Int): List<T> {
        val queue = queues.getOrPut(key) { mutableListOf() }
        val popped = queue.take(count)
        repeat(popped.size) { queue.removeAt(0) }
        return popped.map { adapter.fromJson(it)!! }
    }

    @Synchronized
    override fun removeItem(key: String) {
        items.remove(key)
        queues.remove(key)
    }
}
