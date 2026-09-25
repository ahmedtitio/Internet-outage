package com.example.wifiscanner.util

import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * قراءة جيران IPv6 (link-local) من `ip -6 neigh show`.
 * بعض الأجهزة الحديثة تظهر في جدول IPv6 فقط، كما أن MAC الخاص بالجهاز نفسه
 * يُقرأ من واجهة wlan لعرضه في الشاشة حتى لا يبدو "جهازي غير ظاهر".
 */
object NetworkNeighbors {

    /** ip -> mac من جدول جيران IPv6 */
    fun ipv6Neighbors(): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        try {
            exec("ip -6 neigh show").forEach { line ->
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.isNotEmpty() && parts[0].contains(":")) { // عنوان IPv6
                    val idx = parts.indexOf("lladdr")
                    if (idx >= 0 && idx + 1 < parts.size) {
                        val mac = parts[idx + 1]
                        if (mac.matches(Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")))
                            result[parts[0]] = mac.uppercase()
                    }
                }
            }
        } catch (_: Exception) {
        }
        return result
    }

    /** MAC الخاص بواجهة الواي فاي في هذا الجهاز (قد يكون محجوباً في Android 12+) */
    fun ownWifiMac(context: android.content.Context): String? {
        @Suppress("DEPRECATION")
        return try {
            val wifi = context.applicationContext
                .getSystemService(android.content.Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            val mac = wifi?.connectionInfo?.macAddress
            if (mac != null && mac != "02:00:00:00:00:00" &&
                mac.matches(Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$"))) mac.uppercase()
            else null
        } catch (_: Exception) {
            null
        }
    }

    private fun exec(cmd: String): List<String> {
        val out = mutableListOf<String>()
        try {
            val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            BufferedReader(InputStreamReader(p.inputStream)).useLines { seq ->
                seq.forEach { out.add(it) }
            }
            p.waitFor()
        } catch (_: Exception) {
        }
        return out
    }
}
