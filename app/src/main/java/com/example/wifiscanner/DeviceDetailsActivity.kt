package com.example.wifiscanner

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.wifiscanner.databinding.ActivityDeviceDetailsBinding
import com.example.wifiscanner.util.DataUsageTracker
import com.example.wifiscanner.util.DeviceStatsStore
import com.example.wifiscanner.util.NetworkScanner
import com.example.wifiscanner.util.WifiUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/** شاشة تفاصيل جهاز: المنافذ المفتوحة + إحصائيات الاستهلاك المخزنة. */
class DeviceDetailsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_IP = "extra_ip"
        const val EXTRA_MAC = "extra_mac"
        const val EXTRA_HOST = "extra_host"
        const val EXTRA_VENDOR = "extra_vendor"
    }

    private lateinit var binding: ActivityDeviceDetailsBinding
    private lateinit var stats: DeviceStatsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeviceDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        stats = DeviceStatsStore(this)

        val ip = intent.getStringExtra(EXTRA_IP) ?: run { finish(); return }
        val mac = intent.getStringExtra(EXTRA_MAC) ?: "--"
        val host = intent.getStringExtra(EXTRA_HOST).orEmpty()
        val vendor = intent.getStringExtra(EXTRA_VENDOR).orEmpty()

        binding.toolbar.title = host.ifBlank { ip }
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.textIp.text = "عنوان IP: $ip"
        binding.textMac.text = "MAC: $mac"
        binding.textVendor.text = if (vendor.isBlank()) "المصنّع: غير معروف" else "المصنّع: $vendor"
        binding.textHostname.text = if (host.isBlank()) "اسم المضيف: غير متاح" else "اسم المضيف: $host"

        renderUsage(mac)

        binding.btnReset.setOnClickListener {
            stats.resetDevice(mac)
            renderUsage(mac)
        }

        // فحص المنافذ في الخلفية
        binding.progressPorts.visibility = View.VISIBLE
        thread(isDaemon = true) {
            val scanner = NetworkScanner(WifiUtils.prefixOfPublic(ip))
            val ports = try { scanner.probeOpenPorts(ip) } catch (_: Exception) { emptyList() }
            runOnUiThread {
                binding.progressPorts.visibility = View.GONE
                binding.textPorts.text = if (ports.isEmpty())
                    "لم تُكتشف منافذ مفتوحة (قد يكون الجهاز يمنع الفحص)"
                else
                    "منافذ مفتوحة: " + ports.joinToString(", ") { "${it} (${WifiUtils.serviceName(it)})" }
            }
        }
    }

    private fun renderUsage(mac: String) {
        val total = stats.getTotalBytes(mac)
        val first = stats.getFirstSeen(mac)
        binding.textTotalUsage.text = "إجمالي الاستهلاك المسجّل: ${DataUsageTracker.formatBytes(total)}"
        binding.textFirstSeen.text = if (first > 0)
            "أول ظهور: " + SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(first))
        else
            "أول ظهور: لم يُسجّل بعد"
    }
}
