package com.marketap.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.marketap.sdk.Marketap
import com.marketap.sdk.model.external.EventProperty

class MainActivity : AppCompatActivity() {
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        Marketap.signup("test", mapOf(), mapOf(), false)
        Marketap.trackPageView(
            EventProperty.Builder()
                .setAll(mapOf("mkt_page_title" to "홈", "mkt_page_name" to "상세화면"))
                .build()
        )

        val button = findViewById<Button>(R.id.button)
        button.setOnClickListener {
            Marketap.trackPageView(
                EventProperty.Builder()
                    .setAll(mapOf("mkt_page_title" to "홈", "mkt_page_name" to "상세화면"))
                    .build()
            )
        }

        val button2 = findViewById<Button>(R.id.button2)
        button2.setOnClickListener {
            Marketap.setClickHandler {
                if (it.url != null) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it.url)).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    })
                } else {
                    startActivity(packageManager.getLaunchIntentForPackage(packageName)?.apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    })
                }
            }
        }

        val button3 = findViewById<Button>(R.id.button3)
        button3.setOnClickListener {
            Marketap.requestAuthorizationForPushNotifications(this)
        }

        // 버튼 찾기
        val button4 = findViewById<Button>(R.id.button4)
        // 버튼 클릭 이벤트 설정
        button4.setOnClickListener {
            val intent = Intent(this, DetailActivity::class.java)
            intent.putExtra("extra_data", "테스트 데이터") // DetailActivity로 데이터 전달
            intent.setData(Uri.parse("myapp://notification/detail?push_id=1234")) // 딥링크 URI 설정
            startActivity(intent)
        }

        val button5 = findViewById<Button>(R.id.button5)
        button5.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }



        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun createNotificationChannel() {
        // API 레벨 26 이상에서만 필요
        Log.d("FCM Token", "WOW!!")
        val channelId = "default_channel_id"
        val channelName = "Default Channel"
        val descriptionText = "This is the default notification channel"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(channelId, channelName, importance).apply {
            description = descriptionText
            lightColor = android.graphics.Color.RED // LED 색상 설정
        }
        // NotificationManager를 통해 채널을 시스템에 등록
        val notificationManager: NotificationManager =
            getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }
}