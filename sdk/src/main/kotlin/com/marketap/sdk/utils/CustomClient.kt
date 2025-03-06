package com.marketap.sdk.utils

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.android.Android
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText

internal class CustomClient(
    private val debug: Boolean = false,
    block: HttpClientConfig<HttpClientEngineConfig>.() -> Unit = {}
) {
    internal val client = HttpClient(Android, block)

    suspend inline fun <S, reified T> post(
        endpoint: String,
        request: S,
        builder: HttpRequestBuilder.() -> Unit = {}
    ): T {
        val body = request.serialize()
        if (debug) Log.d("Marketap", "Request: $body, endpoint: $endpoint")
        val getResponse = client.post(endpoint) {
            builder()
            header("Content-Type", "application/json")
            setBody(body)
        }
        val res = getResponse.bodyAsText()

        if (debug) Log.d("Marketap", "Response: $getResponse, body: $res")
        return res.deserialize()
    }
}