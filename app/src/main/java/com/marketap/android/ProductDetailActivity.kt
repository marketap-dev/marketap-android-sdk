package com.marketap.android

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.marketap.sdk.Marketap.marketap

class ProductDetailActivity : AppCompatActivity() {

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product_detail)

        val product = intent.getSerializableExtra("PRODUCT") as? Product ?: return

        findViewById<TextView>(R.id.textViewProductName).text = product.name
        findViewById<TextView>(R.id.textViewProductId).text = "상품 ID: ${product.id}"
        findViewById<TextView>(R.id.textViewCategory).text = "카테고리: ${product.category}"
        findViewById<TextView>(R.id.textViewManufacturer).text = "제조사: ${product.manufacturer}"
        findViewById<TextView>(R.id.textViewPrice).text = "가격: ${product.price}원"
        findViewById<TextView>(R.id.textViewDescription).text = product.description

        findViewById<Button>(R.id.buttonBuy).setOnClickListener {
            Log.d("ProductDetail", "${product.name} 구매 버튼 클릭됨")
            marketap.trackPurchase(
                product.price, mapOf(
                    "mkt_items" to listOf(
                        mapOf(
                            "mkt_product_id" to product.id,
                            "mkt_product_name" to product.name,
                            "mkt_category1" to product.category,
                            "mkt_product_price" to product.price,
                            "mkt_quantity" to 1
                        )
                    )
                )
            )
        }

        findViewById<Button>(R.id.buttonAddToCart).setOnClickListener {
            Log.d("ProductDetail", "${product.name} 장바구니 담기 버튼 클릭됨")
        }
    }
}