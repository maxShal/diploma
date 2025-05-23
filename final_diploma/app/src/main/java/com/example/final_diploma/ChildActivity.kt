package com.example.final_diploma

import android.Manifest
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class ChildActivity : AppCompatActivity() {
    private lateinit var startButton: Button
    private lateinit var durationInput: EditText
    private lateinit var selectAppsButton: Button
    private lateinit var blockedAppsList: ListView
    private val blockedApps = mutableSetOf<String>()
    private lateinit var deviceId: String
    private lateinit var adapter: AppAdapter


    private val REQUEST_CODE_FGS_PERMISSION = 101

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_child)

        startButton = findViewById(R.id.startButton)
        durationInput = findViewById(R.id.durationInput)
        selectAppsButton = findViewById(R.id.selectAppsButton)
        blockedAppsList = findViewById(R.id.blockedAppsList)
        deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)


        adapter = AppAdapter(this, emptyList(), blockedApps)
        blockedAppsList.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        blockedAppsList.adapter = adapter
        blockedAppsList.visibility = View.GONE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                100
            )
        }

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            AlertDialog.Builder(this)
                .setMessage("Требуется убрать оптимизацию батареи")
                .setPositiveButton("Перейти в настройки") { _, _ ->
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                }
                .show()
        }

        if (!Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this)
                .setMessage("Требуется разрешение для отображения поверх других окон")
                .setPositiveButton("Перейти в настройки") { _, _ ->
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                }
                .show()

            AlertDialog.Builder(this)
                .setMessage("Включите «Отображать всплывающие окна» в настройках приложения.")
                .setPositiveButton("Перейти в настройки") { _, _ ->
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                }
                .show()
        }


        if (!hasUsageStatsPermission()) {
            AlertDialog.Builder(this)
                .setMessage("Предоставте доступ к статистике")
                .setPositiveButton("Перейти в настройки") { _, _ ->
                    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                }
                .show()
        }

/*        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_multiple_choice, blockedApps.toMutableList())
        blockedAppsList.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        blockedAppsList.adapter = adapter

        selectAppsButton.setOnClickListener {
            showAppSelectionDialog(adapter)
        }*/

        selectAppsButton.setOnClickListener {
            val installedApps = packageManager.getInstalledApplications(0)
                .filter {
                    it.packageName != packageName && // Exclude this app
                            (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 // Exclude system apps
                }
                .map { AppInfo(it.packageName, packageManager.getApplicationLabel(it).toString(), packageManager.getApplicationIcon(it)) }
                .sortedBy { it.name }

            adapter = AppAdapter(this, installedApps, blockedApps)
            blockedAppsList.adapter = adapter
            blockedAppsList.visibility = View.VISIBLE // Show the list
            adapter.notifyDataSetChanged()
        }

        startButton.setOnClickListener {
            if (!hasUsageStatsPermission()) {
                Toast.makeText(this, "Требуется разрешение на доступ к статистике", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                return@setOnClickListener
            }
            val minutes = durationInput.text.toString().toIntOrNull()
            if (minutes != null) {
                val intent = Intent(this, BlockerService::class.java).apply {
                    putExtra("duration", minutes)
                    putStringArrayListExtra("blockedApps", ArrayList(blockedApps))
                    putExtra("deviceId", deviceId)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                Toast.makeText(this, "Фокус начат на $minutes мин.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Введите корректное число минут", Toast.LENGTH_SHORT).show()
            }
        }

    }

/*    private fun showAppSelectionDialog(adapter: ArrayAdapter<String>) {
        val pm = packageManager
        val installedApps = pm.getInstalledApplications(0)
            .filter { it.packageName != packageName }
            .map { it.packageName to (pm.getApplicationLabel(it).toString()) }
            .sortedBy { it.second }

        val appNames = installedApps.map { it.second }.toTypedArray()
        val appPackageNames = installedApps.map { it.first }

        AlertDialog.Builder(this)
            .setTitle("Select Apps to Block")
            .setMultiChoiceItems(appNames, null) { _, which, isChecked ->
                val packageName = appPackageNames[which]
                if (isChecked) {
                    blockedApps.add(packageName)
                } else {
                    blockedApps.remove(packageName)
                }
                adapter.notifyDataSetChanged()
            }
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }*/

    private fun hasUsageStatsPermission(): Boolean {
        return try {
            val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    packageName
                )
            } else {
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Разрешение на уведомления не предоставлено", Toast.LENGTH_LONG).show()
        }
    }
}

data class AppInfo(val packageName: String, val name: String, val icon: android.graphics.drawable.Drawable)

class AppAdapter(
    context: Context,
    private var apps: List<AppInfo>,
    private val selectedApps: MutableSet<String>
) : ArrayAdapter<AppInfo>(context, 0, apps) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.list_item_app, parent, false)
        val app = getItem(position)!!

        val iconView = view.findViewById<ImageView>(R.id.app_icon)
        val nameView = view.findViewById<TextView>(R.id.app_name)
        val checkBox = view.findViewById<CheckBox>(R.id.app_checkbox)

        iconView.setImageDrawable(app.icon)
        nameView.text = app.name
        checkBox.isChecked = selectedApps.contains(app.packageName)

        // Handle selection via ListView's choice mode
        view.setOnClickListener {
            val isChecked = !checkBox.isChecked
            checkBox.isChecked = isChecked
            if (isChecked) {
                selectedApps.add(app.packageName)
            } else {
                selectedApps.remove(app.packageName)
            }
            notifyDataSetChanged()
        }

        return view
    }
}