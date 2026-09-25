package com.example.wifiscanner

import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.wifiscanner.ads.AdManager
import com.example.wifiscanner.databinding.ActivityMainBinding
import com.example.wifiscanner.ui.DeviceAdapter
import com.example.wifiscanner.util.BandwidthControlManager
import com.example.wifiscanner.util.ConnectedDevice
import com.example.wifiscanner.util.DataUsageTracker
import com.example.wifiscanner.util.DeviceStatsStore
import com.example.wifiscanner.util.InternetCommander
import com.example.wifiscanner.util.MulticastHelper
import com.example.wifiscanner.util.NetworkNeighbors
import com.example.wifiscanner.util.NetworkScanner
import com.example.wifiscanner.util.PermissionHelper
import com.example.wifiscanner.util.VendorLookup
import com.example.wifiscanner.util.WifiInfoProvider
import com.example.wifiscanner.util.WifiUtils

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: DeviceAdapter
    private lateinit var statsStore: DeviceStatsStore
    private lateinit var controlManager: BandwidthControlManager
    private lateinit var commander: InternetCommander
    private val devices = mutableListOf<ConnectedDevice>()
    private var scanner: NetworkScanner? = null
    private lateinit var multicast: MulticastHelper
    private var scanning = false
    private var subnetPrefix: String? = null
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
        // نص مختصر داخل الزر حتى لا يتداخل مع الأزرار المجاورة
        binding.btnSort.text = when (sortMode) {
            1 -> getString(R.string.sort_btn_name)
            2 -> getString(R.string.sort_btn_ip)
            3 -> getString(R.string.sort_btn_usage)
            else -> getString(R.string.sort)
        }
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.example.wifiscanner.util.LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        statsStore = DeviceStatsStore(this)
        controlManager = BandwidthControlManager(this)
        commander = InternetCommander(this)
        multicast = MulticastHelper(this)
        controlManager.cleanupExpiredBlocks()
        adapter = DeviceAdapter(
            devices,
            onClick = { device -> openDeviceMenu(device) },
            isBlocked = { mac -> controlManager.isDeviceBlocked(mac) },
            throttleLabel = { mac ->
                controlManager.getThrottleRuleForDevice(mac)?.let {
                    BandwidthControlManager.formatSpeed(it.downloadLimitKbps)
                }
            },
            onBlockToggle = { device -> toggleBlock(device) }
        )
        binding.recyclerDevices.layoutManager = LinearLayoutManager(this)
        binding.recyclerDevices.adapter = adapter

        binding.swipeRefresh.setColorSchemeResources(R.color.teal_200)
        binding.swipeRefresh.setOnRefreshListener { startScan() }

        binding.btnScan.setOnClickListener { startScan() }
        binding.btnSort.setOnClickListener { cycleSortMode() }

        // زر الإضافة اليدوية — للحالات التي يحجب فيها الراوتر ARP
        binding.btnAddManual.setOnClickListener { showAddManualDialog() }

        // تجهيز المساحة الإعلانية (AdManager يتخطيها تلقائياً حتى تفعيل AdMob)
        AdManager.showBanner(this, binding.adContainerMain)

        binding.btnSettings.setOnClickListener {
            startActivity(android.content.Intent(this, SettingsActivity::class.java))
        }

        if (!PermissionHelper.hasScanPermissions(this)) {
            PermissionHelper.requestScanPermissions(this)
        } else {
            startScan()
        }
    }

    /** تعطيل زر الفحص أثناء المسح حتى لا تتداخل النصوص والحالة. */
    private fun setScanningUi(scanning: Boolean) {
        binding.btnScan.isEnabled = !scanning
        binding.btnScan.text = getString(if (scanning) R.string.scanning else R.string.scan)
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

        // تشخيص واضح للمستخدم بدل قائمة فارغة بلا سبب
        if (!PermissionHelper.hasScanPermissions(this)) {
            binding.textStatus.text = getString(R.string.status_need_permission)
            PermissionHelper.requestScanPermissions(this)
            return
        }
        if (!PermissionHelper.isOnWifi(this)) {
            binding.textStatus.text = getString(R.string.status_not_connected)
            return
        }
        val prefix = WifiUtils.getLocalSubnetPrefix(this)
        if (prefix == null) {
            binding.textStatus.text = getString(R.string.status_no_subnet)
            return
        }

        scanning = true
        subnetPrefix = prefix
        // قفل البث المتعدد — ضروري لاستقبال حزم mDNS/SSDP على معظم الأجهزة
        MulticastHelper.acquireMulticastLock(this)
        multicast.start()
        statsStore.resetAll() // نبدأ جولة قياس جديدة لكل الأجهزة
        devices.clear()
        adapter.notifyDataSetChanged()
        updateNetworkHeader(prefix)
        binding.textStatus.text = getString(R.string.status_scanning)
        binding.swipeRefresh.isRefreshing = true
        binding.progressScan.visibility = View.VISIBLE
        setScanningUi(true)

        val myIp = WifiInfoProvider.deviceIp(WifiInfoProvider.currentWifiInfo(this))
        val s = NetworkScanner(prefix)
        scanner = s

        /** إضافة جهاز إلى القائمة مع منع التكرار حسب IP أو MAC */
        fun addDevice(ip: String, mac: String, nameOverride: String? = null) {
            if (devices.any { it.ip == ip || (mac != "00:00:00:00:00:00" && it.mac == mac) }) return
            val hostname = nameOverride ?: s.resolveHostname(ip)
            val dev = ConnectedDevice(
                ip = ip,
                mac = mac,
                hostname = hostname,
                vendor = VendorLookup.vendorOf(mac),
                isMe = ip == myIp,
                totalBytes = 0L
            )
            statsStore.setFirstSeen(mac, System.currentTimeMillis())
            statsStore.setSnapshotBytes(mac, DataUsageTracker.deviceTotalRx() + DataUsageTracker.deviceTotalTx())
            devices.add(dev)
            adapter.notifyItemInserted(devices.size - 1)
            binding.textStatus.text = getString(R.string.status_found_n, devices.size)
        }

        s.scan(object : NetworkScanner.ScanCallback {
            override fun onDeviceFound(ip: String, mac: String) {
                runOnUiThread { addDevice(ip, mac) }
            }

            override fun onFinished(map: Map<String, String>) {
                runOnUiThread {
                    // 5) جيران IPv6 (link-local) — تلتقط أجهزة تظهر فقط عبر IPv6
                    for ((ip, mac) in NetworkNeighbors.ipv6Neighbors()) {
                        if (ip.startsWith("$subnetPrefix.")) addDevice(ip, mac)
                    }
                    // 6) الأجهزة المضافة يدوياً من شاشة الإعدادات/القائمة
                    for ((ip, mac) in manualDevices()) {
                        addDevice(ip, mac, nameOverride = manualNameOf(ip))
                    }
                    // أوقف الاستماع بعد اكتمال الفحص وحرّر قفل البث المتعدد
                    multicast.stop()
                    MulticastHelper.releaseMulticastLock()

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
                    binding.progressScan.visibility = View.GONE
                    setScanningUi(false)
                    binding.textStatus.text = when {
                        devices.isNotEmpty() -> getString(R.string.status_done, devices.size)
                        else -> getString(R.string.status_empty_diagnosis,
                            WifiUtils.gatewayOf(prefix))
                    }
                }
            }
        })
    }

    // ==================== الأجهزة المضافة يدوياً ====================

    private fun manualPrefs() = getSharedPreferences("manual_devices", MODE_PRIVATE)

    private fun manualDevices(): Map<String, String> {
        // ip -> mac
        val all = manualPrefs().all
        return all.entries
            .filter { (k, v) -> !k.startsWith("name_") && v is String && v.contains(":") }
            .associate { (k, v) -> k to (v as String) }
    }

    private fun manualNameOf(ip: String): String? =
        manualPrefs().getString("name_$ip", null)

    /** حوار إضافة جهاز يدوياً عندما تحجب الراوتر معلومات ARP */
    private fun showAddManualDialog() {
        val layout = android.widget.LinearLayout(this).apply {
            setPadding(48, 24, 48, 0)
            orientation = android.widget.LinearLayout.VERTICAL
        }
        val etName = EditText(this).apply { hint = getString(R.string.manual_name_hint) }
        val etIp = EditText(this).apply {
            hint = getString(R.string.manual_ip_hint)
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setText(subnetPrefix?.let { "$it." } ?: "")
        }
        val etMac = EditText(this).apply {
            hint = getString(R.string.manual_mac_hint)
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        layout.addView(etName); layout.addView(etIp); layout.addView(etMac)
        AlertDialog.Builder(this)
            .setTitle(R.string.add_manual_device)
            .setView(layout)
            .setPositiveButton(R.string.save) { _, _ ->
                val ip = etIp.text.toString().trim()
                val mac = etMac.text.toString().trim().uppercase()
                if (ip.matches(Regex("^\\d+\\.\\d+\\.\\d+\\.\\d+$"))) {
                    val validMac = if (mac.matches(Regex("^([0-9A-F]{2}:){5}[0-9A-F]{2}$"))) mac
                                   else "00:00:00:00:00:00"
                    manualPrefs().edit()
                        .putString(ip, validMac)
                        .putString("name_$ip", etName.text.toString().trim().ifBlank { ip })
                        .apply()
                    val name = etName.text.toString().trim().ifBlank { null }
                    if (devices.none { it.ip == ip }) {
                        devices.add(ConnectedDevice(
                            ip = ip, mac = validMac,
                            hostname = name ?: manualNameOf(ip),
                            vendor = VendorLookup.vendorOf(validMac),
                            isMe = false, totalBytes = 0L))
                        adapter.notifyItemInserted(devices.size - 1)
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
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

    // ==================== التحكم في الإنترنت للأجهزة المتطفلة ====================

    /** قائمة الخيارات عند الضغط على جهاز: تفاصيل / قطع الاتصال / تحديد السرعة. */
    private fun openDeviceMenu(device: ConnectedDevice) {
        if (device.isMe) { openDetails(device); return }
        val blocked = controlManager.isDeviceBlocked(device.mac)
        val options = arrayOf(
            getString(R.string.device_details),
            if (blocked) getString(R.string.unblock_device) else getString(R.string.block_device),
            getString(R.string.throttle_limit),
            getString(R.string.action_log)
        )
        AlertDialog.Builder(this)
            .setTitle(device.hostname ?: device.ip)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openDetails(device)
                    1 -> toggleBlock(device)
                    2 -> showThrottleDialog(device)
                    3 -> showActionLog()
                }
            }
            .show()
    }

    /** حظر/رفع حظر الجهاز: يحفظ القاعدة محلياً ويرسلها للراوتر إذا كان مدعوماً. */
    private fun toggleBlock(device: ConnectedDevice) {
        val name = device.hostname?.takeIf { it.isNotBlank() } ?: device.ip
        val gateway = subnetPrefix?.let { WifiUtils.gatewayOf(it) }
            ?: WifiUtils.getLocalSubnetPrefix(this)?.let { WifiUtils.gatewayOf(it) }
            ?: "192.168.1.1"

        if (controlManager.isDeviceBlocked(device.mac)) {
            AlertDialog.Builder(this)
                .setMessage(getString(R.string.confirm_unblock, name))
                .setPositiveButton(R.string.save) { _, _ ->
                    controlManager.unblockDevice(device.mac)
                    commander.allowDevice(gateway, device.mac) { result ->
                        runOnUiThread { reportRouterResult(result, device.mac) }
                    }
                    adapter.notifyDataSetChanged()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        } else {
            AlertDialog.Builder(this)
                .setMessage(getString(R.string.confirm_block, name))
                .setPositiveButton(R.string.block_device) { _, _ ->
                    controlManager.blockDevice(device.mac, device.ip, name)
                    commander.blockDevice(gateway, device.mac) { result ->
                        runOnUiThread { reportRouterResult(result, device.mac) }
                    }
                    adapter.notifyDataSetChanged()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    /** حوار تحديد سرعة التنزيل/الرفع لجهاز معين. */
    private fun showThrottleDialog(device: ConnectedDevice) {
        val name = device.hostname?.takeIf { it.isNotBlank() } ?: device.ip
        val existing = controlManager.getThrottleRules()
            .find { it.macAddress.equals(device.mac, ignoreCase = true) }

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
                if (down == 0 && up == 0) {
                    controlManager.removeThrottleRule(device.mac)
                } else {
                    controlManager.addThrottleRule(device.mac, name, down, up)
                }
                adapter.notifyDataSetChanged()
            }
            .setNeutralButton(
                if (existing != null) getString(R.string.remove_throttle) else ""
            ) { _, _ ->
                controlManager.removeThrottleRule(device.mac)
                adapter.notifyDataSetChanged()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** عرض سجل الإجراءات المنفذة. */
    private fun showActionLog() {
        val logs = controlManager.getActionLogs()
        AlertDialog.Builder(this)
            .setTitle(R.string.action_log)
            .setMessage(if (logs.isEmpty()) getString(R.string.no_actions)
                        else logs.joinToString("\n"))
            .setNeutralButton(R.string.clear_log) { _, _ -> controlManager.clearActionLogs() }
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun reportRouterResult(result: InternetCommander.Result, mac: String) {
        val msg = when (result) {
            is InternetCommander.Result.Success ->
                getString(R.string.router_cmd_sent, result.action)
            is InternetCommander.Result.Unsupported ->
                getString(R.string.router_cmd_failed, mac)
            is InternetCommander.Result.Failed ->
                getString(R.string.router_cmd_failed, mac)
        }
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        scanner = null
        if (::multicast.isInitialized) {
            multicast.stop()
            MulticastHelper.releaseMulticastLock()
        }
    }
}
