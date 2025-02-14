package com.marketap.android

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        Log.d("FCM Message", "Token: $token")

        super.onNewToken(token)
        // Display token on screen or send it to your server as needed
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // Log the message details
        Log.d("FCM Message", "From: ${remoteMessage.from}")

        // Check if the message contains data payload
        if (remoteMessage.data.isNotEmpty()) {
            Log.d("FCM Message", "Message data payload: ${remoteMessage.data}")
            val title = remoteMessage.data["title"] ?: "Default Title"
            val body = remoteMessage.data["message"] ?: "Default Body"

            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

            // PendingIntent 생성
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE // FLAG_IMMUTABLE은 Android 12 이상에서 필수
            )

            // 알림 표시 코드 (NotificationCompat 사용)
            val builder = NotificationCompat.Builder(this, "default_channel_id")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setColor(android.graphics.Color.RED)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true) // 클릭 시 알림 자동 제거

            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                with(NotificationManagerCompat.from(this)) {
                    notify(1, builder.build())
                }
            }
        }

        // Check if the message contains a notification payload
        remoteMessage.notification?.let {
            Log.d("FCM Message", "Notification title: ${it.title}")
            Log.d("FCM Message", "Notification body: ${it.body}")
        }
    }

}