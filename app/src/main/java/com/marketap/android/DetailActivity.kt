package com.marketap.android

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.marketap.sdk.Marketap
import com.marketap.sdk.model.external.EventProperty

class DetailActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // XML 레이아웃 설정 (예: activity_detail.xml)
        setContentView(R.layout.activity_detail)

        // 딥링크 데이터 확인
        intent?.data?.let { uri ->
            val pushId = uri.getQueryParameter("push_id")
            Log.d("DeepLink", "Push ID: $pushId")
        }

        findViewById<View>(R.id.button3).setOnClickListener {
            intent?.data?.let {
                Marketap.trackPageView(
                    EventProperty.Builder()
                        .set("mkt_page_name", "상세화면")
                        .set("mkt_page_uri", it.toString())
                        .set("mkt_page_title", "홈")
                        .build()
                )
            }
        }
    }
}