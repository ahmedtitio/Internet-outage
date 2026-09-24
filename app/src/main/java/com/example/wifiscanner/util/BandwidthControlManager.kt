package com.example.wifiscanner.util

import android.content.Context
import android.net.TrafficStats
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * مدير التحكم في سرعة الإنترنت / قطع الاتصال عن الأجهزة المتطفلة على الشبكة.
 *
 * ملاحظة تقنية مهمة: التحكم الحقيقي في أجهزة أخرى على الشبكة (Throttling/Block)
 * يتطلب صلاحيات إدارية على الراوتر أو استخدام تقنيات متقدمة مثل:
 * - ARP spoofing + iptables (يتطلب root)
 * - Access Point Isolation من إعدادات الراوتر
 * - MAC Filtering في الراوتر
 *
 * هذا الكلاس يوفر واجهة برمجية لإدارة قوائم الحظر والتحكم، مع تخزين محلي للإعدادات.
 */
class BandwidthControlManager(context: Context) {

    private val prefs = context.getSharedPreferences("bandwidth_control", Context.MODE_PRIVATE)
    private val blockedDevicesFile = File(context.filesDir, "blocked_devices.json")
    private val throttleRulesFile = File(context.filesDir, "throttle_rules.json")

    data class ThrottleRule(
        val macAddress: String,
        val deviceName: String,
        val downloadLimitKbps: Int,  // 0 = غير محدود
        val uploadLimitKbps: Int,    // 0 = غير محدود
        val isActive: Boolean = true,
        val createdAt: Long = System.currentTimeMillis()
    )

    data class BlockedDevice(
        val macAddress: String,
        val ipAddress: String,
        val deviceName: String,
        val reason: String = "",
        val blockedAt: Long = System.currentTimeMillis(),
        val expiresAt: Long? = null  // null = حظر دائم
    )

    // ==================== إدارة الحظر ====================

    /** حظر جهاز من الوصول للإنترنت */
    fun blockDevice(mac: String, ip: String, name: String, reason: String = ""): Boolean {
        val blocks = getBlockedDevices().toMutableList()
        if (blocks.any { it.macAddress.equals(mac, ignoreCase = true) }) return false
        
        blocks.add(BlockedDevice(mac, ip, name, reason))
        saveBlockedDevices(blocks)
        
        // تسجيل الحدث
        logAction("BLOCK", mac, name)
        return true
    }

    /** رفع الحظر عن جهاز */
    fun unblockDevice(mac: String): Boolean {
        val blocks = getBlockedDevices().toMutableList()
        val removed = blocks.removeAll { it.macAddress.equals(mac, ignoreCase = true) }
        if (removed) {
            saveBlockedDevices(blocks)
            logAction("UNBLOCK", mac, "")
        }
        return removed
    }

    /** التحقق إذا كان الجهاز محظوراً */
    fun isDeviceBlocked(mac: String): Boolean {
        return getBlockedDevices().any { 
            it.macAddress.equals(mac, ignoreCase = true) && 
            (it.expiresAt == null || it.expiresAt > System.currentTimeMillis())
        }
    }

    /** الحصول على قائمة الأجهزة المحظورة */
    fun getBlockedDevices(): List<BlockedDevice> {
        if (!blockedDevicesFile.exists()) return emptyList()
        return try {
            val json = JSONArray(blockedDevicesFile.readText())
            (0 until json.length()).map { i ->
                val obj = json.getJSONObject(i)
                BlockedDevice(
                    macAddress = obj.getString("mac"),
                    ipAddress = obj.getString("ip"),
                    deviceName = obj.getString("name"),
                    reason = obj.optString("reason", ""),
                    blockedAt = obj.optLong("blockedAt", 0),
                    expiresAt = if (obj.has("expiresAt")) obj.getLong("expiresAt") else null
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveBlockedDevices(devices: List<BlockedDevice>) {
        val json = JSONArray()
        devices.forEach { device ->
            json.put(JSONObject().apply {
                put("mac", device.macAddress)
                put("ip", device.ipAddress)
                put("name", device.deviceName)
                put("reason", device.reason)
                put("blockedAt", device.blockedAt)
                device.expiresAt?.let { put("expiresAt", it) }
            })
        }
        blockedDevicesFile.writeText(json.toString(2))
    }

    // ==================== إدارة قواعد الحد من السرعة ====================

    /** إضافة قاعدة تحديد سرعة لجهاز */
    fun addThrottleRule(mac: String, name: String, downloadKbps: Int, uploadKbps: Int): Boolean {
        val rules = getThrottleRules().toMutableList()
        if (rules.any { it.macAddress.equals(mac, ignoreCase = true) }) {
            rules.removeAll { it.macAddress.equals(mac, ignoreCase = true) }
        }
        
        rules.add(ThrottleRule(mac, name, downloadKbps, uploadKbps))
        saveThrottleRules(rules)
        logAction("THROTTLE", mac, "$downloadKbps/$uploadKbps Kbps")
        return true
    }

    /** حذف قاعدة تحديد سرعة */
    fun removeThrottleRule(mac: String): Boolean {
        val rules = getThrottleRules().toMutableList()
        val removed = rules.removeAll { it.macAddress.equals(mac, ignoreCase = true) }
        if (removed) {
            saveThrottleRules(rules)
            logAction("REMOVE_THROTTLE", mac, "")
        }
        return removed
    }

    /** تفعيل/تعطيل قاعدة */
    fun toggleThrottleRule(mac: String, active: Boolean): Boolean {
        val rules = getThrottleRules().toMutableList()
        val index = rules.indexOfFirst { it.macAddress.equals(mac, ignoreCase = true) }
        if (index >= 0) {
            rules[index] = rules[index].copy(isActive = active)
            saveThrottleRules(rules)
            return true
        }
        return false
    }

    /** الحصول على جميع قواعد التحديد */
    fun getThrottleRules(): List<ThrottleRule> {
        if (!throttleRulesFile.exists()) return emptyList()
        return try {
            val json = JSONArray(throttleRulesFile.readText())
            (0 until json.length()).map { i ->
                val obj = json.getJSONObject(i)
                ThrottleRule(
                    macAddress = obj.getString("mac"),
                    deviceName = obj.getString("name"),
                    downloadLimitKbps = obj.getInt("download"),
                    uploadLimitKbps = obj.getInt("upload"),
                    isActive = obj.optBoolean("active", true),
                    createdAt = obj.optLong("createdAt", 0)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** الحصول على قاعدة لجهاز معين */
    fun getThrottleRuleForDevice(mac: String): ThrottleRule? {
        return getThrottleRules().find { 
            it.macAddress.equals(mac, ignoreCase = true) && it.isActive 
        }
    }

    private fun saveThrottleRules(rules: List<ThrottleRule>) {
        val json = JSONArray()
        rules.forEach { rule ->
            json.put(JSONObject().apply {
                put("mac", rule.macAddress)
                put("name", rule.deviceName)
                put("download", rule.downloadLimitKbps)
                put("upload", rule.uploadLimitKbps)
                put("active", rule.isActive)
                put("createdAt", rule.createdAt)
            })
        }
        throttleRulesFile.writeText(json.toString(2))
    }

    // ==================== إحصائيات الاستهلاك الحالية ====================

    /**
     * تقدير استهلاك الجهاز الحالي للـ bandwidth (محدود للتطبيقات المحلية فقط).
     * للحصول على بيانات دقيقة لأجهزة أخرى، يجب استخدام بروتوكولات الشبكة المتقدمة.
     */
    fun estimateDeviceUsage(mac: String): Pair<Long, Long> {
        // TrafficStats يعطي إحصائيات لكل UID على نفس الجهاز فقط
        // لأجهزة أخرى نحتاج طرق مختلفة (SNMP, NetFlow, etc.)
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        return Pair(rx, tx)
    }

    /** تنظيف القواعد المنتهية الصلاحية */
    fun cleanupExpiredBlocks() {
        val blocks = getBlockedDevices().toMutableList()
        val now = System.currentTimeMillis()
        val removed = blocks.removeAll { it.expiresAt != null && it.expiresAt < now }
        if (removed) saveBlockedDevices(blocks)
    }

    // ==================== سجل الأحداث ====================

    private fun logAction(action: String, mac: String, details: String) {
        val logs = getPrefsString("action_logs").split("\n").filter { it.isNotBlank() }.toMutableList()
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).format(Date())
        logs.add(0, "[$timestamp] $action | MAC=$mac | $details")
        if (logs.size > 100) logs.subList(100, logs.size).clear()
        setPrefsString("action_logs", logs.joinToString("\n"))
    }

    fun getActionLogs(): List<String> {
        return getPrefsString("action_logs").split("\n").filter { it.isNotBlank() }
    }

    fun clearActionLogs() {
        setPrefsString("action_logs", "")
    }

    // ==================== أدوات مساعدة ====================

    private fun getPrefsString(key: String): String = prefs.getString(key, "") ?: ""
    private fun setPrefsString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    /** تنسيق عرض السرعات */
    companion object {
        fun formatSpeed(kbps: Int): String {
            return when {
                kbps >= 1_000_000 -> String.format(Locale.ENGLISH, "%.1f Gbps", kbps / 1_000_000.0)
                kbps >= 1_000 -> String.format(Locale.ENGLISH, "%.1f Mbps", kbps / 1_000.0)
                else -> "$kbps Kbps"
            }
        }

        fun parseSpeed(text: String): Int {
            val num = text.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: return 0
            val unit = text.lowercase().trim()
            return when {
                unit.contains("gbps") -> (num * 1_000_000).toInt()
                unit.contains("mbps") -> (num * 1_000).toInt()
                else -> num.toInt()
            }
        }
    }
}
