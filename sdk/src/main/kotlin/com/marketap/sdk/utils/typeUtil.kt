package com.marketap.sdk.utils


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

fun Date.toUTCString(): String {
    val df: DateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    df.timeZone = TimeZone.getTimeZone("UTC")
    return df.format(this)
}

fun String.toDate(): Date {
    val df: DateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    df.timeZone = TimeZone.getTimeZone("UTC")
    return df.parse(this)!!
}

data class MarketapDate(
    val year: Int,
    val month: Int,
    val day: Int,
) {
    companion object {
        // YYYY-MM-DD
        fun fromString(date: String): MarketapDate {
            val split = date.split("-")
            return MarketapDate(
                split[0].toInt(),
                split[1].toInt(),
                split[2].toInt()
            )
        }
    }
}