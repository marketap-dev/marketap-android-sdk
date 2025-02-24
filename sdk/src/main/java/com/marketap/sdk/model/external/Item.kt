package com.marketap.sdk.model.external

open class Item private constructor(
    private val properties: Map<String, Any>
) {
    fun toMap(): Map<String, Any> {
        return properties
    }

    open class Builder : PropertyBuilder<Builder>() {
        override fun self(): Builder {
            return this
        }

        fun setProductId(productId: String): Builder {
            properties["mkt_product_id"] = productId
            return this
        }

        fun setProductName(productName: String): Builder {
            properties["mkt_product_name"] = productName
            return this
        }

        fun setCategory1(category1: String): Builder {
            properties["mkt_category1"] = category1
            return this
        }

        fun setCategory2(category2: String): Builder {
            properties["mkt_category2"] = category2
            return this
        }

        fun setCategory3(category3: String): Builder {
            properties["mkt_category3"] = category3
            return this
        }

        fun setCategory4(category4: String): Builder {
            properties["mkt_category4"] = category4
            return this
        }

        fun setCategory5(category5: String): Builder {
            properties["mkt_category5"] = category5
            return this
        }

        fun setProductPrice(productPrice: Double): Builder {
            properties["mkt_product_price"] = productPrice
            return this
        }

        fun setQuantity(quantity: Int): Builder {
            properties["mkt_quantity"] = quantity
            return this
        }

        fun setShippingInfo(shippingInfo: String): Builder {
            properties["mkt_shipping_info"] = shippingInfo
            return this
        }

        fun setBundleInfo(bundleInfo: String): Builder {
            properties["mkt_bundle_info"] = bundleInfo
            return this
        }

        fun setCreatedDateTime(createdDateTime: String): Builder {
            properties["mkt_created_datetime"] = createdDateTime
            return this
        }

        fun setVariant(variant: String): Builder {
            properties["mkt_variant"] = variant
            return this
        }

        fun setBrand(brand: String): Builder {
            properties["mkt_brand"] = brand
            return this
        }

        fun setOptionPrice(optionPrice: Double): Builder {
            properties["mkt_option_price"] = optionPrice
            return this
        }


        fun build(): Item {
            return Item(properties)
        }
    }
}