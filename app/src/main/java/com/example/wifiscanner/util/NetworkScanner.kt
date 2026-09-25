package com.example.wifiscanner.util

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/**
 * ماسح الشبكة المحلية المطوّر: يجمع بين ثلاث طرق للكشف عن الأجهزة:
 *  1) ARP cache (قراءة /proc/net/arp) — يعرض كل الأجهزة التي تواصلت
 *     مع الراوتر مؤخراً حتى لو كانت تحجب الـ ping.
 *  2) Ping sweep متوازي على كامل نطاق /24.
 *  3) UDP sweep على منافذ الخدمات الشائعة (mDNS/SSDP/SMB/DLNA...)
 *     لإيقاظ ARP cache للأجهزة التي ترفض ICMP.
 */
class NetworkScanner(private val subnetPrefix: String) {

    private val pool = Executors.newFixedThreadPool(64)

    interface ScanCallback {
        fun onDeviceFound(ip: String, mac: String)
        fun onFinished(devices: Map<String, String>) // ip -> mac
    }

    /** تنفيذ الفحص بشكل غير متزامن. يعالج النتائج عبر callback في خلفية العمل. */
    fun scan(callback: ScanCallback) {
        val found = LinkedHashMap<String, String>()
        val myIp = localIpAddress()

        fun report(ip: String, mac: String) {
            if (ip == myIp) return // لا نضيف جهازنا مرتين بطريقة خاطئة
            synchronized(found) {
                if (!found.containsKey(ip)) {
                    found[ip] = mac
                    callback.onDeviceFound(ip, mac)
                }
            }
        }

        // 1) ARP cache أولاً — أغنى مصدر للمعلومات بدون أي حجب
        readArpCache().forEach { (ip, mac) ->
            if (ip.startsWith("$subnetPrefix.") && isValidMac(mac)) report(ip, mac)
        }

        // 2) Ping sweep + 3) UDP sweep معاً لبقية العناوين
        val commonPorts = intArrayOf(137, 1900, 5353, 5000, 8080, 445)
        val futures = (1..254).map { host ->
            pool.submit {
                val ip = "$subnetPrefix.$host"
                var alive = ping(ip)
                if (!alive) alive = udpProbe(ip, commonPorts)
                if (!alive) return@submit
                // بعد أي تواصل ناجح يظهر الجهاز غالباً في ARP cache
                val mac = arpLookup(ip) ?: "00:00:00:00:00:00"
                report(ip, mac)
            }
        }

        thread(isDaemon = true) {
            futures.forEach { try { it.get() } catch (_: Exception) {} }
            // جولة أخيرة من ARP cache لالتقاط ما ظهر أثناء الفحص
            readArpCache().forEach { (ip, mac) ->
                if (ip.startsWith("$subnetPrefix.") && isValidMac(mac)) report(ip, mac)
            }
            pool.shutdown()
            callback.onFinished(LinkedHashMap(found))
        }
    }

    private fun ping(ip: String): Boolean {
        return try {
            java.net.InetAddress.getByName(ip).isReachable(500)
        } catch (_: Exception) {
            false
        }
    }

    /** إرسال حزمة UDP قصيرة لإيقاظ استجابة/تسجيل في ARP عند الأجهزة الرافضة لـ ICMP */
    private fun udpProbe(ip: String, ports: IntArray): Boolean {
        return try {
            java.net.DatagramSocket().use { sock ->
                sock.soTimeout = 250
                for (port in ports) {
                    try {
                        val msg = ByteArray(4)
                        sock.send(java.net.DatagramPacket(msg, msg.size,
                            java.net.InetAddress.getByName(ip), port))
                    } catch (_: Exception) {}
                }
            }
            // مهلة قصيرة ليُسجَّل الرد في ARP cache
            Thread.sleep(120)
            arpLookup(ip) != null
        } catch (_: Exception) {
            false
        }
    }

    /** قراءة جدول ARP من النظام */
    private fun readArpCache(): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        try {
            ProcessBuilder("cat", "/proc/net/arp")
                .redirectErrorStream(true)
                .start().let { p ->
                BufferedReader(InputStreamReader(p.inputStream)).useLines { lines ->
                    lines.drop(1).forEach { line ->
                        val cols = line.trim().split(Regex("\\s+"))
                        if (cols.size >= 4) {
                            val ip = cols[0]
                            val mac = cols[3]
                            if (isValidMac(mac)) result[ip] = mac.uppercase()
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        return result
    }

    /** البحث عن MAC لجهاز معين في ARP cache بعد ping */
    private fun arpLookup(ip: String): String? = readArpCache()[ip]

    private fun isValidMac(mac: String): Boolean =
        mac.matches(Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")) &&
                mac != "00:00:00:00:00:00"

    /** عنوان IPv4 الحالي للجهاز على الواي فاي (لاكتشاف "نفسه") */
    private fun localIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                if (!intf.name.lowercase().startsWith("wlan")) continue
                for (addr in intf.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (_: Exception) {}
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
    fun probeOpenPorts(ip: String, ports: IntArray = intArrayOf(22, 80, 443, 445, 5353, 631, 8080)): List<Int> {
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
}
