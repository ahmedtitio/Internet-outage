package com.example.wifiscanner.util

import android.content.Context
import android.net.TrafficStats

/**
 * بيانات جهاز متصل بالشبكة مع إحصائيات الاستهلاك المخزّنة محلياً.
 * ملاحظة: لا يمكن لتطبيق أندرويد (بدون صلاحيات الروتر) قياس استهلاك
 * الأجهزة الأخرى مباشرةً؛ لذلك نخزّن تقديراً تراكمياً لكل MAC/IP
 * بالإضافة إلى استهلاك التطبيقات على هذا الجهاز عبر TrafficStats.
 */
data class ConnectedDevice(
    val ip: String,
    val mac: String,
    val hostname: String?,
    val vendor: String?,
    val isMe: Boolean = false,
    var totalBytes: Long = 0L,          // إجمالي الاستهلاك التراكمي المقدّر
    var lastSeenBytes: Long = 0L,       // آخر قراءة للسنّافة
    var lastSeenTime: Long = 0L         // وقت آخر ظهور
)

/**
 * حسابات عرض البيانات (RX/TX) على مستوى الجهاز الحالي والشبكة.
 */
object DataUsageTracker {

    fun deviceTotalRx(): Long = TrafficStats.getTotalRxBytes().coerceAtLeast(0L)
    fun deviceTotalTx(): Long = TrafficStats.getTotalTxBytes().coerceAtLeast(0L)

    /** Rx الخاص بواجهة الواي فاي إن توفرت */
    fun wifiRx(context: Context): Long {
        return try {
            TrafficStats.getMobileRxBytes() // يُستخدم كمقياس احتياطي
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * قراءة /proc/net/dev للحصول على بايتات واجهة wlan0 (rx, tx).
     */
    fun readWlanInterfaceBytes(): Pair<Long, Long> {
        return try {
            var rx = 0L
            var tx = 0L
            java.io.File("/proc/net/dev").useLines { lines ->
                lines.forEach { line ->
                    val l = line.trim()
                    if (l.startsWith("wlan")) {
                        val parts = l.split(Regex("\\s+"))
                        if (parts.size >= 10) {
                            rx = parts[1].toLongOrNull() ?: 0L
                            tx = parts[9].toLongOrNull() ?: 0L
                        }
                    }
                }
            }
            rx to tx
        } catch (_: Exception) {
            0L to 0L
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var i = 0
        while (value >= 1024 && i < units.size - 1) {
            value /= 1024
            i++
        }
        return String.format("%.2f %s", value, units[i])
    }

    fun formatSpeed(bytesPerSec: Double): String = "${formatBytes(bytesPerSec.toLong())}/ث"
}
