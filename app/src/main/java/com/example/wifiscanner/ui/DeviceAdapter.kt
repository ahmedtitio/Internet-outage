package com.example.wifiscanner.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.wifiscanner.R
import com.example.wifiscanner.databinding.ItemDeviceBinding
import com.example.wifiscanner.util.ConnectedDevice
import com.example.wifiscanner.util.DataUsageTracker

class DeviceAdapter(
    private val devices: List<ConnectedDevice>,
    private val onClick: (ConnectedDevice) -> Unit,
    private val isBlocked: (String) -> Boolean = { false },
    private val throttleLabel: (String) -> String? = { null },
    private val onBlockToggle: (ConnectedDevice) -> Unit = {}
) : RecyclerView.Adapter<DeviceAdapter.VH>() {

    inner class VH(val binding: ItemDeviceBinding) : RecyclerView.ViewHolder(binding.root) {
        val context = binding.root.context
    }

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

            // حالة التحكم (محظور / محدود السرعة)
            val blocked = !device.isMe && isBlocked(device.mac)
            val throttle = if (!device.isMe) throttleLabel(device.mac) else null
            when {
                blocked -> {
                    textControlStatus.visibility = ViewGroup.VISIBLE
                    textControlStatus.setTextColor(
                        context.getColor(R.color.red_block))
                    textControlStatus.text = context.getString(R.string.blocked_badge)
                    btnBlockToggle.text = context.getString(R.string.unblock_device)
                }
                throttle != null -> {
                    textControlStatus.visibility = ViewGroup.VISIBLE
                    textControlStatus.setTextColor(
                        context.getColor(R.color.orange_throttle))
                    textControlStatus.text =
                        context.getString(R.string.throttled_badge, throttle)
                    btnBlockToggle.text = context.getString(R.string.block_device)
                }
                else -> {
                    textControlStatus.visibility = ViewGroup.GONE
                    btnBlockToggle.text = context.getString(R.string.block_device)
                }
            }

            if (device.isMe) {
                btnBlockToggle.visibility = ViewGroup.GONE
            } else {
                btnBlockToggle.visibility = ViewGroup.VISIBLE
                btnBlockToggle.setOnClickListener { onBlockToggle(device) }
            }

            iconType.setImageResource(
                when {
                    device.isMe -> R.drawable.ic_phone
                    (device.vendor?.contains("TV") == true) ||
                            (device.hostname?.contains("tv", true) == true) ->
                        R.drawable.ic_tv
                    else -> R.drawable.ic_device
                }
            )
            root.setOnClickListener { onClick(device) }
        }
    }

    override fun getItemCount(): Int = devices.size
}
