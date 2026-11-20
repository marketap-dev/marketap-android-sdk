package com.marketap.sdk.utils

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.lang.reflect.Type

/**
 * 타입 정보 없이 읽는 값(`Any`)의 JSON 숫자를 정수면 Long, 아니면 Double 로 읽는다.
 * Long 범위를 넘는 정수는 Double 로 읽는다.
 */
internal object LongOrDoubleAnyAdapterFactory : JsonAdapter.Factory {
    override fun create(
        type: Type,
        annotations: Set<Annotation>,
        moshi: Moshi
    ): JsonAdapter<*>? {
        if (type != Any::class.java || annotations.isNotEmpty()) return null
        val delegate = moshi.nextAdapter<Any>(this, Any::class.java, annotations)
        return LongOrDoubleAnyAdapter(delegate)
    }
}

private class LongOrDoubleAnyAdapter(
    private val delegate: JsonAdapter<Any>
) : JsonAdapter<Any>() {
    override fun fromJson(reader: JsonReader): Any? {
        return when (reader.peek()) {
            JsonReader.Token.BEGIN_ARRAY -> {
                val list = ArrayList<Any?>()
                reader.beginArray()
                while (reader.hasNext()) {
                    list.add(fromJson(reader))
                }
                reader.endArray()
                list
            }

            JsonReader.Token.BEGIN_OBJECT -> {
                val map = LinkedHashMap<String, Any?>()
                reader.beginObject()
                while (reader.hasNext()) {
                    map[reader.nextName()] = fromJson(reader)
                }
                reader.endObject()
                map
            }

            JsonReader.Token.NUMBER -> {
                val raw = reader.nextString()
                val isInteger = raw.none { it == '.' || it == 'e' || it == 'E' }
                if (isInteger) raw.toLongOrNull() ?: raw.toDouble() else raw.toDouble()
            }

            else -> delegate.fromJson(reader)
        }
    }

    override fun toJson(writer: JsonWriter, value: Any?) {
        delegate.toJson(writer, value)
    }
}

internal val moshi: Moshi = Moshi.Builder()
    .add(LongOrDoubleAnyAdapterFactory)
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

/** 자바스크립트 코드에 넣을 문자열 리터럴(JSON 문자열). */
internal fun String.toJsStringLiteral(): String = stringAdapter.toJson(this)
