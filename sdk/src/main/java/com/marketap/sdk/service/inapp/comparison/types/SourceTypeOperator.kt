package com.marketap.sdk.service.inapp.comparison.types

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.marketap.sdk.model.internal.inapp.DataType
import com.marketap.sdk.service.inapp.comparison.types.util.toDate
import java.math.BigInteger

class SourceTypeOperator(
    val dataType: DataType
) {
    inline operator fun <reified T> invoke(
        source: Any?,
        block: (safeSource: T) -> Boolean
    ): Boolean {
        if (source == null) return false

        val convertedSource: T = when (dataType) {
            DataType.STRING -> {
                if (source is String && T::class == String::class) {
                    source as T
                } else if (source is JsonPrimitive && source.isString) {
                    source.asString as T
                } else {
                    return false
                }
            }

            DataType.INT -> {
                if (source is Int && T::class == Int::class) {
                    source as T
                } else if (source is JsonPrimitive && source.isNumber) {
                    source.asInt as T
                } else {
                    return false
                }
            }

            DataType.BIGINT -> {
                if (source is BigInteger && T::class == BigInteger::class) {
                    source as T
                } else if (source is JsonPrimitive && source.isNumber) {
                    source.asBigInteger as T
                } else {
                    return false
                }
            }

            DataType.DOUBLE -> {
                if (source is Double && T::class == Double::class) {
                    source as T
                } else if (source is JsonPrimitive && source.isNumber) {
                    source.asDouble as T
                } else {
                    return false
                }
            }

            DataType.BOOLEAN -> {
                if (source is Boolean && T::class == Boolean::class) {
                    source as T
                } else if (source is JsonPrimitive && source.isBoolean) {
                    source.asBoolean as T
                } else {
                    return false
                }
            }

            DataType.DATETIME -> {
                if (source is String) {
                    try {
                        source.toDate() as T
                    } catch (e: Exception) {
                        return false
                    }
                } else if (source is JsonPrimitive && source.isString) {
                    try {
                        source.asString.toDate() as T
                    } catch (e: Exception) {
                        return false
                    }
                } else {
                    return false
                }
            }

            DataType.DATE -> {
                if (source is String) {
                    try {
                        source as T
                    } catch (e: Exception) {
                        return false
                    }
                } else if (source is JsonPrimitive && source.isString) {
                    try {
                        source as T
                    } catch (e: Exception) {
                        return false
                    }
                } else {
                    return false
                }
            }

            DataType.OBJECT -> {
                if (source is JsonObject && T::class == JsonObject::class) {
                    source as T
                } else {
                    return false
                }
            }

            DataType.STRING_ARRAY -> {
                if (source is List<*> && source.all { it is String }) {
                    source as T
                } else if (source is JsonArray) {
                    val list =
                        source.mapNotNull { if (it is JsonPrimitive && it.isString) it.asString else null }
                    list as T
                } else {
                    return false
                }
            }

            DataType.OBJECT_ARRAY -> {
                if (source is List<*> && source.all { it is JsonObject }) {
                    source as T
                } else if (source is JsonArray) {
                    val list = source.mapNotNull { if (it is JsonObject) it else null }
                    list as T
                } else {
                    return false
                }
            }
        }
        return block(convertedSource)
    }
}