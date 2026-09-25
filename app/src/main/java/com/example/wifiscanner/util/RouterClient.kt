package com.example.wifiscanner.util

import android.util.Base64
import java.io.BufferedReader
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

/**
 * عميل الراوتر: يكتشف بوابة الشبكة وطريقة الدخول (HTTP/HTTPS + Basic/Digest)،
 * ويحاول تنفيذ أوامر ACL (حظر/سماح) عبر الواجهات الشائعة (TMAC في راوترات stb).
 */
object RouterClient {

    /** منافذ إدارية شائعة لراوترات ISPs والراوترات المنزلية */
    private val ADMIN_PORTS = intArrayOf(80, 443, 8080, 8443, 7547)

    data class DigestChallenge(val realm: String, val nonce: String, val opaque: String?)

    /** فحص سريع لأي من المنافذ الإدارية مفتوح على البوابة */
    fun detectAdminPort(gateway: String): Int? {
        for (port in ADMIN_PORTS) {
            try {
                Socket().use { s ->
                    s.connect(InetSocketAddress(gateway, port), 250)
                    return port
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun openConnection(gateway: String, path: String, timeoutMs: Int = 3000): HttpURLConnection {
        // نجرب HTTPS أولاً على المنافذ الآمنة ثم HTTP
        val candidates = listOf(
            "https://$gateway${if (path.startsWith(":")) "" else ""}" ,
            "http://$gateway"
        )
        var lastError: Exception? = null
        for (base in candidates) {
            try {
                val url = URL(base + path)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = timeoutMs
                conn.readTimeout = timeoutMs
                conn.instanceFollowRedirects = false
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (WiFiScanner)")
                return conn
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: Exception("cannot open $gateway$path")
    }

    /** GET بسيط مع دعم Basic auth — يُستخدم لقراءة صفحات الراوتر عند توفر الدخول */
    fun httpGet(gateway: String, path: String, user: String?, pass: String?): Pair<Int, String>? {
        return try {
            val conn = openConnection(gateway, path)
            if (user != null && pass != null) {
                val token = Base64.encodeToString("$user:$pass".toByteArray(), Base64.NO_WRAP)
                conn.setRequestProperty("Authorization", "Basic $token")
            }
            val code = conn.responseCode
            val stream: InputStream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
            val body = stream.bufferedReader().use(BufferedReader::readText)
            conn.disconnect()
            code to body
        } catch (_: Exception) {
            null
        }
    }

    /**
     * محاولة حظر/رفع حظر عبر واجهة TMAC الموجودة في كثير من راوترات ISPs
     * (Huawei/ZTE/HG-series admin pages). تنجح فقط إذا كانت جلسة تسجيل الدخول
     * نشطة في نفس المتصفح/التطبيق أو لم يكن الراوتر يتطلب جلسة.
     */
    fun tryTmacBlock(gateway: String, mac: String, block: Boolean): Boolean {
        val port = detectAdminPort(gateway) ?: return false
        if (port != 80) return false // واجهات TMAC تعمل عادة على المنفذ 80
        val action = if (block) "add" else "del"
        val urlStr = "http://$gateway/cgi-bin/tmconf?form=tmac&action=$action&tmusercfg1=[$mac]"
        return try {
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.connectTimeout = 2500
            conn.readTimeout = 2500
            val code = conn.responseCode
            conn.disconnect()
            code in 200..399
        } catch (_: Exception) {
            false
        }
    }

    /** حساب استجابة Digest Auth (RFC 2617) لتنفيذ أوامر على الراوتر */
    fun digestHeader(
        user: String, pass: String, challenge: DigestChallenge,
        method: String, uriPath: String, cnonce: String = "wifi-scanner"
    ): String {
        fun md5(s: String) = java.security.MessageDigest.getInstance("MD5")
            .digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
        val ha1 = md5("$user:${challenge.realm}:$pass")
        val ha2 = md5("$method:$uriPath")
        val nc = "00000001"
        val qop = "auth"
        val response = md5("$ha1:${challenge.nonce}:$nc:$cnonce:$qop:$ha2")
        val opaquePart = challenge.opaque?.let { ", opaque=\"$it\"" } ?: ""
        return "Digest username=\"$user\", realm=\"${challenge.realm}\", " +
                "nonce=\"${challenge.nonce}\", uri=\"$uriPath\", qop=$qop, " +
                "nc=$nc, cnonce=\"$cnonce\", response=\"$response\"$opaquePart"
    }
}
