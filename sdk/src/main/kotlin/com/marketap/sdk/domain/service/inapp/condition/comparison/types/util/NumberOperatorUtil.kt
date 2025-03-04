package com.marketap.sdk.domain.service.inapp.condition.comparison.types.util

import com.marketap.sdk.domain.service.inapp.condition.comparison.types.TargetTypeOperator


fun String.toNumber(): Number {
    return if (contains(".")) {
        toDouble()
    } else {
        toLong()
    }
}

fun pairDoubleTargetOperator(): TargetTypeOperator<Pair<Double, Double>> {
    val op = pairTargetOperator<Number>()

    return TargetTypeOperator { targets ->
        op(targets) {
            Pair(it.first.toDouble(), it.second.toDouble())
        }
    }
}

fun singleDoubleTargetOperator(): TargetTypeOperator<Double> {
    return TargetTypeOperator {
        val value = when {
            it.size == 1 && it[0] is Array<*> -> (it[0] as Array<*>).firstOrNull()
            else -> throw IllegalArgumentException("Expected exactly one target value")
        }

        when (value) {
            is Number -> {
                value.toDouble()
            }

            is String -> {
                value.toNumber().toDouble()
            }

            else -> {
                throw IllegalArgumentException("Expected target value to be of type Int")
            }
        }
    }
}


fun pairIntTargetOperator(): TargetTypeOperator<Pair<Int, Int>> {
    val op = pairTargetOperator<Number>()

    return TargetTypeOperator { targets ->
        op(targets) {
            Pair(it.first.toInt(), it.second.toInt())
        }
    }
}

fun singleIntTargetOperator(): TargetTypeOperator<Int> {
    return TargetTypeOperator {
        val value = when {
            it.size == 1 && it[0] is Array<*> -> (it[0] as Array<*>).firstOrNull()
            else -> throw IllegalArgumentException("Expected exactly one target value")
        }
//        println("value ${value.serialize()}, ${value?.javaClass}")
        when (value) {
            is Int -> {
                value
            }

            is String -> {
                value.toInt()
            }

            is Long -> {
                value.toInt()
            }

            else -> {
                throw IllegalArgumentException("Expected target value to be of type Int")
            }
        }
    }
}