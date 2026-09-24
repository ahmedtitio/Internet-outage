package com.example.wifiscanner.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.wifiscanner.databinding.ItemDeviceBinding
import com.example.wifiscanner.util.ConnectedDevice
import com.example.wifiscanner.util.DataUsageTracker

class DeviceAdapter(
    private val devices: List<ConnectedDevice>,
    private val onClick: (ConnectedDevice) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.VH>() {

    inner class VH(val binding: ItemDeviceBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemDeviceBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val device = devices[position]
        with(holder.binding) {
            textName.text = buildString {
                append(device.hostname ?: device.ip)
                if (device.isMe) append("  (هذا الجهاز)")
            }
            textIpMac.text = "IP: ${device.ip}   MAC: ${device.mac}"
            textVendor.text = device.vendor ?: "المصنّع: غير معروف"
            textUsage.text = "الاستهلاك التراكمي: ${DataUsageTracker.formatBytes(device.totalBytes)}"
            iconType.setImageResource(
                when {
                    device.isMe -> com.example.wifiscanner.R.drawable.ic_phone
                    (device.vendor?.contains("TV") == true) ||
                            (device.hostname?.contains("tv", true) == true) ->
                        com.example.wifiscanner.R.drawable.ic_tv
                    else -> com.example.wifiscanner.R.drawable.ic_device
                }
            )
            root.setOnClickListener { onClick(device) }
        }
    }

    override fun getItemCount(): Int = devices.size
}
