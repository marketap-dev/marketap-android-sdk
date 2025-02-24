package com.marketap.sdk.model.external

class UserProperty private constructor(
    properties: Map<String, Any>
) : Map<String, Any> by properties {

    class Builder : PropertyBuilder<Builder>() {
        override fun self(): Builder {
            return this
        }

        fun setName(name: String): Builder {
            properties["mkt_name"] = name
            return this
        }

        fun setPhoneNumber(phoneNumber: String): Builder {
            properties["mkt_phone_number"] = phoneNumber
            return this
        }

        fun setGender(gender: Gender): Builder {
            properties["mkt_gender"] = gender.name
            return this
        }

        fun setEmail(email: String): Builder {
            properties["mkt_email"] = email
            return this
        }

        fun setGrade(grade: String): Builder {
            properties["mkt_grade"] = grade
            return this
        }

        fun setDateOfBirth(year: Int, month: Int, day: Int): Builder {
            properties["mkt_date_of_birth"] = intsToDate(year, month, day)
            return this
        }

        fun setTextMessageOptIn(optIn: Boolean): Builder {
            properties["mkt_text_message_opt_in"] = optIn
            return this
        }

        fun setKakaoOptIn(optIn: Boolean): Builder {
            properties["mkt_kakao_opt_in"] = optIn
            return this
        }

        fun setEmailOptIn(optIn: Boolean): Builder {
            properties["mkt_email_opt_in"] = optIn
            return this
        }

        fun setAvailablePoints(availablePoints: Int): Builder {
            properties["mkt_available_points"] = availablePoints
            return this
        }

        fun setCartTotalPrice(cartTotalPrice: Double): Builder {
            properties["mkt_cart_total_price"] = cartTotalPrice
            return this
        }

        fun setWishListTotalPrice(wishListTotalPrice: Double): Builder {
            properties["mkt_wish_list_total_price"] = wishListTotalPrice
            return this
        }

        fun setCart(cart: List<Item>): Builder {
            properties["mkt_cart"] = cart.map { it.toMap() }
            return this
        }

        fun setWishList(wishList: List<Item>): Builder {
            properties["mkt_wish_list"] = wishList.map { it.toMap() }
            return this
        }

        fun setCoupons(coupons: List<Coupon>): Builder {
            properties["mkt_coupons"] = coupons.map { it.toMap() }
            return this
        }

        fun build(): UserProperty {
            return UserProperty(properties)
        }
    }
}