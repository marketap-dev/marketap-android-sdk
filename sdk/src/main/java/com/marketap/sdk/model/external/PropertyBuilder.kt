package com.marketap.sdk.model.external

import java.time.Instant

abstract class PropertyBuilder<T : PropertyBuilder<T>> {
    protected val properties = mutableMapOf<String, Any>()

    abstract fun self(): T

    fun setAll(properties: Map<String, Any>): T {
        this.properties.putAll(properties)
        return self()
    }

    fun set(key: String, value: Long): T {
        properties[key] = value
        return self()
    }

    fun set(key: String, value: String): T {
        properties[key] = value
        return self()
    }

    fun set(key: String, value: Boolean): T {
        properties[key] = value
        return self()
    }

    fun set(key: String, value: Double): T {
        properties[key] = value
        return self()
    }

    fun set(key: String, value: List<String>): T {
        properties[key] = value
        return self()
    }

    fun set(key: String, value: Instant): T {
        properties[key] = value.toString()
        return self()
    }

    fun setDate(key: String, year: Int, month: Int, day: Int): T {
        properties[key] = intsToDate(year, month, day)
        return self()
    }

    protected fun intsToDate(year: Int, month: Int, day: Int): String {
        return "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
    }
}