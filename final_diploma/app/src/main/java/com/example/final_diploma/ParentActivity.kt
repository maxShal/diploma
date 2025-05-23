package com.example.final_diploma

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class ParentActivity :AppCompatActivity() {
    companion object {
        private const val SERVER_URL = "http://192.168.0.103:8080/api/block"
    }

    private lateinit var deviceIdInput: EditText
    private lateinit var packageNameInput: EditText
    private lateinit var blockButton: Button
    private lateinit var unblockButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_parent)

        deviceIdInput = findViewById(R.id.deviceIdInput)
        packageNameInput = findViewById(R.id.packageNameInput)
        blockButton = findViewById(R.id.blockButton)
        unblockButton = findViewById(R.id.unblockButton)

        blockButton.setOnClickListener { sendBlockRequest(true) }
        unblockButton.setOnClickListener { sendBlockRequest(false) }
    }

    private fun sendBlockRequest(block: Boolean) {
        val deviceId = deviceIdInput.text.toString().trim()
        val packageName = packageNameInput.text.toString().trim()

        if (deviceId.isEmpty() || packageName.isEmpty()) {
            Toast.makeText(this, "Please enter device ID and package name", Toast.LENGTH_SHORT).show()
            return
        }

        Thread {
            try {
                val client = OkHttpClient()
                val json = JSONObject().apply {
                    put("deviceId", deviceId)
                    put("packageName", packageName)
                    put("block", block)
                }

                val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url(SERVER_URL)
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    runOnUiThread {
                        if (response.isSuccessful) {
                            try {
                                Toast.makeText(this, response.body?.string() ?: "Success", Toast.LENGTH_LONG).show()
                            } catch (e: IOException) {
                                Toast.makeText(this, "Error reading response", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(this, "Request failed: ${response.code}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
}