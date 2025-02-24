package com.marketap.android

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.marketap.sdk.Marketap
import com.marketap.sdk.model.external.EventProperty
import com.marketap.sdk.model.external.Item

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
            Marketap.trackPurchase(
                product.price,
                EventProperty.Builder()
                    .setItem(
                        Item.Builder()
                            .setProductId(product.id)
                            .setProductName(product.name)
                            .setCategory1(product.category)
                            .setProductPrice(product.price)
                            .build()
                    ).build()
            )
        }

        findViewById<Button>(R.id.buttonAddToCart).setOnClickListener {
            Log.d("ProductDetail", "${product.name} 장바구니 담기 버튼 클릭됨")
        }
    }
}