package com.marketap.sdk.utils

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText

internal class CustomClient(
    block: HttpClientConfig<HttpClientEngineConfig>.() -> Unit = {}
) {
    internal val client = HttpClient(Android, block)

    suspend inline fun <S, reified T> post(
        endpoint: String,
        request: S,
        timeout: Long = 5000,
        builder: HttpRequestBuilder.() -> Unit = {}
    ): T {
        val body = request.serialize()
        Log.d("CustomClient", "Request: $body, endpoint: $endpoint")
        val getResponse = client.post(endpoint) {
            builder()
            header("Content-Type", "application/json")
            setBody(body)

            this.timeout {
                requestTimeoutMillis = timeout
                connectTimeoutMillis = timeout
                socketTimeoutMillis = timeout
            }
        }
        val res = getResponse.bodyAsText()

        Log.d("CustomClient", "Response: $getResponse")
        Log.d("CustomClient", "Response body: $res")
        return res.deserialize()
    }
}