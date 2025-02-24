package com.marketap.sdk.model.external

class Coupon private constructor(
    private val properties: Map<String, Any>
) {
    fun toMap(): Map<String, Any> {
        return properties
    }

    class Builder {
        private val properties = mutableMapOf<String, Any>()

        fun build(): Coupon {
            return Coupon(properties)
        }
    }
}