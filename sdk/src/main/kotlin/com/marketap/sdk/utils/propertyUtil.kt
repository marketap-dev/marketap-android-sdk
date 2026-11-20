package com.marketap.sdk.utils

private val propertyValueAdapter = moshi.adapter(Any::class.java)

/** 속성 값으로 보낼 수 있는지. 전송할 때와 같은 방식으로 직렬화되는 값만 보낼 수 있다. */
private fun isSupportedPropertyValue(value: Any?): Boolean {
    return try {
        propertyValueAdapter.toJson(value)
        true
    } catch (e: Exception) {
        false
    }
}

/** 보낼 수 없는 값을 가진 첫 속성 이름. 없으면 null. */
internal fun Map<String, Any>?.findUnsupportedPropertyName(): String? {
    if (this == null) return null
    return entries.firstOrNull { !isSupportedPropertyValue(it.value) }?.key
}

/** 보낼 수 없는 값을 가진 속성만 뺀 맵. 뺀 속성 이름은 [onUnsupported] 로 알린다. */
internal fun Map<String, Any>?.filterSupportedProperties(onUnsupported: (name: String) -> Unit): Map<String, Any> {
    if (this == null) return emptyMap()
    val result = LinkedHashMap<String, Any>(size)
    for ((key, value) in this) {
        if (isSupportedPropertyValue(value)) result[key] = value else onUnsupported(key)
    }
    return result
}
