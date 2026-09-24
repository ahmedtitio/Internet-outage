package com.example.wifiscanner.util

import android.content.Context
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface
import java.nio.ByteOrder

/**
 * أدوات مساعدة خاصة بشبكة الواي فاي:
 * - حساب نطاق الشبكة المحلية (Subnet) من عنوان IP الحالي.
 * - استخراج البادئة (Prefix) مثل 192.168.1.
 */
object WifiUtils {

    /**
     * يعيد بادئة الشبكة المحلية (أول ثلاثة أوكتتات) مثل "192.168.1"
     * عن طريق قراءة واجهة wlan، وإن فشل يحاول عبر WifiManager.
     */
    fun getLocalSubnetPrefix(context: Context): String? {
        // الطريقة الأولى: البحث عن عنوان IPv4 في واجهات الشبكة (wlan0 / ap ...)
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                val name = intf.name.lowercase()
                if (!(name.startsWith("wlan") || name == "ap")) continue
                for (addr in intf.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress ?: continue
                        return prefixOf(host)
                    }
                }
            }
        } catch (_: Exception) {
        }

        // الطريقة الثانية: من WifiManager (عنوان IP للجهاز بصيغة little-endian)
        try {
            val wifi = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val ipInfo = wifi?.connectionInfo
            if (ipInfo != null && ipInfo.ipAddress != 0) {
                var ip = ipInfo.ipAddress
                if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
                    ip = Integer.reverseBytes(ip)
                }
                val host = String.format(
                    "%d.%d.%d.%d",
                    (ip shr 24) and 0xFF,
                    (ip shr 16) and 0xFF,
                    (ip shr 8) and 0xFF,
                    ip and 0xFF
                )
                return prefixOf(host)
            }
        } catch (_: Exception) {
        }
        return null
    }

    /** بادئة IP عامة متاحة خارج الوحدة */
    fun prefixOfPublic(ip: String): String = prefixOf(ip)

    private fun prefixOf(ip: String): String {
        val parts = ip.split(".")
        return if (parts.size >= 3) "${parts[0]}.${parts[1]}.${parts[2]}" else ip
    }

    /** عنوان البوابة (الراوتر) الافتراضي المتوقع لشبكة /24 */
    fun gatewayOf(prefix: String): String = "$prefix.1"

    /** تحويل رقم المنفذ إلى اسم خدمة معروف عند الإمكان */
    fun serviceName(port: Int): String = when (port) {
        20, 21 -> "FTP"
        22 -> "SSH"
        23 -> "Telnet"
        25 -> "SMTP"
        53 -> "DNS"
        80 -> "HTTP"
        110 -> "POP3"
        137, 138, 139 -> "NetBIOS"
        143 -> "IMAP"
        443 -> "HTTPS"
        445 -> "SMB"
        3389 -> "RDP"
        5000 -> "UPnP"
        5353 -> "mDNS"
        8080 -> "HTTP-Alt"
        else -> "Port $port"
    }
}
