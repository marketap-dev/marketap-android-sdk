package com.marketap.sdk.domain.repository

import com.squareup.moshi.JsonAdapter

interface InternalStorage {
    // Set and get item
    fun <T> setItem(key: String, value: T?, adapter: JsonAdapter<T>)
    fun <T> getItem(key: String, adapter: JsonAdapter<T>): T?
    fun <T> cacheAndGetItem(
        key: String,
        value: () -> T,
        adapter: JsonAdapter<T>,
        invalidationTime: Long
    ): T?

    // Queue and pop item (list)
    fun <T> queueItem(key: String, value: T, adapter: JsonAdapter<T>)
    fun <T> getItemList(key: String, adapter: JsonAdapter<T>): List<T>
    fun <T> popItems(key: String, adapter: JsonAdapter<T>, count: Int): List<T>
    fun removeItem(key: String)
}