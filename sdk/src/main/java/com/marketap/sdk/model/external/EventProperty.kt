package com.marketap.sdk.model.external

class EventProperty private constructor(
    properties: Map<String, Any>
) : Map<String, Any> by properties {
    class Builder : PropertyBuilder<Builder>() {
        private val items: MutableList<Item> = mutableListOf()

        override fun self(): Builder {
            return this
        }

        fun setRevenue(revenue: Double): Builder {
            properties["mkt_revenue"] = revenue
            return this
        }

        fun setOrderId(orderId: String): Builder {
            properties["mkt_order_id"] = orderId
            return this
        }

        fun setItems(items: List<Item>): Builder {
            this.items.addAll(items)
            return this
        }

        fun build(): EventProperty {
            return EventProperty(properties + mapOf("mkt_items" to items.map { it.toMap() }))
        }
    }
}