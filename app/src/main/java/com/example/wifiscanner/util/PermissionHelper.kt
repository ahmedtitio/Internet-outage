package com.example.wifiscanner.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/** إدارة أذونات فحص الشبكة حسب إصدار أندرويد. */
object PermissionHelper {

    const val REQ_CODE = 1001

    fun requiredPermissions(): Array<String> {
        val list = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // NEARBY_WIFI_DEVICES يكفي لفحص الشبكة؛ الموقع لم يعد مطلوباً
            list += Manifest.permission.NEARBY_WIFI_DEVICES
        } else {
            list += Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list += Manifest.permission.POST_NOTIFICATIONS
        }
        return list.toTypedArray()
    }

    /** الأذونات الحرجة التي بدونها لا يعمل الفحص إطلاقاً. */
    private val criticalPermissions: List<String>
        get() = listOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                Manifest.permission.NEARBY_WIFI_DEVICES
            else Manifest.permission.ACCESS_FINE_LOCATION
        )

    fun hasCriticalPermissions(context: Context): Boolean =
        criticalPermissions.all {
            ContextCompat.checkSelfPermission(context, it) ==
                    PackageManager.PERMISSION_GRANTED
        }

    /** هل رفض المستخدم الإذن نهائياً (النظام لن يعرض الحوار مرة أخرى)؟ */
    fun isPermanentlyDenied(activity: Activity): Boolean =
        criticalPermissions.any {
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, it) &&
                    ContextCompat.checkSelfPermission(activity, it) !=
                    PackageManager.PERMISSION_GRANTED
        }

    /** هل المستخدم متصل فعلياً بشبكة واي فاي الآن؟ */
    fun isOnWifi(context: Context): Boolean {
        return try {
            val cm = context.applicationContext
                .getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            val net = cm?.activeNetwork
            val caps = net?.let { cm.getNetworkCapabilities(it) }
            caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
        } catch (_: Exception) {
            false
        }
    }

    fun hasScanPermissions(context: Context): Boolean =
        requiredPermissions().all {
            ContextCompat.checkSelfPermission(context, it) ==
                    PackageManager.PERMISSION_GRANTED
        }

    fun requestScanPermissions(activity: Activity) {
        ActivityCompat.requestPermissions(activity, requiredPermissions(), REQ_CODE)
    }
}
