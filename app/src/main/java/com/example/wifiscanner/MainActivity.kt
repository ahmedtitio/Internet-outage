package com.example.wifiscanner

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.wifiscanner.databinding.ActivityMainBinding
import com.example.wifiscanner.ui.DeviceAdapter
import com.example.wifiscanner.util.ConnectedDevice
import com.example.wifiscanner.util.DataUsageTracker
import com.example.wifiscanner.util.DeviceStatsStore
import com.example.wifiscanner.util.NetworkScanner
import com.example.wifiscanner.util.PermissionHelper
import com.example.wifiscanner.util.VendorLookup
import com.example.wifiscanner.util.WifiInfoProvider
import com.example.wifiscanner.util.WifiUtils

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: DeviceAdapter
    private lateinit var statsStore: DeviceStatsStore
    private val devices = mutableListOf<ConnectedDevice>()
    private var scanner: NetworkScanner? = null
    private var scanning = false
    private var sortMode = 0 // 0 = افتراضي (حسب الاكتشاف)، 1 = الاسم، 2 = IP، 3 = الاستهلاك

    /** التبديل بين أوضاع ترتيب قائمة الأجهزة مع تحديث العرض. */
    private fun cycleSortMode() {
        sortMode = (sortMode + 1) % 4
        when (sortMode) {
            1 -> devices.sortBy { (it.hostname ?: it.ip).lowercase() }
            2 -> devices.sortBy { it.ip.split(".").joinToString(".") { p -> (p.toIntOrNull() ?: 0).toString().padStart(3, '0') } }
            3 -> devices.sortByDescending { it.totalBytes }
            else -> { /* الترتيب الافتراضي: حسب ظهور الجهاز أثناء الفحص */ }
        }
        adapter.notifyDataSetChanged()
        binding.btnSort.text = when (sortMode) {
            1 -> getString(R.string.sort_name)
            2 -> getString(R.string.sort_ip)
            3 -> getString(R.string.sort_usage)
            else -> getString(R.string.sort)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        statsStore = DeviceStatsStore(this)
        adapter = DeviceAdapter(devices) { device -> openDetails(device) }
        binding.recyclerDevices.layoutManager = LinearLayoutManager(this)
        binding.recyclerDevices.adapter = adapter

        binding.swipeRefresh.setColorSchemeResources(R.color.teal_200)
        binding.swipeRefresh.setOnRefreshListener { startScan() }

        binding.btnScan.setOnClickListener { startScan() }
        binding.btnSort.setOnClickListener { cycleSortMode() }

        binding.btnSettings.setOnClickListener {
            startActivity(android.content.Intent(this, SettingsActivity::class.java))
        }

        if (!PermissionHelper.hasScanPermissions(this)) {
            PermissionHelper.requestScanPermissions(this)
        } else {
            startScan()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PermissionHelper.REQ_CODE &&
            grantResults.isNotEmpty() && grantResults.all { it >= 0 }
        ) {
            startScan()
        }
    }

    private fun startScan() {
        if (scanning) return
        val prefix = WifiUtils.getLocalSubnetPrefix(this)
        if (prefix == null) {
            binding.textStatus.text = getString(R.string.status_not_connected)
            binding.swipeRefresh.isRefreshing = false
            return
        }

        scanning = true
        statsStore.resetAll() // نبدأ جولة قياس جديدة لكل الأجهزة
        devices.clear()
        adapter.notifyDataSetChanged()
        updateNetworkHeader(prefix)
        binding.textStatus.text = getString(R.string.status_scanning)
        binding.swipeRefresh.isRefreshing = true
        binding.progressScan.visibility = android.view.View.VISIBLE

        val s = NetworkScanner(prefix)
        scanner = s
        s.scan(object : NetworkScanner.ScanCallback {
            override fun onDeviceFound(ip: String, mac: String) {
                runOnUiThread {
                    val hostname = s.resolveHostname(ip)
                    val dev = ConnectedDevice(
                        ip = ip,
                        mac = mac,
                        hostname = hostname,
                        vendor = VendorLookup.vendorOf(mac),
                        isMe = ip == WifiInfoProvider.deviceIp(WifiInfoProvider.currentWifiInfo(this@MainActivity)),
                        totalBytes = 0L
                    )
                    statsStore.setFirstSeen(mac, System.currentTimeMillis())
                    statsStore.setSnapshotBytes(mac, DataUsageTracker.deviceTotalRx() + DataUsageTracker.deviceTotalTx())
                    devices.add(dev)
                    adapter.notifyItemInserted(devices.size - 1)
                    binding.textStatus.text =
                        getString(R.string.status_found_n, devices.size)
                }
            }

            override fun onFinished(map: Map<String, String>) {
                runOnUiThread {
                    // حساب الفروق منذ بداية الجولة كتقدير تراكمي للنشاط على الشبكة
                    val now = DataUsageTracker.deviceTotalRx() + DataUsageTracker.deviceTotalTx()
                    map.keys.forEach { ip ->
                        val dev = devices.firstOrNull { it.ip == ip } ?: return@forEach
                        val snap = statsStore.getSnapshotBytes(dev.mac)
                        val delta = (now - snap).coerceAtLeast(0L) / map.size.coerceAtLeast(1)
                        statsStore.addDelta(dev.mac, delta)
                        dev.totalBytes = statsStore.getTotalBytes(dev.mac)
                    }
                    adapter.notifyDataSetChanged()
                    scanning = false
                    binding.swipeRefresh.isRefreshing = false
                    binding.progressScan.visibility = android.view.View.GONE
                    binding.textStatus.text =
                        getString(R.string.status_done, devices.size)
                }
            }
        })
    }

    private fun updateNetworkHeader(prefix: String) {
        val info = WifiInfoProvider.currentWifiInfo(this)
        binding.textSsid.text = "${getString(R.string.ssid)}: ${WifiInfoProvider.ssid(info)}"
        binding.textGateway.text = "${getString(R.string.gateway)}: ${WifiUtils.gatewayOf(prefix)}.1"
        binding.textMyIp.text = "${getString(R.string.my_ip)}: ${WifiInfoProvider.deviceIp(info)}"
        binding.textRx.text = "RX: ${DataUsageTracker.formatBytes(DataUsageTracker.deviceTotalRx())}"
        binding.textTx.text = "TX: ${DataUsageTracker.formatBytes(DataUsageTracker.deviceTotalTx())}"
    }

    private fun openDetails(device: ConnectedDevice) {
        val intent = android.content.Intent(this, DeviceDetailsActivity::class.java).apply {
            putExtra(DeviceDetailsActivity.EXTRA_IP, device.ip)
            putExtra(DeviceDetailsActivity.EXTRA_MAC, device.mac)
            putExtra(DeviceDetailsActivity.EXTRA_HOST, device.hostname ?: "")
            putExtra(DeviceDetailsActivity.EXTRA_VENDOR, device.vendor ?: "")
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        scanner = null
    }
}
