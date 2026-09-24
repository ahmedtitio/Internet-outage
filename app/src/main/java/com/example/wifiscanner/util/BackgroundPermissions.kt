package com.example.wifiscanner.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * أدوات طلب الأذونات extras للتشغيل الخلفي المستقر:
 * - تجاهل تحسين البطارية (Work in background بدون حدود بطارية).
 * - إذن الإشعارات (Android 13+) لإظهار إشعار الفحص المستمر.
 * - فتح إعدادات التطبيقات لبقية الأذونات (Auto-start لدى Xiaomi/Huawei/... إلخ).
 */
object BackgroundPermissions {

    /** هل التطبيق معفى من تحسين البطارية؟ */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** فتح نافذة "السماح بالعمل في الخلفية / عدم تقييد البطارية". */
    fun requestIgnoreBatteryOptimizations(activity: android.app.Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !isIgnoringBatteryOptimizations(activity)
        ) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
                activity.startActivity(intent)
            } catch (e: Exception) {
                // بعض الأجهزة تمنع هذا الإجراء — نفتح القائمة العامة بدلاً منه
                openBatterySettings(activity)
            }
        }
    }

    /** فتح قائمة إعدادات البطارية العامة. */
    fun openBatterySettings(activity: android.app.Activity) {
        try {
            activity.startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
        } catch (e: Exception) {
            openAppSettings(activity)
        }
    }

    /** هل إذن الإشعارات ممنوح؟ */
    fun hasNotificationPermission(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** طلب إذن الإشعارات (Android 13+). */
    fun requestNotificationPermission(activity: android.app.Activity, requestCode: Int = 2002) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            androidx.core.app.ActivityCompat.requestPermissions(
                activity,
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                requestCode
            )
        }
    }

    /** فتح صفحة إعدادات التطبيق (لمنح التشغيل التلقائي/الخلفي يدوياً في أجهزة Xiaomi/Samsung...). */
    fun openAppSettings(activity: android.app.Activity) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${activity.packageName}")
        }
        try {
            activity.startActivity(intent)
        } catch (e: Exception) {
            // لا شيء آخر يمكن فعله
        }
    }
}
