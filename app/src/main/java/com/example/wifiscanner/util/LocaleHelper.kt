package com.example.wifiscanner.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * إدارة لغة التطبيق:
 * - اللغة الافتراضية = لغة الجهاز الأساسية (system).
 * - يمكن للمستخدم اختيار أي لغة مدعومة من شاشة الإعدادات.
 * - الاختيار يُحفظ في SharedPreferences ويُطبَّق على كل الأنشطة عبر attachBaseContext.
 */
object LocaleHelper {

    private const val PREF_LANG = "app_language"
    const val LANG_SYSTEM = "system"

    /** اللغات المدعومة: رمز اللغة -> اسمها المحلي */
    val SUPPORTED_LANGUAGES: LinkedHashMap<String, String> = linkedMapOf(
        LANG_SYSTEM to "لغة الجهاز (تلقائي)",
        "ar" to "العربية",
        "en" to "English",
        "fr" to "Français",
        "es" to "Español",
        "de" to "Deutsch",
        "tr" to "Türkçe",
        "ur" to "اردو",
        "fa" to "فارسی",
        "hi" to "हिन्दी",
        "id" to "Bahasa Indonesia",
        "ru" to "Русский",
        "zh" to "中文",
        "pt" to "Português",
        "it" to "Italiano",
        "nl" to "Nederlands",
        "sv" to "Svenska",
        "pl" to "Polski",
        "ja" to "日本語",
        "ko" to "한국어"
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    fun getSavedLanguage(context: Context): String =
        prefs(context).getString(PREF_LANG, LANG_SYSTEM) ?: LANG_SYSTEM

    fun setLanguage(context: Context, langCode: String) {
        prefs(context).edit().putString(PREF_LANG, langCode).apply()
    }

    /** إرجاع Locale المناسب: لغة الجهاز إذا كان الاختيار system. */
    fun resolveLocale(langCode: String): Locale {
        if (langCode == LANG_SYSTEM) {
            return ResourcesLocaleCompat.getSystemLocale()
        }
        return Locale(langCode)
    }

    /** تغليف سياق النشاط/الخدمة بلغة التطبيق المختارة. */
    fun wrap(context: Context): Context {
        val locale = resolveLocale(getSavedLanguage(context))
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)

        // ضبط اتجاه RTL/LTR تلقائياً حسب اللغة
        config.setLayoutDirection(locale)

        return context.createConfigurationContext(config)
    }
}

/** توافق جلب لغة النظام بين إصدارات أندرويد. */
private object ResourcesLocaleCompat {
    @Suppress("DEPRECATION")
    fun getSystemLocale(): Locale =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            LocaleListCompat.firstOrEnglish()
        } else {
            Locale.getDefault()
        }
}

@androidx.annotation.RequiresApi(api = 24)
private object LocaleListCompat {
    fun firstOrEnglish(): Locale {
        val list = android.content.res.Resources.getSystem().configuration.locales
        return if (list.size() > 0) list[0] else Locale.ENGLISH
    }
}
