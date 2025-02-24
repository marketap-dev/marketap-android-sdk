package com.marketap.sdk.service.inapp.comparison.types.util

import com.marketap.sdk.service.inapp.comparison.types.TargetTypeOperator
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone


fun getNow(timeZone: TimeZone = TimeZone.getTimeZone("UTC")): String {
    val df: DateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    df.timeZone = timeZone
    val date: String = df.format(Date())
    return date
}

fun String.toDate(): Date {
    val df: DateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    return df.parse(this)!!
}

fun Int.getRecentDays(): Pair<Date, Date> {
    val now = Date()
    val start = Date(now.time - this * 24 * 3600 * 1000)
    return Pair(start, now)
}

fun Date.toLocalDate(): String {
    return DateFormat.getDateInstance().format(this)
}

val String.year: Int
    get() = this.substring(0, 4).toInt()

val String.month: Int
    get() = this.substring(5, 7).toInt()

val String.day: Int
    get() = this.substring(8, 10).toInt()


fun singleDateTargetOperator(): TargetTypeOperator<String> {
    return TargetTypeOperator {
        val value = when {
            it.size == 1 && it[0] is Array<*> -> (it[0] as Array<*>).firstOrNull()
            else -> throw IllegalArgumentException("Expected exactly one target value")
        }

        value as String
    }
}

fun pairDateTargetOperator(): TargetTypeOperator<Pair<String, String>> {
    return TargetTypeOperator {
        val value = when {
            it.size == 1 && it[0] is Array<*> -> (it[0] as Array<*>).toList()
            it.size == 1 && it[0] is List<*> -> it[0] as List<*>
            else -> throw IllegalArgumentException("Expected exactly two target values")
        }

        if (value.size != 2) throw IllegalArgumentException("Expected exactly two target values")
        val first = value[0]
        val second = value[1]

        (first as String) to (second as String)
    }
}