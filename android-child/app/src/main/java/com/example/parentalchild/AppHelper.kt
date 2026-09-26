package com.example.parentalchild

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AppHelper(private val context: Context) {

    // ✅ Ilovalar statistikasi ruxsati bor-yo'qligini tekshirish
    fun hasUsagePermission(): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    // ✅ O'rnatilgan ilovalar ro'yxatini JSON sifatida qaytarish
    fun getInstalledApps(): String {
        return try {
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val arr = JSONArray()
            for (app in apps) {
                // Tizim ilovalarini o'tkazib yuborish (ixtiyoriy)
                val isSystem = (app.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                val obj = JSONObject().apply {
                    put("package", app.packageName)
                    put("name", pm.getApplicationLabel(app).toString())
                    put("isSystem", isSystem)
                }
                arr.put(obj)
            }
            arr.toString()
        } catch (_: Exception) {
            "[]"
        }
    }

    // ✅ So'nggi 24 soatdagi ilovalar foydalanish statistikasini JSON sifatida qaytarish
    fun getAppUsage(): String {
        return try {
            if (!hasUsagePermission()) return "[]"

            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val start = now - TimeUnit.HOURS.toMillis(24)

            val stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, start, now
            )

            if (stats.isNullOrEmpty()) return "[]"

            val arr = JSONArray()
            for (stat in stats) {
                if (stat.totalTimeInForeground <= 0) continue
                val obj = JSONObject().apply {
                    put("package", stat.packageName)
                    put("totalTimeMs", stat.totalTimeInForeground)
                    put("lastUsed", stat.lastTimeUsed)
                }
                arr.put(obj)
            }
            arr.toString()
        } catch (_: Exception) {
            "[]"
        }
    }
}
