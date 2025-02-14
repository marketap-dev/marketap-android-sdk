package com.marketap.sdk

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.ToNumberPolicy

private val gson: Gson = GsonBuilder()
    .applyDefaultOptions()
    .create()

private fun GsonBuilder.applyDefaultOptions(): GsonBuilder {
    return this
        .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
}

internal fun <T> T.serialize(): String {
    return gson.toJson(this)
}