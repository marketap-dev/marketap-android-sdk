package com.marketap.sdk.service.state

import com.google.gson.reflect.TypeToken

interface InternalStorage {
    // Set and get item
    fun <T> setItem(key: String, value: T?)
    fun <T> getItem(key: String, type: TypeToken<T>): T?

    // Queue and pop item (list)
    fun <T> queueItem(key: String, value: T)
    fun <T> getItemList(key: String, type: TypeToken<T>): List<T>
    fun <T> popItems(key: String, type: TypeToken<T>, count: Int): List<T>
    fun removeItem(key: String)
}