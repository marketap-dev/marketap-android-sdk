package com.marketap.sdk.model.internal.api


data class ServerResponse<T>(
    val code: Int,
    val message: String,
    val data: T?
)