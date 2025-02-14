package com.marketap.android

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.marketap.sdk.Marketap
import com.marketap.sdk.MarketapConfig
import com.marketap.sdk.MarketapSDK

class MainActivity : AppCompatActivity() {

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)


        // SDK 초기화
        Marketap.initialize(MarketapConfig("xziewjm", this))


        val button = findViewById<Button>(R.id.button)
        button.setOnClickListener {
            Marketap.trackPageView()
        }

        val button2 = findViewById<Button>(R.id.button2)
        button2.setOnClickListener {
            Marketap.setDeviceId(appSetId = "test_app_set_id_1234")
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                1
            )
        } else {
            printToken()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        printToken()
    }

    private fun printToken() {
        val tokenTextView: TextView = findViewById(R.id.textView3)
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                tokenTextView.text = token
                Log.d("FCM Token", "aaaToken: $token")
            } else {
                Log.w("FCM Token", "aaaFetching FCM token failed", task.exception)
            }
        }
    }

    private fun createNotificationChannel() {
        // API 레벨 26 이상에서만 필요
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
}