package com.marketap.sdk.utils

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.ToNumberPolicy
import com.google.gson.reflect.TypeToken

private val gson: Gson = GsonBuilder()
    .applyDefaultOptions()
    .create()

private fun GsonBuilder.applyDefaultOptions(): GsonBuilder {
    return this
        .disableHtmlEscaping()
        .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
}

internal fun <T> T.serialize(): String {
    return gson.toJson(this)
}

internal fun <T> T.serializeToJson(): JsonObject {
    return gson.toJsonTree(this).asJsonObject
}


internal inline fun <reified T> String.deserialize(): T {
    val token = object : TypeToken<T>() {}.type
    return gson.fromJson(this, token)
}

internal inline fun <reified T> getTypeToken(): TypeToken<T> {
    return object : TypeToken<T>() {}
}

internal fun <T> String.deserializeObject(type: TypeToken<T>): T {
    return gson.fromJson(this, type)
}