package com.marketap.android

import android.content.Intent
import android.os.Bundle
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity

class ShoppingActivity : AppCompatActivity() {

    private val productList = listOf(
        Product(
            "P001",
            "iPhone 15",
            "스마트폰",
            "Apple",
            1350000.0,
            "최신 A16 Bionic 칩과 강력한 카메라 성능을 갖춘 스마트폰."
        ),
        Product(
            "P002",
            "Galaxy S23",
            "스마트폰",
            "Samsung",
            1250000.0,
            "삼성의 최신 플래그십 스마트폰으로 강력한 배터리와 성능 제공."
        ),
        Product(
            "P003",
            "MacBook Pro 16\"",
            "노트북",
            "Apple",
            3500000.0,
            "M2 Max 칩셋과 16인치 Retina 디스플레이 탑재."
        ),
        Product("P004", "LG 올레드 TV 55인치", "가전", "LG", 2500000.0, "최신 올레드 패널과 AI 업스케일링 기술 적용."),
        Product(
            "P005",
            "PlayStation 5",
            "게임기",
            "Sony",
            680000.0,
            "강력한 GPU와 듀얼센스 컨트롤러가 제공하는 최고의 게임 경험."
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shopping)

        val listView = findViewById<ListView>(R.id.listViewProducts)
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, productList)
        listView.adapter = adapter

        listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val intent = Intent(this, ProductDetailActivity::class.java)
            intent.putExtra("PRODUCT", productList[position])
            startActivity(intent)
        }
    }
}