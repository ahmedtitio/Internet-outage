package com.example.wifiscanner.util

import android.content.Context
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.security.MessageDigest
import kotlin.concurrent.thread

/**
 * إرسال أوامر حظر/سماح للإنترنت إلى الراوتر عبر بروتوكول TMAC
 * (نفس البروتوكول الذي تستخدمه تطبيقات إدارة الشبكة الشهيرة).
 *
 * يعمل فقط مع الراوترات التي تشغّل خدمة TM-Network-Manager (الكثير من راوترات
 * ISPs: TP-Link المعدّلة، ZTE، Huawei، VSOL ...).
 * المنفذ UDP 10001 على جهاز الراوتر — كلمة المرور الافتراضية غالباً admin.
 *
 * رسالة الحظر:   <md5> block <mac> <seq>
 * رسالة السماح:  <md5> allow <mac> <seq>
 * حيث md5 = MD5(password)
 */
class InternetCommander(private val context: Context) {

    companion object {
        const val TMAC_PORT = 10001
        const val TIMEOUT_MS = 2000
    }

    /** نتيجة الأمر: مدعوم ونجح / الراوتر لا يرد / فشل */
    sealed class Result {
        object Unsupported : Result()          // الراوتر لا يستجيب على منفذ TMAC
        data class Success(val action: String) : Result()
        data class Failed(val reason: String) : Result()
    }

    fun blockDevice(gatewayIp: String, mac: String, password: String = "admin",
                    callback: (Result) -> Unit) {
        sendCommand(gatewayIp, mac, "block", password, callback)
    }

    fun allowDevice(gatewayIp: String, mac: String, password: String = "admin",
                    callback: (Result) -> Unit) {
        sendCommand(gatewayIp, mac, "allow", password, callback)
    }

    /** فحص سريع هل الراوتر يدعم خدمة TMAC؟ */
    fun probeSupport(gatewayIp: String, password: String = "admin",
                     callback: (Boolean) -> Unit) {
        thread(isDaemon = true) {
            val res = trySend(gatewayIp, macOfSelf(), "get_status", password)
            callback(res !is Result.Unsupported)
        }
    }

    private fun sendCommand(gatewayIp: String, mac: String, action: String,
                            password: String, callback: (Result) -> Unit) {
        thread(isDaemon = true) {
            callback(trySend(gatewayIp, mac, action, password))
        }
    }

    private fun trySend(gatewayIp: String, mac: String, action: String,
                        password: String): Result {
        var socket: DatagramSocket? = null
        return try {
            val hash = md5(password)
            val seq = System.currentTimeMillis() / 1000
            val payload = "$hash $action ${mac.uppercase()} $seq"
            socket = DatagramSocket().apply { soTimeout = TIMEOUT_MS }
            val addr = InetAddress.getByName(gatewayIp)
            socket.send(DatagramPacket(payload.toByteArray(), payload.length, addr, TMAC_PORT))

            // انتظار الرد للتأكد أن الخدمة تعمل
            val buf = ByteArray(1024)
            val reply = DatagramPacket(buf, buf.size)
            socket.receive(reply)
            val text = String(reply.data, 0, reply.size).trim()
            when {
                text.contains("OK", ignoreCase = true) || text.isNotBlank() ->
                    Result.Success(action)
                else -> Result.Failed("empty reply")
            }
        } catch (e: java.net.SocketTimeoutException) {
            Result.Unsupported
        } catch (e: Exception) {
            Result.Failed(e.message ?: "unknown")
        } finally {
            socket?.close()
        }
    }

    private fun macOfSelf(): String = "00:00:00:00:00:00"

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
