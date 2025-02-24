package com.marketap.android

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class WelcomeActivity : AppCompatActivity() {
    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)

        val userId = intent.getStringExtra("USER_ID") ?: "Unknown User"
        findViewById<TextView>(R.id.textViewWelcome).text = "Welcome! Your ID: $userId"
        Log.d("WelcomeActivity", "User ID: $userId")

        val buttonShop = findViewById<Button>(R.id.buttonShop)
        buttonShop.setOnClickListener {
            startActivity(Intent(this, ShoppingActivity::class.java))
        }
    }
}