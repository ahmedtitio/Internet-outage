package com.example.wifiscanner.util

import android.content.Context
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build

/** معلومات شبكة الواي فاي الحالية (SSID، BSSID، IP الجهاز، MAC). */
object WifiInfoProvider {

    @Suppress("DEPRECATION")
    fun currentWifiInfo(context: Context): WifiInfo? {
        val wifi = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager
        return try {
            wifi.connectionInfo.takeIf { it.networkId != -1 }
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    fun ssid(info: WifiInfo?): String {
        val raw = info?.ssid ?: return "غير متصل"
        return if (raw == "<unknown ssid>" || raw.isBlank()) "شبكة مخفية/غير معروفة" else raw.removeSurrounding("\"")
    }

    fun bssid(info: WifiInfo?): String = info?.bssid ?: "--"

    @Suppress("DEPRECATION")
    fun deviceIp(info: WifiInfo?): String {
        if (info == null || info.ipAddress == 0) return "--"
        val ip = info.ipAddress
        return String.format(
            "%d.%d.%d.%d",
            ip and 0xFF,
            (ip shr 8) and 0xFF,
            (ip shr 16) and 0xFF,
            (ip shr 24) and 0xFF
        )
    }

    /** MAC الخاص بالجهاز — لا يمكن الحصول عليه في أندرويد الحديث بدون صلاحيات خاصة */
    fun deviceMac(): String = "متاح فقط عبر الراوتر أو الروت"

    fun rssi(info: WifiInfo?): Int = info?.rssi ?: 0

    fun frequencyMhz(info: WifiInfo?): Int = info?.frequency ?: 0

    fun linkSpeed(info: WifiInfo?): Int = info?.linkSpeed ?: 0

    fun isAndroid13Plus(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}
