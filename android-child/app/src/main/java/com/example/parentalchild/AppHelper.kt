package com.example.parentalchild

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import org.json.JSONArray
import org.json.JSONObject

class AppHelper(private val ctx: Context) {

    fun getInstalledApps(): String {
        val pm = ctx.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val arr = JSONArray()
        apps.filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
            .sortedBy { pm.getApplicationLabel(it).toString() }
            .forEach { app ->
                try {
                    arr.put(JSONObject()
                        .put("name", pm.getApplicationLabel(app).toString())
                        .put("package", app.packageName))
                } catch (_: Exception) {}
            }
        return arr.toString()
    }

    fun getAppUsage(): String {
        return try {
            val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end   = System.currentTimeMillis()
            val start = end - 24 * 60 * 60 * 1000L
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)

            if (stats.isNullOrEmpty()) return "[]"

            val pm  = ctx.packageManager
            val arr = JSONArray()

            stats.filter { it.totalTimeInForeground > 60_000L }
                 .sortedByDescending { it.totalTimeInForeground }
                 .take(30)
                 .forEach { stat ->
                     try {
                         val info = pm.getApplicationInfo(stat.packageName, 0)
                         val name = pm.getApplicationLabel(info).toString()
                         val min  = stat.totalTimeInForeground / 60_000L
                         arr.put(JSONObject()
                             .put("name", name)
                             .put("package", stat.packageName)
                             .put("minutes", min))
                     } catch (_: Exception) {}
                 }
            arr.toString()
        } catch (_: Exception) { "[]" }
    }

    fun hasUsagePermission(): Boolean {
        return try {
            val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, end - 10_000, end)
            !stats.isNullOrEmpty()
        } catch (_: Exception) { false }
    }
}
