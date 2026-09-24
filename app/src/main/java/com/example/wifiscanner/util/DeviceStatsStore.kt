package com.example.wifiscanner.util

import android.content.Context
import androidx.core.content.edit

/**
 * تخزين محلي لإحصائيات استهلاك البيانات لكل جهاز (حسب عنوان MAC).
 */
class DeviceStatsStore(context: Context) {

    private val prefs = context.getSharedPreferences("device_stats", Context.MODE_PRIVATE)

    fun getTotalBytes(mac: String): Long = prefs.getLong(key(mac, "total"), 0L)
    fun setTotalBytes(mac: String, value: Long) = prefs.edit { putLong(key(mac, "total"), value) }

    fun getSnapshotBytes(mac: String): Long = prefs.getLong(key(mac, "snap"), 0L)
    fun setSnapshotBytes(mac: String, value: Long) = prefs.edit { putLong(key(mac, "snap"), value) }

    fun getFirstSeen(mac: String): Long = prefs.getLong(key(mac, "first"), 0L)
    fun setFirstSeen(mac: String, value: Long) {
        if (getFirstSeen(mac) == 0L) prefs.edit { putLong(key(mac, "first"), value) }
    }

    fun addDelta(mac: String, delta: Long) {
        setTotalBytes(mac, getTotalBytes(mac) + delta.coerceAtLeast(0L))
    }

    fun resetAll() = prefs.edit { clear() }

    fun resetDevice(mac: String) {
        prefs.edit {
            remove(key(mac, "total"))
            remove(key(mac, "snap"))
            remove(key(mac, "first"))
        }
    }

    private fun key(mac: String, suffix: String) = "${mac.uppercase()}_$suffix"
}
