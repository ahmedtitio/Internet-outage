package com.example.wifiscanner

import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.wifiscanner.databinding.ActivityDeviceDetailsBinding
import com.example.wifiscanner.util.BandwidthControlManager
import com.example.wifiscanner.util.DataUsageTracker
import com.example.wifiscanner.util.DeviceStatsStore
import com.example.wifiscanner.util.InternetCommander
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
    private lateinit var controlManager: BandwidthControlManager
    private lateinit var commander: InternetCommander

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeviceDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        stats = DeviceStatsStore(this)
        controlManager = BandwidthControlManager(this)
        commander = InternetCommander(this)

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

        setupBandwidthControls(ip, mac, host.ifBlank { ip })

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

    // ==================== التحكم في الإنترنت عن بعد ====================

    private fun setupBandwidthControls(ip: String, mac: String, name: String) {
        renderControlState(mac)

        binding.btnBlockToggle.setOnClickListener {
            if (controlManager.isDeviceBlocked(mac)) confirmUnblock(mac, name) else confirmBlock(ip, mac, name)
        }
        binding.btnThrottle.setOnClickListener { showThrottleDialog(mac, name) }
    }

    private fun renderControlState(mac: String) {
        val blocked = controlManager.isDeviceBlocked(mac)
        val rule = controlManager.getThrottleRuleForDevice(mac)
        when {
            blocked -> {
                binding.textControlState.visibility = View.VISIBLE
                binding.textControlState.setTextColor(getColor(R.color.red_block))
                binding.textControlState.text = getString(R.string.blocked_badge)
                binding.btnBlockToggle.text = getString(R.string.unblock_device)
            }
            rule != null -> {
                binding.textControlState.visibility = View.VISIBLE
                binding.textControlState.setTextColor(getColor(R.color.orange_throttle))
                binding.textControlState.text = getString(
                    R.string.throttled_badge,
                    "${BandwidthControlManager.formatSpeed(rule.downloadLimitKbps)} ↓ / " +
                            BandwidthControlManager.formatSpeed(rule.uploadLimitKbps) + " ↑"
                )
                binding.btnBlockToggle.text = getString(R.string.block_device)
            }
            else -> {
                binding.textControlState.visibility = View.GONE
                binding.btnBlockToggle.text = getString(R.string.block_device)
            }
        }
    }

    private fun gatewayIp(): String =
        WifiUtils.getLocalSubnetPrefix(this)?.let { WifiUtils.gatewayOf(it) } ?: "192.168.1.1"

    private fun confirmBlock(ip: String, mac: String, name: String) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.confirm_block, name))
            .setPositiveButton(R.string.block_device) { _, _ ->
                controlManager.blockDevice(mac, ip, name)
                commander.blockDevice(gatewayIp(), mac) { result ->
                    runOnUiThread { reportRouterResult(result, mac) }
                }
                renderControlState(mac)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmUnblock(mac: String, name: String) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.confirm_unblock, name))
            .setPositiveButton(R.string.save) { _, _ ->
                controlManager.unblockDevice(mac)
                commander.allowDevice(gatewayIp(), mac) { result ->
                    runOnUiThread { reportRouterResult(result, mac) }
                }
                renderControlState(mac)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showThrottleDialog(mac: String, name: String) {
        val existing = controlManager.getThrottleRules()
            .find { it.macAddress.equals(mac, ignoreCase = true) }

        val layout = android.widget.LinearLayout(this).apply {
            setPadding(48, 24, 48, 0)
            orientation = android.widget.LinearLayout.VERTICAL
        }
        val etDownload = EditText(this).apply {
            hint = getString(R.string.download_limit)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(existing?.downloadLimitKbps?.toString().orEmpty())
        }
        val etUpload = EditText(this).apply {
            hint = getString(R.string.upload_limit)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(existing?.uploadLimitKbps?.toString().orEmpty())
        }
        layout.addView(etDownload)
        layout.addView(etUpload)

        AlertDialog.Builder(this)
            .setTitle("$name — ${getString(R.string.throttle_limit)}")
            .setView(layout)
            .setPositiveButton(R.string.save) { _, _ ->
                val down = etDownload.text.toString().toIntOrNull() ?: 0
                val up = etUpload.text.toString().toIntOrNull() ?: 0
                if (down == 0 && up == 0) controlManager.removeThrottleRule(mac)
                else controlManager.addThrottleRule(mac, name, down, up)
                renderControlState(mac)
            }
            .setNeutralButton(
                if (existing != null) getString(R.string.remove_throttle) else ""
            ) { _, _ ->
                controlManager.removeThrottleRule(mac)
                renderControlState(mac)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun reportRouterResult(result: InternetCommander.Result, mac: String) {
        val msg = when (result) {
            is InternetCommander.Result.Success ->
                getString(R.string.router_cmd_sent, result.action)
            else -> getString(R.string.router_cmd_failed, mac)
        }
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show()
    }
}
