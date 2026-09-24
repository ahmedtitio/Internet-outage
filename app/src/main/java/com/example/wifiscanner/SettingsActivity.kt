package com.example.wifiscanner

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.wifiscanner.databinding.ActivitySettingsBinding
import com.example.wifiscanner.util.DeviceStatsStore

/** شاشة الإعدادات: إعادة ضبط إحصائيات الاستهلاك + معلومات عن التطبيق. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnResetAll.setOnClickListener {
            DeviceStatsStore(this).resetAll()
            Toast.makeText(this, "تمت إعادة ضبط جميع الإحصائيات", Toast.LENGTH_SHORT).show()
        }

        binding.textDisclaimer.text = getString(R.string.disclaimer)
    }
}
