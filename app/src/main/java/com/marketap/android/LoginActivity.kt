package com.marketap.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.marketap.sdk.Marketap.marketap
import java.util.UUID

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val editTextId = findViewById<EditText>(R.id.editTextId)
        val editTextPassword = findViewById<EditText>(R.id.editTextPassword)
        val buttonLogin = findViewById<Button>(R.id.buttonLogin)

        buttonLogin.setOnClickListener {
            val userId = editTextId.text.toString()
            val password = editTextPassword.text.toString()

            if (userId.isBlank() || password.isBlank()) {
                Toast.makeText(this, "아이디와 비밀번호를 입력하세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val sharedPreferences = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
            val storedUserId = sharedPreferences.getString("$userId:$password", null)

            val finalUserId = storedUserId ?: UUID.randomUUID().toString().also {
                sharedPreferences.edit().putString("$userId:$password", it).apply()
            }

            val intent = Intent(this, WelcomeActivity::class.java).apply {
                putExtra("USER_ID", finalUserId)
            }
            marketap.login(finalUserId)
            startActivity(intent)
            finish()
        }
    }
}