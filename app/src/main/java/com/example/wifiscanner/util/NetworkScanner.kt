package com.example.wifiscanner.util

import android.content.Context
import android.net.wifi.WifiManager
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/**
 * ماسح الشبكة المحلية المطوّر — يجمع كل الأجهزة المتصلة مهما كانت (أندرويد/آيفون/ويندوز/طابعات/IoT)
 * عبر أربع طبقات كشف:
 *  1) ARP cache من /proc/net/arp ثم أمر `ip neigh` كبديل موثوق.
 *     هذا هو المصدر الأدق: النظام يسجل كل جهاز تواصل معه حتى لو كان يحجب ping.
 *  2) Ping sweep متوازي على كامل نطاق /24.
 *  3) UDP sweep على منافذ الخدمات الشائعة لإيقاظ الأجهزة الرافضة لـ ICMP وتسجيلها في ARP.
 *  4) TCP connect sweep على المنافذ الشائعة (أي محاولة تواصل تسجّل في ARP).
 * + جولة ARP ثانية بعد انتهاء كل الجولات لالتقاط ما ظهر متأخراً.
 */
class NetworkScanner(private val subnetPrefix: String) {

    private val pool = Executors.newFixedThreadPool(80)

    interface ScanCallback {
        fun onDeviceFound(ip: String, mac: String)
        fun onFinished(devices: Map<String, String>) // ip -> mac
    }

    /** تنفيذ الفحص بشكل غير متزامن. يعالج النتائج عبر callback في خلفية العمل. */
    fun scan(callback: ScanCallback) {
        val found = LinkedHashMap<String, String>()

        fun report(ip: String, mac: String) {
            synchronized(found) {
                if (!found.containsKey(ip)) {
                    found[ip] = mac
                    callback.onDeviceFound(ip, mac)
                }
            }
        }

        // 1) ARP cache أولاً — أغنى مصدر للمعلومات بدون أي حجب
        readArpTable().forEach { (ip, mac) ->
            if (inSubnet(ip) && isValidMac(mac)) report(ip, mac)
        }

        val commonUdpPorts = intArrayOf(137, 1900, 5353, 5000, 8080, 445, 53)
        val commonTcpPorts = intArrayOf(22, 80, 443, 445, 631, 8080, 9100)

        // 2+3+4) Ping + UDP + TCP sweeps بالتوازي على كامل النطاق
        val futures = (1..254).map { host ->
            pool.submit {
                val ip = "$subnetPrefix.$host"
                var woke = ping(ip)
                if (!woke) woke = udpProbeQuiet(ip, commonUdpPorts)
                if (!woke) woke = tcpProbeQuiet(ip, commonTcpPorts)
                if (!woke) return@submit
                // بعد أي تواصل يظهر الجهاز غالباً في ARP cache
                val mac = arpLookup(ip) ?: "00:00:00:00:00:00"
                report(ip, mac)
            }
        }

        thread(isDaemon = true) {
            futures.forEach { try { it.get() } catch (_: Exception) {} }
            // جولة أخيرة من ARP cache لالتقاط ما ظهر أثناء الفحص
            readArpTable().forEach { (ip, mac) ->
                if (inSubnet(ip) && isValidMac(mac)) report(ip, mac)
            }
            pool.shutdown()
            callback.onFinished(LinkedHashMap(found))
        }
    }

    private fun inSubnet(ip: String): Boolean = ip.startsWith("$subnetPrefix.")

    private fun ping(ip: String): Boolean {
        return try {
            java.net.InetAddress.getByName(ip).isReachable(400)
        } catch (_: Exception) {
            false
        }
    }

    /** إرسال حزم UDP قصيرة لإيقاظ استجابة/تسجيل في ARP عند الأجهزة الرافضة لـ ICMP */
    private fun udpProbeQuiet(ip: String, ports: IntArray): Boolean {
        var sent = false
        try {
            java.net.DatagramSocket().use { sock ->
                sock.soTimeout = 150
                for (port in ports) {
                    try {
                        val msg = ByteArray(4)
                        sock.send(java.net.DatagramPacket(msg, msg.size,
                            java.net.InetAddress.getByName(ip), port))
                        sent = true
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (_: Exception) {
        }
        if (!sent) return false
        Thread.sleep(100)
        return arpLookup(ip) != null
    }

    /** محاولات TCP قصيرة الأمد — تفشل سريعاً لكنها تُسجّل صاحبها في ARP cache */
    private fun tcpProbeQuiet(ip: String, ports: IntArray): Boolean {
        var tried = false
        for (port in ports) {
            try {
                java.net.Socket().use { s ->
                    s.connect(java.net.InetSocketAddress(ip, port), 120)
                    return true
                }
            } catch (_: Exception) {
                tried = true
            }
        }
        if (!tried) return false
        Thread.sleep(100)
        return arpLookup(ip) != null
    }

    /**
     * قراءة جدول ARP من النظام عبر مصدرين:
     *  - /proc/net/arp (متوفر تاريخياً)
     *  - `ip -4 neigh show` (على بعض الأجهزة الحديثة يكون /proc فارغاً)
     */
    private fun readArpTable(): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        // المصدر 1: /proc/net/arp
        try {
            exec("cat /proc/net/arp").let { lines ->
                lines.drop(1).forEach { line ->
                    val cols = line.trim().split(Regex("\\s+"))
                    if (cols.size >= 4) {
                        val ip = cols[0]
                        val mac = cols[3]
                        if (isValidMac(mac)) result[ip] = mac.uppercase()
                    }
                }
            }
        } catch (_: Exception) {
        }
        // المصدر 2: ip neigh (صيغة: "IP dev X lladdr MAC REACHABLE")
        try {
            exec("ip -4 neigh show").forEach { line ->
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.isNotEmpty() && parts[0].matches(Regex("^\\d+\\.\\d+\\.\\d+\\.\\d+$"))) {
                    val idx = parts.indexOf("lladdr")
                    if (idx >= 0 && idx + 1 < parts.size) {
                        val mac = parts[idx + 1]
                        if (isValidMac(mac) && !result.containsKey(parts[0]))
                            result[parts[0]] = mac.uppercase()
                    }
                }
            }
        } catch (_: Exception) {
        }
        return result
    }

    private fun exec(cmd: String): List<String> {
        val out = mutableListOf<String>()
        try {
            val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            BufferedReader(InputStreamReader(p.inputStream)).useLines { seq ->
                seq.forEach { l -> out.add(l) }
            }
            p.waitFor()
        } catch (_: Exception) {
        }
        return out
    }

    /** البحث عن MAC لجهاز معين في ARP cache بعد probe */
    private fun arpLookup(ip: String): String? = readArpTable()[ip]

    private fun isValidMac(mac: String): Boolean =
        mac.matches(Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")) &&
                mac != "00:00:00:00:00:00"

    /** عنوان IPv4 الحالي للجهاز على الواي فاي */
    private fun localIpAddress(): String? {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                if (!intf.name.lowercase().startsWith("wlan")) continue
                for (addr in intf.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (_: Exception) {
        }
        return null
    }

    /** محاولة التعرف على اسم مضيف عبر reverse DNS */
    fun resolveHostname(ip: String): String? {
        return try {
            val addr = java.net.InetAddress.getByName(ip)
            val name = addr.canonicalHostName
            if (name == ip) null else name
        } catch (_: Exception) {
            null
        }
    }

    /** فحص منافذ شائعة لمعرفة نوع الجهاز تقديرياً */
    fun probeOpenPorts(ip: String, ports: IntArray = intArrayOf(22, 80, 443, 445, 5353, 631, 8080, 9100)): List<Int> {
        val open = mutableListOf<Int>()
        val tasks = ports.map { port ->
            pool.submit {
                try {
                    java.net.Socket().use { s ->
                        s.connect(java.net.InetSocketAddress(ip, port), 300)
                        synchronized(open) { open.add(port) }
                    }
                } catch (_: Exception) {
                }
            }
        }
        tasks.forEach { try { it.get() } catch (_: Exception) {} }
        return open.sorted()
    }

    companion object {
        /** التحقق من أن البوابة .1 موجودة فعلاً في ARP — مفيد للتشخيص */
        fun gatewayInArp(subnetPrefix: String): Boolean {
            val gw = "$subnetPrefix.1"
            return try {
                ProcessBuilder("cat", "/proc/net/arp").start().let { p ->
                    BufferedReader(InputStreamReader(p.inputStream)).useLines { lines ->
                        lines.any { it.trim().startsWith(gw) }
                    }
                }
            } catch (_: Exception) {
                false
            }
        }

        @Suppress("DEPRECATION")
        fun wifiManager(context: Context): WifiManager? =
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    }
}
