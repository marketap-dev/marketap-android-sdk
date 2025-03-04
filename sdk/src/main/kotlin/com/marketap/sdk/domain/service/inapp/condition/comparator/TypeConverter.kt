package com.marketap.sdk.domain.service.inapp.condition.comparator

import com.google.gson.JsonElement
import com.marketap.sdk.utils.MarketapDate
import com.marketap.sdk.utils.toDate
import java.util.Date
import java.util.Locale

object TypeConverter {
    inline fun <reified T> List<Any>.getSingleOrNull(): T? {
        return if (size == 1) invoke(first()) else null
    }

    inline fun <reified T> List<Any>.getPairOrNull(): Pair<T, T>? {
        return if (size == 2) {
            val first = invoke<T>(get(0))
            val second = invoke<T>(get(1))
            if (first != null && second != null) first to second else null
        } else null
    }

    inline fun <reified T> List<Any>.getList(): List<T> {
        return mapNotNull { invoke<T>(it) }
    }


    inline operator fun <reified T> invoke(source: Any): T? {
        return when (T::class) {
            // String 변환
            String::class -> {
                when (source) {
                    is String -> source as T
                    is JsonElement -> if (source.isJsonPrimitive) source.asString as T else null
                    else -> null
                }
            }
            // Int 변환
            Int::class -> {
                when (source) {
                    is Int -> source as T
                    is Number -> source.toInt() as T
                    is JsonElement -> if (source.isJsonPrimitive) source.asInt as T else null
                    else -> null
                }
            }
            // Long 변환
            Long::class -> {
                when (source) {
                    is Long -> source as T
                    is Number -> source.toLong() as T
                    is JsonElement -> if (source.isJsonPrimitive) source.asLong as T else null
                    else -> null
                }
            }
            // Boolean 변환 (문자열, JsonElement, Boolean 모두 처리)
            Boolean::class -> {
                when (source) {
                    is Boolean -> source as T
                    is JsonElement -> {
                        if (source.isJsonPrimitive) {
                            try {
                                source.asBoolean as T
                            } catch (e: Exception) {
                                // 만약 boolean이 아닌 문자열로 저장된 경우
                                val str = source.asString
                                when (str.lowercase(Locale.getDefault())) {
                                    "true" -> true as T
                                    "false" -> false as T
                                    else -> null
                                }
                            }
                        } else null
                    }

                    is String -> {
                        when (source.lowercase(Locale.getDefault())) {
                            "true" -> true as T
                            "false" -> false as T
                            else -> null
                        }
                    }

                    else -> null
                }
            }

            Double::class -> {
                when (source) {
                    is Double -> source as T
                    is Number -> source.toDouble() as T
                    is JsonElement -> if (source.isJsonPrimitive) source.asDouble as T else null
                    else -> null
                }
            }

            // Date 변환 (Date, Long, String, JsonElement 지원)
            Date::class -> {
                when (source) {
                    is Date -> source as T
                    is Long -> Date(source) as T
                    is String -> source.toDate() as T
                    is JsonElement -> {
                        if (source.isJsonPrimitive) source.asString.toDate() as T else null
                    }

                    else -> null
                }
            }
            // MarketapDate 변환 (MarketapDate, String, JsonElement 지원)
            MarketapDate::class -> {
                when (source) {
                    is MarketapDate -> source as T
                    is String -> {
                        try {
                            MarketapDate.fromString(source) as T
                        } catch (e: Exception) {
                            null
                        }
                    }

                    is JsonElement -> {
                        if (source.isJsonPrimitive) {
                            try {
                                MarketapDate.fromString(source.asString) as T
                            } catch (e: Exception) {
                                null
                            }
                        } else null
                    }

                    else -> null
                }
            }
            // List<String> 변환 (List일 경우와 JsonArray를 통한 변환 지원)
            List::class -> {
                if (source is List<*>) {
                    // 모든 요소가 String인지 확인
                    if (source.all { it is String }) {
                        source as T
                    } else {
                        null
                    }
                } else if (source is JsonElement && source.isJsonArray) {
                    try {
                        // JsonArray의 각 요소를 String으로 추출 (primitive일 경우)
                        val list = source.asJsonArray.mapNotNull {
                            if (it.isJsonPrimitive) it.asString else null
                        }
                        list as T
                    } catch (e: Exception) {
                        null
                    }
                } else {
                    null
                }
            }

            else -> null
        }
    }
}