package com.example.wifiscanner.util

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/**
 * ماسح الشبكة المحلية: يكتشف الأجهزة المتصلة بشبكة الواي فاي الخاصة بك
 * عن طريق:
 *  1) فحص ARP cache (قراءة /proc/net/arp).
 *  2) Ping sweep متوازي على نطاق /24.
 *  3) استعلام اسم المضيف (reverse DNS) لكل جهاز.
 */
class NetworkScanner(private val subnetPrefix: String) {

    private val pool = Executors.newFixedThreadPool(64)

    interface ScanCallback {
        fun onDeviceFound(ip: String, mac: String)
        fun onFinished(devices: Map<String, String>) // ip -> mac
    }

    /**
     * تنفيذ الفحص بشكل غير متزامن. يعالج النتائج عبر callback في خلفية العمل.
     */
    fun scan(callback: ScanCallback) {
        val found = LinkedHashMap<String, String>()

        // 1) ARP cache أولاً
        readArpCache().forEach { (ip, mac) ->
            if (ip.startsWith("$subnetPrefix.") && isValidMac(mac)) {
                synchronized(found) { found[ip] = mac }
                callback.onDeviceFound(ip, mac)
            }
        }

        // 2) Ping sweep للأماكن الناقصة
        val futures = (1..254).map { host ->
            pool.submit {
                val ip = "$subnetPrefix.$host"
                if (!ping(ip)) return@submit
                val mac = arpLookup(ip) ?: "00:00:00:00:00:00"
                synchronized(found) {
                    if (!found.containsKey(ip)) {
                        found[ip] = mac
                        callback.onDeviceFound(ip, mac)
                    }
                }
            }
        }
        thread(isDaemon = true) {
            futures.forEach { try { it.get() } catch (_: Exception) {} }
            pool.shutdown()
            callback.onFinished(LinkedHashMap(found))
        }
    }

    private fun ping(ip: String): Boolean {
        return try {
            InetAddress.getByName(ip).isReachable(700)
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

    /** محاولة التعرف على اسم مضيف عبر reverse DNS */
    fun resolveHostname(ip: String): String? {
        return try {
            val addr = InetAddress.getByName(ip)
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
                    Socket().use { s ->
                        s.connect(InetSocketAddress(ip, port), 300)
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
