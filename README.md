# Internet-outage
Android WiFi Network Scanner

## البناء (Build)
```bash
./gradlew assembleDebug
# الناتج: app/build/outputs/apk/debug/app-debug.apk
```
يتطلب Android SDK مع `platforms;android-34` و `build-tools;34.0.0` (حدّد المسار في `local.properties` عبر `sdk.dir`).

## البناء السحابي عبر Codemagic (codemagic.io)
1. ارفع هذا المستودع إلى GitHub (تأكد من عدم رفع `local.properties` أو مجلدات `build/` — هي مستثناة في `.gitignore`).
2. سجّل الدخول إلى [codemagic.io](https://codemagic.io) بحساب GitHub واربط المستودع.
3. سيكتشف Codemagic ملف `codemagic.yaml` تلقائياً — اختر workflow **android-app**.
4. اضغط **Start your first build**؛ الناتج APK Debug متاح في تبويب Artifacts بعد نجاح البناء (`app/build/outputs/apk/debug/app-debug.apk`).

### بناء Release موقّع (اختياري)
- أنشئ مفتاح توقيع: `keytool -genkey -v -keystore release.keystore -alias myalias -keyalg RSA -keysize 2048 -validity 10000`
- في Codemagic UI أضف Environment variables/group باسم `android-signing` تحوي: `KEYSTORE_BASE64` (نسخة base64 من الملف)، `KEYSTORE_PASSWORD`، `KEY_ALIAS`، `KEY_PASSWORD`.
- في `codemagic.yaml` أزل التعليق عن خطوات "Decode keystore" و"Build Release APK".

## الميزات
- فحص الشبكة المحلية (ARP + Ping sweep) واكتشاف الأجهزة المتصلة بالواي فاي.
- تقدير اسم المضيف (reverse DNS) والشركة المصنّعة من بادئة OUI لعنوان MAC.
- عرض استهلاك البيانات RX/TX لهذا الجهاز وتقدير تراكمي لكل جهاز.
- شاشة تفاصيل الجهاز: المنافذ المفتوحة + الإحصائيات المخزنة محلياً.
- زر ترتيب القائمة (اسم / IP / استهلاك) وإعادة سحب للتحديث.
