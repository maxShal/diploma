package com.example.final_diploma


import android.app.*
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.*
import java.util.concurrent.TimeUnit

class BlockerService : Service() {
    companion object {
        private const val TAG = "BlockerService"
        private const val CHANNEL_ID = "BlockerChannel"
        private const val NOTIFICATION_ID = 2
        private const val SERVER_URL_GET = "http://192.168.0.103:8080/api/block/"
    }

    private var handler: Handler? = null
    private var checkForegroundAppRunnable: Runnable? = null
    private var syncWithServerRunnable: Runnable? = null
    private var blockedApps = mutableSetOf<String>() // Combined local and server block list
    private var lastBlockedApp: String? = null
    private var durationMinutes: Int = 0
    private var endTime: Long = 0
    private lateinit var deviceId: String

    override fun onCreate() {
        super.onCreate()
        deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, getForegroundNotification())

        if (!hasUsageStatsPermission()) {
            Log.w(TAG, "Usage stats permission not granted. Prompting user.")
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to prompt for usage stats permission: ${e.message}")
            }
        }

        handler = Handler(Looper.getMainLooper())
        checkForegroundAppRunnable = Runnable {
            checkForegroundApp()
            handler?.postDelayed(checkForegroundAppRunnable!!, 500)
        }
        syncWithServerRunnable = Runnable {
            fetchBlockedAppsFromServer()
            handler?.postDelayed(syncWithServerRunnable!!, 5000)
        }
        handler?.post(checkForegroundAppRunnable!!)
        handler?.post(syncWithServerRunnable!!)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        durationMinutes = intent?.getIntExtra("duration", 0) ?: 0
        endTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(durationMinutes.toLong())
        val localBlockedApps = intent?.getStringArrayListExtra("blockedApps") ?: emptyList()
        blockedApps.addAll(localBlockedApps)
        fetchBlockedAppsFromServer()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun fetchBlockedAppsFromServer() {
        Thread {
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url(SERVER_URL_GET + deviceId)
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        val jsonArray = JSONArray(responseBody)
                        val serverBlockedApps = mutableSetOf<String>()
                        for (i in 0 until jsonArray.length()) {
                            serverBlockedApps.add(jsonArray.getString(i))
                        }
                        synchronized(blockedApps) {
                            blockedApps.addAll(serverBlockedApps)
                        }
                        Log.d(TAG, "Updated blocked apps from server: $serverBlockedApps")
                    } else {
                        Log.w(TAG, "Failed to fetch blocked apps: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching blocked apps: ${e.message}")
            }
        }.start()
    }

    private fun checkForegroundApp() {
        if (System.currentTimeMillis() >= endTime) {
            stopSelf()
            return
        }

        val currentApp = getCurrentForegroundApp()
        Log.d(TAG, "Current foreground app: $currentApp")

        if (currentApp != null && blockedApps.contains(currentApp) && currentApp != lastBlockedApp) {
            Log.d(TAG, "Blocking app: $currentApp")
            lastBlockedApp = currentApp
            launchBlockScreen()
            showBlockScreenNotification(currentApp)
        } else if (currentApp == null || !blockedApps.contains(currentApp)) {
            lastBlockedApp = null
        }
    }

    fun setBlockedApps(apps: Set<String>) {
        synchronized(blockedApps) {
            blockedApps.addAll(apps)
        }
    }

    private fun getCurrentForegroundApp(): String? {
        if (!hasUsageStatsPermission()) {
            Log.w(TAG, "Cannot access usage stats: permission not granted")
            return null
        }

        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val currentTime = System.currentTimeMillis()
        val usageStatsList = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            currentTime - 5000,
            currentTime
        )

        if (usageStatsList.isNullOrEmpty()) {
            Log.w(TAG, "No usage stats available")
            return fallbackToActivityManager()
        }

        val sortedStats: SortedMap<Long, UsageStats> = TreeMap()
        for (stats in usageStatsList) {
            if (stats.lastTimeUsed > 0) {
                sortedStats[stats.lastTimeUsed] = stats
                Log.d(TAG, "UsageStats: pkg=${stats.packageName}, lastUsed=${stats.lastTimeUsed}")
            }
        }

        if (sortedStats.isNotEmpty()) {
            val recentStats = sortedStats[sortedStats.lastKey()]
            if (recentStats != null) {
                Log.d(TAG, "Foreground app detected: ${recentStats.packageName}")
                return recentStats.packageName
            }
        }

        Log.w(TAG, "No foreground app detected via UsageStatsManager")
        return fallbackToActivityManager()
    }

    private fun fallbackToActivityManager(): String? {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningProcesses = am.runningAppProcesses
        if (runningProcesses != null) {
            for (processInfo in runningProcesses) {
                if (processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                    Log.d(TAG, "Fallback foreground app detected: ${processInfo.processName}")
                    return processInfo.processName
                }
            }
        }
        Log.w(TAG, "No foreground app detected via ActivityManager")
        return null
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun launchBlockScreen() {
        val intent = Intent(this, BlockScreenActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            putExtra("blockedApp", lastBlockedApp)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch BlockScreenActivity: ${e.message}")
        }
    }

    private fun showBlockScreenNotification(blockedApp: String) {
        val notificationIntent = Intent(this, BlockScreenActivity::class.java).apply {
            putExtra("blockedApp", blockedApp)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("App Blocked")
            .setContentText("Access to $blockedApp is restricted.")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID + 1, notification)
    }

    private fun getForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Focus Mode")
            .setContentText("Monitoring apps in focus mode")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Blocker Service", NotificationManager.IMPORTANCE_DEFAULT
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler?.removeCallbacks(checkForegroundAppRunnable!!)
        handler?.removeCallbacks(syncWithServerRunnable!!)
    }
}