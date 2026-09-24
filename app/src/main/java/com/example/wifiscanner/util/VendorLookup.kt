package com.example.wifiscanner.util

/**
 * محاولة تقدير الشركة المصنّعة للجهاز من البادئة OUI لعنوان MAC.
 * قائمة مبسطة محلية؛ للحصول على قاعدة كاملة يمكن ربطها بملف IEEE OUI.
 */
object VendorLookup {

    private val ouiMap = mapOf(
        "F4:5C:89" to "Apple",
        "A4:83:E7" to "Apple",
        "DC:A6:32" to "Raspberry Pi",
        "B8:27:EB" to "Raspberry Pi",
        "E4:5F:01" to "Raspberry Pi",
        "00:1A:11" to "Google",
        "3C:5A:B4" to "Google",
        "F0:99:B9" to "Sony",
        "AC:C9:35" to "Nintendo",
        "D8:31:CF" to "Samsung",
        "5C:0A:5B" to "Samsung",
        "94:DB:49" to "Samsung",
        "00:15:6D" to "Samsung",
        "20:DF:F9" to "Xiaomi",
        "64:B4:73" to "Xiaomi",
        "F8:A4:5F" to "Xiaomi",
        "78:11:DC" to "Espressif (IoT)",
        "24:0A:64" to "Espressif (IoT)",
        "CC:50:E3" to "Espressif (IoT)",
        "1C:BC:99" to "Amazon (Echo)",
        "40:B4:C8" to "Amazon",
        "68:37:E9" to "Netgear",
        "C0:FF:D4" to "Netgear",
        "50:C7:89" to "TP-Link",
        "14:CC:20" to "TP-Link",
        "AC:84:C6" to "Huawei",
        "78:45:C4" to "CyberTan",
        "00:11:32" to "Synology (NAS)",
        "00:11:FC" to "Huffmann",
        "30:B5:C2" to "Xiaomi Mi Router",
        "02:42:AC" to "Docker Container",
        "00:16:3E" to "Xen/KVM VM"
    )

    fun vendorOf(mac: String): String? {
        if (mac.length < 8) return null
        val oui = mac.uppercase().substring(0, 8)
        return ouiMap[oui]
    }
}
