package com.example.wifiscanner.util

import android.content.Context
import android.net.wifi.WifiManager
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.util.concurrent.ConcurrentHashMap

/**
 * مُكتشِف الأجهزة عبر بروتوكولات mDNS / SSDP (موجات البث المتعدد).
 * معظم الهواتف والأجهزة الذكية تعلن عن نفسها باستمرار عبر هذه البروتوكولات،
 * لذا الاستماع لها يعطي نتائج أدق وأسرع من ping sweep الذي تحجبه الجدران النارية.
 */
class MulticastHelper(private val context: Context) {

    @Volatile private var running = false
    private var socket: MulticastSocket? = null
    // mac -> friendly name (من TXT/USN records)
    val discoveredNames = ConcurrentHashMap<String, String>()

    /**
     * بدء الاستماع لموجات mDNS (المنفذ 5353) وSSDP (المنفذ 1900) في خيط منفصل.
     * يجب أن يسبقه lock متعدد البث عبر [acquireMulticastLock].
     */
    fun start() {
        if (running) return
        running = true
        Thread {
            try {
                val s = MulticastSocket(null as InetSocketAddress?)
                s.reuseAddress = true
                s.bind(InetSocketAddress(5353))
                socket = s
                val groups = listOf(
                    InetAddress.getByName("224.0.0.251"), // mDNS
                    InetAddress.getByName("239.255.255.250") // SSDP
                )
                groups.forEach { try { s.joinGroup(it) } catch (_: Exception) {} }

                // إرسال استعلام SSDP للتعجيل بالكشف
                Thread {
                    try {
                        val q = ("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\n" +
                                "MAN: \"ssdp:discover\"\r\nMX: 2\r\nST: ssdp:all\r\n\r\n").toByteArray()
                        val p = DatagramPacket(q, q.size, InetAddress.getByName("239.255.255.250"), 1900)
                        repeat(2) { s.send(p); Thread.sleep(600) }
                    } catch (_: Exception) {}
                }.start()

                val buf = ByteArray(4096)
                while (running) {
                    val packet = DatagramPacket(buf, buf.size)
                    try {
                        s.soTimeout = 1000
                        s.receive(packet)
                        parse(packet.data, packet.length)
                    } catch (_: Exception) {
                        // timeout — نكمل الحلقة
                    }
                }
            } catch (_: Exception) {
            } finally {
                try { socket?.close() } catch (_: Exception) {}
                socket = null
            }
        }.apply { isDaemon = true }.start()
    }

    private fun parse(data: ByteArray, len: Int) {
        try {
            val text = String(data, 0, len, Charsets.ISO_8859_1)
            // استخراج اسم ودود من سجلات SSDP/mDNS
            Regex("(?:SERVER:|NT:|CN=|n=)([A-Za-z0-9 ._\\-()]{3,40})")
                .find(text)?.groupValues?.get(1)?.trim()?.let { name ->
                    discoveredNames.putIfAbsent(name.lowercase(), name)
                }
        } catch (_: Exception) {}
    }

    fun stop() {
        running = false
        try { socket?.close() } catch (_: Exception) {}
    }

    companion object {
        private var lock: WifiManager.MulticastLock? = null

        /**
         * الحصول على MulticastLock — بدونه لا تستلم التطبيقات موجات البث المتعدد
         * على معظم أجهزة Android حتى لو كان الكود صحيحاً.
         */
        fun acquireMulticastLock(context: Context) {
            try {
                val wifi = context.applicationContext
                    .getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
                val l = lock ?: wifi.createMulticastLock("wifiScannerMcast").apply {
                    setReferenceCounted(false)
                }
                lock = l
                if (!l.isHeld) l.acquire()
            } catch (_: Exception) {}
        }

        fun releaseMulticastLock() {
            try {
                if (lock?.isHeld == true) lock?.release()
            } catch (_: Exception) {}
        }
    }
}
