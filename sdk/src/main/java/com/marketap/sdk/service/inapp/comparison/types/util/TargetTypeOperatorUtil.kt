package com.marketap.sdk.service.inapp.comparison.types.util

import com.marketap.sdk.service.inapp.comparison.types.TargetTypeOperator

fun noTargetOperator(): TargetTypeOperator<Unit> {
    return TargetTypeOperator {
        if (it.isNotEmpty()) throw IllegalArgumentException("Expected no target values")
        Unit
    }
}


inline fun <reified T> singleTargetOperator(): TargetTypeOperator<T> {
    return TargetTypeOperator {
        val value = when {
            it.size == 1 && it[0] is Array<*> -> (it[0] as Array<*>).firstOrNull()
            else -> throw IllegalArgumentException("Expected exactly one target value")
        }
//        println("value ${value.serialize()}, ${value?.javaClass}")
        if (value !is T) throw IllegalArgumentException("Expected target value to be of type ${T::class.java}")
        value
    }
}

inline fun <reified T> pairTargetOperator(): TargetTypeOperator<Pair<T, T>> {
    return TargetTypeOperator {
        val value = when {
            it.size == 1 && it[0] is Array<*> -> (it[0] as Array<*>).toList()
            it.size == 1 && it[0] is List<*> -> it[0] as List<*>
            else -> throw IllegalArgumentException("Expected exactly two target values")
        }

        if (value.size != 2) throw IllegalArgumentException("Expected exactly two target values")
        val first = value[0]
        val second = value[1]

        if (first !is T || second !is T) {
            throw IllegalArgumentException("Expected target values to be of type ${T::class.java}")
        }

        Pair(first as T, second as T)
    }
}

inline fun <reified T> listTargetOperator(): TargetTypeOperator<List<T>> {
    return TargetTypeOperator { it ->
        val value: List<T> = when {
            it.size == 1 && it[0] is Array<*> -> {
                val array = it[0] as Array<*>
                array.mapNotNull { element ->
                    try {
                        element as T
                    } catch (e: ClassCastException) {
                        null
                    }
                }
            }

            it.size == 1 && it[0] is List<*> -> {
                val list = it[0] as List<*>
                list.mapNotNull { element ->
                    try {
                        element as T
                    } catch (e: ClassCastException) {
                        null
                    }
                }
            }

            else -> throw IllegalArgumentException("Expected exactly one target value")
        }

        if (value.isEmpty()) throw IllegalArgumentException("Expected at least one target value")
        value
    }
}