package com.example.wifiscanner.ads

import android.content.Context
import android.util.Log
import android.view.View
import android.widget.FrameLayout

/**
 * مدير المساحات الإعلانية (AdMob) — طبقة تجريد آمنة:
 *
 * حالياً يعرّف أماكن الإعلانات (Banner أسفل الشاشة الرئيسية، Banner في الإعدادات،
 * مساحة في شاشة البداية، وInterstitial بين الشاشات) دون تحميل مكتبة AdMob،
 * بحيث يعمل التطبيق الآن بدون إعلانات وبدون أي اعتماد خارجي.
 *
 * عند تفعيل الإعلانات لاحقاً:
 *  1. أضف في app/build.gradle:
 *       implementation 'com.google.android.gms:play-services-ads:23.0.0'
 *  2. ضع مفتاح التطبيق الحقيقي في Manifest عبر:
 *       <meta-data android:name="com.google.android.gms.ads.APPLICATION_ID" .../>
 *  3. استبدل دوال show* أدناه بكود AdView / InterstitialAd الفعلي.
 */
object AdManager {

    private const val TAG = "AdManager"

    // معرّفات اختبار رسمية من Google (آمنة للاستخدام قبل النشر)
    const val TEST_BANNER_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
    const val TEST_INTERSTITIAL_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"

    /** هل الإعلانات مفعّلة؟ ضع true بعد إضافة play-services-ads والمفاتيح الحقيقية. */
    const val ADS_ENABLED = false

    /** إظهار بانر داخل الحاوية المحددة (لا يفعل شيئاً إذا كانت ADS_ENABLED = false). */
    fun showBanner(context: Context, container: FrameLayout, unitId: String = TEST_BANNER_UNIT_ID) {
        if (!ADS_ENABLED) {
            Log.d(TAG, "الإعلانات غير مفعّلة — تم تخطي البانر ($unitId)")
            container.visibility = View.GONE
            return
        }
        // TODO: عند التفعيل — إنشاء AdView بوحدة إعلانية BANNER وإضافته إلى container
    }

    /** إظهار إعلان بيني (Interstitial) عند نقطة انتقال. */
    fun showInterstitial(activity: android.app.Activity, unitId: String = TEST_INTERSTITIAL_UNIT_ID) {
        if (!ADS_ENABLED) {
            Log.d(TAG, "الإعلانات غير مفعّلة — تم تخطي الإعلان البيني")
            return
        }
        // TODO: عند التفعيل — تحميل وعرض InterstitialAd
    }
}
