package com.marketap.sdk.model.external

import java.time.Instant

class Coupon private constructor(
    private val properties: Map<String, Any>
) {
    fun toMap(): Map<String, Any> {
        return properties
    }

    class Builder {
        private val properties = mutableMapOf<String, Any>()

        fun setIssueId(issueId: String): Builder {
            properties["mkt_issue_id"] = issueId
            return this
        }

        fun setIssuedAt(issuedAt: Instant): Builder {
            properties["mkt_issued_at"] = issuedAt.toString()
            return this
        }

        fun setExpiresAt(expiresAt: Instant): Builder {
            properties["mkt_expires_at"] = expiresAt.toString()
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

        fun setName(name: String): Builder {
            properties["mkt_coupon_name"] = name
            return this
        }

        fun setCouponId(couponId: String): Builder {
            properties["mkt_coupon_id"] = couponId
            return this
        }

        fun setIsUsed(isUsedCoupon: Boolean): Builder {
            properties["mkt_is_used"] = isUsedCoupon
            return this
        }

        fun build(): Coupon {
            return Coupon(properties)
        }
    }
}