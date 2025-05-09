package com.marketap.sdk.utils

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

internal val moshi: Moshi = Moshi.Builder()
    .add(KotlinJsonAdapterFactory())  // code-gen adapter 우선 적용
    .build()

/** 문자열(JSON)로 직렬화 */
internal fun <T> T.serialize(adapter: JsonAdapter<T>): String {
    return try {
        adapter.toJson(this)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    } ?: throw IllegalArgumentException("Failed to serialize object: $this")
}

internal fun <T> String.deserialize(adapter: JsonAdapter<T>): T {
    return try {
        adapter.fromJson(this)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    } ?: throw IllegalArgumentException("Failed to deserialize JSON: $this")
}

internal inline fun <reified T> listAdapter(): JsonAdapter<List<T>> {
    val type = Types.newParameterizedType(List::class.java, T::class.java)
    return moshi.adapter(type)
}

internal inline fun <reified K, reified V> mapAdapter(): JsonAdapter<Map<K, V>> {
    val type = Types.newParameterizedType(Map::class.java, K::class.java, V::class.java)
    return moshi.adapter(type)
}

@JsonClass(generateAdapter = true)
data class PairEntry<K, V>(
    val first: K,
    val second: V
)

internal inline fun <reified K, reified V> pairAdapter(): JsonAdapter<PairEntry<K, V>> {
    val type = Types.newParameterizedType(PairEntry::class.java, K::class.java, V::class.java)
    return moshi.adapter(type)
}

internal inline fun <reified T> adapter(): JsonAdapter<T> {
    return moshi.adapter(T::class.java)
}

internal val stringAdapter: JsonAdapter<String> = moshi.adapter(String::class.java)
internal val longAdapter: JsonAdapter<Long> = moshi.adapter(Long::class.java)
internal val booleanAdapter: JsonAdapter<Boolean> = moshi.adapter(Boolean::class.java)