package com.example.wifiscanner

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.wifiscanner.ads.AdManager
import com.example.wifiscanner.databinding.ActivitySettingsBinding
import com.example.wifiscanner.util.BackgroundPermissions
import com.example.wifiscanner.util.DeviceStatsStore
import com.example.wifiscanner.util.LocaleHelper

/**
 * شاشة الإعدادات المنظمة في أقسام:
 * 1) المظهر واللغة — اختيار لغة التطبيق (الافتراضي = لغة الجهاز).
 * 2) أذونات التشغيل في الخلفية — تجاهل البطارية، Auto-start، الإشعارات.
 * 3) البيانات — إعادة ضبط الإحصائيات.
 * 4) القانوني والمطور — سياسة الخصوصية، شروط الاستخدام، عن التطبيق، اتصل بنا.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        setupLanguageRow()
        setupPermissionRows()

        binding.btnResetAll.setOnClickListener {
            DeviceStatsStore(this).resetAll()
            Toast.makeText(this, R.string.reset_stats, Toast.LENGTH_SHORT).show()
        }

        // الأقسام القانونية
        binding.rowPrivacy.setOnClickListener {
            openLegal(getString(R.string.privacy_policy_title), getString(R.string.privacy_policy_full))
        }
        binding.rowTerms.setOnClickListener {
            openLegal(getString(R.string.terms_title), getString(R.string.terms_full))
        }
        binding.rowAbout.setOnClickListener {
            openLegal(getString(R.string.about_title), getString(R.string.about_full))
        }
        binding.rowContact.setOnClickListener {
            startActivity(Intent(this, ContactActivity::class.java))
        }

        binding.textDisclaimer.text = getString(R.string.disclaimer)

        // المساحات الإعلانية (تُتخطى تلقائياً حتى تفعيل AdMob)
        AdManager.showBanner(this, binding.adContainerSettings)
        AdManager.showBanner(this, binding.adContainerSettingsInline)
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStates()
    }

    // ===== اللغة =====
    private fun setupLanguageRow() {
        updateLanguageLabel()
        binding.rowLanguage.setOnClickListener { showLanguageDialog() }
    }

    private fun updateLanguageLabel() {
        val saved = LocaleHelper.getSavedLanguage(this)
        val name = LocaleHelper.SUPPORTED_LANGUAGES[saved] ?: saved
        binding.textLanguageCurrent.text =
            if (saved == LocaleHelper.LANG_SYSTEM) getString(R.string.language_summary)
            else name
    }

    private fun showLanguageDialog() {
        val entries = LocaleHelper.SUPPORTED_LANGUAGES.values.toTypedArray()
        val codes = LocaleHelper.SUPPORTED_LANGUAGES.keys.toTypedArray()
        val current = codes.indexOf(LocaleHelper.getSavedLanguage(this)).coerceAtLeast(0)

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.language_title)
            .setSingleChoiceItems(entries, current) { dialog, which ->
                LocaleHelper.setLanguage(this, codes[which])
                dialog.dismiss()
                recreateApp()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** إعادة تشغيل التطبيق لتطبيق اللغة الجديدة على كل الشاشات. */
    private fun recreateApp() {
        Toast.makeText(this, R.string.language_changed, Toast.LENGTH_SHORT).show()
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        finishAffinity()
        if (intent != null) startActivity(intent)
        finish()
    }

    // ===== أذونات الخلفية =====
    private fun setupPermissionRows() {
        binding.rowBattery.setOnClickListener {
            BackgroundPermissions.requestIgnoreBatteryOptimizations(this)
        }
        binding.rowAutostart.setOnClickListener {
            // فتح إعدادات التطبيق ليفعّل المستخدم Auto-start حسب الشركة المصنعة
            BackgroundPermissions.openAppSettings(this)
        }
        binding.rowNotifications.setOnClickListener {
            if (!BackgroundPermissions.hasNotificationPermission(this)) {
                BackgroundPermissions.requestNotificationPermission(this)
            }
        }
    }

    private fun refreshPermissionStates() {
        binding.textBatteryStatus.setText(
            if (BackgroundPermissions.isIgnoringBatteryOptimizations(this))
                R.string.battery_summary_on else R.string.battery_summary_off
        )
        binding.textNotificationStatus.setText(
            if (BackgroundPermissions.hasNotificationPermission(this))
                R.string.notifications_summary_on else R.string.notifications_summary_off
        )
        updateLanguageLabel()
    }

    private fun openLegal(title: String, content: String) {
        val intent = Intent(this, LegalActivity::class.java).apply {
            putExtra(LegalActivity.EXTRA_TITLE, title)
            putExtra(LegalActivity.EXTRA_CONTENT, content)
        }
        startActivity(intent)
    }
}
