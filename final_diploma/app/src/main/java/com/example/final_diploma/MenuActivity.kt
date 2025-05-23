package com.example.final_diploma

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import android.provider.Settings
import android.widget.TextView


class MenuActivity : AppCompatActivity() {
    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu)

        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        Log.d("DeviceID", androidId)

        val parentButton: Button = findViewById(R.id.parentButton)
        val childButton: Button = findViewById(R.id.childButton)

        parentButton.setOnClickListener {
            startActivity(Intent(this, ParentActivity::class.java))
        }

        childButton.setOnClickListener {
            startActivity(Intent(this, ChildActivity::class.java))
        }
    }
}